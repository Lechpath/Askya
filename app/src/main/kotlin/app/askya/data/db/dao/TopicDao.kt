package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.ScrollTopic
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(topic: ScrollTopic): Long

    @Update
    suspend fun update(topic: ScrollTopic)

    @Query("DELETE FROM scroll_topics WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM scroll_topics ORDER BY title")
    fun observeAll(): Flow<List<ScrollTopic>>

    @Query("SELECT * FROM scroll_topics ORDER BY title")
    suspend fun all(): List<ScrollTopic>

    @Query("SELECT * FROM scroll_topics WHERE id = :id")
    fun observeById(id: Long): Flow<ScrollTopic?>
}
