package app.askya.data.repository

import androidx.room.withTransaction
import app.askya.data.db.AppDatabase
import app.askya.data.entity.GeneratedDay
import app.askya.data.entity.RoutineItem
import app.askya.data.entity.ScheduleItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Список дел — то, из чего собирается день, — и перенос отобранного в дату.
 *
 * База нужна здесь целиком, а не один DAO: перенос в день трогает две таблицы,
 * и без общей транзакции возможен день, помеченный как заполненный, но пустой.
 */
class RoutineRepository(private val db: AppDatabase) {

    private val routineDao = db.routineDao()
    private val scheduleDao = db.scheduleDao()

    fun items(): Flow<List<RoutineItem>> = routineDao.observeAll()

    suspend fun get(id: Long): RoutineItem? = routineDao.getById(id)

    suspend fun add(item: RoutineItem): Long = routineDao.insert(item)

    suspend fun save(item: RoutineItem) = routineDao.update(item)

    suspend fun delete(id: Long) = routineDao.deleteById(id)

    /**
     * Кладёт в день названные дела списка — те, что человек выбрал сам.
     *
     * Не то же, что сборка дня ([app.askya.data.repository.DayRepository]): там
     * список разворачивается целиком и одинаковые дела отсеиваются, здесь —
     * берётся отобранное по одному, ровно то и ровно столько. Выключенное в
     * списке дело сюда тоже попадает, если его выбрали: переключатель говорит,
     * чего не разворачивать самой, а не что запрещено брать руками.
     *
     * День помечается заполненным: иначе распорядок развернулся бы в него
     * целиком при следующем открытии — поверх того, что человек только что
     * отобрал вручную.
     */
    suspend fun addToDay(date: LocalDate, items: List<RoutineItem>) = db.withTransaction {
        if (items.isEmpty()) return@withTransaction
        items.forEach { item ->
            scheduleDao.insert(
                ScheduleItem(
                    date = date,
                    startTime = item.startTime,
                    endTime = item.endTime,
                    title = item.title,
                    icon = item.icon,
                )
            )
        }
        routineDao.markGenerated(GeneratedDay(date))
    }
}
