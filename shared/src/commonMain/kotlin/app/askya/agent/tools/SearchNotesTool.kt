package app.askya.agent.tools

import app.askya.agent.AgentContext
import app.askya.agent.ArgsResult
import app.askya.agent.PageRequest
import app.askya.agent.ReadTool
import app.askya.agent.ToolPage
import app.askya.agent.ToolPage.Companion.pageRequest
import app.askya.agent.ToolResult
import app.askya.agent.readArgs
import app.askya.data.repository.NoteRepository
import app.askya.domain.search.Ask
import app.askya.domain.search.askOf
import app.askya.domain.search.findTextNotes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * `search_notes` — поиск по текстовым заметкам Scroll.
 *
 * Ищет тем же правилом, что строка поиска на экране Scroll
 * (`app.askya.domain.search`): слова требуются все, регистр не важен,
 * `#слово` — тег. Только текстовые заметки: файлы, голос и картинки не
 * ищутся. Убранные в корзину не видны — их не отдаёт `NoteRepository.notes()`.
 *
 * Только читает. Куда уйдёт ответ — на устройство или в сеть — инструмент не
 * знает и не решает: это проверяет тот, кто отправляет разговор модели
 * (`AgentPolicy.allowsClient`).
 *
 * **Вход:** `query` (обязательно), `limit` (1…[NoteHits.MAX_LIMIT], по
 * умолчанию [NoteHits.DEFAULT_LIMIT]), `cursor` (`nextCursor` прошлого ответа).
 *
 * **Ответ** — страница [ToolPage]:
 * ```
 * {
 *   "items": [ {id, title, snippet, tags: [...], updatedAt} ],
 *   "hasMore": true | false,
 *   "nextCursor": "o:20" | null
 * }
 * ```
 * Строка и её пределы — в [NoteHits]. `limit` — потолок, а не обещание: при
 * длинных заметках строк в странице меньше, и остальное приходит по
 * `nextCursor`. Курсор верен, пока заметки между страницами не меняются.
 *
 * Пустой запрос — не поиск, как и на экране: пустая строка Scroll ничего не
 * ищет. Инструмент отвечает отказом словами, а не всеми заметками разом.
 */
class SearchNotesTool(private val notes: NoteRepository) : ReadTool {

    override val name = "search_notes"

    override val description =
        "Ищет по текстовым заметкам Scroll и отдаёт кусок текста вокруг найденного. " +
            "Все слова запроса должны найтись; регистр не важен; слово с # ищется " +
            "по тегам. Файлы, голосовые заметки и картинки не ищутся. Если hasMore — " +
            "продолжение по cursor = nextCursor. Ничего не меняет."

    override val inputSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            QUERY to mapOf(
                "type" to "string",
                "description" to "Что искать: слова через пробел, #тег — по тегам.",
            ),
            ToolPage.LIMIT to mapOf(
                "type" to "integer",
                "minimum" to 1,
                "maximum" to NoteHits.MAX_LIMIT,
                "description" to "Сколько заметок за раз, не больше ${NoteHits.MAX_LIMIT}. " +
                    "По умолчанию ${NoteHits.DEFAULT_LIMIT}.",
            ),
            ToolPage.CURSOR to mapOf(
                "type" to "string",
                "description" to "nextCursor из прошлого ответа, как есть.",
            ),
        ),
        "required" to listOf(QUERY),
        "additionalProperties" to false,
    )

    override suspend fun read(input: Map<String, Any?>, context: AgentContext): ToolResult {
        val request = when (val parsed = parse(input)) {
            is ArgsResult.Bad -> return ToolResult.Failed(parsed.reason)
            is ArgsResult.Ok -> parsed.value
        }
        return try {
            val found = findTextNotes(notes.notes().first(), request.ask)
            ToolResult.Ok(NoteHits.page(found, request.ask, request.page))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Ни текста исключения, ни SQL: в них бывает то, что человек записал.
            ToolResult.Failed(COULD_NOT_READ)
        }
    }

    // --- Аргументы -----------------------------------------------------------

    private class Request(val ask: Ask, val page: PageRequest)

    private fun parse(input: Map<String, Any?>): ArgsResult<Request> = readArgs(input, KEYS) {
        val ask = askOf(requireString(QUERY))
        // «#» без слова — тоже пусто: экран на нём не ищет.
        if (ask.empty) reject(EMPTY_QUERY)
        Request(ask, pageRequest(NoteHits.DEFAULT_LIMIT, NoteHits.MAX_LIMIT))
    }

    private companion object {
        const val QUERY = "query"
        val KEYS = ToolPage.KEYS + QUERY
        const val EMPTY_QUERY = "query: нужно хотя бы одно слово или #тег"
        const val COULD_NOT_READ = "не удалось прочитать заметки"
    }
}
