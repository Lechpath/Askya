package app.askya.echo

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Дорожка из музыки на телефоне. */
data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    /** Папка, в которой лежит файл, — по ней собирается раздел «Папки». */
    val folder: String,
    /**
     * Жанр из тега файла. Пустой — тега нет, и это обычное дело.
     *
     * Умолчанием, потому что дорожку собирают не только из MediaStore: та, что
     * поднята из памяти «на чём остановились», знает о себе ровно то, что было
     * записано в хранилище, и жанра среди этого нет.
     *
     * Пустоту разбирают правила раскладки ([EchoRules]): жанр, которого нет в
     * файле, берётся у соседей того же исполнителя.
     */
    val genre: String = "",
)

/** Папка с музыкой: имя и всё, что в ней лежит. */
data class MusicFolder(
    val name: String,
    val tracks: List<Track>,
)

/**
 * Музыка на телефоне — через `MediaStore`.
 *
 * Своей библиотеки Echo не ведёт: файлы уже разложены и подписаны системой, а
 * второй список тех же песен пришлось бы держать в согласии с первым. Читается
 * при каждом открытии заново — телефон между открытиями пополняют.
 *
 * Рингтоны и уведомления отброшены: `is_music` в MediaStore ровно для этого, а
 * без него в плеере первым делом оказываются системные звуки.
 */
object EchoLibrary {

    suspend fun load(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val columns = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            // Жанр система отдаёт колонкой только с Android 11. Ниже он лежит
            // отдельной таблицей, и его дочитывает [genres] вторым запросом:
            // просить несуществующую колонку нельзя — запрос упадёт целиком.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
            }
            // Имя папки система считает сама начиная с Android 10; на более
            // старых его приходится вырезать из пути файла.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        val tracks = mutableListOf<Track>()

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                columns,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val place = cursor.getColumnIndex(columns.last())
                val kind = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val trackId = cursor.getLong(id)
                    tracks += Track(
                        id = trackId,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            trackId,
                        ).toString(),
                        title = cursor.getString(title) ?: "Без названия",
                        artist = cursor.getString(artist).orEmpty().takeIf { it != "<unknown>" }
                            ?: "Неизвестный исполнитель",
                        album = cursor.getString(album).orEmpty(),
                        albumId = cursor.getLong(albumId),
                        durationMs = cursor.getLong(duration),
                        folder = folderName(cursor.getString(place)),
                        genre = if (kind >= 0) cursor.getString(kind).orEmpty() else "",
                    )
                }
            }
        }

        // На Android 10 и ниже жанры дочитываются отдельной таблицей.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val known = genres(context)
            if (known.isNotEmpty()) {
                for (at in tracks.indices) {
                    val name = known[tracks[at].id] ?: continue
                    tracks[at] = tracks[at].copy(genre = name)
                }
            }
        }

        tracks
    }

    /**
     * Жанры на системах до Android 11: какой дорожке какой.
     *
     * До появления колонки `GENRE` жанр в MediaStore лежит отдельной таблицей,
     * и связь «дорожка — жанр» читается только через список участников каждого
     * жанра. Жанров на телефоне единицы, поэтому проходов немного; всё равно
     * это стоит целого второго обхода базы, и потому делается один раз при
     * чтении библиотеки, а не при каждом обращении к дорожке.
     *
     * Не получилось — пусто: без жанров разложить музыку всё равно есть чем, и
     * [EchoRules] умеет достраивать их по соседям.
     */
    private fun genres(context: Context): Map<Long, String> {
        val found = mutableMapOf<Long, String>()

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)

                while (cursor.moveToNext()) {
                    val genreId = cursor.getLong(id)
                    val title = cursor.getString(name).orEmpty()
                    if (title.isBlank()) continue

                    context.contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null,
                        null,
                        null,
                    )?.use { members ->
                        val audio = members.getColumnIndexOrThrow(
                            MediaStore.Audio.Genres.Members.AUDIO_ID,
                        )
                        while (members.moveToNext()) found[members.getLong(audio)] = title
                    }
                }
            }
        }

        return found
    }

    /**
     * Та же музыка, разложенная по папкам устройства.
     *
     * Считается из уже прочитанного списка, а не вторым запросом к MediaStore:
     * это те же самые файлы, и два прохода по базе дали бы два разных среза,
     * стоило бы что-нибудь скачаться между ними.
     */
    fun folders(tracks: List<Track>): List<MusicFolder> = tracks
        .groupBy { it.folder }
        .map { (name, inside) -> MusicFolder(name, inside) }
        .sortedBy { it.name.lowercase() }

    /**
     * Обложка альбома. Ссылка на `albumart` — то, что MediaStore ведёт сам по
     * тегам файлов; читать её умеет и старая система, и новая.
     */
    fun coverUri(albumId: Long): Uri =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    /**
     * Имя папки: на Android 10+ система отдаёт его готовым, ниже — это
     * последний каталог в пути. Пустое имя бывает у файлов, отданных чужим
     * провайдером; такие собираются в одну общую кучу.
     */
    private fun folderName(value: String?): String {
        val raw = value.orEmpty()
        if (raw.isBlank()) return "Без папки"
        if (!raw.contains('/')) return raw
        return raw.substringBeforeLast('/').substringAfterLast('/').ifBlank { "Без папки" }
    }
}

/** Длительность словами: «3:07». Часы появляются только когда они есть. */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
