package app.askya.data.repository

import app.askya.data.db.dao.DeedTaskDao
import app.askya.data.entity.DeedTask
import app.askya.domain.markdown.ListInput
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Заголовок раздела в списке дела: «# Взять с собой».
 *
 * Своё правило, а не общее из [ListInput]: заголовки есть только здесь. В
 * списке Yet раздел не нужен — там сами списки и есть разделы, а вот дело в
 * дне одно, и три перечня внутри него разделять нечем.
 */
private val HEADING = Regex("""^#{1,6}\s+(.*)$""")

/**
 * Списки внутри дел дня.
 *
 * Своё хранилище, а не поле [ScheduleRepository]: строки списка переписываются
 * от каждой галочки, а само дело — раз в день, и держать их в одном потоке
 * значило бы пересобирать карточку дня на каждую отметку в чужом деле.
 */
class DeedTaskRepository(private val dao: DeedTaskDao) {

    fun tasksOf(deedId: Long): Flow<List<DeedTask>> = dao.observeOf(deedId)

    /** Все строки дня разом — по ним карточки показывают «3 из 7». */
    fun tasksOn(date: LocalDate): Flow<List<DeedTask>> = dao.observeOnDate(date)

    /** То же разовым чтением: шторка собирается в приёмнике, без подписок. */
    suspend fun tasksOnce(date: LocalDate): List<DeedTask> = dao.onDate(date)

    /** У каких дел дня список есть — это нужно пересборке дня. */
    suspend fun deedsWithTasksOn(date: LocalDate): List<Long> = dao.deedsWithTasksOn(date)

    suspend fun get(id: Long): DeedTask? = dao.getById(id)

    /**
     * Написанное в строке ложится строками: одна отправка — столько строк,
     * сколько их набрали или вставили. Разметка при этом снимается и остаётся
     * отметкой ([ListInput]) — так же, как в списке Yet: в списке хранится
     * написанное, а не то, чем его записали.
     *
     * Подпункты сюда не переносятся: список дела короткий и плоский, а уровень
     * внутри одного дела означал бы дело внутри дела — для этого в Askya есть
     * само расписание.
     *
     * Строка с решёткой впереди — «# Взять с собой» — становится заголовком
     * раздела ([DeedTask.heading]): в одном деле умещаются и то, что надо
     * сделать, и то, что надо взять или купить, и без заголовка они читаются
     * одной кашей. Знак тот же, каким заголовок набирают в заметке, и из
     * текста он снимается — как снимаются маркеры со строк списка.
     */
    suspend fun addLines(deedId: Long, source: String) {
        source.lines().forEach { raw ->
            val head = HEADING.matchEntire(raw.trim())
            if (head != null) {
                val text = head.groupValues[1].trim().removeSuffix(":").trim()
                if (text.isNotEmpty()) {
                    dao.insert(DeedTask(deedId = deedId, text = text, heading = true))
                }
                return@forEach
            }
            // По строке за раз, а не всей отправкой сразу: заголовок стоит
            // среди строк, и разобрать их одним куском значило бы потерять его
            // место в списке.
            ListInput.parse(raw).forEach { line ->
                dao.insert(DeedTask(deedId = deedId, text = line.text, done = line.done))
            }
        }
    }

    /** Заголовок не отмечают: у него нет квадрата и отмечать в нём нечего. */
    suspend fun toggle(task: DeedTask) {
        if (task.heading) return
        dao.update(task.copy(done = !task.done))
    }

    /** Отметить по номеру — так приходит нажатие из шторки уведомлений. */
    suspend fun toggle(id: Long) {
        val task = dao.getById(id) ?: return
        if (task.heading) return
        dao.update(task.copy(done = !task.done))
    }

    /** Убрать строку — в корзину на сутки. См. [ScheduleRepository.remove]. */
    suspend fun remove(id: Long) = dao.setRemoved(id, LocalDateTime.now())

    suspend fun restore(id: Long) = dao.setRemoved(id, null)

    suspend fun clearDone(deedId: Long) = dao.deleteDoneOf(deedId)

    /** Дело ушло из базы совсем — строки уходят следом. */
    suspend fun deleteOf(deedId: Long) = dao.deleteOf(deedId)

    /**
     * Уборка при запуске: пролежавшее убранным сутки и осиротевшее.
     *
     * Сироты — строки дел, которых больше нет: дело стирают очисткой дня,
     * пересборкой и уборкой корзины, и ловить каждое из этих мест значило бы
     * однажды забыть одно.
     */
    suspend fun purgeTrash() {
        dao.purge(LocalDateTime.now().minusDays(1))
        dao.purgeOrphans()
    }
}
