package app.askya.domain.docs

/**
 * Документ Word — разметкой Askya.
 *
 * `.docx` — это zip, внутри которого `word/document.xml`: абзацы (`w:p`),
 * куски текста (`w:t`), таблицы (`w:tbl`) и ссылки на стили. Из всего этого
 * берётся то, что есть в разметке Askya: заголовки, абзацы, списки и таблицы.
 * Шрифты, поля, колонтитулы и цвета отбрасываются — документ читают на
 * кремовой странице Scroll, а не показывают, каким его свёрстали.
 *
 * Без Apache POI и прочих библиотек: они тянут по десятку мегабайт и половину
 * java.desktop, которой на Android нет. Из вордовского xml текст достаётся
 * тем же разбором разметки, что и книга.
 *
 * Заголовок узнаётся по имени стиля (`Heading2`, «Заголовок 2»): номер в конце
 * имени и есть уровень. Стиль без номера считается заголовком первого уровня.
 *
 * Старый `.doc` (до 2007 года) сюда не относится: это не zip и не xml, а
 * двоичный формат Word 97, и разбирать его пришлось бы отдельной библиотекой.
 * Такой файл Scroll по-прежнему отдаёт наружу.
 */
internal fun parseDocx(parts: Map<String, ByteArray>): String? {
    val document = parts["word/document.xml"]?.asMarkup() ?: return null

    val out = StringBuilder()
    val line = StringBuilder()
    var heading = 0
    var bullet = false
    var hidden = 0

    // Таблица собирается целиком: разметке нужен разделитель после первой
    // строки, а узнать, что строка первая, можно только собрав таблицу.
    var table: ArrayList<List<String>>? = null
    var row: ArrayList<String>? = null

    fun take(): String {
        val text = line.toString().normalizeSpaces()
        line.setLength(0)
        return text
    }

    fun flush() {
        val text = take()
        if (text.isEmpty()) return
        out.append(
            when {
                heading > 0 -> "#".repeat(heading.coerceIn(1, 6)) + " " + text
                bullet -> "- $text"
                else -> text
            },
        )
        out.append("\n\n")
    }

    scanMarkup(
        document,
        object : MarkupSink {
            override fun open(name: String, attributes: Map<String, String>) {
                when (name) {
                    // Удалённое при правках лежит в документе рядом с
                    // оставленным: показывать его — значит показывать текст,
                    // которого в документе уже нет.
                    "w:del", "w:instrtext" -> hidden++
                    "w:tbl" -> {
                        flush()
                        table = ArrayList()
                    }
                    "w:tr" -> row = ArrayList()
                    "w:tc" -> line.setLength(0)
                    "w:pstyle" -> {
                        val style = attributes["w:val"].orEmpty()
                        heading = headingLevel(style)
                    }
                    "w:numpr" -> bullet = true
                    "w:tab" -> line.append(' ')
                    "w:br", "w:cr" -> line.append(' ')
                }
            }

            override fun close(name: String) {
                when (name) {
                    "w:del", "w:instrtext" -> if (hidden > 0) hidden--
                    "w:p" -> {
                        // Абзац внутри ячейки не выливается на страницу: он
                        // остаётся строкой этой ячейки.
                        if (row == null) {
                            flush()
                            heading = 0
                            bullet = false
                        } else {
                            line.append(' ')
                        }
                    }
                    "w:tc" -> row?.add(take().replace("|", "\\|"))
                    "w:tr" -> {
                        val cells = row ?: return
                        row = null
                        if (cells.any { it.isNotEmpty() }) table?.add(cells)
                    }
                    "w:tbl" -> {
                        val rows = table ?: return
                        table = null
                        out.append(markdownTable(rows))
                    }
                }
            }

            override fun text(value: String) {
                if (hidden > 0) return
                line.append(value)
            }
        },
    )

    flush()
    return out.toString().trim().ifEmpty { null }
}

/**
 * Таблица разметкой: шапка, черта под ней и строки.
 *
 * Черта обязательна — без неё разбор разметки видит просто абзац с палками.
 * Первая строка становится шапкой: в документах она почти всегда ею и
 * задумана, а таблица без шапки читается и так.
 */
internal fun markdownTable(rows: List<List<String>>): String {
    if (rows.isEmpty()) return ""
    val width = rows.maxOf { it.size }
    if (width == 0) return ""

    val out = StringBuilder()
    rows.forEachIndexed { index, cells ->
        val line = (0 until width).joinToString(" | ") { cells.getOrElse(it) { "" } }
        out.append("| ").append(line).append(" |\n")
        if (index == 0) {
            out.append("|").append(" --- |".repeat(width)).append("\n")
        }
    }
    out.append("\n")
    return out.toString()
}

/**
 * Уровень заголовка по имени стиля.
 *
 * Имена бывают какие угодно — `Heading1`, `heading 2`, `Заголовок3`, `Title`,
 * — поэтому смотрится не всё имя, а два признака: похоже ли оно на заголовок
 * и есть ли в нём номер. Всё остальное — обычный абзац.
 */
private fun headingLevel(style: String): Int {
    val lower = style.lowercase()
    val heading = lower.startsWith("heading") || lower.startsWith("заголовок") ||
        lower == "title" || lower == "название" || lower == "subtitle" || lower == "подзаголовок"
    if (!heading) return 0
    val number = lower.filter { it.isDigit() }.toIntOrNull()
    return when {
        number != null -> number.coerceIn(1, 6)
        lower == "subtitle" || lower == "подзаголовок" -> 2
        else -> 1
    }
}
