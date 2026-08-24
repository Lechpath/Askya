package app.askya.data.images

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Своя папка Askya под картинки: «Внутренняя память → Pictures → Askya».
 *
 * Раньше папка лежала внутри `Android/data/app.askya/files`. Копия там
 * сохранялась исправно, но добраться до неё с телефона нельзя: начиная с
 * Android 11 проводник в `Android/data` не пускает — папки Askya для человека
 * фактически не существовало.
 *
 * Теперь файл пишется через MediaStore в общий раздел Pictures. Это
 * единственный способ положить картинку в видимую папку, не прося доступ ко
 * всем файлам телефона: система сама заводит `Pictures/Askya` и отдаёт ссылку
 * на созданную запись. Плата за это — картинки видны и в галерее, вперемешку
 * с фотографиями; видимая папка важнее.
 *
 * До Android 10 MediaStore так не умеет, а писать в общие папки напрямую можно
 * только с разрешением на всё хранилище. Там остаётся прежняя папка
 * приложения: на тех версиях она проводником ещё открывается.
 *
 * Ссылка в записи — то, что вернула система: `content://media/…` у новых копий
 * и `file://…` у старых. Читаются они одинаково, через
 * `ContentResolver.openInputStream`, поэтому просмотр, превью и правка
 * работают одним кодом и с теми, и с другими.
 */
class ImageStore(private val context: Context) {

    /** Где лежат картинки — словами, для окон и подсказок. */
    val folderName: String =
        if (MODERN) FOLDER else "Android/data/${context.packageName}/files/$LEGACY_DIR"

    /**
     * Копирует выбранный документ в папку Askya и отдаёт ссылку на копию.
     *
     * `null` означает, что копия не сделана — файл не прочитался или на нём
     * кончилось место. Незавершённая копия убирается: половина картинки в
     * разделе выглядела бы как испорченный файл, а не как несостоявшееся
     * добавление.
     */
    suspend fun importFrom(source: Uri, name: String, mime: String): String? =
        withContext(Dispatchers.IO) {
            val type = imageMime(name, mime)
            write(fileName(name, type), type) { output ->
                context.contentResolver.openInputStream(source)?.use { input ->
                    input.copyTo(output)
                } ?: error("документ не открылся")
            }
        }

    /**
     * Кладёт готовую картинку в папку Askya — итог правки или коллаж.
     *
     * Всегда новым файлом, даже когда правят уже лежащую здесь картинку:
     * перезапись на месте оставила бы в списках старое превью (оно помнится по
     * ссылке, а ссылка не изменилась бы) и потеряла бы исходник, если запись
     * оборвётся на середине. Старый файл удаляется после того, как новый лёг
     * целиком.
     */
    suspend fun save(bitmap: Bitmap, name: String = "askya"): String? =
        withContext(Dispatchers.IO) {
            // PNG для картинок с прозрачностью: JPEG залил бы её чёрным.
            val png = bitmap.hasAlpha()
            val type = if (png) "image/png" else "image/jpeg"
            write(stamped(name) + if (png) ".png" else ".jpg", type) { output ->
                val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                if (!bitmap.compress(format, QUALITY, output)) error("не записалось")
            }
        }

    /**
     * Переносит старую копию из папки приложения в видимую папку Askya.
     *
     * Отдаёт ссылку на новое место — или `null`, если переносить нечего либо
     * не вышло. Тогда запись остаётся смотреть на старый файл: он читается
     * по-прежнему, просто лежит там, куда не зайти проводником.
     */
    suspend fun adopt(uri: String?): String? {
        if (!MODERN) return null
        val file = legacyFile(uri) ?: return null
        val moved = withContext(Dispatchers.IO) {
            val name = file.name
            write(name, imageMime(name, "")) { output ->
                file.inputStream().use { it.copyTo(output) }
            }
        }
        // Старый файл убирается только после того, как новый лёг целиком.
        if (moved != null) withContext(Dispatchers.IO) { runCatching { file.delete() } }
        return moved
    }

    /**
     * Переименовывает файл в папке вслед за подписью.
     *
     * Подпись и имя файла — одно и то же имя, просто в двух местах: папку
     * завели, чтобы в неё заходить, и картинка, подписанная в Askya «Крым»,
     * не должна лежать там как `file_00000000a474.png`.
     *
     * Время добавления в начале имени сохраняется: по нему файлы в папке идут
     * по порядку добавления, и переименование не должно этот порядок ломать.
     * Расширение тоже остаётся прежним — оно про то, чем файл открывать, а не
     * про то, как он называется.
     *
     * Отдаёт ссылку на переименованный файл: у записи MediaStore она прежняя
     * (в ней номер, а не имя), у старой копии — новая. `null` означает, что
     * файл не переименовался: чужой документ, пропавший файл или занятое имя.
     * Подпись в Askya от этого не отменяется — переименовать её важнее.
     */
    suspend fun rename(uri: String?, name: String): String? {
        if (!isOurs(uri)) return null
        val parsed = Uri.parse(uri)

        return withContext(Dispatchers.IO) {
            if (parsed.scheme == "file") {
                val file = legacyFile(uri) ?: return@withContext null
                val target = File(file.parentFile, renamed(file.name, name))
                runCatching {
                    if (file.renameTo(target)) Uri.fromFile(target).toString() else null
                }.getOrNull()
            } else {
                val current = displayName(parsed) ?: return@withContext null
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, renamed(current, name))
                }
                runCatching {
                    if (context.contentResolver.update(parsed, values, null, null) > 0) uri else null
                }.getOrNull()
            }
        }
    }

    /**
     * Ссылка на лежащий файл, годная для чужого приложения.
     *
     * Записи MediaStore годятся как есть — их читает кто угодно, кому выдали
     * право вместе с намерением. Старую копию в папке приложения так не
     * отдать: `file://` с Android 7 вылетает исключением, поэтому для неё
     * ссылку выписывает FileProvider.
     */
    fun shareable(uri: String?): Uri? {
        val parsed = uri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        return when (parsed.scheme) {
            "content" -> parsed
            "file" -> parsed.path
                ?.let { path -> runCatching { provided(File(path)) }.getOrNull() }
            else -> null
        }
    }

    /**
     * Кладёт картинку в кэш и отдаёт ссылку на неё — так уходит наружу то, что
     * человек видит в правке, вместе с несохранёнными поворотами и надписями.
     * В папку Askya это не пишется: отправить — не то же самое, что сохранить,
     * и лишний файл в папке был бы неожиданностью.
     *
     * Файл всегда один и тот же: кэш чистит система, а копить в нём по снимку
     * на каждую отправку незачем.
     */
    suspend fun shareableCopy(bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val png = bitmap.hasAlpha()
        val dir = File(context.cacheDir, SHARE_DIR)
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, "askya" + if (png) ".png" else ".jpg")

        runCatching {
            target.outputStream().use { output ->
                val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                if (!bitmap.compress(format, QUALITY, output)) error("не записалось")
            }
            provided(target)
        }.getOrNull()
    }

    private fun provided(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Имя файла у записи MediaStore. В ссылке его нет — только номер. */
    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /**
     * Новое имя файла: прежнее время, новое имя, прежнее расширение.
     *
     * Расширение из подписи отбрасывается, только если оно и есть расширение
     * файла: подпись «19.08 Крым» — это имя целиком, а не имя с расширением
     * «08 Крым».
     */
    private fun renamed(current: String, name: String): String {
        val ext = current.substringAfterLast('.', "")
        val stamp = STAMPED.find(current)?.value ?: (STAMP.format(LocalDateTime.now()) + "-")
        val given = name.trim().substringAfterLast('/')
        val bare = if (ext.isNotEmpty() && given.endsWith(".$ext", ignoreCase = true)) {
            given.dropLast(ext.length + 1)
        } else {
            given
        }
        val base = bare.replace(UNSAFE, "_").trim('_').take(48).ifBlank { "image" }
        return stamp + base + if (ext.isEmpty()) "" else ".$ext"
    }

    /**
     * Тип картинки по ссылке — спрашивается у системы.
     *
     * По расширению его больше не вывести: ссылка на запись MediaStore — это
     * номер (`content://media/external/images/media/17`), и имени файла в ней
     * нет. Система тип помнит, потому что мы сами его и записали.
     */
    fun mimeOf(uri: String): String {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return "image/jpeg"
        val known = runCatching { context.contentResolver.getType(parsed) }.getOrNull()
        return known?.takeIf { it.startsWith("image/") } ?: imageMime(parsed.path.orEmpty(), "")
    }

    /** Лежит ли файл в нашей папке. Чужие ссылки трогать нельзя. */
    fun isOurs(uri: String?): Boolean {
        val parsed = uri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return when (parsed.scheme) {
            "file" -> legacyFile(uri) != null
            "content" -> parsed.authority == MediaStore.AUTHORITY && inOurFolder(parsed)
            else -> false
        }
    }

    /**
     * Удаляет копию. Чужие документы не трогаются: на них у нас только доступ
     * на чтение, и удалять чужой файл, убирая запись из Scroll, — не то, чего
     * ждут.
     */
    suspend fun delete(uri: String?) {
        if (!isOurs(uri)) return
        withContext(Dispatchers.IO) {
            val parsed = Uri.parse(uri)
            runCatching {
                if (parsed.scheme == "file") {
                    File(requireNotNull(parsed.path)).delete()
                } else {
                    context.contentResolver.delete(parsed, null, null)
                }
            }
        }
    }

    /**
     * Запись файла — одна дорога для копии, правки и коллажа.
     *
     * На Android 10 и новее файл заводится в MediaStore и до конца записи
     * помечен `IS_PENDING`: пока он неполон, его не видно ни галерее, ни
     * проводнику, и половина картинки никому не покажется. Не записалось —
     * запись убирается совсем.
     */
    private fun write(displayName: String, mime: String, body: (OutputStream) -> Unit): String? =
        if (MODERN) writeToFolder(displayName, mime, body) else writeToAppFolder(displayName, body)

    private fun writeToFolder(
        displayName: String,
        mime: String,
        body: (OutputStream) -> Unit,
    ): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null

        return runCatching {
            resolver.openOutputStream(uri)?.use(body) ?: error("папка не открылась на запись")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            uri.toString()
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun writeToAppFolder(displayName: String, body: (OutputStream) -> Unit): String? {
        val target = File(legacyDir(), displayName)
        return runCatching {
            target.outputStream().use(body)
            Uri.fromFile(target).toString()
        }.getOrElse {
            target.delete()
            null
        }
    }

    /** Прежняя папка приложения: запасная на старых версиях и дом старых копий. */
    private fun legacyDir(): File {
        val external = runCatching { context.getExternalFilesDir(LEGACY_DIR) }.getOrNull()
        val dir = external ?: File(context.filesDir, LEGACY_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Файл старой копии — или `null`, если ссылка ведёт не в папку приложения. */
    private fun legacyFile(uri: String?): File? {
        val path = uri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf { it.scheme == "file" }
            ?.path
            ?: return null
        val root = runCatching { legacyDir().canonicalPath }.getOrNull() ?: return null
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.path.startsWith(root + File.separator) && it.exists() }
    }

    /**
     * Наша ли это запись в MediaStore — по папке, в которой лежит файл.
     * Папка спрашивается у системы, а не выводится из ссылки: в самой ссылке
     * только номер записи, и по нему о папке ничего не сказать.
     */
    private fun inOurFolder(uri: Uri): Boolean = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use false
            cursor.getString(0).orEmpty().trimEnd('/').equals(FOLDER, ignoreCase = true)
        } ?: false
    }.getOrDefault(false)

    /**
     * Имя копии: время добавления плюс исходное имя. Время впереди, чтобы
     * файлы в папке шли по порядку добавления, а имя сохраняется — по нему
     * картинку узнают, зайдя в папку проводником.
     */
    private fun fileName(name: String, mime: String): String {
        val clean = name.substringAfterLast('/').replace(UNSAFE, "_").take(48)
        val base = clean.substringBeforeLast('.', clean).ifBlank { "image" }
        val ext = clean.substringAfterLast('.', "").lowercase()
            .takeIf { it.isNotEmpty() && it.length <= 4 }
            ?: mime.substringAfterLast('/', "").lowercase().takeIf { it.isNotEmpty() }
            ?: "jpg"
        return stamped(base) + "." + ext
    }

    private fun stamped(base: String): String = STAMP.format(LocalDateTime.now()) + "-" + base

    /**
     * Тип картинки. MediaStore заводит запись по типу, а не по расширению, и
     * провайдер тип отдавать не обязан: пустая строка увела бы файл в раздел
     * «прочее», где его не показывает ни галерея, ни наш раздел.
     */
    private fun imageMime(name: String, mime: String): String {
        if (mime.startsWith("image/")) return mime
        return when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heif"
            else -> "image/jpeg"
        }
    }

    private companion object {
        /** Видимая папка. Заводится системой при первой записи. */
        val FOLDER: String = Environment.DIRECTORY_PICTURES + "/Askya"
        const val LEGACY_DIR = "Images"
        const val SHARE_DIR = "share"
        const val QUALITY = 92
        val MODERN = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        val UNSAFE = Regex("""[^A-Za-z0-9А-Яа-яЁё._-]""")

        /** Время добавления в начале имени: `20260819-201618-500-`. */
        val STAMPED = Regex("""^\d{8}-\d{6}-\d{3}-""")
    }
}
