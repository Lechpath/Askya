package app.askya.agent.tools

import app.askya.agent.AgentContext
import app.askya.agent.AgentPolicy
import app.askya.agent.RefusalReason
import app.askya.agent.ToolCallOutcome
import app.askya.agent.ToolPage
import app.askya.agent.ToolRegistry
import app.askya.agent.ToolResult
import app.askya.data.entity.Note
import app.askya.data.repository.NoteRepository
import app.askya.data.sync.Json
import app.askya.domain.search.SNIPPET_SIZE
import app.askya.testing.NoImages
import app.askya.testing.NoVoices
import app.askya.testing.TestDatabase
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `search_notes` на настоящей базе Askya: что находит, как листает, что отдаёт. */
class SearchNotesToolTest {

    private val base = TestDatabase()
    private val db = base.db
    private val repository = NoteRepository(db.noteDao(), db.topicDao(), db.albumDao(), NoImages, NoVoices)
    private val tool = SearchNotesTool(repository)
    private val context = AgentContext(LocalDate.of(2030, 5, 10), LocalTime.of(9, 0), ZoneId.of("UTC"), true, true)

    @AfterTest
    fun close() = base.close()

    /** Время правки задаёт порядок: чем больше [minute], тем свежее. */
    private var minute = 0L
    private suspend fun note(title: String = "", body: String = "", tags: List<String> = emptyList(), make: (Note) -> Note = { it }): Long {
        val at = LocalDateTime.of(2030, 5, 1, 0, 0).plusMinutes(++minute)
        return db.noteDao().insert(make(Note(title = title, body = body, tags = tags, createdAt = at, updatedAt = at)))
    }

    private suspend fun ok(vararg args: Pair<String, Any?>): Map<String, Any?> =
        assertIs<ToolResult.Ok>(tool.read(mapOf(*args), context)).data

    private suspend fun failed(vararg args: Pair<String, Any?>): String =
        assertIs<ToolResult.Failed>(tool.read(mapOf(*args), context)).reason

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.items() = this[ToolPage.ITEMS] as List<Map<String, Any?>>

    private fun Map<String, Any?>.ids() = items().map { it["id"] }

    // --- Поиск ----------------------------------------------------------------

    @Test
    fun `совпадение найдено`() = runTest {
        val id = note(title = "Покупки", body = "молоко и хлеб")
        note(title = "Другое", body = "ничего такого")
        val data = ok("query" to "хлеб")
        assertEquals(listOf<Any?>(id), data.ids())
        assertEquals(false, data[ToolPage.HAS_MORE])
        assertNull(data[ToolPage.NEXT_CURSOR])
    }

    @Test
    fun `несколько совпадений — сначала недавно правленные`() = runTest {
        val old = note(body = "дача весной")
        val middle = note(title = "Дача")
        val fresh = note(body = "снова на дачу — дача")
        assertEquals(listOf<Any?>(fresh, middle, old), ok("query" to "дача").ids())
    }

    @Test
    fun `без совпадений — пустая страница, а не ошибка`() = runTest {
        note(body = "дача")
        val data = ok("query" to "океан")
        assertEquals(emptyList(), data.items())
        assertEquals(false, data[ToolPage.HAS_MORE])
        assertNull(data[ToolPage.NEXT_CURSOR])
    }

    @Test
    fun `регистр не важен, кириллица тоже`() = runTest {
        val id = note(title = "ДАЧА у Реки", body = "Seeds")
        listOf("дача", "Дача", "дАЧА", "реки", "SEEDS").forEach { query ->
            assertEquals(listOf<Any?>(id), ok("query" to query).ids(), query)
        }
    }

    @Test
    fun `несколько слов — нужны все, в любом порядке, и тег`() = runTest {
        val both = note(body = "позвонить маме в субботу", tags = listOf("семья"))
        note(body = "позвонить в субботу")
        assertEquals(listOf<Any?>(both), ok("query" to "субботу маме").ids())
        assertEquals(listOf<Any?>(both), ok("query" to "позвонить #семья").ids())
        assertEquals(emptyList<Any?>(), ok("query" to "маме воскресенье").ids())
    }

    // --- Пустой запрос и аргументы --------------------------------------------

    @Test
    fun `пустой запрос — не поиск, как и на экране`() = runTest {
        note(body = "что угодно")
        assertEquals("query обязателен", failed())
        assertEquals("query обязателен", failed("query" to "   "))
        assertEquals("query: нужно хотя бы одно слово или #тег", failed("query" to "#"))
    }

    @Test
    fun `чужие аргументы и не тот вид — отказ`() = runTest {
        assertEquals("неизвестные аргументы: all", failed("query" to "дача", "all" to true))
        assertEquals("query должен быть строкой", failed("query" to 5L))
    }

    // --- Фрагмент -------------------------------------------------------------

    private val filler = "слово ".repeat(200)

    @Test
    fun `фрагмент содержит совпадение, а не весь текст`() = runTest {
        val body = filler + "Дача у реки " + filler
        note(title = "Длинная", body = body)
        val snippet = ok("query" to "дача").items().single()["snippet"] as String
        assertTrue("Дача у реки" in snippet, snippet)
        assertTrue(snippet.length <= SNIPPET_SIZE, "${snippet.length}")
        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"))
        assertFalse(Json.write(ok("query" to "дача")).contains(body), "полный текст наружу не уходит")
    }

    @Test
    fun `совпадение в начале и в конце заметки`() = runTest {
        note(title = "a", body = "Начало дача " + filler)
        note(title = "b", body = filler + "в конце огород")
        val start = ok("query" to "дача").items().single()["snippet"] as String
        val end = ok("query" to "огород").items().single()["snippet"] as String
        assertTrue(start.startsWith("Начало дача"), start)
        assertTrue(end.endsWith("в конце огород"), end)
    }

    @Test
    fun `короткая заметка — фрагмент равен тексту`() = runTest {
        note(title = "Список", body = "молоко\nхлеб")
        assertEquals("молоко хлеб", ok("query" to "хлеб").items().single()["snippet"])
    }

    // --- Страницы -------------------------------------------------------------

    @Test
    fun `по умолчанию 20, дальше по курсору, без повторов`() = runTest {
        val ids = List(45) { note(title = "Дача $it") }.reversed() // свежие первыми
        val first = ok("query" to "дача")
        assertEquals(20, first.items().size)
        assertEquals(true, first[ToolPage.HAS_MORE])
        assertEquals("o:20", first[ToolPage.NEXT_CURSOR])

        val second = ok("query" to "дача", "cursor" to first[ToolPage.NEXT_CURSOR])
        assertEquals(ids.subList(20, 40), second.ids())

        val third = ok("query" to "дача", "cursor" to second[ToolPage.NEXT_CURSOR])
        assertEquals(ids.subList(40, 45), third.ids())
        assertNull(third[ToolPage.NEXT_CURSOR])

        assertEquals<List<Any?>>(ids, first.ids() + second.ids() + third.ids(), "каждая ровно раз и по порядку")
    }

    @Test
    fun `limit — от 1 до 30`() = runTest {
        repeat(35) { note(title = "Дача $it") }
        assertEquals(30, ok("query" to "дача", "limit" to 30L).items().size)
        assertEquals(1, ok("query" to "дача", "limit" to 1L).items().size)
        assertEquals("limit должен быть от 1 до 30", failed("query" to "дача", "limit" to 31L))
        assertEquals("limit должен быть от 1 до 30", failed("query" to "дача", "limit" to 0L))
    }

    @Test
    fun `чужой курсор — отказ`() = runTest {
        note(title = "Дача")
        assertTrue(failed("query" to "дача", "cursor" to "20").startsWith("cursor"))
    }

    // --- Что входит в поиск ---------------------------------------------------

    @Test
    fun `убранные, картинки, файлы и голос не ищутся`() = runTest {
        val text = note(title = "дача", body = "текст")
        note(title = "дача убранная", body = "дача") { it.copy(removedAt = LocalDateTime.of(2030, 5, 9, 12, 0)) }
        note(title = "дача.jpg") { it.copy(uri = "images/1.jpg", mime = "image/jpeg", isImage = true) }
        note(title = "дача.pdf") { it.copy(uri = "content://docs/1", mime = "application/pdf") }
        note(title = "дача голосом") { it.copy(uri = "voice/1.m4a", mime = "audio/mp4", durationMs = 4_000) }

        assertEquals(listOf<Any?>(text), ok("query" to "дача").ids())
    }

    @Test
    fun `поиск ничего не пишет`() = runTest {
        note(title = "Дача", body = "огород")
        val before = base.snapshot()
        ok("query" to "дача")
        assertEquals(before, base.snapshot())
    }

    // --- Контракт ответа ------------------------------------------------------

    @Test
    fun `наружу только простые значения, без сущностей Room`() = runTest {
        note(title = "Дача", body = "огород", tags = listOf("лето"))
        val data = ok("query" to "дача")
        assertEquals(setOf("items", "hasMore", "nextCursor"), data.keys)
        val row = data.items().single()
        assertEquals(setOf("id", "title", "snippet", "tags", "updatedAt"), row.keys)
        assertEquals(listOf("лето"), row["tags"])
        assertEquals("2030-05-01T00:01", row["updatedAt"])

        fun plain(value: Any?): Boolean = when (value) {
            null, is String, is Long, is Int, is Boolean -> true
            is List<*> -> value.all(::plain)
            is Map<*, *> -> value.keys.all { it is String } && value.values.all(::plain)
            else -> false
        }
        assertTrue(plain(data), "в ответе есть не простое значение: $data")
        // И это записывается и читается как JSON без потерь.
        assertEquals(Json.write(data), Json.write(Json.read(Json.write(data))))
    }

    // --- Размер: страхует ход ---------------------------------------------------

    @Test
    fun `ответ проходит через ход и его предел`() = runTest {
        repeat(30) { note(title = "Дача $it", body = "огород ".repeat(100)) }
        val registry = ToolRegistry(listOf(tool))

        val fits = registry.openTurn(AgentPolicy(), context).call("search_notes", mapOf("query" to "дача", "limit" to 30L))
        assertTrue(Json.write(assertIs<ToolCallOutcome.Read>(fits).data).length <= AgentPolicy().maxResultSize)

        // Предел меньше — отказывает ход, целиком, а не инструмент и не обрезкой.
        val small = registry.openTurn(AgentPolicy(maxResultSize = 500), context)
            .call("search_notes", mapOf("query" to "дача", "limit" to 30L))
        assertEquals(RefusalReason.RESULT_TOO_LARGE, assertIs<ToolCallOutcome.Refused>(small).reason)
        // Сам инструмент при этом отвечает как обычно: своего предела ответа у него нет.
        assertIs<ToolResult.Ok>(tool.read(mapOf("query" to "дача", "limit" to 30L), context))
    }

    @Test
    fun `описание для модели`() {
        assertEquals("search_notes", tool.name)
        assertEquals(false, tool.inputSchema["additionalProperties"])
        assertEquals(listOf("query"), tool.inputSchema["required"])
        @Suppress("UNCHECKED_CAST")
        val properties = tool.inputSchema["properties"] as Map<String, Any?>
        assertEquals(setOf("query", "limit", "cursor"), properties.keys)
        Json.write(tool.inputSchema) // записывается
    }
}
