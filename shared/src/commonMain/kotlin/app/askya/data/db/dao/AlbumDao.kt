package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.ImageAlbum
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: ImageAlbum): Long

    @Update
    suspend fun update(album: ImageAlbum)

    @Query("DELETE FROM image_albums WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** По времени заведения, а не по названию: альбомы помнят по порядку. */
    @Query("SELECT * FROM image_albums ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImageAlbum>>

    @Query("SELECT * FROM image_albums WHERE id = :id")
    fun observeById(id: Long): Flow<ImageAlbum?>
}
