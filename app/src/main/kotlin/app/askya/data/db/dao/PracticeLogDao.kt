package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.PracticeLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PracticeLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: PracticeLog): Long

    @Update
    suspend fun update(log: PracticeLog)

    @Delete
    suspend fun delete(log: PracticeLog)

    @Query("SELECT * FROM practice_logs ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<PracticeLog>>

    @Query("SELECT * FROM practice_logs WHERE date >= :from ORDER BY date DESC, id DESC")
    fun observeSince(from: LocalDate): Flow<List<PracticeLog>>

    /** Названия ранее записанных практик — для подсказок при вводе. */
    @Query("SELECT DISTINCT practice FROM practice_logs ORDER BY practice")
    fun observePracticeNames(): Flow<List<String>>
}
