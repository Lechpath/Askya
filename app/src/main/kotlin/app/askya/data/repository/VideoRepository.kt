package app.askya.data.repository

import app.askya.data.db.dao.VideoDao
import app.askya.data.entity.VideoPlaylist
import app.askya.data.entity.VideoPlaylistClip
import app.askya.video.Clip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Плейлисты AskyaV. Всё, что человек сложил сам, — здесь; видео на телефоне
 * читается отдельно, через `VideoLibrary`.
 */
class VideoRepository(private val dao: VideoDao) {

    fun playlists(): Flow<List<VideoPlaylist>> = dao.observePlaylists()

    fun clips(playlistId: Long): Flow<List<VideoPlaylistClip>> = dao.observeClips(playlistId)

    /** Сколько роликов в каждом плейлисте — карточка показывает это в списке. */
    fun sizes(): Flow<Map<Long, Int>> = dao.observeAllClips()
        .map { all -> all.groupingBy { it.playlistId }.eachCount() }

    /**
     * Сколько времени в каждом плейлисте.
     *
     * Часы и минуты, а не только число строк: у видео «пять роликов» может
     * значить и десять минут, и целый вечер, — а плейлист и заводят под вечер.
     */
    fun lengths(): Flow<Map<Long, Long>> = dao.observeAllClips()
        .map { all -> all.groupBy { it.playlistId }.mapValues { (_, rows) -> rows.sumOf { it.durationMs } } }

    /**
     * Первый ролик каждого плейлиста — по нему карточка берёт кадр.
     *
     * Своей картинки у плейлиста нет и заводить её незачем: человек складывает
     * фильмы, а не рисует папке лицо. Первый сложенный — то, с чего плейлист
     * начался, и узнаётся он по нему.
     */
    fun covers(): Flow<Map<Long, VideoPlaylistClip>> = dao.observeAllClips().map { all ->
        all.groupBy { it.playlistId }
            .mapValues { (_, rows) -> rows.minByOrNull { it.position } }
            .filterValues { it != null }
            .mapValues { (_, row) -> requireNotNull(row) }
    }

    suspend fun addPlaylist(title: String): Long =
        dao.insertPlaylist(VideoPlaylist(title = title.trim()))

    suspend fun rename(id: Long, title: String) {
        val playlist = dao.playlistById(id) ?: return
        dao.updatePlaylist(playlist.copy(title = title.trim()))
    }

    /** Плейлист уходит вместе со своими строками, как список Yet. */
    suspend fun deletePlaylist(id: Long) {
        dao.deleteClipsOf(id)
        dao.deletePlaylistById(id)
    }

    suspend fun add(playlistId: Long, clip: Clip) {
        dao.insertClip(
            VideoPlaylistClip(
                playlistId = playlistId,
                uri = clip.uri,
                title = clip.title,
                durationMs = clip.durationMs,
                sizeBytes = clip.sizeBytes,
                width = clip.width,
                height = clip.height,
                position = dao.nextPosition(playlistId),
            ),
        )
    }

    suspend fun remove(id: Long) = dao.deleteClipById(id)

    /** Ролик переименовали — подпись меняется и во всех плейлистах разом. */
    suspend fun renamed(uri: String, title: String) = dao.renameClips(uri, title)

    /**
     * Файл стёрт с телефона. Ссылка на пропавший файл — строка, которая молча
     * не играет, поэтому она уходит из всех плейлистов, а не ждёт, пока на неё
     * наткнутся.
     */
    suspend fun forget(uri: String) = dao.deleteClipsByUri(uri)
}

/**
 * Строка плейлиста как ролик библиотеки.
 *
 * Плееру и списку нужны ссылка, подпись и размеры — ровно то, что в плейлисте
 * и хранится, так что обращаться за ними в `MediaStore` незачем: файла там
 * может уже и не быть. Номер берётся свой: у записи плейлиста и у файла на
 * телефоне это разные счётчики. Папка не хранится — в плейлисте она ничего не
 * значит: он и собран поперёк папок.
 */
fun VideoPlaylistClip.asClip(): Clip = Clip(
    id = id,
    uri = uri,
    title = title,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    folder = "",
    addedAt = 0,
)
