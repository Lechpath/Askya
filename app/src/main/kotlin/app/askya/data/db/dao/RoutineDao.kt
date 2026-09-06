package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.GeneratedDay
import app.askya.data.entity.RoutineItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RoutineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RoutineItem): Long

    @Update
    suspend fun update(item: RoutineItem)

    @Query("DELETE FROM routine_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM routine_items ORDER BY startTime, id")
    fun observeAll(): Flow<List<RoutineItem>>

    @Query("SELECT * FROM routine_items WHERE id = :id")
    suspend fun getById(id: Long): RoutineItem?

    /** Только включённые дела: именно они разворачиваются в день. */
    @Query("SELECT * FROM routine_items WHERE enabled = 1 ORDER BY startTime, id")
    suspend fun enabled(): List<RoutineItem>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markGenerated(day: GeneratedDay)

    @Query("SELECT COUNT(*) FROM generated_days WHERE date = :date")
    suspend fun isGenerated(date: LocalDate): Int

    /**
     * Дни, уже собранные из списка, начиная с названного.
     *
     * Нужны правке списка: собранный день сам за списком не следит, и
     * заведённое сегодня дело иначе появилось бы в нём только после
     * пересборки. Прошлые дни не спрашиваются вовсе — их не трогают.
     */
    @Query("SELECT date FROM generated_days WHERE date >= :from ORDER BY date")
    suspend fun generatedFrom(from: LocalDate): List<LocalDate>
}
