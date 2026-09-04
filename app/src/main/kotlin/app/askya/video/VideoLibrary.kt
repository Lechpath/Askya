package app.askya.video

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ролик с телефона. */
data class Clip(
    val id: Long,
    val uri: String,
    val title: String,
    /**
     * Как файл называется на телефоне.
     *
     * Рядом с [title] потому, что переименование в AskyaV меняет имя ролика в
     * разделе, а не файл на диске (см. `VideoPreferences.rename`): [title] —
     * то, как ролик зовут здесь, [fileName] — то, как его найдут проводником.
     * Пока файл не переименовывали, это одно и то же.
     */
    val fileName: String = title,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    /** Папка, в которой лежит файл, — по ней собирается раздел «Папки». */
    val folder: String,
    /** Когда файл появился на телефоне, в секундах эпохи. */
    val addedAt: Long,
) {
    /** «1920×1080» — или пусто, если система размера не знает. */
    val resolution: String get() = if (width > 0 && height > 0) "$width×$height" else ""
}

/** Папка с видео: имя и всё, что в ней лежит. */
data class VideoFolder(val name: String, val clips: List<Clip>)

/**
 * Видео на телефоне — через `MediaStore`, как музыка в Echo.
 *
 * Своей библиотеки AskyaV не ведёт по той же причине, что и Echo: файлы уже
 * разложены системой, а второй список тех же файлов пришлось бы держать с ней
 * в согласии.
 *
 * **Чего `MediaStore` не покажет.** Система заводит в нём то, что сама признала
 * видео, — а `.ts`, `.flv`, `.rmvb` и прочую экзотику её сканер часто не
 * признаёт, хотя плеер их открывает. Это не изъян раздела, а цена отказа от
 * `MANAGE_EXTERNAL_STORAGE`: право читать весь телефон целиком ради того,
 * чтобы найти в нём фильмы, — слишком много за список. Поэтому рядом со
 * списком всегда стоит «Открыть файл»: через системный выбор открывается
 * что угодно, включая то, чего в списке нет.
 */
object VideoLibrary {

    suspend fun load(context: Context): List<Clip> = withContext(Dispatchers.IO) {
        val columns = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.DATE_ADDED)
            // Имя папки система считает сама начиная с Android 10; на более
            // старых его приходится вырезать из пути файла — как в Echo.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Video.Media.DATA)
            }
        }.toTypedArray()

        val clips = mutableListOf<Clip>()

        runCatching {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                columns,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val size = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val width = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val height = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val added = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val place = cursor.getColumnIndex(columns.last())

                while (cursor.moveToNext()) {
                    val clipId = cursor.getLong(id)
                    val fileName = cursor.getString(name).orEmpty()
                    clips += Clip(
                        id = clipId,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            clipId,
                        ).toString(),
                        // Расширение снимается: в списке оно только шумит, а
                        // формат виден по значку и по карточке файла.
                        title = fileName.substringBeforeLast('.', fileName)
                            .ifBlank { "Без названия" },
                        durationMs = cursor.getLong(duration),
                        sizeBytes = cursor.getLong(size),
                        width = cursor.getInt(width),
                        height = cursor.getInt(height),
                        folder = folderName(cursor.getString(place)),
                        addedAt = cursor.getLong(added),
                    )
                }
            }
        }

        clips
    }

    /**
     * То же видео, разложенное по папкам устройства.
     *
     * Считается из уже прочитанного списка, а не вторым запросом: это те же
     * файлы, и два прохода дали бы два разных среза, скачайся что-нибудь между
     * ними.
     */
    fun folders(clips: List<Clip>): List<VideoFolder> = clips
        .groupBy { it.folder }
        .map { (name, inside) -> VideoFolder(name, inside) }
        .sortedBy { it.name.lowercase() }

    /**
     * Кадр для карточки.
     *
     * С Android 10 система отдаёт готовую заставку сама и держит её в своём
     * кэше; ниже — приходится вынимать кадр из файла. Вынутый кадр берётся не
     * с нуля, а с трёх секунд: в начале фильма чаще всего чёрный экран, и
     * стена чёрных карточек — ровно то, ради чего заставки и не показывают.
     *
     * `null` означает «кадра нет»: на его месте карточка рисует свой знак, а
     * не пустой прямоугольник.
     */
    suspend fun frame(context: Context, clip: Clip, side: Int = 512): Bitmap? =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(clip.uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    context.contentResolver.loadThumbnail(uri, Size(side, side), null)
                }.getOrNull()?.let { return@withContext it }
            }
            // `release`, а не `use`: закрываться по `Closeable` этот класс
            // научился только с Android 29, а раздел работает с 26.
            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(
                    FRAME_AT_US.coerceAtMost(clip.durationMs * 1000 / 2),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                )
            }.also { runCatching { retriever.release() } }.getOrNull()
        }

    /**
     * Имя папки: на Android 10+ система отдаёт его готовым, ниже — это
     * последний каталог в пути. Пустое бывает у файлов от чужого провайдера;
     * такие собираются в одну кучу.
     */
    private fun folderName(value: String?): String {
        val raw = value.orEmpty()
        if (raw.isBlank()) return "Без папки"
        if (!raw.contains('/')) return raw
        return raw.substringBeforeLast('/').substringAfterLast('/').ifBlank { "Без папки" }
    }

    /** Три секунды: столько обычно длится чёрная заставка в начале. */
    private const val FRAME_AT_US = 3_000_000L
}

/** Размер файла словами: «1,4 ГБ». */
fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "%.0f КБ".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f МБ".format(bytes / 1024.0 / 1024)
    else -> "%.1f ГБ".format(bytes / 1024.0 / 1024 / 1024)
}
