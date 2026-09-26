package app.askya.agent.tools

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.askya.agent.AgentContext
import app.askya.agent.AgentPolicy
import app.askya.agent.AgentSession
import app.askya.agent.TurnOutcome
import app.askya.agent.llm.LlmPart
import app.askya.agent.llm.LlmReply
import app.askya.agent.llm.LlmRequest
import app.askya.agent.llm.Role
import app.askya.agent.llm.ScriptedClient
import app.askya.agent.llm.ScriptedClient.Companion.call
import app.askya.agent.llm.ScriptedClient.Companion.text
import app.askya.agent.llm.lastResults
import app.askya.data.entity.Note
import app.askya.data.preferences.SettingsPreferences
import app.askya.data.repository.DeedTaskRepository
import app.askya.data.repository.NoteRepository
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.data.repository.YetRepository
import app.askya.data.sync.Json
import app.askya.testing.NoImages
import app.askya.testing.NoVoices
import app.askya.testing.TestDatabase
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `search_notes` через настоящий цикл: сессия → модель → набор инструментов
 * Askya → ход → база → обратно модели. И граница приватности на том шаге, где
 * в разговоре появляется текст заметок.
 */
class SearchNotesLoopTest {

    private val base = TestDatabase()
    private val db = base.db
    private val prefsFolder = Files.createTempDirectory("askya-settings").toFile()
    private val settings = SettingsPreferences(
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { File(prefsFolder, "settings.preferences_pb").absolutePath.toPath() },
        ),
    )
    private val notes = NoteRepository(db.noteDao(), db.topicDao(), db.albumDao(), NoImages, NoVoices)
    private val tools = askyaTools(
        ScheduleRepository(db.scheduleDao()),
        DeedTaskRepository(db.deedTaskDao()),
        ReminderRepository(db.reminderDao()),
        YetRepository(db.yetDao()),
        notes,
        settings,
    )
    private val context = AgentContext(LocalDate.of(2030, 5, 10), LocalTime.of(9, 0), ZoneId.of("UTC"), true, true)

    @AfterTest
    fun close() {
        base.close()
        prefsFolder.deleteRecursively()
    }

    private val mountains = "Летом пойти в горы: Эльбрус, палатка, кошки."

    private suspend fun seed(): Long {
        val at = LocalDateTime.of(2030, 5, 1, 10, 0)
        db.noteDao().insert(Note(title = "Покупки", body = "молоко", createdAt = at, updatedAt = at))
        return db.noteDao().insert(Note(title = "Походы", body = mountains, tags = listOf("лето"), createdAt = at, updatedAt = at))
    }

    private val askSearch: suspend (LlmRequest) -> LlmReply = { call("c1", "search_notes", "query" to "горы") }

    @Test
    fun `найди мои заметки про горы`() = runTest {
        val id = seed()
        val client = ScriptedClient(
            online = false,
            askSearch,
            { request ->
                val result = request.lastResults.single()
                assertFalse(result.isError, result.content)
                @Suppress("UNCHECKED_CAST")
                val items = (Json.read(result.content) as Map<String, Any?>)["items"] as List<Map<String, Any?>>
                assertEquals(listOf<Any?>(id), items.map { it["id"] })
                text("Нашлась одна заметка: «${items.single()["title"]}».")
            },
        )
        val session = AgentSession(client, tools, { AgentPolicy() }, { context })

        val outcome = assertIs<TurnOutcome.Answered>(session.send("Найди мои заметки про горы"))
        assertEquals("Нашлась одна заметка: «Походы».", outcome.text)

        // Второй запрос модели получил результат, и он же лежит в истории.
        assertEquals(2, client.requests.size)
        val sent = client.requests[1].lastResults.single().content
        assertTrue(mountains in sent, sent)
        val stored = session.history.flatMap { it.parts }.filterIsInstance<LlmPart.ToolResult>().single()
        assertEquals(sent, stored.content)
        assertEquals(listOf(Role.USER, Role.ASSISTANT, Role.USER, Role.ASSISTANT), session.history.map { it.role })

        // Наружу — JSON из простых значений, а не сущность Room.
        assertFalse("Note(" in sent || "uid" in sent || "removedAt" in sent, sent)
    }

    @Test
    fun `search_notes среди описаний — у клиента на устройстве и у облачного при открытом облаке`() = runTest {
        val id = seed()
        listOf(false to AgentPolicy(allowCloud = false), true to AgentPolicy(allowCloud = true)).forEach { (online, policy) ->
            val client = ScriptedClient(online, askSearch, { text("ок") })
            val session = AgentSession(client, tools, { policy }, { context })

            assertIs<TurnOutcome.Answered>(session.send("Найди мои заметки про горы"), "online=$online")
            // Набор один и тот же — отдельного разрешения для заметок нет.
            assertEquals(tools.specs, client.requests.first().tools, "online=$online")
            assertTrue(client.requests.first().tools.any { it.name == "search_notes" })
            @Suppress("UNCHECKED_CAST")
            val items = (Json.read(client.requests[1].lastResults.single().content) as Map<String, Any?>)["items"] as List<Map<String, Any?>>
            assertEquals(listOf<Any?>(id), items.map { it["id"] }, "online=$online")
        }
    }

    @Test
    fun `облако закрыто — модель в сети не получает ничего, даже вопроса`() = runTest {
        seed()
        val client = ScriptedClient(online = true, askSearch, { text("не должно случиться") })
        val session = AgentSession(client, tools, { AgentPolicy(allowCloud = false) }, { context })

        assertEquals(TurnOutcome.Blocked, session.send("Найди мои заметки про горы"))
        assertEquals(0, client.requests.size)
    }

    @Test
    fun `облако закрыли после поиска — текст заметки до модели не доходит`() = runTest {
        seed()
        var policy = AgentPolicy(allowCloud = true)
        val client = ScriptedClient(
            online = true,
            { request -> policy = AgentPolicy(allowCloud = false); askSearch(request) },
            { text("не должно случиться") },
        )
        val session = AgentSession(client, tools, { policy }, { context })

        assertEquals(TurnOutcome.Blocked, session.send("Найди мои заметки про горы"))
        assertEquals(1, client.requests.size)
        assertTrue(client.requests.none { mountains in it.toString() }, "текст заметки клиенту не ушёл")
        assertTrue(session.history.none { mountains in it.toString() })
    }
}
