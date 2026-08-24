package app.askya.domain.docs

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Чем документ является для Askya.
 *
 * Не тип из системы и не расширение по отдельности, а вывод из обоих:
 * провайдеры отдают за `.epub` то `application/epub+zip`, то
 * `application/octet-stream`, то пустую строку, — а расширение врёт реже.
 */
enum class DocFormat {
    /** Своя заметка: файла за ней нет. */
    NOTE,
    IMAGE,
    PDF,

    /** Простой текст и разметка: `.txt`, `.md`, `.csv`, `.log`. */
    TEXT,

    /** Книга: `.epub`, `.fb2`, `.fb2.zip`. */
    BOOK,

    /** Word: `.docx`. */
    WORD,

    /** Excel: `.xlsx`. */
    EXCEL,

    /** Всё прочее — его показывает чужое приложение. */
    OTHER,
}

/**
 * Что это за документ — по имени и типу.
 *
 * Имя считается вернее типа: тип приходит от провайдера, и у половины из них
 * всё, кроме картинок, — `application/octet-stream`. Тип остаётся запасным
 * ответом для файлов без расширения.
 */
fun documentFormat(name: String, mime: String): DocFormat {
    val lower = name.lowercase().trim()
    val type = mime.lowercase()

    return when {
        lower.endsWith(".epub") || lower.endsWith(".fb2") || lower.endsWith(".fb2.zip") -> DocFormat.BOOK
        lower.endsWith(".docx") -> DocFormat.WORD
        lower.endsWith(".xlsx") || lower.endsWith(".xlsm") -> DocFormat.EXCEL
        lower.endsWith(".pdf") -> DocFormat.PDF
        // `.rtf` сюда не входит нарочно: внутри него разметка вперемешку с
        // текстом, и показанный как текст он читается набором команд.
        lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown") ||
            lower.endsWith(".csv") || lower.endsWith(".log") -> DocFormat.TEXT

        type.startsWith("image/") -> DocFormat.IMAGE
        type == "application/pdf" -> DocFormat.PDF
        type.contains("epub") || type.contains("fb2") -> DocFormat.BOOK
        type.contains("wordprocessingml") -> DocFormat.WORD
        type.contains("spreadsheetml") -> DocFormat.EXCEL
        type.startsWith("text/") -> DocFormat.TEXT

        else -> DocFormat.OTHER
    }
}

/**
 * Книга, разобранная по главам, — или `null`, если это не книга.
 *
 * Что внутри — epub или fb2, — решает не расширение, а первые байты: книги
 * приходят и как `.fb2.zip`, и как `.epub` с fb2 внутри, и просто с чужим
 * именем. Zip разбирается как epub, а если внутри лежит fb2 — как fb2.
 */
suspend fun readBook(context: Context, uri: String): BookText? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.buffered()?.use { stream ->
            if (looksZipped(stream)) {
                val parts = readArchive(stream) { name -> keepBookEntry(name) }
                val fb2 = parts.entries.firstOrNull { it.key.endsWith(".fb2", ignoreCase = true) }
                if (fb2 != null) parseFb2(fb2.value.asMarkup()) else parseEpub(parts)
            } else {
                parseFb2(readCapped(stream).asMarkup())
            }
        }
    }.getOrNull()
}

/**
 * Word или Excel — разметкой Askya.
 *
 * `null` означает и «не прочиталось», и «внутри пусто»: для страницы это одно
 * и то же — показывать нечего.
 */
suspend fun readOfficeDocument(
    context: Context,
    uri: String,
    format: DocFormat,
): String? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.buffered()?.use { stream ->
            if (!looksZipped(stream)) return@use null
            when (format) {
                DocFormat.WORD -> parseDocx(readArchive(stream) { it == "word/document.xml" })
                DocFormat.EXCEL -> parseXlsx(readArchive(stream) { it.startsWith("xl/") && it.endsWith(".xml") })
                else -> null
            }
        }
    }.getOrNull()
}

/**
 * Zip ли это.
 *
 * Смотрится подпись в первых байтах, а не имя: `.docx`, `.xlsx`, `.epub` и
 * `.fb2.zip` — всё это zip, а старые `.doc` и `.xls` с теми же на вид именами
 * — нет, и разбирать их как архив бессмысленно.
 *
 * Поток после проверки остаётся нетронутым: читать его дальше будет разбор
 * архива, и потерять первые четыре байта нельзя.
 */
private fun looksZipped(stream: InputStream): Boolean {
    if (!stream.markSupported()) return false
    stream.mark(4)
    val head = ByteArray(4)
    var read = 0
    while (read < 4) {
        val step = stream.read(head, read, 4 - read)
        if (step < 0) break
        read += step
    }
    stream.reset()
    return read == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
}

/** Что внутри книги стоит читать: разметка, а не картинки со шрифтами. */
private fun keepBookEntry(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm") ||
        lower.endsWith(".xml") || lower.endsWith(".opf") || lower.endsWith(".ncx") ||
        lower.endsWith(".fb2")
}

/** Чтение с потолком: подсунутый гигабайт не должен класть приложение. */
private fun readCapped(stream: InputStream, limit: Int = MAX_BOOK_BYTES): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (total < limit) {
        val read = stream.read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}

private const val MAX_BOOK_BYTES = 24 * 1024 * 1024
