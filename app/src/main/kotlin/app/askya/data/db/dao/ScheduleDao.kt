package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.ScheduleItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScheduleItem): Long

    @Update
    suspend fun update(item: ScheduleItem)

    @Delete
    suspend fun delete(item: ScheduleItem)

    /** Время лежит ISO-строкой, поэтому сортировка по колонке уже хронологическая. */
    @Query("SELECT * FROM schedule_items WHERE date = :date ORDER BY startTime, id")
    fun observeByDate(date: LocalDate): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedule_items WHERE date = :date ORDER BY startTime, id")
    suspend fun itemsOn(date: LocalDate): List<ScheduleItem>

    /**
     * Убирает из дня всё, кроме сделанного. Так пересобирается день: сделанное
     * — это запись о том, что уже произошло, и переписывать её планом нельзя.
     */
    @Query("DELETE FROM schedule_items WHERE date = :date AND done = 0")
    suspend fun deleteUndoneOn(date: LocalDate)

    @Query("SELECT * FROM schedule_items WHERE id = :id")
    suspend fun getById(id: Long): ScheduleItem?

    @Query("UPDATE schedule_items SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM schedule_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Очистить день целиком — вместе со сделанным: это осознанное «стереть». */
    @Query("DELETE FROM schedule_items WHERE date = :date")
    suspend fun deleteOn(date: LocalDate)
}
