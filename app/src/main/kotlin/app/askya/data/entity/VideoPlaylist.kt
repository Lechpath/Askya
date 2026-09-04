package app.askya.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Плейлист AskyaV — свой порядок роликов, собранный человеком.
 *
 * Устроен как плейлист Echo ([EchoPlaylist]) и по той же причине: система
 * раскладывает файлы по папкам, а папка — это про место на диске, а не про то,
 * что человек собирается смотреть подряд. Три серии из разных папок и один
 * фильм, отложенный «на вечер», в папках не выражаются никак.
 *
 * Заводится своей таблицей, а не общей с музыкой: у ролика нет исполнителя и
 * альбома, зато есть размер кадра и вес файла, — общая строка на двоих была бы
 * наполовину пустой у каждого из них.
 */
@Entity(tableName = "video_playlists")
data class VideoPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * Ролик в плейлисте.
 *
 * Подписи и размеры лежат рядом со ссылкой, а не спрашиваются у `MediaStore`
 * при открытии, — ровно как у дорожки Echo: плейлист должен читаться и тогда,
 * когда файл с телефона убрали. Иначе список молча укорачивается, и непонятно,
 * что из него пропало.
 *
 * [position] задаёт порядок: в плейлисте он свой, а не по дате появления
 * файла на телефоне, — ради него плейлисты и заводят.
 */
@Entity(tableName = "video_playlist_clips", indices = [Index("playlistId")])
data class VideoPlaylistClip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uri: String,
    val title: String = "",
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val position: Int = 0,
)
