package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.ScheduleItem
import app.askya.domain.lived.LivedRow
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface ScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScheduleItem): Long

    @Update
    suspend fun update(item: ScheduleItem)

    @Delete
    suspend fun delete(item: ScheduleItem)

    /** Время лежит ISO-строкой, поэтому сортировка по колонке уже хронологическая. */
    @Query("SELECT * FROM schedule_items WHERE date = :date AND removedAt IS NULL ORDER BY startTime, id")
    fun observeByDate(date: LocalDate): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedule_items WHERE date = :date AND removedAt IS NULL ORDER BY startTime, id")
    suspend fun itemsOn(date: LocalDate): List<ScheduleItem>

    /**
     * То же, но вместе с убранным в корзину.
     *
     * Спрашивает один — дело списка, которое само встаёт в день
     * ([app.askya.data.repository.DayRepository.ensureStanding]). Убранное
     * из дня дело для него значит «сегодня не надо»: не увидев корзины, оно
     * поставило бы его обратно через секунду после того, как его убрали, и
     * вычеркнуть такое дело из одного дня стало бы нельзя вовсе.
     */
    @Query("SELECT * FROM schedule_items WHERE date = :date ORDER BY startTime, id")
    suspend fun allOn(date: LocalDate): List<ScheduleItem>

    /**
     * Убирает из дня всё, кроме сделанного и того, у чего есть свой список.
     *
     * Так пересобирается день. Сделанное — это запись о том, что уже
     * произошло, и переписывать её планом нельзя. Дело со списком — то же
     * самое, только незаконченное: человек своими руками написал в «Работу»
     * четыре задачи и половину отметил, и стереть это ради того, чтобы
     * поставить на то же место пустую «Работу» из списка дел, значило бы
     * потерять сделанную работу вместе с планом.
     *
     * Подзапросом, а не списком номеров: `IN ()` с пустым списком SQLite не
     * понимает, а день без единого списка — обычное дело.
     */
    @Query(
        "DELETE FROM schedule_items WHERE date = :date AND done = 0 AND removedAt IS NULL " +
            "AND id NOT IN (SELECT deedId FROM deed_tasks WHERE removedAt IS NULL)",
    )
    suspend fun deleteUndoneOn(date: LocalDate)

    @Query("SELECT * FROM schedule_items WHERE id = :id")
    suspend fun getById(id: Long): ScheduleItem?

    @Query("UPDATE schedule_items SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM schedule_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Дни, в которых что-то стоит, — для карточки месяца.
     *
     * Возвращаются одни даты, а не сами дела: календарю нужно знать про день
     * ровно одно — пустой он или нет, — а тащить ради точки под числом все
     * дела месяца значило бы прочитать сотню строк, чтобы посчитать тридцать
     * точек.
     *
     * Дата лежит строкой ISO, и `BETWEEN` по ней работает как по дате: у
     * такой записи порядок букв совпадает с порядком дней.
     */
    @Query("SELECT DISTINCT date FROM schedule_items WHERE date BETWEEN :from AND :to AND removedAt IS NULL")
    fun observeDatesBetween(from: LocalDate, to: LocalDate): Flow<List<LocalDate>>

    /** Очистить день целиком — вместе со сделанным: это осознанное «стереть». */
    @Query("DELETE FROM schedule_items WHERE date = :date")
    suspend fun deleteOn(date: LocalDate)

    /**
     * Всё, что нужно «Прожитому», одним запросом: название, день и отметка.
     *
     * Одним, а не пятью: год — это несколько тысяч строк, и посчитать по ним
     * череды, доли и провалы в Kotlin дешевле, чем гонять пять разных GROUP BY.
     * Заодно все правила остаются чистыми и покрываются тестами — на телефоне
     * их не проверить.
     */
    @Query(
        "SELECT title, date, done FROM schedule_items " +
            "WHERE date BETWEEN :from AND :to AND removedAt IS NULL",
    )
    suspend fun lived(from: LocalDate, to: LocalDate): List<LivedRow>

    /** Убрать в корзину: дело остаётся, но день его не показывает. */
    @Query("UPDATE schedule_items SET removedAt = :at WHERE id = :id")
    suspend fun setRemoved(id: Long, at: LocalDateTime?)

    /** Выбросить всё, что пролежало убранным дольше суток. */
    @Query("DELETE FROM schedule_items WHERE removedAt IS NOT NULL AND removedAt < :before")
    suspend fun purge(before: LocalDateTime)
}
