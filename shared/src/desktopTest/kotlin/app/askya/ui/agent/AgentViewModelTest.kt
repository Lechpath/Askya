package app.askya.ui.agent

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.agent.AgentContext
import app.askya.agent.AgentPolicy
import app.askya.agent.AgentSession
import app.askya.agent.ToolRegistry
import app.askya.agent.llm.LlmReply
import app.askya.agent.llm.LlmRequest
import app.askya.agent.llm.ScriptedClient
import app.askya.agent.llm.ScriptedClient.Companion.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Модель экрана агента: один разговор, ход, отмена и уход вместе с экраном. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentViewModelTest {

    private val main = StandardTestDispatcher()
    private val context = AgentContext(LocalDate.of(2030, 5, 10), LocalTime.of(9, 0), ZoneId.of("UTC"), true, true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun session(client: ScriptedClient, policy: AgentPolicy = AgentPolicy()) =
        AgentSession(client, ToolRegistry(emptyList()), { policy }, { context })

    private fun local(vararg steps: suspend (LlmRequest) -> LlmReply) = ScriptedClient(online = false, *steps)

    @Test
    fun `реплика и ответ`() = runTest(main) {
        val model = AgentViewModel(session(local({ text("Привет") })))
        model.send("  Здравствуй  ")
        assertTrue(model.state.value.busy)
        advanceUntilIdle()

        assertEquals(listOf(AgentLine.Asked("Здравствуй"), AgentLine.Answered("Привет")), model.state.value.lines)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun `пустое и посреди хода не отправляется`() = runTest(main) {
        val client = local({ text("один") }, { text("два") })
        val model = AgentViewModel(session(client))
        model.send("   ")
        model.send("раз")
        model.send("ещё") // ход ещё идёт
        advanceUntilIdle()

        assertEquals(1, client.requests.size)
        assertEquals(listOf(AgentLine.Asked("раз"), AgentLine.Answered("один")), model.state.value.lines)
    }

    @Test
    fun `облако закрыто — строка отказа, а не падение`() = runTest(main) {
        val client = ScriptedClient(online = true, { text("не должно случиться") })
        val model = AgentViewModel(session(client, AgentPolicy(allowCloud = false)))
        model.send("привет")
        advanceUntilIdle()

        assertEquals(AgentLine.Blocked, model.state.value.lines.last())
        assertEquals(0, client.requests.size)
    }

    @Test
    fun `отмена хода`() = runTest(main) {
        val session = session(local({ awaitCancellation() }, { text("после") }))
        val model = AgentViewModel(session)
        model.send("долгий")
        advanceUntilIdle()
        assertTrue(model.state.value.busy)

        model.cancel()
        advanceUntilIdle()
        assertEquals(AgentLine.Cancelled, model.state.value.lines.last())
        assertFalse(model.state.value.busy)
        assertEquals(emptyList(), session.history)

        model.send("ещё")
        advanceUntilIdle()
        assertEquals(AgentLine.Answered("после"), model.state.value.lines.last())
    }

    @Test
    fun `другой клиент — другая модель экрана и другой разговор`() = runTest(main) {
        val local = local({ text("локально") })
        val cloud = ScriptedClient(online = true, { text("из облака") })
        val store = ViewModelStore()
        fun modelFor(client: ScriptedClient) = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { AgentViewModel(session(client, AgentPolicy(allowCloud = true))) } },
        )[AgentViewModel.key(client), AgentViewModel::class]

        val first = modelFor(local)
        first.send("секрет")
        advanceUntilIdle()

        val second = modelFor(cloud)
        assertTrue(first !== second, "ключ по клиенту — новая модель")
        assertTrue(modelFor(local) === first, "тот же клиент — та же модель")
        assertEquals(emptyList(), second.state.value.lines)

        second.send("привет")
        advanceUntilIdle()
        assertEquals(1, cloud.requests.single().messages.size, "разговор с локальной моделью сюда не попал")
        store.clear()
    }

    @Test
    fun `ушли с экрана — ход отменён вместе с моделью`() = runTest(main) {
        var cancelled = false
        val client = local({
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        })
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { AgentViewModel(session(client)) } }
        val model = ViewModelProvider.create(store, factory)[AgentViewModel::class]
        model.send("долгий")
        advanceUntilIdle()

        store.clear()
        advanceUntilIdle()
        assertTrue(cancelled)
    }
}
