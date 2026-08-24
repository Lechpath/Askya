package app.askya.domain.docs

/**
 * Кусок книги: заголовок или абзац.
 *
 * Не разметка и не html: читалке нужно ровно два вида кусков — то, что стоит
 * заглавием, и то, что читают. Всё остальное — курсив, ссылки, сноски —
 * страница книги переживает без потери смысла, а вот разбор их стоил бы
 * настоящего движка вёрстки.
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
 * Одна и та же для epub и fb2 — читалке всё равно, из чего её собрали, а
 * разбирать два формата в одно и то же значит написать читалку один раз.
 */
data class BookText(
    val title: String,
    val author: String,
    val chapters: List<BookChapter>,
) {
    val words: Int by lazy { chapters.sumOf { chapter -> chapter.text.count { it == ' ' } + 1 } }
}
