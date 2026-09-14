package app.askya.domain.docs

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Заметка, вынутая из чужого файла: то, как её назвали, и то, что в ней. */
data class ImportedNote(val title: String, val body: String)

/**
 * Перенос заметок из другого приложения.
 *
 * ## Почему через файл, а не «подключиться»
 *
 * Ни у Keep, ни у Evernote, ни у Заметок Samsung нет способа отдать записи
 * другому приложению на телефоне: каждое держит их у себя и умеет ровно одно —
 * **выгрузить в файл**. Это не обход и не костыль, а единственная дверь,
 * которая там есть, и вдобавок самая честная: Askya не просит доступа к чужому
 * аккаунту, не ходит в сеть и не видит ничего, кроме того файла, который ей
 * дали сами.
 *
 * Поэтому перенос здесь — это «принесите выгрузку», а не «войдите в Google».
 *
 * ## Что она понимает
 *
 * Четыре вида файлов, и это не список ради списка — это ровно то, во что
 * выгружаются распространённые блокноты:
 *
 * — `.txt` и `.md` — Simplenote, Obsidian, Joplin, Standard Notes, «Заметки»
 *   почти любого телефона. Самый частый случай и самый простой: файл и есть
 *   заметка;
 * — `.enex` — Evernote. Внутри XML, в нём подряд лежат все записи разом;
 * — `.json` — выгрузка Google Keep («Google Такаут»): по файлу на заметку;
 * — `.html` — тот же Keep, а ещё Notion, Zoho и десяток других: разметка
 *   выбрасывается, остаётся текст;
 * — `.zip` — то, во что всё перечисленное обычно и запаковано. Разбирается
 *   насквозь, потому что просить человека сначала распаковать архив
 *   проводником значило бы отправить его за вторым приложением ради первого.
 *
 * Чего она не делает: не тащит картинки, вложения и форматирование. Заметка в
 * Askya — это текст, и притворяться, будто перенеслось больше, чем перенеслось,
 * хуже, чем сказать прямо.
 *
 * ## Почему разбор здесь, а не в экране
 *
 * Здесь нет ни одного обращения к системе: на входе имя и байты, на выходе
 * список заметок. Значит, вся эта половина проверяется обычными тестами — а
 * ошибка в разборе чужого формата тем и опасна, что на экране выглядит как
 * «столько там и было».
 */
object NoteImport {

    /**
     * Что предлагать в системном выборе.
     *
     * Широко нарочно: провайдеры сплошь и рядом отдают выгрузке
     * `application/octet-stream`, и узкий список означал бы, что нужный файл в
     * выборе виден серым и не нажимается. Лишнее отсеет сам разбор.
     */
    val pickTypes = arrayOf(
        "text/*",
        "application/json",
        "application/zip",
        "application/xml",
        "application/octet-stream",
    )

    /**
     * Разобрать один принесённый файл.
     *
     * [depth] стережёт архив в архиве: вложенность выгрузкам не свойственна, а
     * зацикленный разбор — свойственен всякому, кто ходит вглубь.
     */
    fun read(name: String, bytes: ByteArray, depth: Int = 0): List<ImportedNote> {
        val kind = name.substringAfterLast('.', "").lowercase()
        return when (kind) {
            "zip" -> if (depth >= DEEP) emptyList() else fromZip(bytes, depth)
            "enex" -> fromEnex(text(bytes))
            "json" -> listOfNotNull(fromKeep(text(bytes)))
            "html", "htm" -> listOfNotNull(fromHtml(name, text(bytes)))
            else -> listOfNotNull(fromPlain(name, text(bytes)))
        }
    }

    /**
     * Архив насквозь.
     *
     * Папки, служебные файлы Такаута и всё, из чего не вышло ни одной заметки,
     * просто пропускаются: у выгрузки Keep рядом с заметками лежат метки,
     * настройки и `archive_browser.html`, и падать на них было бы странно.
     */
    private fun fromZip(bytes: ByteArray, depth: Int): List<ImportedNote> {
        val found = mutableListOf<ImportedNote>()
        runCatching {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/')
                    if (name.startsWith(".") || name.isBlank()) continue

                    val body = zip.readCapped()
                    if (body.isEmpty()) continue
                    found += read(name, body, depth + 1)
                    if (found.size >= MOST) break
                }
            }
        }
        return found.take(MOST)
    }

    /**
     * Evernote: один XML на всю тетрадь.
     *
     * Разбирается вручную, а не разборщиком XML, и это не лень. Выгрузки
     * Evernote славятся тем, что XML в них не всегда правильный: неэкранированные
     * амперсанды, обрубленный конец, встроенные вложения в base64 на десятки
     * мегабайт. Настоящий разборщик на первой же такой мелочи бросает всё, и
     * человек остаётся без единой заметки из двух тысяч; поиск по началу и
     * концу записи достаёт всё, что цело, и молча пропускает то, что нет.
     */
    internal fun fromEnex(xml: String): List<ImportedNote> {
        val notes = mutableListOf<ImportedNote>()
        var at = 0
        while (notes.size < MOST) {
            val from = xml.indexOf("<note>", at).takeIf { it >= 0 } ?: break
            val to = xml.indexOf("</note>", from).takeIf { it >= 0 } ?: break
            val piece = xml.substring(from, to)
            at = to + 1

            val title = tag(piece, "title").let(::unescape).trim()
            val content = tag(piece, "content")
            val body = stripTags(content).trim()
            if (title.isBlank() && body.isBlank()) continue
            notes += ImportedNote(title = title.ifBlank { firstLine(body) }, body = body)
        }
        return notes
    }

    /**
     * Google Keep: по файлу на заметку.
     *
     * Выброшенные в корзину пропускаются: их выбросили, и переносить чужую
     * корзину в новый блокнот — значит начинать с уборки. Убранные в архив,
     * наоборот, переносятся: архив в Keep — это «сделано, но пусть лежит», и
     * это как раз то, что хранят.
     *
     * Список с галочками (`listContent`) складывается строчками с чертой в
     * начале: разметки списка в заметке Askya нет, а порядок и отметки терять
     * нельзя — по ним список и читают.
     */
    internal fun fromKeep(json: String): ImportedNote? = runCatching {
        val note = JSONObject(json)
        if (note.optBoolean("isTrashed", false)) return null

        val title = note.optString("title").trim()
        val text = note.optString("textContent").trim()

        val list = note.optJSONArray("listContent")
        val rows = buildList {
            for (at in 0 until (list?.length() ?: 0)) {
                val row = list?.optJSONObject(at) ?: continue
                val done = if (row.optBoolean("isChecked", false)) "— [x] " else "— [ ] "
                add(done + row.optString("text").trim())
            }
        }

        val body = listOf(text, rows.joinToString("\n"))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

        if (title.isBlank() && body.isBlank()) null
        else ImportedNote(title = title.ifBlank { firstLine(body) }, body = body)
    }.getOrNull()

    /** Страница: заголовок из `<title>`, если он есть, и текст без разметки. */
    internal fun fromHtml(name: String, html: String): ImportedNote? {
        val body = stripTags(html).trim()
        if (body.isBlank()) return null
        val title = tag(html, "title").let(::unescape).trim()
        return ImportedNote(
            title = title.ifBlank { plainName(name) }.ifBlank { firstLine(body) },
            body = body,
        )
    }

    /**
     * Текстовый файл — самый частый случай.
     *
     * Имя заметки берётся у файла: выгрузки так и устроены — файл называется
     * тем же, чем называлась заметка. Кроме одного случая: markdown, начатый
     * заголовком `# ...`. Тогда имя берётся у заголовка, а сам он из текста
     * убирается — иначе название стояло бы в заметке дважды.
     */
    internal fun fromPlain(name: String, text: String): ImportedNote? {
        val body = text.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (body.isBlank()) return null

        val head = body.lineSequence().first().trim()
        return if (head.startsWith("# ")) {
            ImportedNote(
                title = head.removePrefix("# ").trim(),
                body = body.substringAfter('\n', "").trim(),
            )
        } else {
            ImportedNote(title = plainName(name).ifBlank { firstLine(body) }, body = body)
        }
    }

    /**
     * Разметка — вон, текст — остаётся.
     *
     * Абзацы, переводы строки и пункты списков превращаются в переводы строки
     * до того, как теги выбрасываются: выброшенные вместе со всеми прочими,
     * они слепили бы страницу в одну строку на десять тысяч знаков.
     *
     * Строку даёт **закрывающий** тег, а не любой: у `</div><div>` их два
     * подряд, и по строке от каждого дало бы пустую строку между всякими
     * двумя строчками — чужая разметка разъехалась бы вдвое.
     *
     * Содержимое `script` и `style` выбрасывается целиком: это не текст
     * страницы, и в заметке от него остались бы фигурные скобки.
     */
    internal fun stripTags(html: String): String {
        var text = html
        text = CDATA.replace(text) { it.groupValues[1] }
        text = SCRIPT.replace(text, " ")
        text = BREAK.replace(text, "\n")
        text = CLOSED.replace(text, "\n")
        text = TAG.replace(text, "")
        text = unescape(text)
        // Больше двух пустых строк подряд не бывает ни в одной заметке — это
        // след разметки, а не то, как человек писал.
        return text.lines().joinToString("\n") { it.trimEnd() }
            .replace(GAPS, "\n\n")
            .trim()
    }

    /** Содержимое парного тега; пусто — тега нет. */
    private fun tag(xml: String, name: String): String {
        val from = xml.indexOf("<$name", ignoreCase = true, startIndex = 0).takeIf { it >= 0 }
            ?: return ""
        val opened = xml.indexOf('>', from).takeIf { it >= 0 } ?: return ""
        val to = xml.indexOf("</$name", opened, ignoreCase = true).takeIf { it >= 0 } ?: return ""
        return xml.substring(opened + 1, to)
    }

    /** Мнемоники HTML — те, что встречаются в выгрузках. */
    internal fun unescape(text: String): String {
        var out = text
        for ((sign, letter) in SIGNS) out = out.replace(sign, letter)
        out = NUMBERED.replace(out) { found ->
            val hex = found.groupValues[1].isNotEmpty()
            val code = found.groupValues[2].toIntOrNull(if (hex) 16 else 10)
                ?: return@replace found.value
            if (code in 1..0x10FFFF) String(Character.toChars(code)) else found.value
        }
        return out
    }

    /** Имя файла без расширения — им и назовётся заметка. */
    private fun plainName(name: String): String =
        name.substringAfterLast('/').substringBeforeLast('.').trim()

    /** Первая строчка текста: имя для того, у чего имени не оказалось. */
    private fun firstLine(body: String): String =
        body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60).orEmpty()

    /** Байты в текст: выгрузки бывают только в UTF-8, но битые байты не повод падать. */
    private fun text(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    /**
     * Прочитать запись архива, но не больше [BIGGEST].
     *
     * Предел не от жадности: в выгрузке Evernote рядом с текстом лежат
     * вложения в несколько десятков мегабайт, и попытка прочитать такую запись
     * целиком в память кончается тем, что перенос падает на середине.
     */
    private fun InputStream.readCapped(): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (out.size() < BIGGEST) {
            val read = read(chunk)
            if (read <= 0) break
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }

    /** Глубже этого в архив не заходим. */
    private const val DEEP = 2

    /** Больше этого за один перенос не берём: выгрузки бывают и на сто тысяч. */
    private const val MOST = 5_000

    /** Больше этого от одного файла не читаем. */
    private const val BIGGEST = 8 * 1024 * 1024

    private val CDATA = Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL)
    private val SCRIPT = Regex(
        "<(script|style)\\b[^>]*>.*?</\\1>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val BREAK = Regex("<\\s*br\\b[^>]*>", RegexOption.IGNORE_CASE)

    /** Строку даёт закрывающий тег блока — см. рассуждение в [stripTags]. */
    private val CLOSED = Regex(
        "</\\s*(p|div|li|tr|h[1-6]|en-note|blockquote|pre)\\s*>",
        RegexOption.IGNORE_CASE,
    )
    private val TAG = Regex("<[^>]*>", RegexOption.DOT_MATCHES_ALL)
    private val GAPS = Regex("\n{3,}")
    private val NUMBERED = Regex("&#(x?)([0-9a-fA-F]{1,7});", RegexOption.IGNORE_CASE)

    private val SIGNS = listOf(
        "&nbsp;" to " ",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&#39;" to "'",
        "&mdash;" to "—",
        "&ndash;" to "–",
        "&laquo;" to "«",
        "&raquo;" to "»",
        "&hellip;" to "…",
        // Амперсанд — последним: заменённый первым, он превратил бы «&amp;lt;»
        // в «<» и съел бы текст, который человек написал нарочно.
        "&amp;" to "&",
    )
}
