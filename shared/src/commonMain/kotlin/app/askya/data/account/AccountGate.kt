package app.askya.data.account

import app.askya.domain.account.Attempts
import app.askya.domain.account.Secrets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Заперта ли Askya сейчас. */
enum class Gate {
    /** Хранилище ещё не ответило — показывать приложение рано. */
    UNKNOWN,

    /** Аккаунта нет или в него вошли. */
    OPEN,

    /** Аккаунт есть, и в него надо войти. */
    LOCKED,
}

/** Чем кончилась попытка войти или подтвердить пароль. */
sealed interface Verdict {
    data object Right : Verdict

    /** Не совпало. [pinLeft] — сколько попыток пин-кода осталось; у пароля `null`. */
    data class Wrong(val pinLeft: Int? = null) : Verdict

    /** Пин-код исчерпан — дальше только паролем. */
    data object PinSpent : Verdict

    /** Пароль не принимается до [until] (миллисекунды эпохи). */
    data class Paused(val until: Long) : Verdict
}

/**
 * Замок Askya: заперта она или открыта, когда запирается и чем отпирается.
 *
 * Один на приложение, в контейнере. Экраны о замке не знают: пока он заперт,
 * приложение накрыто экраном входа целиком ([app.askya.ui.account.LockScreen]),
 * а под ним остаётся тем, чем было, — с тем же открытым днём и той же
 * страницей книги. Вошедший возвращается туда, откуда ушёл.
 *
 * Когда уходят и возвращаются, говорит система — Activity у телефона, окно у
 * компьютера ([wentAway], [cameBack]). Время отсчитывается от ухода, а не
 * от последнего касания: Askya, открытая на столе, запираться не должна, пока
 * на неё смотрят.
 *
 * Медленное (сверка отпечатков) идёт не на главном потоке: полсекунды PBKDF2
 * посреди отрисовки — это замёрзший экран входа.
 */
class AccountGate(
    private val prefs: AccountPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val gate = MutableStateFlow(Gate.UNKNOWN)
    val state: StateFlow<Gate> = gate.asStateFlow()

    /**
     * Последнее известное состояние аккаунта — чтобы [cameBack] решал сразу,
     * не дожидаясь диска: иначе вернувшийся успевал бы увидеть кадр
     * приложения до того, как его накроет вход.
     */
    @Volatile
    private var known: Account? = null

    @Volatile
    private var awayAt: Long? = null

    init {
        scope.launch {
            prefs.account.collect { account ->
                known = account
                gate.update { current ->
                    when {
                        account == null -> Gate.OPEN
                        // Первый ответ диска: аккаунт есть — входите.
                        current == Gate.UNKNOWN -> Gate.LOCKED
                        // Заведённый только что аккаунт не запирает Askya у
                        // того, кто его завёл, — он уже внутри.
                        else -> current
                    }
                }
            }
        }
    }

    /** Askya ушла с экрана. */
    fun wentAway() {
        val account = known ?: return
        if (gate.value != Gate.OPEN) return
        awayAt = now()
        // «Сразу» запирается уже на уходе, а не на возвращении: так первый
        // кадр вернувшейся Askya — наверняка экран входа.
        if (account.lockAfter == LockAfter.NOW) gate.value = Gate.LOCKED
    }

    /** Askya снова на экране: пора ли запереть — решает, сколько её не было. */
    fun cameBack() {
        val at = awayAt ?: return
        awayAt = null
        val account = known ?: return
        if (now() - at >= account.lockAfter.millis) gate.value = Gate.LOCKED
    }

    /** «Запереть сейчас» из настроек. */
    fun lockNow() {
        if (known != null) gate.value = Gate.LOCKED
    }

    suspend fun enterWithPin(pin: String): Verdict = withContext(Dispatchers.Default) {
        val account = prefs.current() ?: return@withContext open()
        val sealed = account.pin
        if (sealed == null || !account.pinUsable) return@withContext Verdict.PinSpent
        if (Secrets.matches(pin, sealed)) {
            prefs.clearPinMisses()
            open()
        } else {
            val left = Attempts.pinLeft(prefs.missPin())
            if (left == 0) Verdict.PinSpent else Verdict.Wrong(pinLeft = left)
        }
    }

    suspend fun enterWithPassword(password: String): Verdict {
        val verdict = confirm(password)
        return if (verdict == Verdict.Right) open() else verdict
    }

    /**
     * Сверить пароль, не отпирая ничего: так настройки спрашивают его перед
     * тем, как сменить пин-код или убрать аккаунт. Промахи считаются те же,
     * что на входе, — иначе подбирать пароль ходили бы в настройки.
     */
    suspend fun confirm(password: String): Verdict = withContext(Dispatchers.Default) {
        val account = prefs.current() ?: return@withContext Verdict.Right
        paused(account)?.let { return@withContext it }
        if (Secrets.matches(password, account.password)) {
            prefs.clearMisses()
            Verdict.Right
        } else {
            miss()
        }
    }

    /**
     * Сверить код восстановления и сразу поставить новый пароль.
     *
     * Код одноразовый: вошедшему им выдаётся новый ([Recovery.Done.code]), а
     * прежний больше не подходит — бумажка, по которой однажды вошли, могла
     * побывать в чужих руках.
     *
     * Сам замок при этом не открывается: новый код показывают на экране входа,
     * и впускает уже он ([admitRecovered]) — когда код переписан. Открытый
     * сразу, замок убрал бы экран вместе с кодом, которого больше не покажут.
     */
    suspend fun recover(code: String, newPassword: String): Recovery = withContext(Dispatchers.Default) {
        val account = prefs.current() ?: return@withContext Recovery.Done(null)
        paused(account)?.let { return@withContext Recovery.Refused(it) }
        if (!Secrets.matches(Secrets.normalizeCode(code), account.recovery)) {
            return@withContext Recovery.Refused(miss())
        }
        val fresh = Secrets.recoveryCode()
        prefs.setPassword(Secrets.seal(newPassword, Secrets.PASSWORD_ROUNDS))
        prefs.setRecovery(sealCode(fresh))
        prefs.clearMisses()
        recovered = true
        Recovery.Done(fresh)
    }

    /** Впустить после [recover] — только если восстановление и правда было. */
    internal fun admitRecovered() {
        if (!recovered) return
        recovered = false
        open()
    }

    @Volatile
    private var recovered = false

    /** Завести аккаунт; возвращает код восстановления — показать его один раз. */
    suspend fun create(name: String, password: String): String = withContext(Dispatchers.Default) {
        val code = Secrets.recoveryCode()
        prefs.create(
            name = name.trim(),
            password = Secrets.seal(password, Secrets.PASSWORD_ROUNDS),
            recovery = sealCode(code),
        )
        code
    }

    suspend fun changePassword(newPassword: String) = withContext(Dispatchers.Default) {
        prefs.setPassword(Secrets.seal(newPassword, Secrets.PASSWORD_ROUNDS))
    }

    /** Поставить пин-код или убрать его (`null`). */
    suspend fun setPin(pin: String?) = withContext(Dispatchers.Default) {
        if (pin == null) prefs.setPin(null, 0)
        else prefs.setPin(Secrets.seal(pin, Secrets.PIN_ROUNDS), pin.length)
    }

    /** Новый код восстановления взамен прежнего — тот больше не подходит. */
    suspend fun renewRecovery(): String = withContext(Dispatchers.Default) {
        val code = Secrets.recoveryCode()
        prefs.setRecovery(sealCode(code))
        code
    }

    suspend fun rename(name: String) = prefs.setName(name.trim())

    suspend fun setLockAfter(value: LockAfter) = prefs.setLockAfter(value)

    suspend fun remove() {
        prefs.remove()
        gate.value = Gate.OPEN
    }

    private fun open(): Verdict {
        awayAt = null
        gate.value = Gate.OPEN
        return Verdict.Right
    }

    private fun paused(account: Account): Verdict.Paused? =
        account.pausedUntil.takeIf { it > now() }?.let { Verdict.Paused(it) }

    private suspend fun miss(): Verdict {
        val at = now()
        val misses = prefs.missPassword { count ->
            Attempts.pauseAfter(count).let { pause -> if (pause > 0) at + pause else 0L }
        }
        val pause = Attempts.pauseAfter(misses)
        return if (pause > 0) Verdict.Paused(at + pause) else Verdict.Wrong()
    }

    private fun sealCode(code: String) = Secrets.seal(Secrets.normalizeCode(code), Secrets.PIN_ROUNDS)
}

/** Чем кончилось восстановление. */
sealed interface Recovery {
    /** Вошли; [code] — новый код восстановления, его надо показать. */
    data class Done(val code: String?) : Recovery

    data class Refused(val verdict: Verdict) : Recovery
}
