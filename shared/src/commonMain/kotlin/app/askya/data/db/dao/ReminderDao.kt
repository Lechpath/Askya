package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    /**
     * Всё подряд, по времени события. Раздел показывает их карточками, как
     * расписание дня, и порядок в нём — порядок дел, а не порядок звонков
     * будильника: «за полчаса» не должно уводить дело выше того, что начнётся
     * раньше него.
     */
    @Query("SELECT * FROM reminders ORDER BY eventDate, IFNULL(eventStart, time)")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    /**
     * Напоминания дел, чтобы карточка дня знала, что уже стоит. Выключенные
     * тоже: карточка их показывает — приглушённо, но показывает, — иначе
     * правка дела молча стёрла бы выключенное напоминание.
     */
    @Query("SELECT * FROM reminders WHERE itemId IS NOT NULL")
    fun observeForItems(): Flow<List<Reminder>>

    /** Напоминания названных дел — чтобы снять их вместе с самими делами. */
    @Query("SELECT * FROM reminders WHERE itemId IN (:itemIds)")
    suspend fun byItems(itemIds: List<Long>): List<Reminder>

    /** Всё, что нужно перепланировать после перезагрузки или обновления. */
    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun enabled(): List<Reminder>
}
