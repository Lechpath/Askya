package app.askya.ui.agent

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.agent.AgentContext
import app.askya.agent.AgentSession
import app.askya.agent.ToolRegistry
import app.askya.agent.llm.LlmClient
import app.askya.agent.llm.ScriptedClient
import app.askya.agent.llm.ScriptedClient.Companion.text
import app.askya.agent.llm.anthropic.AnthropicLlmClient
import app.askya.agent.llm.anthropic.CallTrace
import app.askya.agent.llm.anthropic.FakeTransport
import app.askya.agent.llm.anthropic.HttpAnswer
import app.askya.agent.llm.anthropic.HttpTransport
import app.askya.agent.llm.anthropic.agentPolicyOf
import app.askya.agent.llm.anthropic.anthropicClientOf
import app.askya.agent.llm.anthropic.ok
import app.askya.agent.tools.SearchNotesTool
import app.askya.data.entity.Note
import app.askya.data.preferences.AgentPreferences
import app.askya.data.repository.NoteRepository
import app.askya.testing.NoImages
import app.askya.testing.NoVoices
import app.askya.testing.TestDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Путь экрана разговора без сети: модель экрана → сессия → клиент Claude →
 * подменённая сеть, с настоящим хранилищем ключа и согласия и той же
 * проводкой, что в `AppContainer` (`agentPolicyOf`, `anthropicClientOf`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentScreenPathTest {

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
    private val diary = "Личное: боюсь высоты."

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storeJob.cancel()
        base.close()
        folder.deleteRecursively()
    }

    private fun model(client: LlmClient) = AgentViewModel(AgentSession(client, tools, agentPolicyOf(agent), { context }))

    /** Дождаться, пока ход кончится и на экране будет [lines] строк. */
    private suspend fun AgentViewModel.settled(lines: Int): AgentState =
        state.first { !it.busy && it.lines.size == lines }

    private suspend fun cloud(allow: Boolean) {
        agent.ready()
        agent.setKey(key)
        agent.setAllowCloud(allow)
    }

    private fun answer(text: String) = ok("""{"content":[{"type":"text","text":"$text"}],"stop_reason":"end_turn"}""")
    private val search = ok(
        """{"content":[{"type":"tool_use","id":"toolu_1","name":"search_notes","input":{"query":"высоты"}}],"stop_reason":"tool_use"}""",
    )

    @Test
    fun `A — облако закрыто — на экране отказ, в сеть ничего`() = runTest {
        cloud(allow = false)
        val transport = FakeTransport(answer("не должно случиться"))
        val model = model(assertNotNull(anthropicClientOf(agent, transport)))

        model.send("Привет")
        assertEquals(listOf(AgentLine.Asked("Привет"), AgentLine.Blocked), model.settled(2).lines)
        assertEquals(0, transport.sent.size)
    }

    @Test
    fun `B — облако открыто — ответ Claude на экране`() = runTest {
        cloud(allow = true)
        val transport = FakeTransport(answer("Здравствуйте!"))
        val client = assertNotNull(anthropicClientOf(agent, transport))
        val model = model(client)

        model.send("Привет")
        assertEquals(listOf(AgentLine.Asked("Привет"), AgentLine.Answered("Здравствуйте!")), model.settled(2).lines)
        assertEquals(1, transport.sent.size)

        val call = assertNotNull(client.lastCall.value)
        assertEquals(CallTrace.Outcome.OK, call.outcome)
        assertEquals(200, call.status)
        assertTrue(traceText(call).startsWith("anthropic:claude-opus-5 · HTTP 200 · "), traceText(call))
    }

    @Test
    fun `C — облако выключили посреди хода — второй запрос с личным не ушёл`() = runTest {
        base.db.noteDao().insert(Note(title = "Горы", body = diary, updatedAt = LocalDateTime.of(2030, 5, 1, 9, 0)))
        cloud(allow = true)
        val sent = mutableListOf<String>()
        val transport = HttpTransport { url, headers, body ->
            sent += body
            agent.setAllowCloud(false) // человек выключил облако, пока модель думала
            search
        }
        val model = model(assertNotNull(anthropicClientOf(agent, transport)))

        model.send("Найди про высоту")
        assertEquals(AgentLine.Blocked, model.settled(2).lines.last())
        assertEquals(1, sent.size)
        assertTrue(sent.none { diary in it })
    }

    @Test
    fun `D — модель на устройстве работает и при закрытом облаке`() = runTest {
        cloud(allow = false)
        val local = ScriptedClient(online = false, { text("я на устройстве") })
        val model = model(local)
        model.send("Привет")
        assertEquals(AgentLine.Answered("я на устройстве"), model.settled(2).lines.last())
    }

    @Test
    fun `E — другой клиент или другой ключ — новый разговор`() = runTest {
        cloud(allow = true)
        val store = ViewModelStore()
        fun modelFor(client: LlmClient, keyId: String?) = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { model(client) } },
        )[AgentViewModel.key(client, keyId), AgentViewModel::class]

        val firstKey = agent.settings.first().keyId
        val first = modelFor(assertNotNull(anthropicClientOf(agent, FakeTransport(answer("раз")))), firstKey)
        first.send("секрет")
        first.settled(2)

        // Заменили ключ: тот же client.id, но другой ключ — другая модель экрана.
        agent.setKey("sk-ant-api03-другой-ключ-9876")
        val secondKey = agent.settings.first().keyId
        assertNotEquals(firstKey, secondKey)
        val secondTransport = FakeTransport(answer("два"))
        val second = modelFor(assertNotNull(anthropicClientOf(agent, secondTransport)), secondKey)

        assertTrue(first !== second)
        assertEquals(emptyList(), second.state.value.lines)
        second.send("привет")
        second.settled(2)
        assertEquals(1, secondTransport.sent.single().messages.size, "разговор со старым ключом сюда не попал")
        assertFalse("секрет" in secondTransport.sent.single().body)

        // А локальная модель — третья.
        val local = ScriptedClient(false, { text("локально") })
        assertTrue(modelFor(local, null) !== first)
        store.clear()
    }

    @Test
    fun `ошибка Claude — коротко и без тела ответа`() = runTest {
        cloud(allow = true)
        val transport = FakeTransport(HttpAnswer(500, """{"error":{"message":"внутренности сервера"}}"""))
        val client = assertNotNull(anthropicClientOf(agent, transport))
        val model = model(client)

        model.send("Привет")
        val line = model.settled(2).lines.last()
        assertEquals(AgentLine.Failed(AnthropicLlmClient.UNAVAILABLE), line)
        assertFalse("внутренности" in line.toString())
        assertEquals(500, client.lastCall.value?.status)
        assertEquals(CallTrace.Outcome.FAILED, client.lastCall.value?.outcome)
    }

    @Test
    fun `пустой ответ — своя строка, а не пустой пузырь`() = runTest {
        cloud(allow = true)
        val model = model(assertNotNull(anthropicClientOf(agent, FakeTransport(ok("""{"content":[],"stop_reason":"end_turn"}""")))))
        model.send("Привет")
        assertEquals(AgentLine.Empty, model.settled(2).lines.last())
    }

    @Test
    fun `отмена — своя строка и отметка в диагностике`() = runTest {
        cloud(allow = true)
        val client = assertNotNull(anthropicClientOf(agent, HttpTransport { _, _, _ -> awaitCancellation() }))
        val model = model(client)
        model.send("Привет")
        assertTrue(model.state.value.busy)

        model.cancel()
        assertEquals(AgentLine.Cancelled, model.settled(2).lines.last())
        assertEquals(CallTrace.Outcome.CANCELLED, client.lastCall.value?.outcome)
    }

    @Test
    fun `F — ключа нет ни в состоянии экрана, ни в истории, ни в диагностике, ни в выводе`() = runTest {
        cloud(allow = true)
        val transport = FakeTransport(answer("ответ"))
        val client = assertNotNull(anthropicClientOf(agent, transport))
        val session = AgentSession(client, tools, agentPolicyOf(agent), { context })
        val model = AgentViewModel(session)

        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val (realOut, realErr) = System.out to System.err
        System.setOut(PrintStream(out, true))
        System.setErr(PrintStream(err, true))
        try {
            model.send("Привет")
            model.settled(2)
        } finally {
            System.setOut(realOut)
            System.setErr(realErr)
        }

        assertFalse(key in model.state.value.toString())
        assertFalse(key in session.history.toString())
        assertFalse(key in client.lastCall.value.toString())
        assertFalse(key in client.toString())
        assertEquals("", out.toString() + err.toString(), "в вывод ничего не пишется")
        assertEquals(key, transport.sent.single().headers["x-api-key"], "ключ — только в заголовке запроса")
    }
}
