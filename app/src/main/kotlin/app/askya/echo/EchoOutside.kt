package app.askya.echo

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import app.askya.app.FileOpenRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Песня, принесённая снаружи: из проводника, из «Загрузок», из мессенджера.
 *
 * ## Почему это не просмотр
 *
 * Книгу и картинку Askya показывает разовым листом поверх приложения и
 * забывает, как только его закрыли. С музыкой так нельзя: её включают и
 * уходят — свернуть приложение, погасить экран, положить телефон в карман, — а
 * лист поверх всего живёт ровно до того мига, как из него вышли.
 *
 * Поэтому песня идёт не в лист, а в сам плеер: становится очередью, начинает
 * играть и поднимает службу переднего плана ([EchoService]) — ту самую, что
 * держит процесс живым и рисует карточку в шторке. Человек оказывается в
 * AskyaEcho, где всё остальное: очередь, обложка, «дальше», сон.
 *
 * ## Про форматы
 *
 * Играет системный `MediaPlayer`, и что именно он открывает — дело телефона, а
 * не Askya: `.wma` и `.ape` на большинстве устройств не откроются, хотя в
 * списке принимаемых стоят. Стоят они там нарочно: отказаться показаться в
 * «Открыть с помощью» значит не дать человеку и попробовать, а не открывшийся
 * файл здесь не пропадает молча — о нём говорят словами и предлагают отдать
 * его другому приложению.
 */
sealed interface OutsideAudio {

    /** Заиграло. [count] — сколько дорожек встало в очередь. */
    data class Playing(val count: Int) : OutsideAudio

    /** Не заиграло, и вот почему — этими словами и покажут. */
    data class Failed(val reason: String) : OutsideAudio
}

/**
 * Отдать плееру то, что принесли снаружи.
 *
 * Плейлист разбирается в очередь, одиночный файл становится очередью из одного.
 * Очередь заменяется, а не дополняется: человек нажал этот файл и ждёт, что
 * заиграет он, а не то, что стояло в плеере с утра.
 */
suspend fun playFromOutside(
    context: Context,
    player: EchoPlayer,
    uri: String,
    name: String,
): OutsideAudio {
    val tracks = withContext(Dispatchers.IO) { tracksOf(context, uri, name) }

    if (tracks.isEmpty()) {
        val playlist = FileOpenRouter.extensionOf(name) in FileOpenRouter.PLAYLISTS
        return OutsideAudio.Failed(
            if (playlist) {
                "В этом списке не нашлось ни одного файла, который лежал бы на телефоне. " +
                    "Списки песен ссылаются на файлы путями, и путь, записанный на чужом " +
                    "устройстве, здесь никуда не ведёт."
            } else {
                "Этот файл не открылся."
            },
        )
    }

    player.play(tracks, tracks.first())

    // Плеер не рассказывает об отказе — он просто остаётся без дорожки
    // (`EchoPlayer.start`). Отсутствие дорожки сразу после `play` и означает,
    // что `MediaPlayer` не взялся за файл.
    if (player.state.value.track == null) {
        return OutsideAudio.Failed(
            "Телефон не открыл этот файл. Так бывает с форматами, которых " +
                "системный проигрыватель не знает, — «.wma», «.ape» и подобными.",
        )
    }

    return OutsideAudio.Playing(tracks.size)
}

/** Что играть: сам файл или всё, на что ссылается список. */
private fun tracksOf(context: Context, uri: String, name: String): List<Track> {
    val ext = FileOpenRouter.extensionOf(name)
    if (ext !in FileOpenRouter.PLAYLISTS) return listOf(trackOf(context, uri, name))

    val text = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull().orEmpty()
    if (text.isBlank()) return emptyList()

    val beside = folderOf(context, uri)
    val paths = if (ext == ".cue") cueFiles(text) else m3uEntries(text)

    return paths
        .mapNotNull { entry -> resolve(entry, beside) }
        .map { resolved -> trackOf(context, resolved, resolved.substringAfterLast('/')) }
}

/**
 * Строки списка `.m3u`: всё, кроме пустого и, что начинается с решётки.
 *
 * Решёткой в m3u помечены и служебные строки (`#EXTM3U`, `#EXTINF`), и
 * обычные примечания. Ни те, ни другие не файлы.
 */
private fun m3uEntries(text: String): List<String> = text
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .toList()

/**
 * Файл, названный в `.cue`.
 *
 * Лист `.cue` описывает не список файлов, а разметку **внутри одного** — где в
 * длинной записи начинается каждая песня. Askya читает из него только имя
 * самого файла и играет его целиком: разрезать одну запись на дорожки плеер
 * не умеет, и делать вид, что умеет, хуже, чем сыграть как есть.
 */
private fun cueFiles(text: String): List<String> = text
    .lineSequence()
    .map { it.trim() }
    .filter { it.startsWith("FILE ", ignoreCase = true) }
    .mapNotNull { line ->
        val quoted = line.substringAfter('"', "").substringBeforeLast('"', "")
        quoted.takeIf { it.isNotBlank() }
            ?: line.removePrefix("FILE ").trim().substringBeforeLast(' ').takeIf { it.isNotBlank() }
    }
    .toList()

/**
 * Строка списка — в ссылку, которую можно открыть.
 *
 * Три случая. Готовая ссылка идёт как есть. Полный путь от корня —
 * проверяется, что файл на месте: список, принесённый с чужого компьютера,
 * ссылается на пути, которых здесь нет, и молча заведённая мёртвая дорожка
 * хуже, чем короткая очередь.
 *
 * Относительный путь считается от папки самого списка — и потому работает лишь
 * тогда, когда эту папку удалось узнать ([folderOf]). У ссылки `content://`
 * из «Загрузок» папки нет вовсе, и относительным строкам взяться неоткуда.
 */
private fun resolve(entry: String, beside: File?): String? {
    if (entry.startsWith("http://") || entry.startsWith("https://") ||
        entry.startsWith("content://") || entry.startsWith("file://")
    ) {
        return entry
    }

    val file = if (entry.startsWith("/")) File(entry) else beside?.let { File(it, entry) }
    return file?.takeIf { it.isFile }?.let { Uri.fromFile(it).toString() }
}

/**
 * Папка, в которой лежит сам список.
 *
 * У `file://` она есть прямо в ссылке. У `content://` пути нет — его
 * спрашивают у `MediaStore` через колонку `DATA`. Колонка объявлена устаревшей
 * и на многих провайдерах пуста; тогда ответ — «не знаю», и относительные
 * строки списка просто не попадут в очередь.
 */
private fun folderOf(context: Context, uri: String): File? {
    val parsed = Uri.parse(uri)
    if (parsed.scheme == "file") return parsed.path?.let { File(it).parentFile }

    @Suppress("DEPRECATION")
    val column = MediaStore.MediaColumns.DATA
    val path = runCatching {
        context.contentResolver.query(parsed, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    return path?.takeIf { it.isNotBlank() }?.let { File(it).parentFile }
}

/**
 * Дорожка из чужого файла.
 *
 * Название, исполнитель и длительность вынимаются из самого файла: в
 * `MediaStore` его может не быть вовсе — присланное в мессенджере лежит в
 * чужой песочнице и системному сканеру не показывается.
 *
 * Номер дорожки — отпечаток ссылки, как и у поднятой из памяти
 * ([EchoLastTrack.asTrack]): номера из MediaStore здесь нет, а список в плеере
 * различает строки именно по номеру.
 */
private fun trackOf(context: Context, uri: String, name: String): Track {
    val reader = MediaMetadataRetriever()
    val tags = runCatching {
        reader.setDataSource(context, Uri.parse(uri))
        Triple(
            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
        )
    }.getOrNull()
    runCatching { reader.release() }

    val bare = name.substringBeforeLast('.', name)

    return Track(
        id = uri.hashCode().toLong(),
        uri = uri,
        title = tags?.first?.takeIf { it.isNotBlank() } ?: bare.ifBlank { "Без названия" },
        artist = tags?.second?.takeIf { it.isNotBlank() } ?: "Неизвестный исполнитель",
        album = "",
        albumId = 0L,
        durationMs = tags?.third ?: 0L,
        folder = "",
    )
}
