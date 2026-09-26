package app.askya.agent.apply

import app.askya.agent.AgentContext
import app.askya.agent.AgentPolicy
import app.askya.agent.AgentTool
import app.askya.agent.Proposal
import app.askya.agent.ProposeTool
import app.askya.agent.ReadTool
import app.askya.agent.ToolCallOutcome
import app.askya.agent.ToolRegistry
import app.askya.agent.ToolSpec
import app.askya.agent.ToolTurn
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Граница между моделью и записью — проверкой, а не обещанием.
 *
 * У того, что видят модель и инструменты, нет ни поля, ни параметра, ни
 * возвращаемого значения, через которое можно дотянуться до применения
 * предложений, репозиториев или будильника. Если однажды кто-то протянет туда
 * ссылку «для удобства», этот тест упадёт.
 */
class BoundaryTest {

    /** Что видят модель и инструменты. */
    private val agentFacing = listOf(
        ToolRegistry::class.java,
        ToolTurn::class.java,
        AgentTool::class.java,
        ReadTool::class.java,
        ProposeTool::class.java,
        ToolSpec::class.java,
        ToolCallOutcome::class.java,
        ToolCallOutcome.Read::class.java,
        ToolCallOutcome.Proposed::class.java,
        ToolCallOutcome.Failed::class.java,
        ToolCallOutcome.Refused::class.java,
        AgentContext::class.java,
        AgentPolicy::class.java,
        Proposal::class.java,
    )

    private fun forbidden(type: Class<*>): Boolean {
        val name = type.name
        return name.startsWith("app.askya.agent.apply.") ||
            name.startsWith("app.askya.data.repository.") ||
            name.startsWith("app.askya.data.db.") ||
            name.startsWith("app.askya.reminders.")
    }

    private fun touched(cls: Class<*>): List<Class<*>> =
        cls.declaredFields.map { it.type } +
            cls.declaredConstructors.flatMap { it.parameterTypes.toList() } +
            cls.declaredMethods.flatMap { it.parameterTypes.toList() + it.returnType }

    @Test
    fun `модели и инструментам применение недоступно`() {
        val leaks = agentFacing.flatMap { cls ->
            touched(cls).filter(::forbidden).map { "${cls.simpleName} → ${it.name}" }
        }
        assertTrue(leaks.isEmpty(), "дыра в границе: $leaks")
    }

    @Test
    fun `ход агента сам предложений не применяет`() {
        val names = ToolTurn::class.java.declaredMethods.map { it.name } +
            ToolRegistry::class.java.declaredMethods.map { it.name }
        assertTrue(names.none { it.contains("apply", ignoreCase = true) || it.contains("execute", ignoreCase = true) }, "$names")
    }
}
