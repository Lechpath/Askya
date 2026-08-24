package app.askya.data.repository

import app.askya.data.db.dao.ScheduleDao
import app.askya.data.entity.ScheduleItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class ScheduleRepository(private val dao: ScheduleDao) {

    fun itemsOn(date: LocalDate): Flow<List<ScheduleItem>> = dao.observeByDate(date)

    /**
     * Разовое чтение дня. Нужно виджету: система вызывает его на своём потоке
     * и ждёт ответа синхронно — подписываться там не на что.
     */
    suspend fun itemsOnce(date: LocalDate): List<ScheduleItem> = dao.itemsOn(date)

    suspend fun get(id: Long): ScheduleItem? = dao.getById(id)

    suspend fun add(item: ScheduleItem): Long = dao.insert(item)

    suspend fun save(item: ScheduleItem) = dao.update(item)

    suspend fun setDone(id: Long, done: Boolean) = dao.setDone(id, done)

    suspend fun delete(id: Long) = dao.deleteById(id)

    /**
     * Стереть день целиком, включая сделанное.
     *
     * Отметка в `generated_days` не снимается: без неё распорядок развернулся
     * бы в этот день снова при следующем открытии, и очистка не пережила бы
     * даже свайпа на соседний день и обратно.
     */
    suspend fun clearDay(date: LocalDate) = dao.deleteOn(date)
}
