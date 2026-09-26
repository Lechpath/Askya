package app.askya.agent

import app.askya.agent.llm.LlmClient
import app.askya.agent.llm.LlmMessage
import app.askya.agent.llm.LlmPart
import app.askya.agent.llm.LlmReply
import app.askya.agent.llm.LlmRequest
import app.askya.agent.llm.Role
import app.askya.data.sync.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import kotlin.concurrent.Volatile

/**
 * Один разговор с одной моделью.
 *
 * **Клиент задаётся при создании и больше не меняется.** Поля для другого
 * клиента нет, метода, который принял бы другого, — тоже. Другой клиент —
 * другой разговор, с пустой историей: то, что прочёл локальный клиент, никогда
 * не уезжает облачному само (`AI_AGENT_ARCHITECTURE.md`, §12).
 *
 * **Граница приватности — здесь, перед каждой отправкой.** Каждый раз, прежде
 * чем отдать историю клиенту, сессия заново спрашивает [policy] и
 * [AgentPolicy.allowsClient]. Не при создании и не один раз на ход: политика
 * могла смениться, пока шёл ход, а ответ инструмента с текстом заметок
 * рождается уже после первого запроса. «Нельзя» — [TurnOutcome.Blocked]:
 * клиент не вызывается, история ему не передаётся.
 *
 * Инструменты берутся только из [tools] — модель видит их описания и больше
 * ничего. Лимиты хода соблюдает [ToolTurn]; сессия их не повторяет.
 *
 * **История** — только в памяти и только из законченных ходов. Ход, который
 * кончился отказом, ошибкой или отменой, в историю не попадает: иначе в ней
 * остался бы вызов инструмента без ответа, и следующий запрос был бы битым.
 * Свёртка длинной истории, когда понадобится, заменит [history] здесь же, не
 * трогая экран.
 *
 * Ходы идут по одному: второй ждёт, пока кончится первый.
 */
class AgentSession(
    val client: LlmClient,
    private val tools: ToolRegistry,
    /** Политика на сейчас — спрашивается перед каждой отправкой, а не запоминается. */
    private val policy: () -> AgentPolicy,
    private val context: () -> AgentContext,
    private val system: String = SYSTEM,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Mutex()

    /** Меняется только под замком и только заменой целиком. */
    @Volatile private var done: List<LlmMessage> = emptyList()

    /** Законченные ходы разговора — ровно то, что уйдёт модели в следующий раз. */
    val history: List<LlmMessage> get() = done

    /** Реплика человека — и весь ход до ответа модели. */
    suspend fun send(text: String): TurnOutcome = lock.withLock { turn(text) }

    private suspend fun turn(text: String): TurnOutcome {
        // Лимиты хода — по политике на его начало; разрешение на отправку —
        // заново перед каждой отправкой.
        val limits = policy()
        val turn = tools.openTurn(limits, context(), clock)
        val pending = mutableListOf(LlmMessage(Role.USER, listOf(LlmPart.Text(text))))
        var pastLimit = false

        while (true) {
            if (!policy().allowsClient(client.online)) return TurnOutcome.Blocked

            val reply = try {
                client.next(LlmRequest(system, done + pending, tools.specs))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // Текст исключения не показывается: в нём бывает запрос целиком.
                return TurnOutcome.Failed(CLIENT_FAILED)
            }

            val parts = when (reply) {
                is LlmReply.Failure -> return TurnOutcome.Failed(reply.message)
                is LlmReply.Turn -> reply.parts
            }
            pending += LlmMessage(Role.ASSISTANT, parts)

            val calls = parts.filterIsInstance<LlmPart.ToolCall>()
            if (calls.isEmpty()) {
                done = done + pending
                return TurnOutcome.Answered(
                    text = parts.filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text },
                    proposals = turn.proposals,
                )
            }

            // Вызовы сверх предела откажет сам ход, и модель узнает об этом из
            // ответа. Но если она и после этого зовёт инструменты, ход не
            // кончится никогда — второй раз его прерывает сессия.
            if (turn.callsMade >= limits.maxToolCalls) {
                if (pastLimit) return TurnOutcome.Failed(NO_END)
                pastLimit = true
            }
            val results = calls.map { call -> resultOf(call, turn.call(call.name, call.input)) }
            pending += LlmMessage(Role.USER, results)
        }
    }

    /** Исход вызова — словами для модели. Ошибка — `isError`, а не исключение. */
    private fun resultOf(call: LlmPart.ToolCall, outcome: ToolCallOutcome): LlmPart.ToolResult =
        when (outcome) {
            is ToolCallOutcome.Read -> LlmPart.ToolResult(call.id, Json.write(outcome.data), isError = false)
            is ToolCallOutcome.Proposed -> LlmPart.ToolResult(
                call.id,
                Json.write(
                    mapOf(
                        "proposal" to outcome.proposal.id.value,
                        "summary" to outcome.proposal.summary,
                        "status" to outcome.proposal.status.name,
                    ),
                ),
                isError = false,
            )
            is ToolCallOutcome.Failed -> LlmPart.ToolResult(call.id, outcome.reason, isError = true)
            is ToolCallOutcome.Refused -> LlmPart.ToolResult(call.id, outcome.message, isError = true)
        }

    companion object {
        /** Неизменная часть запроса — правила из §12 архитектуры. */
        const val SYSTEM =
            "Ты помощник в приложении Askya. Данные человека получай только инструментами. " +
                "Не выдумывай номера записей. Ничего не записывай сам — только предлагай. " +
                "Отвечай по-русски и коротко."

        const val CLIENT_FAILED = "модель не ответила"
        const val NO_END = "модель не остановилась — скажите иначе"
    }
}

/** Чем кончился ход. */
sealed interface TurnOutcome {

    /** Модель ответила. [proposals] — родившиеся в ходе и ждущие человека. */
    data class Answered(val text: String, val proposals: List<Proposal>) : TurnOutcome

    /**
     * Отправлять этому клиенту нельзя (облако выключено). Клиент не
     * вызывался, ход в историю не попал.
     */
    data object Blocked : TurnOutcome

    /** Ход не удался — словами для человека. В историю он не попал. */
    data class Failed(val reason: String) : TurnOutcome
}
