package app.askya.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.data.account.Account
import app.askya.data.account.Recovery
import app.askya.data.account.Verdict
import app.askya.domain.account.AccountRules
import app.askya.platform.BackHandler
import app.askya.ui.components.AskyaFlower
import app.askya.ui.components.BreathingFlower
import app.askya.ui.components.DialogField
import app.askya.ui.components.keepTaps
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.Danger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Вход в Askya — лист поверх всего приложения, пока замок заперт.
 *
 * Тот же кремовый лист и тот же цветок, что на заставке: вход — продолжение
 * заставки, а не чужое окно перед ней. Под листом приложение остаётся
 * собранным и нетронутым, и вошедший попадает туда, откуда ушёл.
 *
 * Пин-код набирается своими кнопками, а не системной клавиатурой: десять
 * крупных цифр под пальцем быстрее клавиатуры, которая сперва выезжает, а на
 * компьютере те же цифры принимаются и с клавиш. Как только набрано столько
 * цифр, сколько в пин-коде, он сверяется сам — нажимать «войти» незачем.
 *
 * Пин-кодом входят, пока он не исчерпан (пять промахов подряд), а паролем —
 * всегда: пин-код — удобство на каждый день, пароль — сам аккаунт.
 *
 * [onLeave] — «назад» на экране входа: у телефона Askya уходит в фон, а не
 * открывается, у компьютера не делается ничего.
 */
@Composable
fun LockScreen(onLeave: () -> Unit, modifier: Modifier = Modifier) {
    val account by appContainer().account.account.collectAsStateWithLifecycle(initialValue = null)

    BackHandler(onBack = onLeave)

    // Лист непрозрачен с первого кадра, даже пока хранилище не ответило:
    // иначе в эти миллисекунды сквозь него было бы видно приложение.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Касания не проходят к приложению под листом.
            .keepTaps()
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        account?.let { LockContent(it) }
    }
}

@Composable
private fun LockContent(account: Account) {
    val gate = appContainer().gate
    var way by remember { mutableStateOf(if (account.pinUsable) Way.PIN else Way.PASSWORD) }
    var busy by remember { mutableStateOf(false) }
    // Новый код восстановления, выданный после входа по старому: пока его не
    // переписали, приложение не открывается — второй раз его не покажут.
    var freshCode by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        // Цветок дышит, пока сверяется набранное: сверка идёт полсекунды,
        // и неподвижный экран на это время читался бы как зависший.
        Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            if (busy) BreathingFlower(size = 88.dp) else AskyaFlower(modifier = Modifier.size(88.dp))
        }
        Text(
            text = account.name.ifBlank { "Askya" },
            fontFamily = FontFamily.Serif,
            fontSize = 28.sp,
            letterSpacing = (-0.3).sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )

        val code = freshCode
        when {
            code != null -> FreshCode(code = code, onDone = {
                freshCode = null
                gate.admitRecovered()
            })
            way == Way.PIN -> PinEntry(
                account = account,
                onBusy = { busy = it },
                onSpent = { way = Way.PASSWORD },
                onPassword = { way = Way.PASSWORD },
            )
            way == Way.PASSWORD -> PasswordEntry(
                account = account,
                onBusy = { busy = it },
                onPin = { way = Way.PIN },
                onForgot = { way = Way.RECOVERY },
            )
            else -> RecoveryEntry(
                onBusy = { busy = it },
                onRecovered = { freshCode = it },
                onBack = { way = Way.PASSWORD },
            )
        }
    }
}

private enum class Way { PIN, PASSWORD, RECOVERY }

@Composable
private fun PinEntry(
    account: Account,
    onBusy: (Boolean) -> Unit,
    onSpent: () -> Unit,
    onPassword: () -> Unit,
) {
    val gate = appContainer().gate
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }

    fun press(digit: Char) {
        if (checking || typed.length >= account.pinLength) return
        typed += digit
        message = ""
        if (typed.length < account.pinLength) return
        checking = true
        onBusy(true)
        scope.launch {
            when (val verdict = gate.enterWithPin(typed)) {
                is Verdict.Wrong -> message = "Не тот пин-код · ${attemptsLeft(verdict.pinLeft ?: 0)}"
                Verdict.PinSpent -> onSpent()
                else -> Unit
            }
            typed = ""
            checking = false
            onBusy(false)
        }
    }

    fun erase() {
        if (!checking) typed = typed.dropLast(1)
    }

    // Цифры с клавиатуры — для компьютера и телефона с настоящими клавишами.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val char = event.utf16CodePoint.toChar()
                when {
                    char in '0'..'9' -> { press(char); true }
                    event.key == Key.Backspace -> { erase(); true }
                    else -> false
                }
            },
    ) {
        Caption("Пин-код")
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(top = 18.dp),
        ) {
            repeat(account.pinLength) { index ->
                val filled = index < typed.length
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (filled) Accent else MaterialTheme.colorScheme.background)
                        .border(1.5.dp, if (filled) Accent else MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
        Message(message)

        val rows = listOf("123", "456", "789")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { digit -> PinKey(label = digit.toString(), onClick = { press(digit) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Spacer(Modifier.size(KEY))
                PinKey(label = "0", onClick = { press('0') })
                Box(
                    modifier = Modifier
                        .size(KEY)
                        .clip(CircleShape)
                        .clickable(onClick = ::erase),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                        contentDescription = "Стереть",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Link("Войти паролем", onClick = onPassword, modifier = Modifier.padding(top = 22.dp))
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(KEY)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private val KEY = 72.dp

@Composable
private fun PasswordEntry(
    account: Account,
    onBusy: (Boolean) -> Unit,
    onPin: () -> Unit,
    onForgot: () -> Unit,
) {
    val gate = appContainer().gate
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var pausedUntil by remember { mutableStateOf(account.pausedUntil) }
    var checking by remember { mutableStateOf(false) }

    val waiting = rememberSecondsLeft(pausedUntil)

    fun submit() {
        if (checking || password.isEmpty() || waiting > 0) return
        checking = true
        onBusy(true)
        scope.launch {
            when (val verdict = gate.enterWithPassword(password)) {
                is Verdict.Wrong -> message = "Не тот пароль"
                is Verdict.Paused -> {
                    message = "Не тот пароль"
                    pausedUntil = verdict.until
                }
                else -> Unit
            }
            password = ""
            checking = false
            onBusy(false)
        }
    }

    Caption(if (account.pin != null && !account.pinUsable) "Пин-код набран неверно пять раз — теперь паролем" else "Пароль")
    DialogField(
        value = password,
        onValueChange = { password = it; message = "" },
        hint = "Пароль",
        secret = true,
        autoFocus = true,
        onDone = ::submit,
        modifier = Modifier.padding(top = 18.dp),
    )
    Message(if (waiting > 0) "Следующая попытка через ${clock(waiting)}" else message)
    EnterButton(
        label = "Войти",
        enabled = password.isNotEmpty() && waiting == 0L && !checking,
        onClick = ::submit,
    )
    if (account.pinUsable) Link("Пин-кодом", onClick = onPin, modifier = Modifier.padding(top = 18.dp))
    Link("Забыли пароль?", onClick = onForgot, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun RecoveryEntry(
    onBusy: (Boolean) -> Unit,
    onRecovered: (String) -> Unit,
    onBack: () -> Unit,
) {
    val gate = appContainer().gate
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var pausedUntil by remember { mutableStateOf(0L) }
    var checking by remember { mutableStateOf(false) }
    val waiting = rememberSecondsLeft(pausedUntil)

    val problem = AccountRules.passwordProblem(password, again)

    fun submit() {
        if (checking || waiting > 0 || code.isBlank()) return
        if (problem != null) {
            message = problem
            return
        }
        checking = true
        onBusy(true)
        scope.launch {
            when (val result = gate.recover(code, password)) {
                is Recovery.Done -> result.code?.let(onRecovered)
                is Recovery.Refused -> {
                    message = "Код не подошёл"
                    (result.verdict as? Verdict.Paused)?.let { pausedUntil = it.until }
                }
            }
            checking = false
            onBusy(false)
        }
    }

    Caption("Код восстановления")
    Text(
        text = "Двадцать знаков, которые Askya показала, когда заводили аккаунт. " +
            "Им ставится новый пароль; записи остаются как были.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp),
    )
    DialogField(
        value = code,
        onValueChange = { code = it; message = "" },
        hint = "XXXX-XXXX-XXXX-XXXX-XXXX",
        autoFocus = true,
        modifier = Modifier.padding(top = 18.dp),
    )
    DialogField(
        value = password,
        onValueChange = { password = it; message = "" },
        hint = "Новый пароль",
        secret = true,
        modifier = Modifier.padding(top = 10.dp),
    )
    DialogField(
        value = again,
        onValueChange = { again = it; message = "" },
        hint = "Он же ещё раз",
        secret = true,
        onDone = ::submit,
        modifier = Modifier.padding(top = 10.dp),
    )
    Message(if (waiting > 0) "Следующая попытка через ${clock(waiting)}" else message)
    EnterButton(
        label = "Сменить пароль и войти",
        enabled = code.isNotBlank() && password.isNotEmpty() && waiting == 0L && !checking,
        onClick = ::submit,
    )
    Link("Назад", onClick = onBack, modifier = Modifier.padding(top = 18.dp))
}

/** Новый код после входа по старому — показать один раз и только потом впустить. */
@Composable
private fun FreshCode(code: String, onDone: () -> Unit) {
    Caption("Новый код восстановления")
    RecoveryCodeCard(code = code, modifier = Modifier.padding(top = 18.dp))
    Text(
        text = "Прежний больше не подходит. Перепишите этот туда же, где лежал старый: " +
            "второй раз Askya его не покажет.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp),
    )
    EnterButton(label = "Записал — войти", enabled = true, onClick = onDone)
}

/** Код восстановления крупно, моноширинным: его переписывают на бумагу по знаку. */
@Composable
fun RecoveryCodeCard(code: String, modifier: Modifier = Modifier) {
    Text(
        text = code,
        fontFamily = FontFamily.Monospace,
        fontSize = 20.sp,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Строка под набором: ошибка или ожидание. Место держит и пустая — экран не прыгает. */
@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Danger,
        textAlign = TextAlign.Center,
        minLines = 2,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun EnterButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Accent else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun Link(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = AccentInk,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Сколько секунд ещё ждать до [until]; тикает раз в секунду, пока не станет нулём. */
@Composable
private fun rememberSecondsLeft(until: Long): Long {
    var left by remember(until) { mutableStateOf(secondsTo(until)) }
    LaunchedEffect(until) {
        while (true) {
            left = secondsTo(until)
            if (left <= 0) break
            delay(1_000)
        }
    }
    return left
}

private fun secondsTo(until: Long): Long =
    ((until - System.currentTimeMillis() + 999) / 1_000).coerceAtLeast(0)

private fun clock(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** «Осталось 3 попытки» — с числом и склонением. */
private fun attemptsLeft(left: Int): String {
    val word = when {
        left % 10 == 1 && left % 100 != 11 -> "попытка"
        left % 10 in 2..4 && left % 100 !in 12..14 -> "попытки"
        else -> "попыток"
    }
    val verb = if (left % 10 == 1 && left % 100 != 11) "осталась" else "осталось"
    return "$verb $left $word"
}
