package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.askya.data.entity.CheckIn
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CheckInDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: CheckIn): Long

    @Delete
    suspend fun delete(checkIn: CheckIn)

    @Query("SELECT * FROM check_ins ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<CheckIn>>

    @Query("SELECT * FROM check_ins WHERE date = :date ORDER BY createdAt DESC")
    fun observeByDate(date: LocalDate): Flow<List<CheckIn>>

    /** Последний чек-ин дня: состояние к вечеру важнее утреннего. */
    @Query("SELECT * FROM check_ins WHERE date = :date ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestOn(date: LocalDate): CheckIn?
}
