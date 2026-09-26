package app.askya.agent

import app.askya.agent.ToolPage.Companion.pageRequest
import app.askya.data.sync.Json
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Страница выборки и её граница с [ToolTurn]: выборку ограничивает инструмент,
 * ход только страхует размер — и страхует отказом целиком, а не обрезкой.
 */
class ToolPageTest {

    private val context = AgentContext(LocalDate.of(2030, 5, 10), LocalTime.of(9, 0), ZoneId.of("UTC"), true, true)

    /** Сорок строк, по которым листает игрушечный поиск. */
    private val rows = List(40) { "строка ${it + 1}" }

    /** Инструмент, который сам ограничивает выборку — как будущий search_notes. */
    private class Paged(private val rows: List<String>, private val maxLimit: Int = 20) : ReadTool {
        override val name = "search"
        override val description = "Листает строки"
        override val inputSchema = mapOf<String, Any?>("type" to "object")
        override suspend fun read(input: Map<String, Any?>, context: AgentContext): ToolResult =
            when (val args = readArgs(input, ToolPage.KEYS) { pageRequest(defaultLimit = 10, maxLimit = maxLimit) }) {
                is ArgsResult.Bad -> ToolResult.Failed(args.reason)
                is ArgsResult.Ok -> ToolResult.Ok(ToolPage.of(rows, args.value).toResult { it })
            }
    }

    /** Инструмент, который выборку не ограничивает и отдаёт всё разом. */
    private class Everything(private val rows: List<String>) : ReadTool {
        override val name = "everything"
        override val description = "Отдаёт всё"
        override val inputSchema = mapOf<String, Any?>("type" to "object")
        override suspend fun read(input: Map<String, Any?>, context: AgentContext) =
            ToolResult.Ok(mapOf("items" to rows))
    }

    private fun turn(tool: ReadTool, maxResultSize: Int = 16_000) =
        ToolRegistry(listOf(tool)).openTurn(AgentPolicy(maxResultSize = maxResultSize, maxToolCalls = 20), context)

    // --- Страница сама по себе ------------------------------------------------

    @Test
    fun `первая страница, середина и конец`() {
        val first = ToolPage.of(rows, PageRequest(limit = 15, offset = 0))
        assertEquals(rows.take(15), first.items)
        assertTrue(first.hasMore)

        val last = ToolPage.of(rows, PageRequest(limit = 15, offset = 30))
        assertEquals(rows.drop(30), last.items)
        assertFalse(last.hasMore)
        assertNull(last.nextCursor)
    }

    @Test
    fun `ровно на границе продолжать нечего`() {
        val page = ToolPage.of(rows, PageRequest(limit = 40, offset = 0))
        assertEquals(40, page.items.size)
        assertFalse(page.hasMore)
        assertNull(page.nextCursor)
    }

    @Test
    fun `пустая выборка`() {
        assertEquals(
            mapOf("items" to emptyList<Any>(), "hasMore" to false, "nextCursor" to null),
            ToolPage.of(emptyList<String>(), PageRequest(10, 0)).toResult { it },
        )
    }

    @Test
    fun `страница по бюджету кончается раньше limit, но ничего не теряет`() {
        val long = List(10) { "ж".repeat(100) }
        val page = ToolPage.within(long, PageRequest(limit = 10, offset = 0), budget = 400) { it }
        assertTrue(page.items.size in 1..9, "${page.items.size}")
        assertTrue(page.hasMore)
        assertEquals("o:${page.items.size}", page.nextCursor)
        assertTrue(Json.write(page.toResult { it }).length <= 400)
    }

    @Test
    fun `первая строка берётся, даже если одна не влезает`() {
        // Иначе листание встало бы на месте; такую страницу откажет страховка хода.
        val page = ToolPage.within(listOf("ж".repeat(500), "к"), PageRequest(10, 0), budget = 100) { it }
        assertEquals(1, page.items.size)
        assertEquals("o:1", page.nextCursor)
    }

    // --- Инструмент ограничивает себя сам -------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun ToolCallOutcome.data() = assertIs<ToolCallOutcome.Read>(this).data

    @Test
    fun `инструмент отдаёт не больше своего limit и продолжает по курсору`() = runTest {
        val turn = turn(Paged(rows))
        val seen = mutableListOf<Any?>()
        var cursor: Any? = null
        do {
            val data = turn.call("search", buildMap { put("limit", 15L); cursor?.let { put("cursor", it) } }).data()
            @Suppress("UNCHECKED_CAST")
            val items = data["items"] as List<Any?>
            assertTrue(items.size <= 15)
            seen.addAll(items)
            cursor = data["nextCursor"]
            assertEquals(cursor != null, data["hasMore"])
        } while (cursor != null)

        assertEquals<List<Any?>>(rows, seen, "каждая строка ровно один раз и по порядку")
    }

    @Test
    fun `больше своего потолка просить нельзя`() = runTest {
        val outcome = turn(Paged(rows, maxLimit = 20)).call("search", mapOf("limit" to 50L))
        assertEquals("limit должен быть от 1 до 20", assertIs<ToolCallOutcome.Failed>(outcome).reason)
    }

    @Test
    fun `чужой или испорченный курсор — ошибка, а не начало сначала`() = runTest {
        listOf("20", "o:-1", "o:x", "offset=5").forEach { bad ->
            assertIs<ToolCallOutcome.Failed>(turn(Paged(rows)).call("search", mapOf("cursor" to bad)), bad)
        }
    }

    @Test
    fun `курсор проходит через ход нетронутым`() = runTest {
        // Ход о курсоре ничего не знает: что вернул инструмент, то и отдано модели.
        val data = turn(Paged(rows)).call("search", mapOf("limit" to 5L)).data()
        assertEquals("o:5", data["nextCursor"])
        assertEquals(setOf("items", "hasMore", "nextCursor"), data.keys)
    }

    // --- Страховка хода -------------------------------------------------------

    @Test
    fun `слишком большой ответ отклоняется целиком, а не обрезается`() = runTest {
        val long = List(500) { "строка номер ${it + 1} с каким-то текстом" }
        val outcome = turn(Everything(long), maxResultSize = 1_000).call("everything", emptyMap())

        val refused = assertIs<ToolCallOutcome.Refused>(outcome)
        assertEquals(RefusalReason.RESULT_TOO_LARGE, refused.reason)
        // В отказе нет ни строк, ни их начала — только размер.
        assertTrue("строка" !in refused.message, refused.message)
    }

    @Test
    fun `страница, которая влезает, проходит страховку`() = runTest {
        val long = List(500) { "строка номер ${it + 1} с каким-то текстом" }
        val outcome = turn(Paged(long), maxResultSize = 1_000).call("search", mapOf("limit" to 10L))

        val data = outcome.data()
        assertTrue(Json.write(data).length <= 1_000)
        assertEquals(true, data["hasMore"])
    }

    @Test
    fun `страница, которая не влезает, тоже отклоняется целиком`() = runTest {
        // Страница — не обход страховки: слишком длинные строки не пройдут и по одной странице.
        val huge = List(20) { "ы".repeat(200) }
        val outcome = turn(Paged(huge), maxResultSize = 1_000).call("search", mapOf("limit" to 20L))
        assertEquals(RefusalReason.RESULT_TOO_LARGE, assertIs<ToolCallOutcome.Refused>(outcome).reason)
    }
}
