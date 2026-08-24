package app.askya.domain.markdown

/** Выравнивание столбца таблицы — задаётся двоеточиями в строке-разделителе. */
enum class MdAlign { START, CENTER, END }

/** Строка списка. Вложенность на один уровень: глубже читать уже тяжело. */
data class MdItem(val text: String, val nested: Boolean = false)

/** Строка чек-листа. Вложенность — как у списка, на один уровень. */
data class MdTask(val text: String, val done: Boolean, val nested: Boolean = false)

/**
 * Блок разметки. Разбор построчный: Markdown — формат построчный, и дерево
 * узлов здесь не окупается.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullets(val items: List<MdItem>) : MdBlock
    data class Numbers(val items: List<MdItem>) : MdBlock
    data class Tasks(val items: List<MdTask>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lang: String, val text: String) : MdBlock
    data class Table(
        val head: List<String>,
        val rows: List<List<String>>,
        val aligns: List<MdAlign>,
    ) : MdBlock

    /** Картинка отдельным абзацем. Внутри строки она не встречается почти никогда. */
    data class Picture(val alt: String, val src: String) : MdBlock
    data class Footnote(val mark: String, val text: String) : MdBlock
    data object Rule : MdBlock
}

/**
 * Разбор Markdown.
 *
 * Поддержано то, что человек действительно пишет в заметке: заголовки, абзацы,
 * три вида списков, цитата, блок кода, таблица, картинка, сноска и черта.
 * Полный CommonMark сюда не тянется намеренно — это библиотека на сотню
 * килобайт ради синтаксиса, которого в заметках не бывает.
 *
 * Неразобранное не пропадает: строка, не подошедшая ни под одно правило,
 * становится абзацем и показывается как есть. Заметка человека важнее
 * чистоты разбора.
 */
object Markdown {

    // Три правила списка открыты наружу: по ним же разбирается написанное в
    // окне записи (`ListInput`). Список пишут одинаково всюду, и держать для
    // ввода вторую пару регулярных выражений значило бы разъезжаться с тем,
    // как та же строка прочитается потом.

    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val RULE = Regex("""^\s*([-*_])\s*\1\s*\1[\s\-*_]*$""")
    private val FENCE = Regex("""^\s*(```|~~~)\s*(\S*)\s*$""")
    internal val TASK = Regex("""^(\s*)[-*+]\s+\[([ xX])]\s+(.*)$""")
    internal val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
    internal val NUMBER = Regex("""^(\s*)\d+[.)]\s+(.*)$""")
    private val FOOTNOTE = Regex("""^\[\^([^]]+)]:\s*(.*)$""")
    private val PICTURE = Regex("""^!\[([^]]*)]\(([^)]*)\)\s*$""")
    private val DIVIDER = Regex("""^\s*\|?[\s:|-]*-[\s:|-]*\|?\s*$""")

    fun parse(source: String): List<MdBlock> {
        val lines = source.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val blocks = mutableListOf<MdBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            if (line.isBlank()) {
                i++
                continue
            }

            val fence = FENCE.matchEntire(line)
            if (fence != null) {
                val close = fence.groupValues[1]
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(close)) {
                    body += lines[i]
                    i++
                }
                // Незакрытая ограда — не повод терять текст: доходим до конца.
                if (i < lines.size) i++
                blocks += MdBlock.Code(fence.groupValues[2], body.joinToString("\n").trimEnd())
                continue
            }

            if (RULE.matchEntire(line) != null) {
                blocks += MdBlock.Rule
                i++
                continue
            }

            val heading = HEADING.matchEntire(line)
            if (heading != null) {
                blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
                i++
                continue
            }

            val footnote = FOOTNOTE.matchEntire(line)
            if (footnote != null) {
                blocks += MdBlock.Footnote(footnote.groupValues[1], footnote.groupValues[2].trim())
                i++
                continue
            }

            val picture = PICTURE.matchEntire(line)
            if (picture != null) {
                blocks += MdBlock.Picture(picture.groupValues[1], picture.groupValues[2])
                i++
                continue
            }

            if (line.trimStart().startsWith(">")) {
                val body = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    body += lines[i].trimStart().removePrefix(">").removePrefix(" ")
                    i++
                }
                blocks += MdBlock.Quote(paragraphsOf(body))
                continue
            }

            // Таблица узнаётся по второй строке: без разделителя это просто
            // абзац с палками, и ломать его в таблицу было бы неверно.
            if (line.trimStart().startsWith("|") && i + 1 < lines.size &&
                DIVIDER.matchEntire(lines[i + 1]) != null &&
                lines[i + 1].contains('-')
            ) {
                val head = cellsOf(line)
                val aligns = cellsOf(lines[i + 1]).map(::alignOf)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows += cellsOf(lines[i])
                    i++
                }
                blocks += MdBlock.Table(head, rows, aligns)
                continue
            }

            if (TASK.matchEntire(line) != null) {
                val items = mutableListOf<MdTask>()
                while (i < lines.size) {
                    val m = TASK.matchEntire(lines[i]) ?: break
                    items += MdTask(
                        text = m.groupValues[3].trim(),
                        done = m.groupValues[2].lowercase() == "x",
                        nested = m.groupValues[1].length >= 2,
                    )
                    i++
                }
                blocks += MdBlock.Tasks(items)
                continue
            }

            if (BULLET.matchEntire(line) != null) {
                val items = mutableListOf<MdItem>()
                while (i < lines.size) {
                    if (TASK.matchEntire(lines[i]) != null) break
                    val m = BULLET.matchEntire(lines[i]) ?: break
                    items += MdItem(m.groupValues[2].trim(), nested = m.groupValues[1].length >= 2)
                    i++
                }
                blocks += MdBlock.Bullets(items)
                continue
            }

            if (NUMBER.matchEntire(line) != null) {
                val items = mutableListOf<MdItem>()
                // Пустая строка между пунктами список не заканчивает.
                //
                // Номер пункта берётся не из написанного, а из места в списке:
                // человек набирает «1.» на каждой строке — так делают и
                // редакторы, которые дописывают маркер сами, — а показать это
                // должно единицей, двойкой, тройкой. Но пока пустая строка
                // рвала список на блоки, счёт в каждом начинался заново, и
                // весь список выходил из одних единиц.
                var at: Int? = i
                while (at != null) {
                    val m = NUMBER.matchEntire(lines[at]) ?: break
                    items += MdItem(m.groupValues[2].trim(), nested = m.groupValues[1].length >= 2)
                    i = at + 1
                    at = numberAfter(lines, i)
                }
                blocks += MdBlock.Numbers(items)
                continue
            }

            // Абзац: соседние строки остаются строками.
            //
            // CommonMark склеил бы их в одну и переносил бы по ширине сам —
            // но здесь этим читают не только чужие .md, а прежде всего свои
            // заметки, а в заметке перенос ставят руками и он значит перенос.
            // Список дат, набранный столбиком, склеенный в строку — это уже
            // не то, что человек записал.
            val body = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank() && plain(lines[i])) {
                body += lines[i].trim()
                i++
            }
            if (body.isEmpty()) {
                // Строка не подошла ни под одно правило и не считается простой:
                // отдаём как есть, чтобы текст не пропал.
                body += line.trim()
                i++
            }
            blocks += MdBlock.Paragraph(body.joinToString("\n"))
        }

        return blocks
    }

    /**
     * Следующий пункт нумерованного списка через пустые строки — или `null`,
     * если дальше идёт что угодно другое и список кончился.
     */
    private fun numberAfter(lines: List<String>, from: Int): Int? {
        var at = from
        while (at < lines.size && lines[at].isBlank()) at++
        return at.takeIf { it < lines.size && NUMBER.matchEntire(lines[it]) != null }
    }

    /** Строка, с которой не начинается никакой блок. */
    private fun plain(line: String): Boolean =
        HEADING.matchEntire(line) == null &&
            RULE.matchEntire(line) == null &&
            FENCE.matchEntire(line) == null &&
            BULLET.matchEntire(line) == null &&
            NUMBER.matchEntire(line) == null &&
            FOOTNOTE.matchEntire(line) == null &&
            PICTURE.matchEntire(line) == null &&
            !line.trimStart().startsWith(">") &&
            !line.trimStart().startsWith("|")

    private fun paragraphsOf(lines: List<String>): String =
        lines.joinToString("\n").trim().replace(Regex("\n{3,}"), "\n\n")

    private fun cellsOf(row: String): List<String> =
        row.trim().trim('|').split('|').map { it.trim() }

    private fun alignOf(cell: String): MdAlign = when {
        cell.startsWith(":") && cell.endsWith(":") -> MdAlign.CENTER
        cell.endsWith(":") -> MdAlign.END
        else -> MdAlign.START
    }
}
