package app.askya.agent.llm.anthropic

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.askya.agent.AgentContext
import app.askya.agent.AgentSession
import app.askya.agent.ToolRegistry
import app.askya.agent.TurnOutcome
import app.askya.agent.llm.ScriptedClient
import app.askya.agent.llm.ScriptedClient.Companion.text
import app.askya.agent.tools.SearchNotesTool
import app.askya.data.entity.Note
import app.askya.data.preferences.AgentPreferences
import app.askya.data.repository.NoteRepository
import app.askya.testing.NoImages
import app.askya.testing.NoVoices
import app.askya.testing.TestDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Настройки агента → политика и клиент Claude → сессия. Та же проводка, что
 * в `AppContainer` (`agentPolicyOf`, `anthropicClientOf`), с подменённой сетью.
 */
class AgentCloudWiringTest {

    private val base = TestDatabase()
    private val db = base.db
    private val folder = Files.createTempDirectory("askya-agent").toFile()
    private val storeJob = Job()
    private val agent = AgentPreferences(
        PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + storeJob),
            produceFile = { File(folder, "agent.preferences_pb").absolutePath.toPath() },
        ),
    )
    private val tools = ToolRegistry(listOf(SearchNotesTool(NoteRepository(db.noteDao(), db.topicDao(), db.albumDao(), NoImages, NoVoices))))
    private val context = AgentContext(LocalDate.of(2030, 5, 10), LocalTime.of(9, 0), ZoneId.of("UTC"), true, true)
    private val key = "sk-ant-api03-не-настоящий-ключ-1234"
    private val diary = "Личное: боюсь высоты, но в горы всё равно пойду."

    @AfterTest
    fun close() {
        storeJob.cancel()
        base.close()
        folder.deleteRecursively()
    }

    private suspend fun seed() {
        val at = LocalDateTime.of(2030, 5, 1, 10, 0)
        db.noteDao().insert(Note(title = "Горы", body = diary, createdAt = at, updatedAt = at))
    }

    private fun session(client: AnthropicLlmClient) = AgentSession(client, tools, agentPolicyOf(agent), { context })

    private val search = ok(
        """{"content":[{"type":"thinking","thinking":"","signature":"sig"},
        {"type":"tool_use","id":"toolu_1","name":"search_notes","input":{"query":"горы"}}],"stop_reason":"tool_use"}""",
    )
    private fun answer(text: String) = ok("""{"content":[{"type":"text","text":"$text"}],"stop_reason":"end_turn"}""")

    @Test
    fun `без ключа клиента нет`() = runBlocking {
        agent.ready()
        assertNull(anthropicClientOf(agent, FakeTransport()))
    }

    @Test
    fun `F — ключ есть, облако закрыто — ни одного запроса`() = runBlocking {
        agent.ready()
        agent.setKey(key)
        val transport = FakeTransport(answer("не должно случиться"))
        val client = assertNotNull(anthropicClientOf(agent, transport))

        assertEquals(TurnOutcome.Blocked, session(client).send("Найди мои заметки про горы"))
        assertEquals(0, transport.sent.size)
    }

    @Test
    fun `G — ключ и согласие — запрос доходит до сети`() = runBlocking {
        agent.ready()
        agent.setKey(key)
        agent.setAllowCloud(true)
        val transport = FakeTransport(answer("Привет"))
        val client = assertNotNull(anthropicClientOf(agent, transport))

        assertTrue(client.online)
        assertEquals(TurnOutcome.Answered("Привет", emptyList()), session(client).send("Здравствуй"))
        assertEquals(1, transport.sent.size)
        assertEquals(key, transport.sent.single().headers["x-api-key"])
        assertFalse(key in transport.sent.single().body)
    }

    @Test
    fun `облако выключили между ходами — личное больше не уходит`() = runBlocking {
        seed()
        agent.ready()
        agent.setKey(key)
        agent.setAllowCloud(true)
        val transport = FakeTransport(search, answer("Нашлась одна"), answer("не должно случиться"))
        val session = session(assertNotNull(anthropicClientOf(agent, transport)))

        // 1–3: облако открыто, поиск вернул личный текст, ход закончен.
        assertIs<TurnOutcome.Answered>(session.send("Найди мои заметки про горы"))
        assertEquals(2, transport.sent.size)
        assertTrue(diary in transport.sent[1].body)

        // 4–5: облако закрыли — следующий запрос с историей не уходит.
        agent.setAllowCloud(false)
        assertEquals(TurnOutcome.Blocked, session.send("А что ещё?"))
        assertEquals(2, transport.sent.size)
    }

    @Test
    fun `облако выключили посреди хода — найденное личное в сеть не ушло`() = runBlocking {
        seed()
        agent.ready()
        agent.setKey(key)
        agent.setAllowCloud(true)
        val first = FakeTransport(search)
        val transport = object : HttpTransport {
            val sent = mutableListOf<String>()
            override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpAnswer {
                sent += body
                // Пока модель думала, человек выключил облако в настройках.
                agent.setAllowCloud(false)
                return first.post(url, headers, body)
            }
        }
        val session = session(assertNotNull(anthropicClientOf(agent, transport)))

        assertEquals(TurnOutcome.Blocked, session.send("Найди мои заметки про горы"))
        assertEquals(1, transport.sent.size, "второй запрос — с результатом поиска — не ушёл")
        assertTrue(transport.sent.none { diary in it })
        assertTrue(session.history.isEmpty())
    }

    @Test
    fun `H — другой клиент — новая сессия с пустой историей`() = runBlocking {
        seed()
        agent.ready()
        agent.setKey(key)
        agent.setAllowCloud(true)

        // Разговор A — с моделью на устройстве, и в нём уже есть личное.
        val local = ScriptedClient(false, { ScriptedClient.call("c1", "search_notes", "query" to "горы") }, { text("нашлось") })
        val sessionA = AgentSession(local, tools, agentPolicyOf(agent), { context })
        sessionA.send("Найди мои заметки про горы")
        assertTrue(diary in sessionA.history.toString())

        // Разговор B — с Claude: новая сессия, история A в неё не попадает.
        val transport = FakeTransport(answer("Привет"))
        val sessionB = session(assertNotNull(anthropicClientOf(agent, transport)))
        assertTrue(sessionB.history.isEmpty())
        sessionB.send("Привет")

        assertEquals(1, transport.sent.single().messages.size)
        assertFalse(diary in transport.sent.single().body)
        assertTrue(sessionA.client === local)
    }
}
