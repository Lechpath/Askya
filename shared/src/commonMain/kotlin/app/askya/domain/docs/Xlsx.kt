package app.askya.domain.docs

/**
 * Таблица Excel — разметкой Askya.
 *
 * `.xlsx` тоже zip: `xl/workbook.xml` перечисляет листы, файлы в `xl/worksheets`
 * держат строки и ячейки, а сами буквы вынесены в общий словарь
 * `xl/sharedStrings.xml` — в файле лежит номер слова, а не слово. Отсюда
 * порядок чтения: сперва словарь, потом листы.
 *
 * Каждый лист становится таблицей разметки со своим заголовком: смотрят в
 * таблицу ради чисел и подписей, и они переносятся целиком.
 *
 * Чего здесь нет и не будет: формул (в файле лежит и формула, и посчитанное ею
 * — берётся посчитанное), оформления и дат в человеческом виде. Дата в Excel
 * хранится числом дней от 1900 года, а её вид задан стилем ячейки; тянуть
 * разбор стилей ради этого — половина библиотеки, и Scroll честно показывает
 * число, а не выдумывает дату.
 *
 * Лист обрезается: таблица на десять тысяч строк — это не то, что читают с
 * телефона, и разложенная в разметку она положила бы экран. Сколько строк
 * осталось за краем, написано под таблицей.
 */
internal fun parseXlsx(parts: Map<String, ByteArray>): String? {
    val shared = parts["xl/sharedStrings.xml"]?.asMarkup()?.let(::parseSharedStrings) ?: emptyList()
    val targets = parts["xl/_rels/workbook.xml.rels"]?.asMarkup()?.let(::parseRelations) ?: emptyMap()
    val sheets = parts["xl/workbook.xml"]?.asMarkup()?.let(::parseSheets) ?: emptyList()

    // Опись листов бывает битой или отсутствует вовсе — тогда читаются все
    // листы подряд, по именам файлов: лучше таблица без имён, чем ничего.
    val ordered = sheets.ifEmpty {
        parts.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .sorted()
            .map { SheetRef(name = it.substringAfterLast('/').removeSuffix(".xml"), id = it) }
    }

    val out = StringBuilder()

    ordered.forEach { sheet ->
        val path = when {
            sheet.id.startsWith("xl/") -> sheet.id
            else -> targets[sheet.id]?.let { "xl/" + it.removePrefix("/xl/").removePrefix("./") }
        } ?: return@forEach
        val body = parts[path]?.asMarkup() ?: return@forEach

        val rows = parseSheet(body, shared)
        if (rows.isEmpty()) return@forEach

        if (ordered.size > 1 || sheet.name.isNotBlank()) {
            out.append("## ").append(sheet.name.ifBlank { "Лист" }).append("\n\n")
        }
        out.append(markdownTable(rows.take(MAX_ROWS)))
        if (rows.size > MAX_ROWS) {
            out.append("_Показаны первые ").append(MAX_ROWS)
                .append(" строк из ").append(rows.size).append("._\n\n")
        }
    }

    return out.toString().trim().ifEmpty { null }
}

/** Лист книги: как называется и где лежит. */
private class SheetRef(val name: String, val id: String)

/** Словарь общих строк: в ячейках вместо букв стоят номера из него. */
private fun parseSharedStrings(source: String): List<String> {
    val strings = ArrayList<String>()
    val text = StringBuilder()
    var inItem = false
    var inText = false

    scanMarkup(
        source,
        object : MarkupSink {
            override fun open(name: String, attributes: Map<String, String>) {
                when (name) {
                    "si" -> {
                        inItem = true
                        text.setLength(0)
                    }
                    "t" -> inText = true
                }
            }

            override fun close(name: String) {
                when (name) {
                    "si" -> {
                        strings += text.toString()
                        inItem = false
                        text.setLength(0)
                    }
                    "t" -> inText = false
                }
            }

            override fun text(value: String) {
                if (inItem && inText) text.append(value)
            }
        },
    )

    return strings
}

/** Куда какая ссылка ведёт: `rId3` → `worksheets/sheet3.xml`. */
private fun parseRelations(source: String): Map<String, String> {
    val relations = HashMap<String, String>()
    scanMarkup(
        source,
        object : MarkupSink {
            override fun open(name: String, attributes: Map<String, String>) {
                if (name != "relationship") return
                val id = attributes["id"] ?: return
                val target = attributes["target"] ?: return
                relations[id] = target
            }
        },
    )
    return relations
}

private fun parseSheets(source: String): List<SheetRef> {
    val sheets = ArrayList<SheetRef>()
    scanMarkup(
        source,
        object : MarkupSink {
            override fun open(name: String, attributes: Map<String, String>) {
                if (name != "sheet") return
                val id = attributes["r:id"] ?: attributes["id"] ?: return
                sheets += SheetRef(name = attributes["name"].orEmpty(), id = id)
            }
        },
    )
    return sheets
}

/**
 * Строки листа.
 *
 * Место ячейки берётся из её имени (`C7`), а не из порядка: пустые ячейки в
 * файл не пишутся вовсе, и без этого таблица со сдвинутым столбцом
 * рассыпалась бы.
 */
private fun parseSheet(source: String, shared: List<String>): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var cells = HashMap<Int, String>()
    var column = 0
    var kind = ""
    var inValue = false
    val value = StringBuilder()

    scanMarkup(
        source,
        object : MarkupSink {
            override fun open(name: String, attributes: Map<String, String>) {
                when (name) {
                    "row" -> cells = HashMap()
                    "c" -> {
                        column = columnOf(attributes["r"].orEmpty(), cells.size)
                        kind = attributes["t"].orEmpty()
                        value.setLength(0)
                    }
                    // `v` — посчитанное значение, `t` внутри `is` — буквы,
                    // вписанные прямо в ячейку.
                    "v", "t" -> inValue = true
                }
            }

            override fun close(name: String) {
                when (name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString().trim()
                        val text = when {
                            raw.isEmpty() -> ""
                            kind == "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                            kind == "b" -> if (raw == "1") "да" else "нет"
                            else -> trimNumber(raw)
                        }
                        if (text.isNotEmpty()) cells[column] = text.replace("|", "\\|")
                        value.setLength(0)
                    }
                    "row" -> {
                        if (cells.isNotEmpty()) {
                            val width = (cells.keys.maxOrNull() ?: -1) + 1
                            rows += (0 until minOf(width, MAX_COLUMNS)).map { cells[it].orEmpty() }
                        }
                        cells = HashMap()
                    }
                }
            }

            override fun text(text: String) {
                if (inValue) value.append(text)
            }
        },
    )

    // Строки в конце листа бывают пустыми: их в таблицу не тянем.
    return rows.dropLastWhile { row -> row.all { it.isBlank() } }
}

/**
 * Номер столбца из имени ячейки: `A` → 0, `AB` → 27.
 *
 * Имени может не быть — тогда ячейка встаёт следующей по счёту: так её и
 * задумывали, просто файл собран без имён.
 */
private fun columnOf(reference: String, fallback: Int): Int {
    val letters = reference.takeWhile { it.isLetter() }.uppercase()
    if (letters.isEmpty()) return fallback
    var index = 0
    letters.forEach { letter -> index = index * 26 + (letter - 'A' + 1) }
    return index - 1
}

/**
 * Число, каким его показывают: Excel пишет целые как «12.0», а длинные дроби —
 * со всем хвостом двоичного округления.
 */
private fun trimNumber(raw: String): String {
    val number = raw.toDoubleOrNull() ?: return raw
    if (number == number.toLong().toDouble() && kotlin.math.abs(number) < 1e15) {
        return number.toLong().toString()
    }
    return raw
}

/** Сколько строк листа попадает на страницу. */
private const val MAX_ROWS = 200

/** И сколько столбцов: шире экрана таблица всё равно не читается. */
private const val MAX_COLUMNS = 20
