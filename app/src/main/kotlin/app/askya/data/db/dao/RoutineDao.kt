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

    @Query("DELETE FROM routine_items")
    suspend fun clear()

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
}
