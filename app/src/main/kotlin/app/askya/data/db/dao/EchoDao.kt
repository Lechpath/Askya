package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.EchoFavorite
import app.askya.data.entity.EchoPlaylist
import app.askya.data.entity.EchoPlaylistTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface EchoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: EchoPlaylist): Long

    @Update
    suspend fun updatePlaylist(playlist: EchoPlaylist)

    @Query("DELETE FROM echo_playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    @Query("SELECT * FROM echo_playlists ORDER BY createdAt")
    fun observePlaylists(): Flow<List<EchoPlaylist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: EchoPlaylistTrack): Long

    @Query("DELETE FROM echo_playlist_tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    @Query("DELETE FROM echo_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksOf(playlistId: Long)

    @Query("SELECT * FROM echo_playlist_tracks WHERE playlistId = :playlistId ORDER BY position, id")
    fun observeTracks(playlistId: Long): Flow<List<EchoPlaylistTrack>>

    /** Все дорожки сразу — из них считается длина каждого плейлиста в списке. */
    @Query("SELECT * FROM echo_playlist_tracks")
    fun observeAllTracks(): Flow<List<EchoPlaylistTrack>>

    /**
     * Переставить строку плейлиста на другое место.
     *
     * Отдельным запросом на каждую строку, а не одним на список: порядок в
     * плейлисте меняют по одной перестановке за раз, и строк там десятки, а не
     * тысячи. Собственный SQL ради этого был бы дороже, чем сам порядок.
     */
    @Query("UPDATE echo_playlist_tracks SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    /** Куда класть следующую: в конец, а не в начало. */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM echo_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: EchoFavorite)

    @Query("DELETE FROM echo_favorites WHERE uri = :uri")
    suspend fun deleteFavorite(uri: String)

    /** Убрать песню изо всех плейлистов разом — так уходит удалённый файл. */
    @Query("DELETE FROM echo_playlist_tracks WHERE uri = :uri")
    suspend fun deleteTracksByUri(uri: String)

    /** Отмеченное — сверху свежее: избранное листают с последнего. */
    @Query("SELECT * FROM echo_favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<EchoFavorite>>
}
