package app.askya.desktop

import app.askya.data.images.ImageFiles
import app.askya.platform.asUri
import app.askya.platform.fileOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.net.URLConnection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Папка Askya под картинки у Windows-версии — `images` внутри данных Askya.
 *
 * Правила те же, что у телефона (`ImageStore`): картинку в Scroll приносят
 * копией, а не ссылкой на чужой файл; имя копии — отметка времени и очищенное
 * имя; удаляют и переименовывают только своё. Ссылка в записи — `file:/…`.
 */
class DesktopImages(private val dir: File) : ImageFiles {

    override val folderName: String
        get() = dir.absolutePath

    override suspend fun importFrom(source: String, name: String, mime: String): String? =
        withContext(Dispatchers.IO) {
            val from = fileOf(source)?.takeIf { it.isFile } ?: return@withContext null
            write(fileName(name, imageMime(name, mime))) { out ->
                from.inputStream().use { it.copyTo(out) }
            }
        }

    /**
     * Кладёт картинку из Слепка и отдаёт ссылку на копию; `null` — не легла.
     * Незаконченная копия убирается: половина картинки выглядела бы порчей.
     */
    suspend fun restore(name: String, mime: String, body: (OutputStream) -> Unit): String? =
        withContext(Dispatchers.IO) { write(fileName(name, imageMime(name, mime)), body) }

    /** Переносить у компьютера нечего: старых мест, как у телефона, у него не было. */
    override suspend fun adopt(uri: String?): String? = null

    override suspend fun rename(uri: String?, name: String): String? {
        if (!isOurs(uri)) return null
        val file = fileOf(uri) ?: return null
        return withContext(Dispatchers.IO) {
            val target = File(file.parentFile, renamed(file.name, name))
            runCatching { if (file.renameTo(target)) target.asUri() else null }.getOrNull()
        }
    }

    override fun mimeOf(uri: String): String {
        val file = fileOf(uri) ?: return "image/jpeg"
        return imageMime(file.name, "")
    }

    override fun shareLink(uri: String?): String? = fileOf(uri)?.takeIf { it.isFile }?.asUri()

    override fun isOurs(uri: String?): Boolean {
        val file = fileOf(uri) ?: return false
        return runCatching { file.canonicalPath.startsWith(dir.canonicalPath + File.separator) }
            .getOrDefault(false)
    }

    override suspend fun delete(uri: String?) {
        if (!isOurs(uri)) return
        withContext(Dispatchers.IO) { runCatching { fileOf(uri)?.delete() } }
    }

    private fun write(fileName: String, body: (OutputStream) -> Unit): String? {
        dir.mkdirs()
        val target = File(dir, fileName)
        return runCatching {
            target.outputStream().use(body)
            target.asUri()
        }.getOrElse {
            target.delete()
            null
        }
    }

    /** Имя с тем же расширением, что было, — меняется только то, что до точки. */
    private fun renamed(current: String, name: String): String {
        val ext = current.substringAfterLast('.', "")
        val base = stamped(clean(name))
        return if (ext.isEmpty()) base else "$base.$ext"
    }

    private fun fileName(name: String, mime: String): String {
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            else -> "jpg"
        }
        return stamped(clean(name.substringBeforeLast('.', name))) + "." + ext
    }

    private fun clean(name: String): String =
        name.replace(UNSAFE, "_").trim('_').take(48).ifBlank { "askya" }

    /**
     * Отметка с миллисекундами, как у телефона: картинок за одну секунду
     * бывает несколько — пачкой из окна выбора.
     */
    private fun stamped(base: String): String = STAMP.format(LocalDateTime.now()) + "-" + base

    private fun imageMime(name: String, mime: String): String {
        if (mime.startsWith("image/")) return mime
        return URLConnection.guessContentTypeFromName(name)?.takeIf { it.startsWith("image/") }
            ?: when (name.substringAfterLast('.', "").lowercase()) {
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        val UNSAFE = Regex("""[^\p{L}\p{N}._-]+""")
    }
}
