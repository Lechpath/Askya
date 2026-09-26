package app.askya.agent.tools

import app.askya.agent.AgentPolicy
import app.askya.agent.PageRequest
import app.askya.agent.ToolPage
import app.askya.data.entity.Note
import app.askya.domain.search.Ask
import app.askya.domain.search.snippetOf

/**
 * Ответ будущего `search_notes` — отдельно от самого инструмента, чтобы его
 * размер можно было проверить до того, как инструмент появится.
 *
 * **Строка ответа:**
 * ```
 * {id, title, snippet, tags: [...], updatedAt}
 * ```
 * Только текстовые заметки, поэтому поля «вид» нет: он у всех один.
 *
 * **Почему страница всегда влезает в `maxResultSize`.** Одна строка ограничена
 * сверху: название — [TITLE_SHOWN] знаков, фрагмент — `SNIPPET_SIZE`, теги —
 * [TAGS_SHOWN] штук по [TAG_SHOWN]. Управляющих знаков в строках нет — они
 * стали пробелами, — так что в JSON любой знак занимает не больше двух.
 * Худшая строка — меньше двух тысяч знаков, а бюджет — [BUDGET]. Сколько
 * строк влезет, решает [ToolPage.within]: при длинных заметках страница
 * кончается раньше `limit`, а остальное приходит следующей.
 *
 * Обрезанное название и теги отмечены «…»: молча урезанное читалось бы как
 * целое.
 *
 * **Курсор** — сдвиг в списке найденного (`ToolPage`). Он верен, пока набор
 * заметок между страницами не меняется. Если человек правит заметки, пока
 * модель листает, строки могут повториться или пропасть: список упорядочен
 * по времени правки, и правленая заметка переезжает в начало. Это осознанное
 * ограничение первой версии — курсора по номеру, времени или снимка нет.
 */
internal object NoteHits {

    const val DEFAULT_LIMIT = 20
    const val MAX_LIMIT = 30

    const val TITLE_SHOWN = 120
    const val TAGS_SHOWN = 10
    const val TAG_SHOWN = 40

    /** Сколько знаков JSON может занять страница — столько же, сколько пропустит ход. */
    val BUDGET: Int = AgentPolicy().maxResultSize

    /** Страница найденного в виде ответа инструмента. */
    fun page(found: List<Note>, ask: Ask, request: PageRequest): Map<String, Any?> =
        ToolPage.within(found, request, BUDGET) { view(it, ask) }.toResult { view(it, ask) }

    fun view(note: Note, ask: Ask): Map<String, Any?> = mapOf(
        "id" to note.id,
        "title" to shown(note.title, TITLE_SHOWN),
        "snippet" to snippetOf(note.body, ask),
        "tags" to tags(note.tags),
        "updatedAt" to note.updatedAt.toString(),
    )

    private fun tags(all: List<String>): List<String> {
        val shown = all.take(TAGS_SHOWN).map { shown(it, TAG_SHOWN) }
        return if (all.size > TAGS_SHOWN) shown + ELLIPSIS else shown
    }

    /** Строка в одну линию и не длиннее [max] знаков вместе с «…». */
    private fun shown(text: String, max: Int): String {
        val flat = text.replace(BLANKS, " ").trim()
        return if (flat.length <= max) flat else flat.take(max - 1).trimEnd() + ELLIPSIS
    }

    private const val ELLIPSIS = "…"
    private val BLANKS = Regex("[\\s\\p{Cntrl}]+")
}
