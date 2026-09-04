package app.askya.data.library

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Библиотека Askya — одна папка в корне памяти телефона.
 *
 * ## Зачем она заведена
 *
 * Прежде сделанное приложением расходилось по чужим общим папкам: картинки в
 * «Pictures/Askya», куски фильмов в «Movies/Askya», а всё остальное — ссылками
 * на чужие файлы, которые Askya не заводила и за сохранность которых не
 * отвечает. Отсюда две беды, и обе замечают поздно.
 *
 * Первая: **приложение помнило ссылку, а не вещь**. Ролик, приложенный к
 * упражнению, лежал там, куда его положила камера; человек чистил галерею — и
 * в Askya оставалась запись, показывающая пустоту. Вторая: **папок было
 * несколько**, и на вопрос «где у Askya файлы» честного ответа не было —
 * приходилось перечислять три места.
 *
 * Библиотека отвечает на оба: у приложения есть **одна папка, и всё, что в
 * неё попало, — копия**. Исходник можно стереть, перенести, потерять вместе с
 * галереей: в папке Askya вещь останется, а значит, останется и в приложении.
 *
 * ## Почему в корне, а не в «Documents»
 *
 * Папку заводят, чтобы в неё заходить. «Внутренняя память → Askya» — путь,
 * который помнят; «Внутренняя память → Documents → Askya → Видео» — путь,
 * который ищут.
 *
 * Плата названа честно: с Android 11 создать папку в корне памяти нельзя иначе
 * как с разрешением «Доступ ко всем файлам»
 * ([android.Manifest.permission.MANAGE_EXTERNAL_STORAGE]). Оно широкое, его
 * выдают отдельным системным экраном, и Askya спрашивает его один раз, объяснив
 * зачем. Пока его нет, библиотека **не притворяется рабочей**: [ready] отвечает
 * «нет», и всё, что умеет писать, пишет туда же, куда писало раньше, — в
 * «Pictures/Askya» и «Movies/Askya». Раздел не ломается от отказа, он остаётся
 * прежним.
 *
 * ## Четыре полки
 *
 * [Shelf] — не украшение: система ищет музыку среди аудиофайлов, а галерея —
 * среди картинок, и папка, где всё свалено вместе, читается ими хуже. Полка —
 * это подпапка с русским именем, потому что в неё заходят глазами, а не кодом.
 *
 * ## Про то, что папку видно системе
 *
 * Файл, записанный напрямую, `MediaStore` не видит: его индекс пополняет не
 * файловая система, а сканер. Поэтому каждый положенный файл прогоняется через
 * [MediaScannerConnection] — иначе музыка из библиотеки не появилась бы в
 * AskyaEcho, а видео в AskyaV, хотя оба читают телефон именно индексом.
 */
class AskyaLibrary(private val context: Context) {

    /**
     * Полки библиотеки.
     *
     * [dir] — имя подпапки на диске. Русское: папку заводили, чтобы в неё
     * заходил человек, и «Видео» ему говорит больше, чем `video`.
     */
    enum class Shelf(val dir: String, val title: String, val about: String) {
        PHOTOS("Фото", "Фото", "Снимки, правки и коллажи"),
        MUSIC("Музыка", "Музыка", "Песни и записанный голос"),
        VIDEO("Видео", "Видео", "Ролики, куски и скачанное"),
        FILES("Файлы", "Файлы", "Книги, документы и всё прочее"),
    }

    /** Корень библиотеки. Существует он или нет — вопрос отдельный, см. [ready]. */
    val root: File
        get() = File(Environment.getExternalStorageDirectory(), FOLDER)

    /** Путь словами — для окон, подсказок и настроек. */
    val folderName: String get() = "Внутренняя память/$FOLDER"

    /** Папка полки. Не создаётся: создание — дело [prepare]. */
    fun shelfDir(shelf: Shelf): File = File(root, shelf.dir)

    /**
     * Дало ли приложение право писать в корень памяти.
     *
     * С Android 11 это отдельное системное разрешение, которое не спрашивают
     * всплывающим окном; до неё хватает обычной записи в хранилище.
     */
    fun granted(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Готова ли библиотека принимать файлы: есть право и есть папка.
     *
     * Спрашивается перед каждой записью, а не запоминается: разрешение снимают
     * в настройках телефона в любой момент, и запомненное «да» после этого
     * увело бы файл в никуда.
     */
    fun ready(): Boolean = granted() && root.isDirectory

    /**
     * Завести папку и полки. Отвечает тем же, чем [ready] ответит после.
     *
     * Зовётся при каждом запуске и после выдачи разрешения: папку удаляют
     * проводником так же легко, как заводят, и приложение должно уметь
     * встретить её отсутствие молча, а не отказом посреди работы.
     */
    fun prepare(): Boolean {
        if (!granted()) return false
        return runCatching {
            if (!root.exists()) root.mkdirs()
            Shelf.entries.forEach { shelf ->
                val dir = shelfDir(shelf)
                if (!dir.exists()) dir.mkdirs()
            }
            root.isDirectory
        }.getOrDefault(false)
    }

    /**
     * Завести пустой файл на полке и отдать его.
     *
     * Имя приходит с отметкой времени впереди — по ней файлы в папке идут по
     * порядку добавления; занятое имя разводится числом в скобках, а не
     * затирается: в папке, куда складывают, потерять прежнее из-за совпадения
     * имён нельзя.
     *
     * Файл возвращается **не показанным системе**: пока в него пишут, он
     * неполон, и [publish] зовётся после записи. Оборвалась запись — файл
     * убирают [discard], и следов не остаётся.
     */
    fun newFile(shelf: Shelf, name: String, mime: String): File? {
        if (!prepare()) return null
        val dir = shelfDir(shelf)
        val target = free(dir, stamped(name, mime))
        return runCatching { if (target.createNewFile()) target else null }.getOrNull()
    }

    /**
     * Сказать системе о готовом файле и отдать ссылку на него.
     *
     * Ссылка — та, что вернул сканер (`content://media/…`): её читает и чужое
     * приложение, которому её передали. Сканер промолчал или не знает такого
     * типа — остаётся `file://`, и она тоже читается, просто своими силами.
     *
     * Ждёт ответа сканера и потому зовётся только с рабочего потока. Не
     * `suspend`: её зовёт и [PendingVideo][app.askya.video.PendingVideo],
     * которому пересборка контейнера не оставляет места для приостановки.
     */
    fun publish(file: File): String = scan(file) ?: Uri.fromFile(file).toString()

    /** Запись оборвалась — убрать недописанное совсем. */
    fun discard(file: File) {
        runCatching { file.delete() }
    }

    /**
     * Записать в библиотеку то, что отдаётся потоком, — правку, коллаж, кадр.
     *
     * `null` означает, что файла не появилось: не хватило места, не дали
     * разрешения или запись оборвалась. Половина файла при этом не остаётся.
     */
    suspend fun write(
        shelf: Shelf,
        name: String,
        mime: String,
        body: (OutputStream) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val target = newFile(shelf, name, mime) ?: return@withContext null
        runCatching {
            target.outputStream().use(body)
            publish(target)
        }.getOrElse {
            discard(target)
            null
        }
    }

    /**
     * Скопировать чужой файл в библиотеку.
     *
     * Копия, а не ссылка, — ради этого библиотека и заведена: исходник после
     * этого можно стереть, и в Askya ничего не пропадёт.
     */
    suspend fun copyIn(source: Uri, name: String, mime: String, shelf: Shelf? = null): String? =
        withContext(Dispatchers.IO) {
            val where = shelf ?: shelfFor(mime, name)
            write(where, name, mime) { out ->
                context.contentResolver.openInputStream(source)?.use { input ->
                    input.copyTo(out)
                } ?: error("файл не открылся на чтение")
            }
        }

    /** Лежит ли файл по этой ссылке в библиотеке. Чужие ссылки трогать нельзя. */
    fun isOurs(uri: String?): Boolean = fileOf(uri) != null

    /**
     * Файл библиотеки по ссылке — или `null`, если ссылка ведёт наружу.
     *
     * Путь сверяется по канонической форме: «..» в чужой ссылке иначе увела бы
     * удаление за пределы папки.
     */
    fun fileOf(uri: String?): File? {
        val parsed = uri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        val path = when (parsed.scheme) {
            "file" -> parsed.path
            "content" -> pathOfMedia(parsed)
            else -> null
        } ?: return null
        val base = runCatching { root.canonicalPath }.getOrNull() ?: return null
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.path.startsWith(base + File.separator) && it.exists() }
    }

    /** Убрать файл библиотеки — и из папки, и из индекса системы. */
    suspend fun remove(uri: String?) {
        val file = fileOf(uri) ?: return
        withContext(Dispatchers.IO) {
            runCatching { file.delete() }
            scan(file)
        }
    }

    /** Что лежит на полке — свежее сверху. */
    suspend fun files(shelf: Shelf): List<File> = withContext(Dispatchers.IO) {
        if (!ready()) return@withContext emptyList()
        shelfDir(shelf).listFiles().orEmpty()
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }
    }

    /** Сколько чего лежит — для строки в настройках и для окна библиотеки. */
    suspend fun counts(): Map<Shelf, Int> = withContext(Dispatchers.IO) {
        Shelf.entries.associateWith { shelf ->
            if (!ready()) 0 else shelfDir(shelf).listFiles().orEmpty().count { it.isFile }
        }
    }

    /** Сколько места занято библиотекой, в байтах. */
    suspend fun weight(): Long = withContext(Dispatchers.IO) {
        if (!ready()) return@withContext 0L
        Shelf.entries.sumOf { shelf ->
            shelfDir(shelf).listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
        }
    }

    /**
     * Полка по типу содержимого.
     *
     * Тип, а не расширение: он приходит от того, кто отдал файл, и врёт реже.
     * Не сказали типа — смотрим на хвост имени; не сказали ничего — «Файлы»:
     * полка, на которой лежит всё, чему не нашлось своей.
     */
    fun shelfFor(mime: String?, name: String = ""): Shelf {
        val type = mime?.lowercase().orEmpty().ifBlank {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                .orEmpty()
        }
        return when {
            type.startsWith("image/") -> Shelf.PHOTOS
            type.startsWith("audio/") -> Shelf.MUSIC
            type.startsWith("video/") -> Shelf.VIDEO
            else -> Shelf.FILES
        }
    }

    /**
     * Показать файл системе и отдать ссылку, которую она на него завела.
     *
     * `scanFile` отвечает обратным вызовом; ждать его приходится, потому что
     * ссылку возвращают наружу. Ожидание ограничено: сканер, промолчавший
     * полторы секунды, скорее всего не ответит вовсе, а держать из-за него
     * запись незаконченной нельзя — файл на диске уже целый.
     */
    private fun scan(file: File): String? {
        val lock = Object()
        var answer: String? = null
        var done = false

        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, uri ->
                synchronized(lock) {
                    answer = uri?.toString()
                    done = true
                    lock.notifyAll()
                }
            }
            synchronized(lock) {
                val until = System.currentTimeMillis() + SCAN_PATIENCE_MS
                while (!done) {
                    val left = until - System.currentTimeMillis()
                    if (left <= 0) break
                    lock.wait(left)
                }
            }
        }
        return answer
    }

    /** Путь файла за ссылкой MediaStore — по нему решается, наш ли это файл. */
    private fun pathOfMedia(uri: Uri): String? = runCatching {
        @Suppress("DEPRECATION")
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /**
     * Имя файла: отметка времени, очищенное имя и хвост по типу.
     *
     * Хвост берётся из имени, если он там есть и похож на расширение, и только
     * потом выводится из типа: переименованный в «.mp4» матрёшечный контейнер
     * от этого mp4 не станет, а вот открываться перестанет.
     */
    private fun stamped(name: String, mime: String): String {
        val given = name.substringAfterLast('/').substringAfterLast('\\')
        // Имя, уже помеченное временем, помечается один раз: сюда приходит и
        // то, что собрал ImageStore по своему правилу, и «20260904-120000-
        // 20260904-120000-снимок.jpg» читалось бы как ошибка, а не как порядок.
        val clean = given.replaceFirst(ALREADY_STAMPED, "")
        val ext = clean.substringAfterLast('.', "").lowercase()
            .takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.lowercase())
            ?: ""
        val base = clean.substringBeforeLast('.', clean)
            .replace(UNSAFE, "_")
            .trim('_')
            .take(48)
            .ifBlank { "askya" }
        return STAMP.format(LocalDateTime.now()) + "-" + base + if (ext.isEmpty()) "" else ".$ext"
    }

    /** Свободное имя в папке: занятое разводится числом, а не затирается. */
    private fun free(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        for (n in 2..999) {
            val next = File(dir, base + " ($n)" + if (ext.isEmpty()) "" else ".$ext")
            if (!next.exists()) return next
        }
        return first
    }

    private companion object {
        const val FOLDER = "Askya"
        const val SCAN_PATIENCE_MS = 1500L
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        val ALREADY_STAMPED = Regex("^\\d{8}-\\d{6}-")
        val UNSAFE = Regex("[^\\p{L}\\p{N}._-]+")
    }
}
