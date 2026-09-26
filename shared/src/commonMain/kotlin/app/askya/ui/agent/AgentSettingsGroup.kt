package app.askya.ui.agent

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.data.preferences.AgentSettings
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogField
import app.askya.ui.components.DialogText
import app.askya.ui.components.DialogTitle
import app.askya.ui.settings.SettingAction
import app.askya.ui.settings.SettingSwitch
import app.askya.ui.settings.SettingsGroup
import app.askya.ui.theme.Danger
import kotlinx.coroutines.launch

/** Какое окно настроек агента открыто. */
enum class AgentAsk { KEY, REMOVE_KEY, CONSENT }

/**
 * Настройки агента: ключ Claude и согласие на облачную модель.
 *
 * Три состояния: нет ключа — облако не включить; ключ есть, облако выключено
 * (так после установки); ключ есть и облако включено. Ключ после сохранения
 * не показывается — только последние знаки.
 */
@Composable
fun AgentGroup(onAsk: (AgentAsk) -> Unit, onOpenAgent: () -> Unit) {
    val agent = appContainer().agent
    val scope = rememberCoroutineScope()
    val settings by agent.settings.collectAsStateWithLifecycle(initialValue = AgentSettings(keyTail = null, allowCloud = false))

    SettingsGroup("Агент") {
        SettingAction(
            title = "Разговор с агентом",
            hint = "Спросить о делах, списках и заметках словами. Разговор живёт, " +
                "пока открыт экран, и нигде не сохраняется.",
            onClick = onOpenAgent,
        )
        SettingAction(
            title = "Ключ Claude",
            hint = "Нужен облачной модели. Хранится только на этом устройстве: " +
                "не попадает ни в Слепок, ни на другие устройства.",
            value = settings.keyTail?.let { "••••••••$it" } ?: "Добавить",
            onClick = { onAsk(AgentAsk.KEY) },
        )
        if (settings.hasKey) {
            SettingAction(
                title = "Удалить ключ",
                hint = "Облачная модель выключится вместе с ним.",
                onClick = { onAsk(AgentAsk.REMOVE_KEY) },
            )
        }
        SettingSwitch(
            title = "Использовать облачную модель",
            hint = CLOUD_WARNING + if (settings.hasKey) "" else " Сначала добавьте ключ Claude.",
            checked = settings.allowCloud,
            enabled = settings.hasKey,
            // Включают — через окно с тем, что уйдёт в сеть; выключают — сразу.
            onChange = { allow -> if (allow) onAsk(AgentAsk.CONSENT) else scope.launch { agent.setAllowCloud(false) } },
        )
    }
}

/** Окна настроек агента — поверх всей страницы, как окна аккаунта. */
@Composable
fun AgentDialogs(ask: AgentAsk?, onAsk: (AgentAsk?) -> Unit) {
    when (ask) {
        AgentAsk.KEY -> KeyDialog(onDismiss = { onAsk(null) })
        AgentAsk.REMOVE_KEY -> RemoveKeyDialog(onDismiss = { onAsk(null) })
        AgentAsk.CONSENT -> ConsentDialog(onDismiss = { onAsk(null) })
        null -> Unit
    }
}

/**
 * Ввод ключа. Набранное живёт только пока открыто окно: закрыли — забыто.
 * Поле скрытое, как у пароля.
 */
@Composable
private fun KeyDialog(onDismiss: () -> Unit) {
    val agent = appContainer().agent
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }

    fun submit() {
        scope.launch {
            if (agent.setKey(key)) onDismiss() else problem = "Ключ — одно слово без пробелов"
        }
    }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Key) }) {
        DialogTitle("Ключ Claude")
        DialogText(
            "Ключ берут в консоли Anthropic. Он хранится только на этом устройстве, " +
                "и облако от него само не включится.",
        )
        DialogField(
            value = key,
            onValueChange = { key = it; problem = null },
            hint = "Ключ",
            secret = true,
            autoFocus = true,
            onDone = ::submit,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (problem != null) {
            Text(
                text = problem.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Danger,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        DialogButtons {
            ActionButton(icon = Icons.Outlined.Check, label = "Сохранить", accent = true, onClick = ::submit)
            Spacer(Modifier.width(16.dp))
            ActionButton(icon = Icons.Outlined.Close, label = "Отмена", onClick = onDismiss)
        }
    }
}

@Composable
private fun RemoveKeyDialog(onDismiss: () -> Unit) {
    val agent = appContainer().agent
    val scope = rememberCoroutineScope()

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Delete, danger = true) }) {
        DialogTitle("Удалить ключ?")
        DialogText("Облачная модель выключится. Чтобы включить её снова, ключ придётся ввести заново.")
        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Delete,
                label = "Удалить",
                color = MaterialTheme.colorScheme.error,
                onClick = { scope.launch { agent.removeKey(); onDismiss() } },
            )
            Spacer(Modifier.width(16.dp))
            ActionButton(icon = Icons.Outlined.Close, label = "Отмена", onClick = onDismiss)
        }
    }
}

/**
 * Согласие на облачную модель — перед включением, с тем, что именно может
 * уйти в сеть. Без ключа включить нечего: окно говорит об этом и не включает.
 */
@Composable
private fun ConsentDialog(onDismiss: () -> Unit) {
    val agent = appContainer().agent
    val scope = rememberCoroutineScope()

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Cloud) }) {
        DialogTitle("Облачная модель")
        DialogText(
            "Разговор будет уходить Claude — облачной модели Anthropic. Вместе с ним " +
                "могут уйти данные, которые агент читает по вашей просьбе:",
        )
        DialogText(
            "• дела\n• списки\n• напоминания\n• тексты заметок",
            modifier = Modifier.padding(top = 8.dp),
        )
        DialogText(
            "Выключить можно в любой момент — следующий запрос уже не уйдёт. " +
                "Согласие действует только на этом устройстве.",
            modifier = Modifier.padding(top = 8.dp),
        )
        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Cloud,
                label = "Включить",
                accent = true,
                onClick = { scope.launch { agent.setAllowCloud(true); onDismiss() } },
            )
            Spacer(Modifier.width(16.dp))
            ActionButton(icon = Icons.Outlined.Close, label = "Отмена", onClick = onDismiss)
        }
    }
}

/** Что уходит в облако — прямо в пояснении к выключателю. */
const val CLOUD_WARNING =
    "При включении облачного режима данные, которые агент получает через инструменты — " +
        "включая дела, списки, напоминания и тексты заметок — могут быть отправлены облачной модели."
