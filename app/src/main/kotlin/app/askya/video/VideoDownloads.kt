package app.askya.video

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Одна начатая закачка — и всё, что о ней стоит сказать.
 *
 * [total] = 0 значит «сколько всего, неизвестно»: так отвечают серверы, не
 * сказавшие длину, и полоска в этом случае не рисуется вовсе — врущая полоска
 * хуже её отсутствия.
 */
data class VideoDownload(
    val id: Long,
    val link: String,
    /** Что сейчас происходит: «Жду очереди», «Ищу видео на странице», «Качаю». */
    val note: String,
    val done: Long,
    val total: Long,
    /** Куски, а не байты: у потока меряется не вес, а число кусков. */
    val counted: Boolean = false,
    val ended: Boolean = false,
    val ok: Boolean = false,
    /** Имя готового файла или объяснение неудачи. */
    val word: String = "",
) {
    val part: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
}

/**
 * Закачки раздела: то, что живёт дольше открытого окна.
 *
 * ## Почему в контейнере приложения, а не в экране
 *
 * Фильм качается минутами, а иногда и дольше. Закачка, живущая в разметке,
 * оборвалась бы от закрытия лаборатории — и человек, решивший посмотреть,
 * что там уже есть в списке, потерял бы половину скачанного. Поэтому она
 * живёт там же, где плеер: на весь век приложения.
 *
 * Приложение при этом не служба: закрытое, оно закачку не продолжает, и
 * обещать обратное было бы враньём. Служба с уведомлением решала бы это, но
 * стоила бы разрешения на уведомления и постоянного значка в шторке — за
 * право докачать полтора файла в год.
 *
 * ## По одной за раз
 *
 * Не потому, что нельзя иначе, а потому, что так быстрее: два фильма разом
 * делят одну и ту же сеть пополам и приходят вдвое позже каждый. Ждущие
 * стоят в очереди и говорят об этом словом, а не пустой полоской.
 */
class VideoDownloads(private val context: Context, private val store: VideoStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Mutex()
    private val jobs = mutableMapOf<Long, Job>()

    private val _state = MutableStateFlow<List<VideoDownload>>(emptyList())
    val state: StateFlow<List<VideoDownload>> = _state.asStateFlow()

    /** Начать закачку. Один и тот же адрес дважды в очередь не встаёт. */
    fun start(link: String) {
        val clean = link.trim()
        if (clean.isEmpty()) return
        if (_state.value.any { it.link == clean && !it.ended }) return

        val id = System.currentTimeMillis()
        _state.value = _state.value + VideoDownload(
            id = id,
            link = clean,
            note = "Жду очереди",
            done = 0,
            total = 0,
        )

        jobs[id] = scope.launch {
            gate.withLock {
                change(id) { it.copy(note = "Начинаю") }

                // Полоска трогается не на каждый прочитанный кусок: за минуту
                // их тысячи, и перерисовывать ради каждого весь экран
                // означало бы тратить на показ больше, чем на саму закачку.
                var shown = 0L

                val done = VideoFetch.grab(
                    context = context,
                    store = store,
                    link = clean,
                    onNote = { note -> change(id) { it.copy(note = note) } },
                    onStep = { made, total ->
                        val counted = total in 1..PIECES_CEILING
                        if (counted || made - shown >= STEP_BYTES || made == total) {
                            shown = made
                            change(id) {
                                it.copy(done = made, total = total, counted = counted)
                            }
                        }
                    },
                )

                change(id) {
                    when (done) {
                        is EditResult.Done -> it.copy(
                            note = "Готово",
                            ended = true,
                            ok = true,
                            word = done.name,
                        )

                        is EditResult.Failed -> it.copy(
                            note = "Не вышло",
                            ended = true,
                            ok = false,
                            word = done.reason,
                        )
                    }
                }
            }
            jobs.remove(id)
        }
    }

    /** Бросить начатое. Недописанный файл убирается сам — см. `PendingVideo`. */
    fun stop(id: Long) {
        jobs.remove(id)?.cancel()
        change(id) { it.copy(note = "Брошено", ended = true, ok = false, word = "Закачка отменена") }
    }

    /** Убрать строчку из списка. Скачанный файл при этом остаётся на месте. */
    fun forget(id: Long) {
        jobs.remove(id)?.cancel()
        _state.value = _state.value.filterNot { it.id == id }
    }

    private fun change(id: Long, edit: (VideoDownload) -> VideoDownload) {
        _state.value = _state.value.map { if (it.id == id) edit(it) else it }
    }

    private companion object {
        /** Через сколько прочитанного трогается полоска. */
        const val STEP_BYTES = 512L * 1024

        /**
         * Выше этого числа «всего» — это байты, ниже — куски.
         *
         * Различать их иначе значило бы тащить через все слои ещё одно поле
         * ради того, что и так видно: кусков у потока бывает несколько тысяч,
         * а байтов у файла — миллионы.
         */
        const val PIECES_CEILING = 100_000L
    }
}
