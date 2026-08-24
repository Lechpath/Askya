package app.askya.domain.docs

/**
 * Страница книги из xhtml — абзацами и заголовками.
 *
 * Внутри epub лежит обычный html, свёрстанный кем угодно и как угодно: у
 * одного издателя абзац — это `<p>`, у другого `<div class="text">`, у
 * третьего строка с `<br/>`. Поэтому границей абзаца считается любой блочный
 * тег, а не какой-то один: текст между границами и есть абзац.
 *
 * Оформление отбрасывается целиком — стили, скрипты, разметка таблиц. Askya
 * показывает книгу своей страницей (кремовой или ночной, своим кеглем), и
 * чужие шрифты с отступами тут не только не нужны, но и мешали бы.
 *
 * Пустые абзацы выбрасываются: в книгах их ставят вместо отступов десятками, и
 * прочитанные буквально они превратили бы страницу в лестницу пустот.
 */
internal fun htmlBlocks(source: String): List<BookBlock> {
    val blocks = ArrayList<BookBlock>()
    val current = StringBuilder()
    var heading = 0
    var hidden = 0
    var list = false

    fun flush() {
        val text = current.toString().normalizeSpaces()
        current.setLength(0)
        if (text.isEmpty()) return
        blocks += if (heading > 0) {
            BookBlock.Heading(level = heading, text = text)
        } else {
            BookBlock.Paragraph(text = if (list) "• $text" else text)
        }
    }

    scanMarkup(
        source,
        object : MarkupSink {
            override fun open(name: String, attributes: Map<String, String>) {
                when (name) {
                    in SILENT -> hidden++
                    in HEADINGS -> {
                        flush()
                        heading = name.last().digitToIntOrNull() ?: 1
                    }
                    "li" -> {
                        flush()
                        list = true
                    }
                    "hr" -> {
                        flush()
                        blocks += BookBlock.Divider
                    }
                    in BREAKS -> flush()
                }
            }

            override fun close(name: String) {
                when (name) {
                    in SILENT -> if (hidden > 0) hidden--
                    in HEADINGS -> {
                        flush()
                        heading = 0
                    }
                    "li" -> {
                        flush()
                        list = false
                    }
                    in BREAKS -> flush()
                }
            }

            override fun text(value: String) {
                if (hidden > 0) return
                current.append(value)
            }
        },
    )

    flush()
    return blocks.trimEdges()
}

/** Первое заглавие страницы — им называется глава, если оглавление молчит. */
internal fun firstHeading(blocks: List<BookBlock>): String? =
    blocks.filterIsInstance<BookBlock.Heading>().firstOrNull()?.text?.takeIf { it.isNotBlank() }

/**
 * Пробелы в разметке — это верстка, а не текст: переводы строк, отступы
 * вложенности и двойные пробелы после тегов должны сойтись в один пробел.
 */
internal fun String.normalizeSpaces(): String =
    replace(' ', ' ').replace(SPACES, " ").trim()

/** Пустые разделители по краям — след от вёрстки, а не часть текста. */
private fun List<BookBlock>.trimEdges(): List<BookBlock> =
    dropWhile { it is BookBlock.Divider }.dropLastWhile { it is BookBlock.Divider }

private val SPACES = Regex("\\s+")

/** То, чьё содержимое на странице не показывают вовсе. */
private val SILENT = setOf("head", "style", "script", "title")

/** Заголовки: от них глава берёт своё имя. */
private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

/** Всё, что кончает абзац. */
private val BREAKS = setOf("p", "div", "br", "tr", "td", "th", "blockquote", "section", "article", "figcaption")
