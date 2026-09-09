package app.askya.data.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.askya.data.files.legacyAppDir
import app.askya.data.files.stampedName
import java.io.File

/**
 * Своя папка Askya под голос: «Внутренняя память → Music → Askya».
 *
 * Устроена как папки под картинки ([app.askya.data.images.ImageStore]) и под
 * видео ([app.askya.video.VideoStore]) и по той же причине: то, что человек
 * наговорил в приложении, должно лежать там, куда он может зайти проводником,
 * а не в `Android/data`, куда с Android 11 не пускают. Раздел Music, а не
 * Movies: звук система раскладывает по своим полкам, и запись, положенная в
 * «фильмы», потом не находится ничем, кроме самой Askya.
 *
 * До Android 10 MediaStore так не умеет, а писать в общие папки напрямую можно
 * только с разрешением на всё хранилище — там остаётся папка приложения.
 *
 * Плата та же, что у картинок: заметки видны и в системном плеере, вперемешку
 * с музыкой. Видимая папка важнее: голос, которого нельзя скопировать на
 * компьютер, — это голос в заложниках.
 */
class VoiceStore(private val context: Context) {

    /** Где лежат заметки — словами, для окон и подсказок. */
    val folderName: String =
        if (MODERN) FOLDER else "Android/data/${context.packageName}/files/$LEGACY_DIR"

    /**
     * Заводит новый файл и отдаёт его вместе с открытым дескриптором.
     *
     * Дескриптор, а не поток: `MediaRecorder` пишет в файл сам и требует
     * именно его. Пока файл неполон, он помечен `IS_PENDING` — до конца записи
     * его не видно ни системному плееру, ни проводнику, и оборванная на
     * полуслове заметка никому не покажется.
     */
    suspend fun create(name: String): PendingVoice? = withContext(Dispatchers.IO) {
        val fileName = stamped(name) + EXTENSION
        if (MODERN) createInFolder(fileName) else createInAppFolder(fileName)
    }

    private fun createInFolder(displayName: String): PendingVoice? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        val descriptor = runCatching { resolver.openFileDescriptor(uri, "rw") }.getOrNull()
        if (descriptor == null) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        return PendingVoice(this, uri, descriptor, displayName)
    }

    private fun createInAppFolder(displayName: String): PendingVoice? {
        val target = File(legacyDir(), displayName)
        val descriptor = runCatching {
            ParcelFileDescriptor.open(
                target,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE,
            )
        }.getOrNull() ?: return null
        return PendingVoice(this, Uri.fromFile(target), descriptor, displayName)
    }

    /**
     * Убирает файл заметки.
     *
     * Файл в этой папке существует ровно ради своей записи в Scroll: без неё
     * его никто не откроет. Поэтому он и уходит вместе с ней — тем же
     * правилом, что копия картинки.
     */
    suspend fun remove(uri: String?) {
        val parsed = uri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return
        withContext(Dispatchers.IO) { discard(parsed) }
    }

    /** Файл дописан — показать его системе. */
    internal fun publish(uri: Uri) {
        if (!MODERN) return
        runCatching {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
    }

    /** Запись оборвалась — убрать недописанное совсем. */
    internal fun discard(uri: Uri) {
        runCatching {
            if (uri.scheme == "file") File(requireNotNull(uri.path)).delete()
            else context.contentResolver.delete(uri, null, null)
        }
    }

    private fun legacyDir(): File = legacyAppDir(context, LEGACY_DIR)

    /**
     * Имя с отметкой времени в начале — как у картинок и кусков видео: по ней
     * файлы в папке идут по порядку и не затирают друг друга, сколько бы
     * заметок ни наговорили в один день. Само правило одно на всю Askya —
     * `data/files/FileNames.kt`.
     */
    private fun stamped(name: String): String = stampedName(name, "golos")

    companion object {
        /**
         * AAC в контейнере mp4 — то, что умеет записывать и играть всякий
         * Android без единой библиотеки. Минута речи весит около четверти
         * мегабайта; wav весил бы десять.
         */
        const val MIME = "audio/mp4"

        private const val EXTENSION = ".m4a"
        val MODERN = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        const val FOLDER = "Music/Askya"
        const val LEGACY_DIR = "Askya"
    }
}

/**
 * Заведённый, но ещё не дописанный файл заметки.
 *
 * Пока он открыт, его не существует ни для системного плеера, ни для
 * проводника. Заканчивается ровно одним из двух: [done] — файл готов и виден,
 * [cancel] — записи не было, и её следов не остаётся.
 */
class PendingVoice(
    private val store: VoiceStore,
    val uri: Uri,
    private val descriptor: ParcelFileDescriptor,
    val displayName: String,
) {
    /** Куда пишет `MediaRecorder`. */
    val fileDescriptor: java.io.FileDescriptor get() = descriptor.fileDescriptor

    fun done(): String {
        runCatching { descriptor.close() }
        store.publish(uri)
        return uri.toString()
    }

    fun cancel() {
        runCatching { descriptor.close() }
        store.discard(uri)
    }
}
