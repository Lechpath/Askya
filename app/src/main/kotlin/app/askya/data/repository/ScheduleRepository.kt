package app.askya.data.repository

import app.askya.data.db.dao.ScheduleDao
import app.askya.data.entity.ScheduleItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleRepository(private val dao: ScheduleDao) {

    fun itemsOn(date: LocalDate): Flow<List<ScheduleItem>> = dao.observeByDate(date)

    /**
     * Разовое чтение дня. Нужно виджету: система вызывает его на своём потоке
     * и ждёт ответа синхронно — подписываться там не на что.
     */
    suspend fun itemsOnce(date: LocalDate): List<ScheduleItem> = dao.itemsOn(date)

    /** В каких днях этого куска месяца что-то есть — для карточки месяца. */
    fun busyDates(from: LocalDate, to: LocalDate): Flow<List<LocalDate>> =
        dao.observeDatesBetween(from, to)

    suspend fun get(id: Long): ScheduleItem? = dao.getById(id)

    suspend fun add(item: ScheduleItem): Long = dao.insert(item)

    suspend fun save(item: ScheduleItem) = dao.update(item)

    suspend fun setDone(id: Long, done: Boolean) = dao.setDone(id, done)

    /**
     * Убрать дело — в корзину на сутки, а не из базы вон.
     *
     * Подтверждение «вы уверены?» не отменяет ошибку, оно перекладывает её на
     * человека, который торопится. Возврат отменяет её по-настоящему, и потому
     * подтверждений у убирания больше нет.
     */
    suspend fun remove(id: Long) = dao.setRemoved(id, LocalDateTime.now())

    /** Вернуть убранное — то, что предлагает полоска внизу экрана. */
    suspend fun restore(id: Long) = dao.setRemoved(id, null)

    /** Выбросить пролежавшее в корзине сутки. Зовётся при запуске приложения. */
    suspend fun purgeTrash() = dao.purge(LocalDateTime.now().minusDays(1))

    suspend fun delete(id: Long) = dao.deleteById(id)

    /**
     * Стереть день целиком, включая сделанное.
     *
     * Отметка в `generated_days` не снимается: без неё распорядок развернулся
     * бы в этот день снова при следующем открытии, и очистка не пережила бы
     * даже свайпа на соседний день и обратно.
     */
    suspend fun clearDay(date: LocalDate) = dao.deleteOn(date)

    /**
     * Год для «Прожитого»: название, день и отметка по каждому делу.
     *
     * Разовый ответ, а не поток: страницу открывают, смотрят и уходят, а год
     * под подпиской пересчитывался бы на каждую галочку в сегодняшнем дне.
     */
    suspend fun lived(from: LocalDate, to: LocalDate) = dao.lived(from, to)
}
