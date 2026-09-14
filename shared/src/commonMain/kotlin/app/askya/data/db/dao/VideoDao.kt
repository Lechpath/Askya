package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.VideoPlaylist
import app.askya.data.entity.VideoPlaylistClip
import kotlinx.coroutines.flow.Flow

/** Плейлисты AskyaV. Устроен как [EchoDao] — те же вопросы к тем же таблицам. */
@Dao
interface VideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: VideoPlaylist): Long

    @Update
    suspend fun updatePlaylist(playlist: VideoPlaylist)

    @Query("SELECT * FROM video_playlists WHERE id = :id")
    suspend fun playlistById(id: Long): VideoPlaylist?

    @Query("DELETE FROM video_playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    @Query("SELECT * FROM video_playlists ORDER BY createdAt")
    fun observePlaylists(): Flow<List<VideoPlaylist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: VideoPlaylistClip): Long

    @Query("DELETE FROM video_playlist_clips WHERE id = :id")
    suspend fun deleteClipById(id: Long)

    @Query("DELETE FROM video_playlist_clips WHERE playlistId = :playlistId")
    suspend fun deleteClipsOf(playlistId: Long)

    @Query("SELECT * FROM video_playlist_clips WHERE playlistId = :playlistId ORDER BY position, id")
    fun observeClips(playlistId: Long): Flow<List<VideoPlaylistClip>>

    /** Все строки сразу — из них считается длина каждого плейлиста в списке. */
    @Query("SELECT * FROM video_playlist_clips")
    fun observeAllClips(): Flow<List<VideoPlaylistClip>>

    /** Куда класть следующий ролик: в конец, а не в начало. */
    @Query(
        "SELECT COALESCE(MAX(position), -1) + 1 FROM video_playlist_clips " +
            "WHERE playlistId = :playlistId",
    )
    suspend fun nextPosition(playlistId: Long): Int

    /**
     * Переименование доходит и до плейлистов.
     *
     * Подпись хранится рядом со ссылкой (см. [VideoPlaylistClip]), и без этого
     * переименованный ролик остался бы в плейлисте под старым именем — то есть
     * под двумя именами сразу, в зависимости от того, откуда на него смотреть.
     */
    @Query("UPDATE video_playlist_clips SET title = :title WHERE uri = :uri")
    suspend fun renameClips(uri: String, title: String)

    /** Файл стёрт с телефона — его строки уходят из всех плейлистов разом. */
    @Query("DELETE FROM video_playlist_clips WHERE uri = :uri")
    suspend fun deleteClipsByUri(uri: String)
}
