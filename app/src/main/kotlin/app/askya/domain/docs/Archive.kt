package app.askya.domain.docs

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Внутренности сжатого документа — по имени файла.
 *
 * `.fb2.zip`, docx и xlsx устроены одинаково: это zip, внутри которого лежит
 * xml. Отсюда одно чтение на все три формата.
 *
 * Читается одним проходом, а не через `ZipFile`: тот умеет открывать только
 * настоящий файл на диске, а документ в Askya — это ссылка на чужой документ у
 * системного провайдера, и ради `ZipFile` его пришлось бы сначала целиком
 * копировать во временную папку.
 *
 * [keep] решает, что вообще доставать. В архиве с книгой рядом с текстом лежат
 * картинки и шрифты — мегабайты, из которых не читается ни один; беря всё
 * подряд, приложение раскладывало бы книгу в памяти целиком ради сотни
 * килобайт разметки.
 *
 * Потолок [limit] — на случай подсунутого гигабайта: лучше прочитать начало
 * книги, чем положить приложение по памяти.
 */
internal fun readArchive(
    stream: InputStream,
    limit: Int = MAX_ARCHIVE_BYTES,
    keep: (String) -> Boolean,
): Map<String, ByteArray> {
    val parts = LinkedHashMap<String, ByteArray>()
    var total = 0

    ZipInputStream(stream.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) {
                zip.closeEntry()
                continue
            }

            val name = entry.name.removePrefix("./")
            if (!keep(name) || total >= limit) {
                zip.closeEntry()
                continue
            }

            val body = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                body.write(buffer, 0, read)
                total += read
                if (total >= limit) break
            }
            zip.closeEntry()
            parts[name] = body.toByteArray()
        }
    }

    return parts
}

/**
 * Кусок документа буквами.
 *
 * Кодировка берётся из самой разметки (`<?xml encoding=…>` или `<meta
 * charset=…>`), а не считается заранее: xml почти всегда в utf-8, но старые
 * fb2 и книги из русских сборников бывают в windows-1251, и прочитанные как
 * utf-8 они превращаются в вопросительные знаки.
 *
 * Метка порядка байтов срезается: в начале первого абзаца она видна.
 */
internal fun ByteArray.asMarkup(): String {
    val head = String(this, 0, minOf(size, 200), Charsets.ISO_8859_1).lowercase()
    val charset = CHARSET.find(head)?.groupValues?.get(1)?.trim('"', '\'', ' ')
    val text = runCatching {
        if (charset.isNullOrEmpty()) toString(Charsets.UTF_8) else toString(charset(charset))
    }.getOrElse { toString(Charsets.UTF_8) }
    return text.removePrefix("\uFEFF")
}

private val CHARSET = Regex("(?:encoding|charset)\\s*=\\s*[\"']?([a-z0-9-]+)")

/** Сколько всего разметки достаётся из одного документа. */
private const val MAX_ARCHIVE_BYTES = 24 * 1024 * 1024

private const val BUFFER = 16 * 1024
