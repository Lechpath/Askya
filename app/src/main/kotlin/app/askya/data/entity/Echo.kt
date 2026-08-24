package app.askya.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Плейлист AskyaEcho — собранный человеком, а не системой.
 *
 * Системные плейлисты MediaStore намеренно не читаются: с Android 11 писать в
 * них приложению уже нельзя, и раздел «Плейлисты» получился бы витриной без
 * возможности что-либо в неё положить.
 */
@Entity(tableName = "echo_playlists")
data class EchoPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * Дорожка в плейлисте.
 *
 * Подписи хранятся рядом со ссылкой, а не берутся из MediaStore при открытии:
 * плейлист должен читаться и тогда, когда файл удалили с телефона, — иначе
 * список молча укорачивается, и непонятно, что из него пропало.
 *
 * [position] задаёт порядок: в плейлисте он свой, а не алфавитный, — ради него
 * плейлисты и заводят.
 */
@Entity(tableName = "echo_playlist_tracks", indices = [Index("playlistId")])
data class EchoPlaylistTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uri: String,
    val title: String = "",
    val artist: String = "",
    val albumId: Long = 0,
    val durationMs: Long = 0,
    val position: Int = 0,
)

/**
 * Отмеченная дорожка — «избранное» плеера.
 *
 * Ключ — ссылка на файл, а не номер: отметку ставят на песню, а не на строку
 * списка, и вторая отметка той же песни должна попадать в ту же запись. Как и
 * в плейлисте, подписи лежат рядом со ссылкой: отмеченное должно читаться и
 * после того, как файл с телефона убрали.
 */
@Entity(tableName = "echo_favorites")
data class EchoFavorite(
    @PrimaryKey val uri: String,
    val title: String = "",
    val artist: String = "",
    val albumId: Long = 0,
    val durationMs: Long = 0,
    val addedAt: LocalDateTime = LocalDateTime.now(),
)
