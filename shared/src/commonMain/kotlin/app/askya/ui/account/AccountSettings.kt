package app.askya.ui.account

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.data.account.Account
import app.askya.data.account.LockAfter
import app.askya.data.account.Verdict
import app.askya.domain.account.AccountRules
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogField
import app.askya.ui.components.DialogText
import app.askya.ui.components.DialogTitle
import app.askya.ui.settings.SettingAction
import app.askya.ui.settings.SettingChoice
import app.askya.ui.settings.SettingsGroup
import app.askya.ui.theme.Danger
import kotlinx.coroutines.launch

/**
 * Какое окно аккаунта открыто. Окна рисуются поверх всей страницы настроек, а
 * строки — внутри неё, поэтому между ними ходит этот вопрос, а не лямбды.
 */
sealed interface AccountAsk {
    data object Create : AccountAsk
    data object Rename : AccountAsk

    /** [fresh] — сразу после заведения: пароль только что набран, второй раз не спрашивается. */
    data class Pin(val fresh: Boolean = false) : AccountAsk
    data object Password : AccountAsk
    data object Renew : AccountAsk
    data object Remove : AccountAsk

    /** Показать код восстановления. [fresh] — аккаунт только что заведён. */
    data class Code(val code: String, val fresh: Boolean) : AccountAsk
}

/**
 * Кучка «Аккаунт» — первой в настройках: это не про раздел, а про то, чья
 * это Askya.
 *
 * Без аккаунта в ней одна строка — «Завести». С аккаунтом — всё, что с ним
 * делают, и каждое действие, которое ослабляет замок (пин-код, пароль, код,
 * «убрать»), спрашивает пароль: открытая Askya на чужом столе не должна
 * позволять сменить замок тому, кто за этот стол сел.
 */
@Composable
fun AccountGroup(onAsk: (AccountAsk) -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val account by container.account.account.collectAsStateWithLifecycle(initialValue = null)

    SettingsGroup("Аккаунт") {
        val known = account
        if (known == null) {
            SettingAction(
                title = "Завести аккаунт",
                hint = "Имя и пароль — вход в Askya на этом устройстве, по паролю или пин-коду. " +
                    "Никуда не отправляются: сервера у Askya нет. Записи остаются как есть.",
                onClick = { onAsk(AccountAsk.Create) },
            )
            return@SettingsGroup
        }
        SettingAction(
            title = "Имя",
            hint = "Его видно на входе.",
            value = known.name,
            onClick = { onAsk(AccountAsk.Rename) },
        )
        SettingAction(
            title = "Пин-код",
            hint = pinHint(known),
            value = if (known.pin != null) "•".repeat(known.pinLength) else "Задать",
            onClick = { onAsk(AccountAsk.Pin()) },
        )
        SettingChoice(
            title = "Запирать",
            hint = "Сколько Askya может пробыть в фоне, прежде чем снова спросит пин-код " +
                "или пароль. При каждом новом запуске спрашивает всегда.",
            values = LockAfter.entries,
            chosen = known.lockAfter,
            label = { it.title },
            onPick = { scope.launch { container.gate.setLockAfter(it) } },
        )
        SettingAction(
            title = "Сменить пароль",
            hint = "Нужен прежний.",
            onClick = { onAsk(AccountAsk.Password) },
        )
        SettingAction(
            title = "Новый код восстановления",
            hint = "Если прежний потерялся. Прежний сразу перестанет подходить.",
            onClick = { onAsk(AccountAsk.Renew) },
        )
        SettingAction(
            title = "Запереть сейчас",
            hint = "Askya закроется экраном входа, не дожидаясь срока.",
            onClick = { container.gate.lockNow() },
        )
        SettingAction(
            title = "Убрать аккаунт",
            hint = "Askya снова будет открываться без пароля. Записи не трогаются.",
            onClick = { onAsk(AccountAsk.Remove) },
        )
    }
}

private fun pinHint(account: Account): String = when {
    account.pin == null -> "Не задан — вход только паролем."
    !account.pinUsable -> "Пять промахов подряд — пока не войдут паролем, пин-код не принимается."
    else -> "Быстрый вход на каждый день. Пять промахов подряд — и дальше только паролем."
}

/** Окна кучки «Аккаунт» — рисуются поверх всей страницы настроек. */
@Composable
fun AccountDialogs(ask: AccountAsk?, onAsk: (AccountAsk?) -> Unit) {
    val close = { onAsk(null) }
    when (ask) {
        null -> Unit
        AccountAsk.Create -> CreateDialog(onCreated = { onAsk(AccountAsk.Code(it, fresh = true)) }, onDismiss = close)
        AccountAsk.Rename -> RenameDialog(onDismiss = close)
        is AccountAsk.Pin -> PinDialog(fresh = ask.fresh, onDismiss = close)
        AccountAsk.Password -> PasswordDialog(onDismiss = close)
        AccountAsk.Renew -> RenewDialog(onRenewed = { onAsk(AccountAsk.Code(it, fresh = false)) }, onDismiss = close)
        AccountAsk.Remove -> RemoveDialog(onDismiss = close)
        is AccountAsk.Code -> CodeDialog(
            code = ask.code,
            fresh = ask.fresh,
            onPin = { onAsk(AccountAsk.Pin(fresh = true)) },
            onDismiss = close,
        )
    }
}

@Composable
private fun CreateDialog(onCreated: (String) -> Unit, onDismiss: () -> Unit) {
    val gate = appContainer().gate
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        problem = AccountRules.nameProblem(name) ?: AccountRules.passwordProblem(password, again)
        if (problem != null) return
        busy = true
        scope.launch { onCreated(gate.create(name, password)) }
    }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Person) }) {
        DialogTitle("Аккаунт Askya")
        DialogText(
            "Имя видно на входе. Пароль — от восьми знаков: он же когда-нибудь закроет " +
                "записи, уехавшие в облако. Пин-код для каждого дня зададите следом.",
        )
        DialogField(value = name, onValueChange = { name = it; problem = null }, hint = "Имя",
            autoFocus = true, modifier = Modifier.padding(top = 16.dp))
        DialogField(value = password, onValueChange = { password = it; problem = null }, hint = "Пароль",
            secret = true, modifier = Modifier.padding(top = 10.dp))
        DialogField(value = again, onValueChange = { again = it; problem = null }, hint = "Он же ещё раз",
            secret = true, onDone = ::submit, modifier = Modifier.padding(top = 10.dp))
        Problem(problem)
        Answers(confirm = "Завести", busy = busy, onConfirm = ::submit, onDismiss = onDismiss)
    }
}

@Composable
private fun RenameDialog(onDismiss: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val account by container.account.account.collectAsStateWithLifecycle(initialValue = null)
    var name by remember(account != null) { mutableStateOf(account?.name.orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun submit() {
        problem = AccountRules.nameProblem(name)
        if (problem != null) return
        scope.launch {
            container.gate.rename(name)
            onDismiss()
        }
    }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Person) }) {
        DialogTitle("Имя")
        DialogField(value = name, onValueChange = { name = it; problem = null }, hint = "Имя",
            autoFocus = true, onDone = ::submit, modifier = Modifier.padding(top = 16.dp))
        Problem(problem)
        Answers(confirm = "Готово", busy = false, onConfirm = ::submit, onDismiss = onDismiss)
    }
}

@Composable
private fun PinDialog(fresh: Boolean, onDismiss: () -> Unit) {
    val container = appContainer()
    val gate = container.gate
    val scope = rememberCoroutineScope()
    val account by container.account.account.collectAsStateWithLifecycle(initialValue = null)
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val had = account?.pin != null

    fun apply(newPin: String?) {
        if (busy) return
        if (newPin != null) {
            problem = AccountRules.pinProblem(newPin, again)
            if (problem != null) return
        }
        busy = true
        scope.launch {
            val verdict = if (fresh) Verdict.Right else gate.confirm(password)
            if (verdict == Verdict.Right) {
                gate.setPin(newPin)
                onDismiss()
            } else {
                problem = refusal(verdict)
                busy = false
            }
        }
    }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Pin) }) {
        DialogTitle(if (had) "Пин-код" else "Задать пин-код")
        DialogText("От четырёх до шести цифр. Им входят каждый день; пароль остаётся на случай, когда пин-код не примут.")
        if (!fresh) {
            DialogField(value = password, onValueChange = { password = it; problem = null }, hint = "Пароль",
                secret = true, autoFocus = true, modifier = Modifier.padding(top = 16.dp))
        }
        DialogField(value = pin, onValueChange = { pin = it.filter { c -> c in '0'..'9' }.take(AccountRules.MAX_PIN); problem = null },
            hint = "Пин-код", secret = true, keyboard = KeyboardType.NumberPassword, autoFocus = fresh,
            modifier = Modifier.padding(top = if (fresh) 16.dp else 10.dp))
        DialogField(value = again, onValueChange = { again = it.filter { c -> c in '0'..'9' }.take(AccountRules.MAX_PIN); problem = null },
            hint = "Он же ещё раз", secret = true, keyboard = KeyboardType.NumberPassword,
            onDone = { apply(pin) }, modifier = Modifier.padding(top = 10.dp))
        Problem(problem)
        DialogButtons {
            if (had && !fresh) {
                ActionButton(
                    icon = Icons.Outlined.LockOpen,
                    label = "Убрать",
                    color = MaterialTheme.colorScheme.error,
                    enabled = !busy,
                    onClick = { apply(null) },
                )
                Spacer(Modifier.width(16.dp))
            }
            ActionButton(icon = Icons.Outlined.Check, label = "Готово", accent = true, enabled = !busy, onClick = { apply(pin) })
            Spacer(Modifier.width(16.dp))
            ActionButton(icon = Icons.Outlined.Close, label = if (fresh) "Позже" else "Отмена", onClick = onDismiss)
        }
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit) {
    val gate = appContainer().gate
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        problem = AccountRules.passwordProblem(password, again)
        if (problem != null) return
        busy = true
        scope.launch {
            val verdict = gate.confirm(old)
            if (verdict == Verdict.Right) {
                gate.changePassword(password)
                onDismiss()
            } else {
                problem = refusal(verdict)
                busy = false
            }
        }
    }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Password) }) {
        DialogTitle("Сменить пароль")
        DialogField(value = old, onValueChange = { old = it; problem = null }, hint = "Прежний пароль",
            secret = true, autoFocus = true, modifier = Modifier.padding(top = 16.dp))
        DialogField(value = password, onValueChange = { password = it; problem = null }, hint = "Новый пароль",
            secret = true, modifier = Modifier.padding(top = 10.dp))
        DialogField(value = again, onValueChange = { again = it; problem = null }, hint = "Он же ещё раз",
            secret = true, onDone = ::submit, modifier = Modifier.padding(top = 10.dp))
        Problem(problem)
        Answers(confirm = "Сменить", busy = busy, onConfirm = ::submit, onDismiss = onDismiss)
    }
}

@Composable
private fun RenewDialog(onRenewed: (String) -> Unit, onDismiss: () -> Unit) {
    val gate = appContainer().gate
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        busy = true
        scope.launch {
            val verdict = gate.confirm(password)
            if (verdict == Verdict.Right) {
                onRenewed(gate.renewRecovery())
            } else {
                problem = refusal(verdict)
                busy = false
            }
        }
    }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Key) }) {
        DialogTitle("Новый код восстановления")
        DialogText("Прежний перестанет подходить сразу, как только появится новый.")
        DialogField(value = password, onValueChange = { password = it; problem = null }, hint = "Пароль",
            secret = true, autoFocus = true, onDone = ::submit, modifier = Modifier.padding(top = 16.dp))
        Problem(problem)
        Answers(confirm = "Выдать", busy = busy, onConfirm = ::submit, onDismiss = onDismiss)
    }
}

@Composable
private fun RemoveDialog(onDismiss: () -> Unit) {
    val gate = appContainer().gate
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        busy = true
        scope.launch {
            val verdict = gate.confirm(password)
            if (verdict == Verdict.Right) {
                gate.remove()
                onDismiss()
            } else {
                problem = refusal(verdict)
                busy = false
            }
        }
    }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.LockOpen, danger = true) }) {
        DialogTitle("Убрать аккаунт?")
        DialogText(
            "Askya снова будет открываться без пароля и пин-кода. Записи, книги и счета " +
                "остаются где были — аккаунт их не хранил, а только запирал.",
        )
        DialogField(value = password, onValueChange = { password = it; problem = null }, hint = "Пароль",
            secret = true, autoFocus = true, onDone = ::submit, modifier = Modifier.padding(top = 16.dp))
        Problem(problem)
        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.LockOpen,
                label = "Убрать",
                color = MaterialTheme.colorScheme.error,
                enabled = !busy,
                onClick = ::submit,
            )
            Spacer(Modifier.width(16.dp))
            ActionButton(icon = Icons.Outlined.Close, label = "Отмена", onClick = onDismiss)
        }
    }
}

/**
 * Код восстановления — единственный раз, когда его видно. Закрыть окно мимо
 * карточки нельзя: код, пропавший от случайного касания, уже не вернуть.
 */
@Composable
private fun CodeDialog(code: String, fresh: Boolean, onPin: () -> Unit, onDismiss: () -> Unit) {
    AskyaDialog(onDismiss = {}, badge = { DialogBadge(Icons.Outlined.Key) }) {
        DialogTitle("Код восстановления")
        DialogText(
            "Если пароль забудется, войти можно этим кодом. Перепишите его на бумагу и " +
                "уберите туда, где лежат документы: второй раз Askya его не покажет, " +
                "а без пароля и кода войти будет нечем.",
        )
        RecoveryCodeCard(code = code, modifier = Modifier.padding(top = 16.dp))
        DialogButtons {
            if (fresh) {
                ActionButton(icon = Icons.Outlined.Pin, label = "Задать пин-код", accent = true, onClick = onPin)
                Spacer(Modifier.width(16.dp))
                ActionButton(icon = Icons.Outlined.Check, label = "Записал", onClick = onDismiss)
            } else {
                ActionButton(icon = Icons.Outlined.Check, label = "Записал", accent = true, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun Problem(text: String?) {
    if (text == null) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Danger,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun Answers(confirm: String, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    DialogButtons {
        ActionButton(icon = Icons.Outlined.Lock, label = confirm, accent = true, enabled = !busy, onClick = onConfirm)
        Spacer(Modifier.width(16.dp))
        ActionButton(icon = Icons.Outlined.Close, label = "Отмена", onClick = onDismiss)
    }
}

/** Почему пароль не приняли — словами для строки под полем. */
private fun refusal(verdict: Verdict): String = when (verdict) {
    is Verdict.Paused -> {
        val seconds = ((verdict.until - System.currentTimeMillis() + 999) / 1_000).coerceAtLeast(1)
        "Пароль не принят. Следующая попытка — через ${seconds / 60}:${"%02d".format(seconds % 60)}"
    }
    else -> "Не тот пароль"
}
