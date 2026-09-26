package app.askya.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.agent.llm.anthropic.AnthropicLlmClient
import app.askya.agent.llm.anthropic.CallTrace
import app.askya.app.appContainer
import app.askya.data.preferences.AgentSettings
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.CardWhite
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Danger
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge

/**
 * Разговор с агентом — пока самый простой: реплики, ответы и строка ввода.
 *
 * Разговор принадлежит одному клиенту. Клиент создаётся заново, когда
 * сменился ключ ([AgentSettings.keyId]), и тогда же начинается новый разговор
 * ([agentViewModel]). История живёт только в памяти, пока открыт экран.
 *
 * Облако выключено — экран об этом говорит, а отправленное не уходит: запрет
 * проверяет сессия перед каждым запросом, а не экран.
 */
@Composable
fun AgentScreen(onBack: () -> Unit) {
    val container = appContainer()
    // null — настройки ещё не прочитаны.
    val settings by container.agent.settings.collectAsStateWithLifecycle(initialValue = null)
    var ask by remember { mutableStateOf<AgentAsk?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenScaffold(title = "Агент", onNavigationClick = onBack, navigationIsBack = true, navigationLabel = "Назад") {
            val known = settings
            when {
                known == null -> Unit
                !known.hasKey -> Notice(
                    text = "Ключ Claude не настроен. Без него облачной модели нечего спросить.",
                    action = "Добавить ключ",
                    onAction = { ask = AgentAsk.KEY },
                )
                else -> {
                    // Клиент — один на ключ: новый ключ даёт новый клиент и новый разговор.
                    val client by produceState<AnthropicLlmClient?>(null, known.keyId) {
                        value = container.cloudClient()
                    }
                    client?.let { Conversation(it, known, onEnableCloud = { ask = AgentAsk.CONSENT }) }
                }
            }
        }
        // Окна — поверх всего экрана, как в настройках.
        AgentDialogs(ask = ask, onAsk = { ask = it })
    }
}

@Composable
private fun Conversation(client: AnthropicLlmClient, settings: AgentSettings, onEnableCloud: () -> Unit) {
    val model = agentViewModel(client, settings.keyId)
    val state by model.state.collectAsStateWithLifecycle()
    val trace by client.lastCall.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListState()

    // Новое внизу — туда и прокручивается.
    LaunchedEffect(state.lines.size, state.busy) {
        val last = list.layoutInfo.totalItemsCount - 1
        if (last >= 0) list.animateScrollToItem(last)
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        if (!settings.allowCloud) {
            Notice(
                text = "Облако выключено: модель в сети ничего не получит, и отправленное не уйдёт.",
                action = "Включить…",
                onAction = onEnableCloud,
            )
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.lines.isEmpty()) {
                item { Status("Спросите о делах, списках или заметках.", Muted) }
            }
            items(state.lines) { line -> Line(line) }
            if (state.busy) item { Status("Думает…", Muted) }
            trace?.let { call -> item { Status(traceText(call), Muted.copy(alpha = 0.7f)) } }
        }
        InputLine(
            draft = draft,
            onDraftChange = { draft = it },
            busy = state.busy,
            onSend = {
                model.send(draft)
                draft = ""
            },
            onStop = model::cancel,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Line(line: AgentLine) {
    when (line) {
        is AgentLine.Asked -> Said(line.text)
        is AgentLine.Answered -> Heard(line.text)
        AgentLine.Blocked -> Status("Облако выключено — сообщение не ушло в сеть.", Danger)
        is AgentLine.Failed -> Status("Не получилось: ${line.reason}", Danger)
        AgentLine.Cancelled -> Status("Остановлено.", Muted)
        AgentLine.Empty -> Status("Модель ничего не ответила.", Muted)
    }
}

/** Сказанное человеком — справа, в краске приложения. */
@Composable
private fun Said(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = AccentInk,
            modifier = Modifier
                .padding(start = 40.dp)
                .clip(SAID)
                .background(AccentSoft)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/** Ответ модели — слева, на белом листе. Текст можно выделить и скопировать. */
@Composable
private fun Heard(text: String) {
    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
            modifier = Modifier
                .padding(end = 40.dp)
                .clip(HEARD)
                .background(CardWhite)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun Status(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** Плашка о том, чего не хватает, и что с этим сделать. */
@Composable
private fun Notice(text: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Cream)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Ink)
        Text(
            text = action,
            style = MaterialTheme.typography.labelLarge,
            color = AccentInk,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAction)
                .padding(vertical = 4.dp),
        )
    }
}

/** Окно сообщения внизу — как везде в Askya. Идёт ход — вместо стрелки «стоп». */
@Composable
private fun InputLine(
    draft: String,
    onDraftChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typed = draft.isNotBlank()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .cardEdge(RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            if (draft.isEmpty()) {
                Text(text = "Спросить агента", style = MaterialTheme.typography.bodyLarge, color = Muted)
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
            )
        }
        if (busy) {
            RoundButton(Icons.Outlined.Stop, "Остановить", background = Ink, tint = Accent, onClick = onStop)
        } else {
            RoundButton(
                icon = Icons.Outlined.ArrowUpward,
                description = "Отправить",
                background = if (typed) Ink else Cream,
                tint = if (typed) Accent else Muted,
                onClick = { if (typed) onSend() },
            )
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, background: Color, tint: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(38.dp).clip(CircleShape).background(background).clickable(onClick = onClick),
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * Строка ручной проверки: кто, код ответа, сколько шёл и чем кончился. Ни
 * запроса, ни ответа, ни ключа в ней нет.
 */
internal fun traceText(call: CallTrace): String {
    val status = call.status?.let { "HTTP $it" } ?: "без ответа"
    val seconds = "%.1f с".format(call.durationMs / 1000.0)
    val outcome = when (call.outcome) {
        CallTrace.Outcome.OK -> "ответ"
        CallTrace.Outcome.FAILED -> "ошибка"
        CallTrace.Outcome.CANCELLED -> "отменён"
    }
    return "${call.clientId} · $status · $seconds · $outcome"
}

private val SAID = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
private val HEARD = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
