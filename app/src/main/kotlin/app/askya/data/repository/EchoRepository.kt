package app.askya.data.repository

import app.askya.data.db.dao.EchoDao
import app.askya.data.entity.EchoFavorite
import app.askya.data.entity.EchoPlaylist
import app.askya.data.entity.EchoPlaylistTrack
import app.askya.echo.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Плейлисты Echo. Всё, что человек собрал сам, — здесь; музыка на телефоне
 * читается отдельно, через `EchoLibrary`.
 */
class EchoRepository(private val dao: EchoDao) {

    fun playlists(): Flow<List<EchoPlaylist>> = dao.observePlaylists()

    fun tracks(playlistId: Long): Flow<List<EchoPlaylistTrack>> = dao.observeTracks(playlistId)

    /** Сколько песен в каждом плейлисте — карточка показывает это в списке. */
    fun sizes(): Flow<Map<Long, Int>> = dao.observeAllTracks()
        .map { all -> all.groupingBy { it.playlistId }.eachCount() }

    /**
     * Первая песня каждого плейлиста — по ней карточка берёт обложку.
     *
     * Своей обложки у плейлиста нет и заводить её не за чем: человек
     * складывает песни, а не рисует папке лицо. Первая сложенная — то, с чего
     * плейлист начался, и узнаётся он по ней.
     */
    fun covers(): Flow<Map<Long, EchoPlaylistTrack>> = dao.observeAllTracks().map { all ->
        all.groupBy { it.playlistId }
            .mapValues { (_, tracks) -> tracks.minByOrNull { it.position } }
            .filterValues { it != null }
            .mapValues { (_, track) -> requireNotNull(track) }
    }

    suspend fun addPlaylist(title: String): Long =
        dao.insertPlaylist(EchoPlaylist(title = title.trim()))

    suspend fun rename(playlist: EchoPlaylist, title: String) =
        dao.updatePlaylist(playlist.copy(title = title.trim()))

    /** Плейлист уходит вместе со своими строками, как список Yet. */
    suspend fun deletePlaylist(id: Long) {
        dao.deleteTracksOf(id)
        dao.deletePlaylistById(id)
    }

    suspend fun add(playlistId: Long, track: Track) {
        dao.insertTrack(
            EchoPlaylistTrack(
                playlistId = playlistId,
                uri = track.uri,
                title = track.title,
                artist = track.artist,
                albumId = track.albumId,
                durationMs = track.durationMs,
                position = dao.nextPosition(playlistId),
            ),
        )
    }

    suspend fun remove(id: Long) = dao.deleteTrackById(id)

    /**
     * Переписать порядок плейлиста целиком — в том виде, в каком его показали.
     *
     * Списком, а не перестановкой двух соседей: номера в базе бывают с
     * пропусками и повторами (строки приходили и уходили годами), и обмен
     * двух одинаковых номеров не меняет ничего. Пересчёт от нуля по списку с
     * экрана не может разойтись с тем, что человек видит.
     */
    suspend fun reorder(rows: List<EchoPlaylistTrack>) {
        rows.forEachIndexed { at, row -> dao.setPosition(row.id, at) }
    }

    /** Отмеченное — свой список, живущий рядом с плейлистами. */
    fun favorites(): Flow<List<EchoFavorite>> = dao.observeFavorites()

    /** Только ссылки: по ним строка песни решает, горит ли у неё сердце. */
    fun favoriteUris(): Flow<Set<String>> = dao.observeFavorites().map { all ->
        all.map { it.uri }.toSet()
    }

    /**
     * Отметить или снять отметку. Одним действием, а не двумя: сердце —
     * выключатель, и вызывающему незачем знать, что было до нажатия.
     */
    /**
     * Забыть песню совсем: убрать из избранного и изо всех плейлистов.
     *
     * Зовётся, когда файл удалён с телефона. Плейлист хранит подписи рядом со
     * ссылкой и потому переживает пропажу файла — но пропажу случайную, а не
     * ту, о которой человек сам попросил: оставить строку значило бы обещать
     * песню, которой больше нет.
     */
    suspend fun forget(uri: String) {
        dao.deleteFavorite(uri)
        dao.deleteTracksByUri(uri)
    }

    suspend fun toggleFavorite(track: Track, favorite: Boolean) {
        if (favorite) {
            dao.insertFavorite(
                EchoFavorite(
                    uri = track.uri,
                    title = track.title,
                    artist = track.artist,
                    albumId = track.albumId,
                    durationMs = track.durationMs,
                ),
            )
        } else {
            dao.deleteFavorite(track.uri)
        }
    }
}

/** Отмеченная дорожка — обратно в дорожку плеера. */
fun EchoFavorite.asTrack(): Track = Track(
    id = uri.hashCode().toLong(),
    uri = uri,
    title = title,
    artist = artist,
    album = "",
    albumId = albumId,
    durationMs = durationMs,
    folder = "",
)

/**
 * Строка плейлиста как дорожка для плеера.
 *
 * Плееру нужна ссылка и подписи — ровно то, что в плейлисте и хранится, так
 * что обращаться за ними в MediaStore незачем. Номер берётся свой: у записи
 * плейлиста и у файла на телефоне это разные счётчики.
 */
fun EchoPlaylistTrack.asTrack(): Track = Track(
    id = id,
    uri = uri,
    title = title,
    artist = artist,
    album = "",
    albumId = albumId,
    durationMs = durationMs,
    folder = "",
)
