package app.askya.domain.docs

/**
 * Кто слушает разобранную разметку: открывающие теги, закрывающие и текст
 * между ними.
 *
 * Ни дерева, ни узлов: всё, что читает Askya, — это поток «начался абзац,
 * такие-то буквы, абзац кончился». Дерево пришлось бы держать в памяти целиком
 * ради книги, из которой всё равно берётся один текст подряд.
 */
internal interface MarkupSink {
    fun open(name: String, attributes: Map<String, String>) = Unit
    fun close(name: String) = Unit
    fun text(value: String) = Unit
}

/**
 * Разбор разметки — общий для всего, что Askya читает: fb2, xml из docx и
 * xlsx.
 *
 * Свой, а не `XmlPullParser`: системный разборщик строг — он падает на не
 * закрытом `<br>` и на неизвестной сущности, а такого в книгах, скачанных
 * откуда попало, полно. Ещё он живёт в Android, и разбор книги нельзя было бы
 * проверить обычным тестом на машине — а именно здесь ошибка всего вероятнее.
 *
 * Терпимость и есть смысл этого кода: незакрытые теги, чужие пространства
 * имён, `<!DOCTYPE>`, комментарии и CDATA не должны мешать достать текст.
 * Ничего проверять он не берётся — только доставать.
 *
 * Имена тегов приводятся к строчным вместе с пространством: `w:p`, `fb:p`.
 * Так они и пишутся в форматах, и разделять их незачем — совпадений между
 * `w:t` из Word и `t` из Excel не бывает в одном файле.
 */
internal fun scanMarkup(source: String, sink: MarkupSink) {
    var at = 0
    val text = StringBuilder()

    fun flush() {
        if (text.isEmpty()) return
        sink.text(decodeEntities(text.toString()))
        text.setLength(0)
    }

    while (at < source.length) {
        val char = source[at]
        if (char != '<') {
            text.append(char)
            at++
            continue
        }

        flush()

        when {
            source.startsWith("<!--", at) -> {
                at = after(source, "-->", at)
                continue
            }
            source.startsWith("<![CDATA[", at) -> {
                val end = source.indexOf("]]>", at)
                val stop = if (end < 0) source.length else end
                // Внутри CDATA сущностей нет: там всё уже написано буквами.
                sink.text(source.substring(at + 9, stop))
                at = if (end < 0) source.length else end + 3
                continue
            }
            source.startsWith("<?", at) || source.startsWith("<!", at) -> {
                at = after(source, ">", at)
                continue
            }
        }

        val end = tagEnd(source, at)
        val raw = source.substring(at + 1, end).trim()
        at = if (end < source.length) end + 1 else source.length
        if (raw.isEmpty()) continue

        val closing = raw.startsWith("/")
        val empty = raw.endsWith("/")
        val body = raw.trim('/', ' ', '\t', '\n', '\r')
        if (body.isEmpty()) continue

        val nameEnd = body.indexOfFirst { it.isWhitespace() }
        val name = (if (nameEnd < 0) body else body.substring(0, nameEnd)).lowercase()
        if (name.isEmpty()) continue

        if (closing) {
            sink.close(name)
        } else {
            val attributes = if (nameEnd < 0) emptyMap() else parseAttributes(body.substring(nameEnd))
            sink.open(name, attributes)
            // Одиночный тег закрывается сам: слушающему всё равно, написали
            // ему `<br/>` или `<br></br>`.
            if (empty) sink.close(name)
        }
    }

    flush()
}

/** Где кончается тег. Кавычки считаются: в них может лежать и «>». */
private fun tagEnd(source: String, start: Int): Int {
    var at = start + 1
    var quote = ' '
    while (at < source.length) {
        val char = source[at]
        when {
            quote != ' ' -> if (char == quote) quote = ' '
            char == '"' || char == '\'' -> quote = char
            char == '>' -> return at
        }
        at++
    }
    return source.length
}

/** Сразу за найденным куском; не нашлось — значит, до конца. */
private fun after(source: String, mark: String, from: Int): Int {
    val found = source.indexOf(mark, from)
    return if (found < 0) source.length else found + mark.length
}

/**
 * Свойства тега. Без кавычек тоже разбирается: в html их часто не ставят, а
 * `width=100` не повод потерять весь тег.
 */
private fun parseAttributes(source: String): Map<String, String> {
    val attributes = LinkedHashMap<String, String>()
    var at = 0

    while (at < source.length) {
        while (at < source.length && (source[at].isWhitespace() || source[at] == '/')) at++
        if (at >= source.length) break

        val nameStart = at
        while (at < source.length && source[at] != '=' && !source[at].isWhitespace()) at++
        val name = source.substring(nameStart, at).lowercase()
        if (name.isEmpty()) break

        while (at < source.length && source[at].isWhitespace()) at++
        if (at >= source.length || source[at] != '=') {
            // Свойство без значения — «есть» и «нет» отличаются самим наличием.
            attributes[name] = ""
            continue
        }
        at++
        while (at < source.length && source[at].isWhitespace()) at++
        if (at >= source.length) break

        val value: String
        val quote = source[at]
        if (quote == '"' || quote == '\'') {
            at++
            val start = at
            while (at < source.length && source[at] != quote) at++
            value = source.substring(start, at)
            if (at < source.length) at++
        } else {
            val start = at
            while (at < source.length && !source[at].isWhitespace()) at++
            value = source.substring(start, at)
        }
        attributes[name] = decodeEntities(value)
    }

    return attributes
}

/**
 * Сущности — буквами.
 *
 * По именам разбираются только те, что вправду встречаются в книгах; всё
 * остальное записано числом, и числа разбираются целиком. Неизвестное имя
 * остаётся как есть: «&copy;» посреди страницы лучше, чем пустое место.
 */
internal fun decodeEntities(source: String): String {
    if (!source.contains('&')) return source

    val out = StringBuilder(source.length)
    var at = 0

    while (at < source.length) {
        val char = source[at]
        if (char != '&') {
            out.append(char)
            at++
            continue
        }

        val end = source.indexOf(';', at)
        if (end < 0 || end - at > 12) {
            out.append(char)
            at++
            continue
        }

        val body = source.substring(at + 1, end)
        val decoded = when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.drop(2).toIntOrNull(16)?.let { code -> String(Character.toChars(code)) }
            body.startsWith("#") ->
                body.drop(1).toIntOrNull()?.let { code -> String(Character.toChars(code)) }
            else -> NAMED[body]
        }

        if (decoded == null) {
            out.append(char)
            at++
        } else {
            out.append(decoded)
            at = end + 1
        }
    }

    return out.toString()
}

/** Имена, которые вправду попадаются в книгах и документах. */
private val NAMED = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    // Неразрывный пробел — обычным: в тексте книги он ничем не отличается, а
    // мерка строки от него ломается.
    "nbsp" to " ",
    "mdash" to "—",
    "ndash" to "–",
    "shy" to "",
    "hellip" to "…",
    "laquo" to "«",
    "raquo" to "»",
    "ldquo" to "“",
    "rdquo" to "”",
    "lsquo" to "‘",
    "rsquo" to "’",
    "bull" to "•",
    "middot" to "·",
    "deg" to "°",
    "copy" to "©",
    "reg" to "®",
    "trade" to "™",
    "euro" to "€",
    "pound" to "£",
    "sect" to "§",
    "para" to "¶",
    "times" to "×",
    "divide" to "÷",
    "plusmn" to "±",
    "frac12" to "½",
    "frac14" to "¼",
)

/**
 * Пробелы в разметке — это вёрстка, а не текст: переводы строк, отступы
 * вложенности и двойные пробелы после тегов должны сойтись в один пробел.
 */
internal fun String.normalizeSpaces(): String =
    replace(' ', ' ').replace(SPACES, " ").trim()

private val SPACES = Regex("\\s+")
