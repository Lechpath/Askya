package app.askya.data.repository

import app.askya.data.db.dao.DeedTaskDao
import app.askya.data.entity.DeedTask
import app.askya.domain.markdown.ListInput
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

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
     */
    suspend fun addLines(deedId: Long, source: String) {
        ListInput.parse(source).forEach { line ->
            dao.insert(DeedTask(deedId = deedId, text = line.text, done = line.done))
        }
    }

    suspend fun toggle(task: DeedTask) = dao.update(task.copy(done = !task.done))

    /** Отметить по номеру — так приходит нажатие из шторки уведомлений. */
    suspend fun toggle(id: Long) {
        val task = dao.getById(id) ?: return
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
