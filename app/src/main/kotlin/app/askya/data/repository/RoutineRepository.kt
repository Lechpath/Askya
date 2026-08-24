package app.askya.data.repository

import androidx.room.withTransaction
import app.askya.data.db.AppDatabase
import app.askya.data.entity.GeneratedDay
import app.askya.data.entity.RoutineItem
import app.askya.data.entity.ScheduleItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Распорядок обычного дня и разворачивание его в конкретную дату.
 *
 * База нужна здесь целиком, а не один DAO: заполнение дня трогает две таблицы,
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
     * Заменяет распорядок целиком — так применяется разбор рассказа о себе.
     * Распорядок производен от текста, поэтому дописывать к старому нельзя:
     * иначе после второго разбора дела задвоятся.
     */
    suspend fun replaceAll(items: List<RoutineItem>) = db.withTransaction {
        routineDao.clear()
        items.forEach { routineDao.insert(it) }
    }

    /**
     * Заполняет день по распорядку, если он ещё не заполнялся. Повторный вызов
     * ничего не делает — иначе удалённые вручную дела возвращались бы при
     * каждом открытии дня.
     */
    suspend fun ensureGenerated(date: LocalDate) {
        if (routineDao.isGenerated(date) > 0) return
        fill(date, force = false)
    }

    /**
     * Заполнение по кнопке. В отличие от [ensureGenerated] работает и по уже
     * помеченному дню — это осознанное действие пользователя, а не автоматика.
     */
    suspend fun fillFromRoutine(date: LocalDate) = fill(date, force = true)

    /**
     * Кладёт в день названные дела списка — те, что человек выбрал сам.
     *
     * Не то же, что [fillFromRoutine]: там разворачивается весь список разом,
     * здесь — отобранное по одному. Выключенное в списке дело сюда тоже
     * попадает, если его выбрали: переключатель говорит, чего не разворачивать
     * самой, а не что запрещено брать руками.
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

    private suspend fun fill(date: LocalDate, force: Boolean) {
        db.withTransaction {
            if (!force && routineDao.isGenerated(date) > 0) return@withTransaction
            val items = routineDao.enabled()
            // Пустой распорядок не считается заполнением: иначе день, открытый
            // до того, как распорядок завели, остался бы пустым навсегда.
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
}
