package app.askya.domain.docs

/**
 * Книга из fb2.
 *
 * Второй формат электронных книг, на который натыкаешься в русских
 * библиотеках: не архив, а один xml-файл, где текст уже разложен по разделам —
 * `body` → `section` → `p`. Разбирать его проще, чем epub, и читалке он
 * приходит тем же [BookText], что и epub: страница книги не должна знать, из
 * чего её собрали.
 *
 * Разделы верхнего уровня становятся главами, вложенные — заголовками внутри
 * главы: в fb2 вложенность бывает на четыре уровня, и делать из каждого
 * отдельную главу значило бы нарезать книгу на абзацы.
 *
 * Картинки (`binary`) пропускаются: они лежат тут же, закодированные буквами,
 * и один рисунок весит больше всей остальной книги.
 *
 * Сноски — тоже отдельные разделы в конце (`body name="notes"`), и они
 * читаются как обычные главы: сноска, которую нельзя открыть, лучше сноски,
 * которая пропала.
 */
internal fun parseFb2(source: String): BookText? {
    var title = ""
    var firstName = ""
    var lastName = ""

    val chapters = ArrayList<BookChapter>()
    var blocks = ArrayList<BookBlock>()
    var chapterTitle: String? = null

    val text = StringBuilder()
    var depth = 0
    var inBody = false
    var inTitle = false
    var heading = 0
    var hidden = 0
    var reading: String? = null

    fun flush() {
        val value = text.toString().normalizeSpaces()
        text.setLength(0)
        if (value.isEmpty()) return
        if (heading > 0) {
            blocks += BookBlock.Heading(level = heading, text = value)
            // Имя главы — её первое заглавие: в fb2 отдельного имени у раздела
            // нет, оно и есть заголовок внутри.
            if (chapterTitle == null) chapterTitle = value
        } else {
            blocks += BookBlock.Paragraph(value)
        }
    }

    fun closeChapter() {
        flush()
        val ready = BookChapter(
            title = chapterTitle?.normalizeSpaces() ?: "Глава ${chapters.size + 1}",
            blocks = blocks,
        )
        if (!ready.empty) chapters += ready
        blocks = ArrayList()
        chapterTitle = null
    }

    scanMarkup(
        source,
        object : MarkupSink {
            override fun open(name: String, attributes: Map<String, String>) {
                when (name) {
                    "binary" -> hidden++
                    "body" -> inBody = true
                    "section" -> {
                        if (inBody) {
                            flush()
                            depth++
                            // Новый раздел верхнего уровня — новая глава;
                            // вложенный продолжает ту же.
                            if (depth == 1) {
                                blocks = ArrayList()
                                chapterTitle = null
                            }
                        }
                    }
                    "title" -> {
                        flush()
                        inTitle = true
                        heading = if (depth <= 1) 1 else 2
                    }
                    "subtitle" -> {
                        flush()
                        heading = 3
                    }
                    "empty-line" -> {
                        flush()
                        blocks += BookBlock.Divider
                    }
                    "p", "v", "text-author" -> flush()
                    "book-title", "first-name", "last-name" -> {
                        reading = name
                        text.setLength(0)
                    }
                }
            }

            override fun close(name: String) {
                when (name) {
                    "binary" -> if (hidden > 0) hidden--
                    "body" -> {
                        if (inBody && depth == 0) closeChapter()
                        inBody = false
                    }
                    "section" -> {
                        if (depth > 0) {
                            if (depth == 1) closeChapter() else flush()
                            depth--
                        }
                    }
                    "title" -> {
                        flush()
                        inTitle = false
                        heading = 0
                    }
                    "subtitle" -> {
                        flush()
                        heading = 0
                    }
                    "p", "v", "text-author" -> flush()
                    "book-title" -> {
                        if (title.isEmpty()) title = text.toString().normalizeSpaces()
                        reading = null
                        text.setLength(0)
                    }
                    "first-name" -> {
                        if (firstName.isEmpty()) firstName = text.toString().normalizeSpaces()
                        reading = null
                        text.setLength(0)
                    }
                    "last-name" -> {
                        if (lastName.isEmpty()) lastName = text.toString().normalizeSpaces()
                        reading = null
                        text.setLength(0)
                    }
                }
            }

            override fun text(value: String) {
                // Внутри картинки лежат не буквы, а закодированный рисунок:
                // положи его в главу — и страница станет стеной из знаков.
                if (hidden > 0) return
                // Описание книги читается только в те поля, которых мы ждём:
                // остальное оттуда в текст попадать не должно.
                if (!inBody && reading == null && !inTitle) return
                text.append(value)
            }
        },
    )

    // Незакрытый последний раздел — обычное дело у книг, собранных вручную.
    if (blocks.isNotEmpty()) closeChapter()

    if (chapters.isEmpty()) return null

    return BookText(
        title = title.ifBlank { "Книга" },
        author = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
        chapters = chapters,
    )
}
