package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.Bridge
import app.askya.domain.model.BlockIcon
import kotlinx.coroutines.flow.Flow

@Dao
interface BridgeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bridge: Bridge): Long

    @Update
    suspend fun update(bridge: Bridge)

    @Delete
    suspend fun delete(bridge: Bridge)

    @Query("SELECT * FROM bridges ORDER BY name")
    fun observeAll(): Flow<List<Bridge>>

    @Query("SELECT * FROM bridges ORDER BY name")
    suspend fun all(): List<Bridge>

    @Query("SELECT * FROM bridges WHERE id = :id")
    suspend fun byId(id: Long): Bridge?

    /**
     * Мост, подключённый к знаку. Знаков меньше, чем мостов быть не может:
     * один знак — один мост, иначе «какой из двух» пришлось бы спрашивать в
     * момент, когда человек просто нажал на дело.
     */
    @Query("SELECT * FROM bridges WHERE icon = :icon LIMIT 1")
    suspend fun byIcon(icon: BlockIcon): Bridge?

    @Query("UPDATE bridges SET usedAt = :at WHERE id = :id")
    suspend fun markUsed(id: Long, at: Long)
}
