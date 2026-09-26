package app.askya.agent.tools

import app.askya.agent.AgentContext
import app.askya.agent.AgentPolicy
import app.askya.agent.ArgsResult
import app.askya.agent.PageRequest
import app.askya.agent.ReadTool
import app.askya.agent.ToolCallOutcome
import app.askya.agent.ToolPage
import app.askya.agent.ToolPage.Companion.pageRequest
import app.askya.agent.ToolRegistry
import app.askya.agent.ToolResult
import app.askya.agent.readArgs
import app.askya.data.entity.Note
import app.askya.data.sync.Json
import app.askya.domain.search.SNIPPET_SIZE
import app.askya.domain.search.askOf
import app.askya.domain.search.findTextNotes
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ответ будущего `search_notes`: страница при любых заметках влезает в
 * `maxResultSize`, ничего не теряя, и листается курсором до конца.
 */
class NoteHitsTest {

    private val ask = askOf("дача")
    private val limit = AgentPolicy().maxResultSize
    private val at = LocalDateTime.of(2030, 5, 10, 9, 0, 0, 123_456_789)

    /** Самая тяжёлая заметка: каждый знак в JSON удваивается, всё по пределу и сверх. */
    private fun worst(id: Long) = Note(
        id = Long.MAX_VALUE - id,
        title = "\"".repeat(1_000),
        body = "\\\"".repeat(2_000) + " дача " + "\"\\".repeat(2_000),
        tags = List(50) { "\\".repeat(500) },
        updatedAt = at,
    )

    /** Обычная длинная заметка по-русски. */
    private fun long(id: Long) = Note(
        id = id,
        title = "Заметка про дачу и огород номер $id ".repeat(10),
        body = "Весной надо посадить огурцы и помидоры. ".repeat(40) + "дача " + "Осенью убрать. ".repeat(40),
        tags = List(12) { "тег-про-дачу-и-огород-$it" },
        updatedAt = at,
    )

    private fun short(id: Long) = Note(id = id, title = "Дача $id", body = "про дачу", updatedAt = at)

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.items() = this[ToolPage.ITEMS] as List<Map<String, Any?>>

    /** Пролистать всё с [limit] на страницу; каждая страница проверяется на размер. */
    private fun pages(found: List<Note>, limit: Int): List<Map<String, Any?>> {
        val pages = mutableListOf<Map<String, Any?>>()
        var offset = 0
        while (true) {
            val page = NoteHits.page(found, ask, PageRequest(limit, offset))
            assertTrue(Json.write(page).length <= this.limit, "страница ${pages.size}: ${Json.write(page).length}")
            pages += page
            val cursor = page[ToolPage.NEXT_CURSOR] as String? ?: return pages
            assertEquals(true, page[ToolPage.HAS_MORE])
            offset = cursor.removePrefix("o:").toInt()
        }
    }

    // --- Одна строка ------------------------------------------------------------

    @Test
    fun `строка ответа — пять полей, всё по пределам`() {
        val row = NoteHits.view(worst(1), ask)
        assertEquals(setOf("id", "title", "snippet", "tags", "updatedAt"), row.keys)
        assertEquals(NoteHits.TITLE_SHOWN, (row["title"] as String).length)
        assertTrue((row["title"] as String).endsWith("…"))
        assertTrue((row["snippet"] as String).length <= SNIPPET_SIZE)
        @Suppress("UNCHECKED_CAST")
        val tags = row["tags"] as List<String>
        assertEquals(NoteHits.TAGS_SHOWN + 1, tags.size)
        assertEquals("…", tags.last(), "урезанные теги отмечены, а не пропали молча")
        assertTrue(tags.dropLast(1).all { it.length == NoteHits.TAG_SHOWN })
        assertEquals("2030-05-10T09:00:00.123456789", row["updatedAt"])
    }

    @Test
    fun `худшая строка заведомо меньше бюджета`() {
        val size = Json.write(NoteHits.view(worst(1), ask)).length
        assertTrue(size < 2_000, "$size")
        assertTrue(size * 8 < NoteHits.BUDGET, "на страницу влезает не меньше восьми худших строк")
    }

    @Test
    fun `бюджет — тот же предел, что у хода`() {
        assertEquals(16_000, NoteHits.BUDGET)
    }

    // --- Страница ---------------------------------------------------------------

    @Test
    fun `максимальная страница худших заметок влезает и не теряет ни одной`() {
        val found = List(30) { worst(it.toLong()) }
        val pages = pages(found, NoteHits.MAX_LIMIT)

        assertTrue(pages.first().items().size < NoteHits.MAX_LIMIT, "в первую страницу влезло не всё")
        assertEquals(found.map { it.id }, pages.flatMap { it.items() }.map { it["id"] }, "каждая ровно раз и по порядку")
    }

    @Test
    fun `максимальная страница длинных русских заметок влезает`() {
        val found = List(30) { long(it + 1L) }
        val pages = pages(found, NoteHits.MAX_LIMIT)
        assertEquals(found.map { it.id }, pages.flatMap { it.items() }.map { it["id"] })
    }

    @Test
    fun `короткие заметки — ровно limit, и на границе продолжать нечего`() {
        val found = List(45) { short(it + 1L) }

        val first = NoteHits.page(found, ask, PageRequest(NoteHits.DEFAULT_LIMIT, 0))
        assertEquals(10, first.items().size)
        assertEquals("o:10", first[ToolPage.NEXT_CURSOR])

        val full = NoteHits.page(found.take(30), ask, PageRequest(NoteHits.MAX_LIMIT, 0))
        assertEquals(30, full.items().size)
        assertEquals(false, full[ToolPage.HAS_MORE])
        assertNull(full[ToolPage.NEXT_CURSOR])

        assertEquals(found.map { it.id }, pages(found, NoteHits.DEFAULT_LIMIT).flatMap { it.items() }.map { it["id"] })
    }

    @Test
    fun `курсор после обрыва по размеру указывает на первую не вошедшую`() {
        val found = List(30) { worst(it.toLong()) }
        val first = NoteHits.page(found, ask, PageRequest(NoteHits.MAX_LIMIT, 0))
        val taken = first.items().size
        assertEquals("o:$taken", first[ToolPage.NEXT_CURSOR])

        val second = NoteHits.page(found, ask, PageRequest(NoteHits.MAX_LIMIT, taken))
        assertEquals(found[taken].id, second.items().first()["id"])
    }

    // --- Через ход --------------------------------------------------------------

    /** Как будущий `search_notes` соберёт ответ — без репозитория и без политики. */
    private class Search(private val notes: List<Note>) : ReadTool {
        override val name = "search"
        override val description = "Ищет заметки"
        override val inputSchema = mapOf<String, Any?>("type" to "object")
        override suspend fun read(input: Map<String, Any?>, context: AgentContext): ToolResult {
            val keys = ToolPage.KEYS + "query"
            val args = when (val read = readArgs(input, keys) {
                askOf(requireString("query")) to pageRequest(NoteHits.DEFAULT_LIMIT, NoteHits.MAX_LIMIT)
            }) {
                is ArgsResult.Bad -> return ToolResult.Failed(read.reason)
                is ArgsResult.Ok -> read.value
            }
            val (ask, page) = args
            return ToolResult.Ok(NoteHits.page(findTextNotes(notes, ask), ask, page))
        }
    }

    private val context = AgentContext(LocalDate.of(2030, 5, 10), LocalTime.of(9, 0), ZoneId.of("UTC"), true, true)

    @Test
    fun `страховка хода пропускает самую тяжёлую страницу`() = runTest {
        val turn = ToolRegistry(listOf(Search(List(30) { worst(it.toLong()) }))).openTurn(AgentPolicy(), context)
        val outcome = turn.call("search", mapOf("query" to "Дача", "limit" to 30L))
        val data = assertIs<ToolCallOutcome.Read>(outcome).data
        assertEquals(true, data[ToolPage.HAS_MORE])
    }

    @Test
    fun `пределы limit`() = runTest {
        val turn = ToolRegistry(listOf(Search(List(45) { short(it + 1L) }))).openTurn(AgentPolicy(), context)
        val byDefault = assertIs<ToolCallOutcome.Read>(turn.call("search", mapOf("query" to "дача"))).data
        assertEquals(10, byDefault.items().size)
        val tooMany = assertIs<ToolCallOutcome.Failed>(turn.call("search", mapOf("query" to "дача", "limit" to 31L)))
        assertEquals("limit должен быть от 1 до 30", tooMany.reason)
    }
}
