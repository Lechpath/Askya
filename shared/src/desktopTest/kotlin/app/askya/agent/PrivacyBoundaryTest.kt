package app.askya.agent

import app.askya.agent.tools.GetTasksTool
import app.askya.agent.tools.GetTodayTool
import app.askya.agent.tools.SearchNotesTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Инструменту неоткуда узнать, локальный у разговора клиент или облачный и
 * разрешена ли сеть, — проверкой, а не обещанием.
 *
 * Решать, куда уходят данные, должен тот, кто отправляет разговор модели
 * ([AgentPolicy.allowsClient] перед каждой отправкой). Инструмент, который
 * сам решал бы «в облако можно», — это вторая точка проверки, и однажды они
 * разойдутся. Если кто-то протянет инструменту политику или признак клиента,
 * этот тест упадёт.
 */
class PrivacyBoundaryTest {

    /** Всё, через что политика или клиент могли бы дойти до инструмента. */
    private val sendSide = setOf(
        AgentPolicy::class.java,
        ToolTurn::class.java,
        ToolRegistry::class.java,
    )

    @Test
    fun `в контексте инструмента нет ничего о клиенте и сети`() {
        val fields = AgentContext::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        assertEquals(setOf("currentDate", "currentTime", "timezone", "weekStartsMonday", "autoFillDay"), fields)
    }

    @Test
    fun `инструмент получает только аргументы и контекст`() {
        val calls = listOf(
            ReadTool::class.java.declaredMethods.single { it.name == "read" },
            ProposeTool::class.java.declaredMethods.single { it.name == "propose" },
        )
        calls.forEach { call ->
            val params = call.parameterTypes.toList()
            // Третий — продолжение suspend-функции.
            assertEquals(Map::class.java, params[0], call.name)
            assertEquals(AgentContext::class.java, params[1], call.name)
            assertEquals(3, params.size, "${call.name}: $params")
        }
    }

    @Test
    fun `готовые инструменты не держат ни политики, ни хода`() {
        listOf(GetTodayTool::class.java, GetTasksTool::class.java, SearchNotesTool::class.java).forEach { tool ->
            val touched = tool.declaredFields.map { it.type } +
                tool.declaredConstructors.flatMap { it.parameterTypes.toList() }
            val leaks = touched.filter { it in sendSide }
            assertTrue(leaks.isEmpty(), "${tool.simpleName}: $leaks")
        }
    }
}
