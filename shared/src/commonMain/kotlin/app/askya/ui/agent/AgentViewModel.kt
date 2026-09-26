package app.askya.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.agent.AgentSession
import app.askya.agent.TurnOutcome
import app.askya.agent.llm.LlmClient
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.AppContainer
import app.askya.app.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Модель экрана агента — хозяин одного разговора.
 *
 * Разговор ([AgentSession]) создаётся вместе с моделью и уходит вместе с ней:
 * ни на диске, ни в контейнере его нет. Другой клиент — другой экран и другая
 * модель, а значит, и другой разговор.
 *
 * Ход идёт в `viewModelScope`: ушёл человек с экрана — ход отменяется сам.
 */
class AgentViewModel(private val session: AgentSession) : ViewModel() {

    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private var current: Job? = null

    /** Реплика человека. Пустая или посреди хода — не отправляется. */
    fun send(text: String) {
        val asked = text.trim()
        if (asked.isEmpty() || current?.isActive == true) return
        _state.update { it.copy(lines = it.lines + AgentLine.Asked(asked), busy = true) }
        current = viewModelScope.launch {
            val line = try {
                lineOf(session.send(asked))
            } catch (cancel: CancellationException) {
                _state.update { it.copy(lines = it.lines + AgentLine.Cancelled, busy = false) }
                throw cancel
            }
            _state.update { it.copy(lines = it.lines + line, busy = false) }
        }
    }

    /** Остановить текущий ход. Незаконченный ход в разговор не попадает. */
    fun cancel() {
        current?.cancel()
    }

    private fun lineOf(outcome: TurnOutcome): AgentLine = when (outcome) {
        // Пустой ответ — не ответ: показать его пустым пузырём значило бы
        // сделать вид, что модель что-то сказала.
        is TurnOutcome.Answered -> if (outcome.text.isBlank()) AgentLine.Empty else AgentLine.Answered(outcome.text)
        TurnOutcome.Blocked -> AgentLine.Blocked
        is TurnOutcome.Failed -> AgentLine.Failed(outcome.reason)
    }

    companion object {
        /** Разговор создаётся здесь — ровно один на модель экрана. */
        fun factory(container: AppContainer, client: LlmClient) = viewModelFactory {
            initializer { AgentViewModel(container.agentSession(client)) }
        }

        /**
         * Ключ модели экрана — по клиенту. Без него экран, попросивший модель
         * для другого клиента, получил бы прежнюю — с прежним разговором и
         * прежним клиентом: так история локальной модели ушла бы облачной.
         *
         * [keyId] — номер ключа, с которым создан клиент (`AgentSettings.keyId`):
         * у Claude с другим ключом тот же [LlmClient.id], а клиент другой.
         */
        fun key(client: LlmClient, keyId: String? = null): String =
            "agent:${client.id}" + (keyId?.let { ":$it" } ?: "")
    }
}

/**
 * Модель экрана агента для [client] — своя на каждого клиента. Другого пути
 * получить её на экране нет: ключ и фабрика ставятся здесь вместе.
 */
@Composable
fun agentViewModel(client: LlmClient, keyId: String? = null): AgentViewModel {
    val container = appContainer()
    return viewModel(key = AgentViewModel.key(client, keyId), factory = AgentViewModel.factory(container, client))
}

/** Что показывает экран агента. */
data class AgentState(
    val lines: List<AgentLine> = emptyList(),
    /** Идёт ход: модель думает или ждёт инструментов. */
    val busy: Boolean = false,
)

/** Строка разговора на экране. */
sealed interface AgentLine {
    data class Asked(val text: String) : AgentLine
    data class Answered(val text: String) : AgentLine

    /** Отправлять этой модели нельзя — облако выключено. */
    data object Blocked : AgentLine

    data class Failed(val reason: String) : AgentLine

    /** Ход остановлен человеком. */
    data object Cancelled : AgentLine

    /** Модель ответила, но ничего не сказала. */
    data object Empty : AgentLine
}
