package app.askya.domain.docs

/**
 * Кусок книги: заголовок или абзац.
 *
 * Не разметка и не html: читалке нужно ровно два вида кусков — то, что стоит
 * заглавием, и то, что читают. Всё остальное — курсив, ссылки, сноски —
 * страница книги переживает без потери смысла, а вот разбор их стоил бы
 * настоящего движка вёрстки.
 *
 * Иллюстраций и роликов здесь нет. Приносил их один формат — epub, — и вместе
 * с ним они и ушли: картинки внутри fb2 лежат закодированными буквами в
 * `binary` и в текст не попадают вовсе.
 */
sealed interface BookBlock {
    /** [level] — от 1 до 6, как в html: по нему заглавие берётся крупнее. */
    data class Heading(val level: Int, val text: String) : BookBlock

    data class Paragraph(val text: String) : BookBlock

    /** Строка звёздочек между сценами. */
    data object Divider : BookBlock
}

/** Глава книги — то, что читают за один заход и чем меряется прогресс. */
data class BookChapter(val title: String, val blocks: List<BookBlock>) {

    /** Текст главы одной строкой — для поиска по книге. */
    val text: String by lazy {
        blocks.joinToString("\n") { block ->
            when (block) {
                is BookBlock.Heading -> block.text
                is BookBlock.Paragraph -> block.text
                BookBlock.Divider -> ""
            }
        }
    }

    /** Пустые главы в оглавление не попадают: обложка и титул — не главы. */
    val empty: Boolean get() = blocks.none { it !is BookBlock.Divider }
}

/**
 * Разобранная книга: имя, автор и главы по порядку чтения.
 *
 * Отдельная от разбора нарочно: читалке незачем знать, из чего книгу собрали,
 * и связаны разбор с показом одним этим видом.
 */
data class BookText(
    val title: String,
    val author: String,
    val chapters: List<BookChapter>,
) {
    val words: Int by lazy { chapters.sumOf { chapter -> chapter.text.count { it == ' ' } + 1 } }
}
