package app.askya.agent

import app.askya.data.sync.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import kotlin.concurrent.Volatile

/**
 * Набор инструментов агента. Собирается один раз и дальше не меняется.
 *
 * Наружу отдаёт только описания ([specs]) — их и видит модель. Вызвать
 * инструмент можно только внутри хода ([openTurn]), где стоят пределы
 * [AgentPolicy].
 *
 * Применять предложения реестр не умеет — и не должен. Всё, что рождается в
 * ходе, — [Proposal] в [ProposalStatus.PENDING]; довести его до
 * [ProposalStatus.APPLIED] может только отдельный механизм, которого здесь нет.
 *
 * Ошибки сборки — исключения: повтор имени, кривое имя, инструмент двух родов
 * разом — это ошибка в коде Askya, а не в разговоре, и ловить её надо при
 * первом же запуске, а не в ответе модели.
 */
class ToolRegistry(tools: List<AgentTool>) {

    private val byName: Map<String, AgentTool>

    init {
        val seen = LinkedHashMap<String, AgentTool>()
        tools.forEach { tool ->
            require(NAME.matches(tool.name)) {
                "имя инструмента «${tool.name}»: только латиница, цифры, _ и -, до 64 знаков"
            }
            require(!(tool is ReadTool && tool is ProposeTool)) {
                "инструмент «${tool.name}» не может читать и предлагать разом"
            }
            require(tool.description.isNotBlank()) { "у инструмента «${tool.name}» нет описания" }
            require(seen.put(tool.name, tool) == null) { "инструмент «${tool.name}» заведён дважды" }
        }
        byName = seen
    }

    /** Описания для модели — в том порядке, в каком инструменты переданы. */
    val specs: List<ToolSpec> = byName.values.map { tool ->
        ToolSpec(tool.name, tool.description, tool.inputSchema, tool.kind)
    }

    /** Род инструмента по имени или `null`, если такого нет. */
    fun kindOf(name: String): ToolKind? = byName[name]?.kind

    /**
     * Открыть ход: один ответ модели на одну реплику человека, со своими
     * счётчиками вызовов и предложений.
     */
    fun openTurn(
        policy: AgentPolicy,
        context: AgentContext,
        clock: Clock = Clock.systemUTC(),
    ): ToolTurn = ToolTurn(byName, policy, context, clock)

    private companion object {
        /** То, что принимают провайдеры моделей в имени инструмента. */
        val NAME = Regex("^[a-zA-Z0-9_-]{1,64}$")
    }
}

/**
 * Один ход агента — здесь соблюдается [AgentPolicy].
 *
 * Каждый вызов считается, даже неизвестный и отклонённый: модель, которая
 * зовёт несуществующее, всё равно тратит ход, и предел должен её остановить.
 * Предложение считается, только если родилось: отказ инструмента места не
 * занимает.
 *
 * Вызовы можно делать параллельно: место под вызов и под предложение
 * занимается под замком, а сам инструмент работает вне его.
 */
class ToolTurn internal constructor(
    private val tools: Map<String, AgentTool>,
    val policy: AgentPolicy,
    val context: AgentContext,
    private val clock: Clock,
) {
    private val lock = Mutex()
    @Volatile private var calls = 0
    private var reserved = 0

    /** Меняется только под замком и только заменой целиком — читать можно без него. */
    @Volatile private var born: List<Proposal> = emptyList()

    /** Сколько вызовов уже сделано в этом ходе. */
    val callsMade: Int get() = calls

    /** Предложения, родившиеся в этом ходе, — все в [ProposalStatus.PENDING]. */
    val proposals: List<Proposal> get() = born

    suspend fun call(name: String, input: Map<String, Any?>): ToolCallOutcome {
        val tool = lock.withLock {
            if (calls >= policy.maxToolCalls) {
                return refuse(name, RefusalReason.TOOL_CALL_LIMIT, "за ход не больше ${policy.maxToolCalls} вызовов")
            }
            calls++
            tools[name] ?: return refuse(name, RefusalReason.UNKNOWN_TOOL, "инструмента «$name» нет")
        }
        return when (tool) {
            is ReadTool -> read(tool, input)
            is ProposeTool -> propose(tool, input)
        }
    }

    private suspend fun read(tool: ReadTool, input: Map<String, Any?>): ToolCallOutcome {
        val result = guarded { tool.read(input, context) }
            ?: return failed(tool.name)
        return when (result) {
            is ToolResult.Failed -> ToolCallOutcome.Failed(tool.name, result.reason)
            is ToolResult.Ok -> {
                val size = sizeOf(result.data)
                    ?: return refuse(tool.name, RefusalReason.INVALID_RESULT, "ответ инструмента не записывается в JSON")
                if (size > policy.maxResultSize) {
                    return refuse(
                        tool.name,
                        RefusalReason.RESULT_TOO_LARGE,
                        "ответ длиной $size знаков, а можно не больше ${policy.maxResultSize}",
                    )
                }
                ToolCallOutcome.Read(tool.name, result.data)
            }
        }
    }

    private suspend fun propose(tool: ProposeTool, input: Map<String, Any?>): ToolCallOutcome {
        if (!policy.allowProposals) {
            return refuse(tool.name, RefusalReason.PROPOSALS_DISABLED, "предлагать изменения сейчас нельзя")
        }
        lock.withLock {
            if (reserved >= policy.maxProposals) {
                return refuse(tool.name, RefusalReason.PROPOSAL_LIMIT, "за ход не больше ${policy.maxProposals} предложений")
            }
            reserved++
        }
        val check = try {
            guarded { tool.propose(input, context) }
        } catch (cancel: CancellationException) {
            lock.withLock { reserved-- }
            throw cancel
        }
        val refusal = when (check) {
            null -> failed(tool.name)
            is ProposalCheck.Invalid -> ToolCallOutcome.Failed(tool.name, check.reason)
            is ProposalCheck.Valid -> when {
                check.summary.isBlank() ->
                    refuse(tool.name, RefusalReason.INVALID_RESULT, "у предложения нет описания")
                check.summary.length > policy.maxResultSize -> refuse(
                    tool.name,
                    RefusalReason.RESULT_TOO_LARGE,
                    "описание предложения длиной ${check.summary.length} знаков, " +
                        "а можно не больше ${policy.maxResultSize}",
                )
                else -> null
            }
        }
        if (refusal != null) {
            // Не родилось — место освобождается: отказ предложением не считается.
            lock.withLock { reserved-- }
            return refusal
        }
        val valid = check as ProposalCheck.Valid
        val now = clock.instant()
        val proposal = Proposal(
            id = ProposalId.new(),
            tool = tool.name,
            summary = valid.summary,
            payload = valid.payload,
            status = ProposalStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
        lock.withLock { born = born + proposal }
        return ToolCallOutcome.Proposed(proposal)
    }

    /**
     * Вызвать инструмент, не дав ему уронить ход. Отмена проходит насквозь —
     * её глушить нельзя, иначе ход не остановится, когда человек ушёл.
     */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }

    /** Подробности исключения модели не отдаются: в них бывает что угодно из базы. */
    private fun failed(name: String) =
        ToolCallOutcome.Failed(name, "инструмент «$name» не смог ответить")

    private fun refuse(name: String, reason: RefusalReason, message: String) =
        ToolCallOutcome.Refused(name, reason, message)

    private fun sizeOf(data: Map<String, Any?>): Int? =
        runCatching { Json.write(data).length }.getOrNull()
}

/** Чем кончился вызов инструмента. Всё это можно передать модели словами. */
sealed interface ToolCallOutcome {
    val tool: String

    /** Прочитано — данные уже проверены на размер и на то, что это JSON. */
    data class Read(override val tool: String, val data: Map<String, Any?>) : ToolCallOutcome

    /** Родилось предложение и ждёт человека. Ничего ещё не сделано. */
    data class Proposed(val proposal: Proposal) : ToolCallOutcome {
        override val tool: String get() = proposal.tool
    }

    /** Инструмент сам отказал или сломался. */
    data class Failed(override val tool: String, val reason: String) : ToolCallOutcome

    /** Вызов не пропустили правила хода — до инструмента дело могло и не дойти. */
    data class Refused(
        override val tool: String,
        val reason: RefusalReason,
        val message: String,
    ) : ToolCallOutcome
}

/** Почему правила хода не пропустили вызов. */
enum class RefusalReason {
    UNKNOWN_TOOL,
    TOOL_CALL_LIMIT,
    PROPOSAL_LIMIT,
    PROPOSALS_DISABLED,
    RESULT_TOO_LARGE,
    INVALID_RESULT,
}
