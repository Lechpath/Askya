package app.askya.video

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import app.askya.data.files.legacyAppDir
import app.askya.data.files.stampedName
import app.askya.data.library.AskyaLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Куда AskyaV кладёт сделанное: куски, повороты, снятый звук и скачанное.
 *
 * ## Три дороги, и первая — библиотека
 *
 * Есть разрешение на папку в корне памяти — файл ложится на полку «Видео»
 * библиотеки Askya ([AskyaLibrary]), и это главная дорога: там же лежит всё
 * остальное, что приложение положило к себе, и найти сделанное можно в одном
 * месте, а не в трёх.
 *
 * Нет разрешения — остаётся прежняя дорога: «Внутренняя память → Movies →
 * Askya» через `MediaStore`. Она работает без всяких особых прав и потому
 * никуда не убирается: отказ от библиотеки не должен отнимать у человека
 * обрезку.
 *
 * До Android 10 `MediaStore` так не умеет вовсе — там третья дорога, папка
 * приложения.
 *
 * ## Почему дескриптор, а не поток
 *
 * `MediaMuxer` пишет в файл сам, кусками, и требует именно дескриптор.
 * Отсюда [PendingVideo]: заведённый, но ещё не показанный системе файл,
 * который кончается ровно одним из двух — [PendingVideo.done] или
 * [PendingVideo.cancel].
 *
 * ## Про звук отдельным файлом
 *
 * Звук — не видео, и в разделе «Movies» ему не место: `MediaStore` заводит
 * аудиозапись в своём разделе и **отказывает** в записи, если путь ведёт в
 * «Movies». Отсюда [AUDIO_FOLDER]: вынутый из фильма звук ложится в
 * «Music/Askya», где его и ищет AskyaEcho. В библиотеке он ложится на полку
 * «Музыка» по той же причине.
 */
class VideoStore(
    private val context: Context,
    private val library: AskyaLibrary,
) {

    /** Где лежат готовые куски — словами, для окон и подсказок. */
    val folderName: String
        get() = when {
            library.ready() -> library.folderName
            MODERN -> FOLDER
            else -> "Android/data/${context.packageName}/files/$LEGACY_DIR"
        }

    /**
     * Заводит новый файл и отдаёт его вместе с открытым дескриптором.
     *
     * Пока файл неполон, его не видно ни галерее, ни проводнику: в библиотеке
     * он ещё не показан сканеру, в `MediaStore` — помечен `IS_PENDING`. Так
     * половина фильма никому не покажется.
     */
    suspend fun create(name: String, mime: String): PendingVideo? = withContext(Dispatchers.IO) {
        val fileName = stamped(name) + extensionOf(mime)
        createInLibrary(name, mime)
            ?: if (MODERN) createInFolder(fileName, mime) else createInAppFolder(fileName)
    }

    /**
     * Полка библиотеки: «Музыка» для звука, «Видео» для всего остального.
     *
     * Имя файла собирает сама библиотека — она же разводит совпадения и ставит
     * отметку времени; повторять это здесь значило бы получить два разных
     * правила именования в одной папке.
     */
    private fun createInLibrary(name: String, mime: String): PendingVideo? {
        if (!library.ready()) return null
        val shelf = if (mime.startsWith("audio/")) {
            AskyaLibrary.Shelf.MUSIC
        } else {
            AskyaLibrary.Shelf.VIDEO
        }
        val target = library.newFile(shelf, name + extensionOf(mime), mime) ?: return null
        val descriptor = runCatching {
            ParcelFileDescriptor.open(
                target,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_READ_WRITE or
                    ParcelFileDescriptor.MODE_TRUNCATE,
            )
        }.getOrNull()
        if (descriptor == null) {
            library.discard(target)
            return null
        }
        return PendingVideo(this, Uri.fromFile(target), descriptor, target.name, target)
    }

    private fun createInFolder(displayName: String, mime: String): PendingVideo? {
        val resolver = context.contentResolver
        val audio = mime.startsWith("audio/")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (audio) AUDIO_FOLDER else FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (audio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        val descriptor = runCatching { resolver.openFileDescriptor(uri, "rw") }.getOrNull()
        if (descriptor == null) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        return PendingVideo(this, uri, descriptor, displayName, null)
    }

    private fun createInAppFolder(displayName: String): PendingVideo? {
        val target = File(legacyDir(), displayName)
        val descriptor = runCatching {
            ParcelFileDescriptor.open(
                target,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE,
            )
        }.getOrNull() ?: return null
        return PendingVideo(this, Uri.fromFile(target), descriptor, displayName, null)
    }

    /**
     * Копирует чужой файл в папку Askya и отдаёт ссылку на копию.
     *
     * Нужно «Показу» упражнений: человек снял себя или тренера и положил ролик
     * к упражнению системным выбором файла. Копия, а не ссылка на чужой файл:
     * ролик снимают, а потом чистят галерею — упражнение при этом остаётся.
     *
     * Незавершённая копия убирается совсем: половина ролика выглядела бы как
     * испорченный файл, а не как несостоявшееся добавление.
     */
    suspend fun copyFrom(source: Uri, name: String, mime: String): String? =
        withContext(Dispatchers.IO) {
            val pending = create(name, mime) ?: return@withContext null
            runCatching {
                java.io.FileOutputStream(pending.fileDescriptor).use { out ->
                    context.contentResolver.openInputStream(source)?.use { input ->
                        input.copyTo(out)
                    } ?: error("файл не открылся на чтение")
                }
                pending.done()
            }.getOrElse {
                pending.cancel()
                null
            }
        }

    /**
     * Убирает копию, сделанную [copyFrom]. Чужие файлы не трогаются: ссылка не
     * в нашу папку сюда просто не попадает.
     */
    suspend fun remove(uri: String?) {
        val parsed = uri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return
        withContext(Dispatchers.IO) { discard(parsed) }
    }

    /**
     * Файл дописан — показать его системе.
     *
     * Отвечает тем, чем на файл теперь ссылаться. У библиотеки это ссылка,
     * которую завёл сканер: по ней файл виден и AskyaV, и галерее, а без
     * сканирования он остался бы для системы несуществующим — папку в корне
     * памяти `MediaStore` сам не обходит.
     */
    internal fun publish(uri: Uri, file: File?): String {
        if (file != null) return library.publish(file)
        if (!MODERN) return uri.toString()
        runCatching {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
        return uri.toString()
    }

    /** Запись оборвалась — убрать недописанное совсем. */
    internal fun discard(uri: Uri) {
        runCatching {
            if (uri.scheme == "file") File(requireNotNull(uri.path)).delete()
            else context.contentResolver.delete(uri, null, null)
        }
    }

    /**
     * Сколько байт получилось. По нему обрезка отличает записанный файл от
     * пустого: `MediaMuxer` собрал заголовок, а места на диске не хватило — и
     * в папке остаётся файл, о котором честнее сказать «не вышло», чем
     * «готово».
     */
    internal fun weightOf(uri: Uri, file: File?): Long {
        if (file != null) return runCatching { file.length() }.getOrDefault(0L)
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull() ?: 0L
    }

    private fun legacyDir(): File = legacyAppDir(context, LEGACY_DIR)

    /**
     * Имя с отметкой времени в начале — как у картинок: по ней файлы в папке
     * идут по порядку и не затирают друг друга, сколько бы кусков ни вырезали
     * из одного фильма.
     */
    private fun stamped(name: String): String = stampedName(name, "video")

    /**
     * Хвост имени по подписи содержимого.
     *
     * Скачанное приходит не только в mp4: поток кусками складывается в `ts`, а
     * с чужих серверов приходят webm и mkv. Хвост должен звать вещь своим
     * именем — переименованный в `.mp4` матрёшечный контейнер не станет от
     * этого mp4, зато перестанет открываться половиной чужих плееров.
     */
    private fun extensionOf(mime: String): String = when {
        mime.startsWith("audio/") -> ".m4a"
        mime == "video/mp2t" -> ".ts"
        mime == "video/webm" -> ".webm"
        mime == "video/x-matroska" -> ".mkv"
        mime == "video/quicktime" -> ".mov"
        mime == "video/x-msvideo" -> ".avi"
        else -> ".mp4"
    }

    private companion object {
        val MODERN = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        const val FOLDER = "Movies/Askya"

        /**
         * Звук ложится в «Music/Askya», а не в «Movies/Askya».
         *
         * Не ради порядка: `MediaStore` не даёт завести аудиозапись по пути,
         * который начинается не с музыкального раздела, и прежняя попытка
         * положить вынутый звук в «Movies» оканчивалась отказом ещё до того,
         * как начиналась запись.
         */
        const val AUDIO_FOLDER = "Music/Askya"
        const val LEGACY_DIR = "Askya"
    }
}

/**
 * Заведённый, но ещё не дописанный файл.
 *
 * Пока он открыт, его не существует ни для галереи, ни для проводника.
 * Заканчивается ровно одним из двух: [done] — файл готов и виден,
 * [cancel] — записи не было, и её следов не остаётся.
 */
class PendingVideo(
    private val store: VideoStore,
    val uri: Uri,
    private val descriptor: ParcelFileDescriptor,
    val displayName: String,
    /** Файл библиотеки — или `null`, если файл заведён в `MediaStore`. */
    private val file: File?,
) {
    /** Куда пишет `MediaMuxer`. */
    val fileDescriptor: java.io.FileDescriptor get() = descriptor.fileDescriptor

    /**
     * Сколько байт легло на диск. Спрашивается после закрытия дескриптора и до
     * того, как о файле сказано «готово».
     */
    val weight: Long get() = store.weightOf(uri, file)

    fun done(): String {
        runCatching { descriptor.close() }
        return store.publish(uri, file)
    }

    fun cancel() {
        runCatching { descriptor.close() }
        store.discard(uri)
    }
}
