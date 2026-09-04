package app.askya.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Что сейчас пишется: идёт ли запись, сколько она длится и насколько громко в
 * микрофон говорят.
 *
 * [level] — от 0 до 1, и нужен он ровно для одного: чтобы человек видел, что
 * его слышно. Полоска, которая дышит от голоса, отвечает на «а оно вообще
 * пишет?» раньше, чем закончится запись, — а красная точка не отвечает на это
 * никак.
 */
data class Recording(
    val going: Boolean = false,
    val seconds: Int = 0,
    val level: Float = 0f,
)

/**
 * Диктофон Askya — тот, которым наговаривают заметку в Scroll.
 *
 * ## Системный `MediaRecorder`, и ничего больше
 *
 * Он входит в Android, пишет AAC в mp4 и умеет всё, что нужно голосовой
 * заметке. Библиотеки записи звука начинаются с собственного кодека и
 * заканчиваются несколькими мегабайтами ради того же файла — по тому же
 * правилу, по которому музыку здесь играет `MediaPlayer`, а не Media3.
 *
 * ## Живёт в контейнере, а не в экране
 *
 * Как плеер и как идущая пробежка: запись не должна обрываться от того, что
 * экран пересобрался. Но, в отличие от них, она **не переживает уход из
 * приложения**: службы в шторке у диктофона нет и не будет. Askya, ушедшая в
 * фон с открытым микрофоном, — это ровно то приложение, каким она быть не
 * хочет, и лучше потерять полминуты записи, чем завести такую привычку.
 * Поэтому уход с экрана запись останавливает — см. `VoiceScreen`.
 *
 * ## Сорок минут — потолок
 *
 * Не ограничение формата, а ограничение смысла: заметка, которую наговорили
 * дольше, — это уже не заметка, а лекция, и ей место в файле, положенном на
 * полку. Дойдя до потолка, запись сама останавливается и сохраняется: обрывать
 * наговорённое без спроса нельзя.
 */
class VoiceRecorder(private val context: Context) {

    private val _state = MutableStateFlow(Recording())
    val state: StateFlow<Recording> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var pending: PendingVoice? = null
    private var startedAt = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val going = recorder ?: return
            // Громкость спрашивается у самого рекордера, а не считается из
            // байтов: считать их пришлось бы своим потоком записи, а он у
            // `MediaRecorder` внутри.
            val amplitude = runCatching { going.maxAmplitude }.getOrDefault(0)
            _state.value = Recording(
                going = true,
                seconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt(),
                // Корень, а не сама доля: слух логарифмичен, и на линейной
                // шкале обычная речь болталась бы у самого низа полоски.
                level = kotlin.math.sqrt((amplitude / MAX_AMPLITUDE).coerceIn(0f, 1f)),
            )
            if (_state.value.seconds >= LIMIT_SECONDS) {
                stop()
                return
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    /**
     * Начать запись. Отвечает тем, началась ли она: микрофон бывает занят
     * звонком или другим приложением, и молчать об этом нельзя.
     *
     * Файл заводится до `prepare`, потому что писать рекордер будет прямо в
     * него — дескриптором. Не завёлся файл (нет места) — записи не начинаем
     * вовсе: диктофон, который пишет в никуда, хуже неработающего.
     */
    fun start(place: PendingVoice): Boolean {
        if (recorder != null) return false

        val made = runCatching {
            val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            fresh.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // Речь, а не музыка: моно и 64 кбит/с при 44,1 кГц. Стерео с
                // одного микрофона — это два одинаковых канала и файл вдвое.
                setAudioChannels(1)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(64_000)
                setOutputFile(place.fileDescriptor)
                prepare()
                start()
            }
        }.getOrNull()

        if (made == null) {
            place.cancel()
            return false
        }

        recorder = made
        pending = place
        startedAt = System.currentTimeMillis()
        _state.value = Recording(going = true)
        handler.post(tick)
        return true
    }

    /**
     * Закончить запись и отдать [Recorded] — ссылку на файл и его длину.
     *
     * `null` означает, что записывать было нечего: рекордер, остановленный
     * раньше первого кадра, оставляет пустой файл, а пустая заметка в списке —
     * это мусор, который потом убирают руками.
     */
    fun stop(): Recorded? {
        val going = recorder ?: return null
        val place = pending
        handler.removeCallbacks(tick)

        val milliseconds = System.currentTimeMillis() - startedAt
        val written = runCatching {
            going.stop()
            true
        }.getOrDefault(false)
        runCatching { going.release() }

        recorder = null
        pending = null
        _state.value = Recording()

        if (place == null) return null
        if (!written || milliseconds < LEAST_MS) {
            place.cancel()
            return null
        }
        return Recorded(uri = place.done(), durationMs = milliseconds)
    }

    /** Бросить запись совсем: файла не остаётся. */
    fun cancel() {
        val going = recorder ?: return
        handler.removeCallbacks(tick)
        runCatching { going.stop() }
        runCatching { going.release() }
        pending?.cancel()
        recorder = null
        pending = null
        _state.value = Recording()
    }

    private companion object {
        /** Столько отдаёт `maxAmplitude` на пределе — 16-битный звук. */
        const val MAX_AMPLITUDE = 32_767f
        const val TICK_MS = 120L
        const val LIMIT_SECONDS = 40 * 60

        /**
         * Короче этого записи не бывает: столько длится промах пальцем по
         * кнопке. Файл в полсекунды — не заметка, а след нажатия.
         */
        const val LEAST_MS = 700L
    }
}

/** Готовая запись: где лежит файл и сколько он длится. */
data class Recorded(val uri: String, val durationMs: Long)
