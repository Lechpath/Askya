package app.askya.data.account

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Замок целиком, на настоящем хранилище: когда запирается, чем отпирается и
 * что бывает после промахов. Часы подставные — ждать минуту ради проверки
 * «через минуту» незачем.
 *
 * Новый [AccountGate] на том же хранилище — это новый запуск приложения:
 * так и проверяется, что запертое остаётся запертым после перезапуска.
 */
class AccountGateTest {

    private var clock = 1_000_000L

    private val prefs = AccountPreferences(
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                File(Files.createTempDirectory("askya-account").toFile(), "account.preferences_pb")
                    .absolutePath.toPath()
            },
        ),
    )

    private fun launch(): AccountGate = AccountGate(prefs) { clock }

    private fun AccountGate.settled(): Gate = runBlocking {
        withTimeout(5_000) { state.first { it != Gate.UNKNOWN } }
    }

    /** Завести аккаунт, «перезапустить» и войти паролем. */
    private fun entered(lockAfter: LockAfter = LockAfter.MINUTE, pin: String? = null): Pair<AccountGate, String> {
        val code = runBlocking {
            val first = launch()
            first.settled()
            val code = first.create("Лёша", PASSWORD)
            first.setLockAfter(lockAfter)
            if (pin != null) first.setPin(pin)
            code
        }
        val gate = launch()
        assertEquals(Gate.LOCKED, gate.settled())
        assertEquals(Verdict.Right, runBlocking { gate.enterWithPassword(PASSWORD) })
        assertEquals(Gate.OPEN, gate.state.value)
        return gate to code
    }

    @Test
    fun `без аккаунта Askya открыта`() {
        assertEquals(Gate.OPEN, launch().settled())
    }

    @Test
    fun `заведённый аккаунт не запирает того, кто его завёл`() = runBlocking {
        val gate = launch()
        gate.settled()
        gate.create("Лёша", PASSWORD)
        assertEquals(Gate.OPEN, gate.state.value)
    }

    @Test
    fun `вернувшийся через полминуты входит без пароля, через минуту — нет`() {
        val (gate, _) = entered(LockAfter.MINUTE)

        gate.wentAway()
        clock += 30_000
        gate.cameBack()
        assertEquals(Gate.OPEN, gate.state.value)

        gate.wentAway()
        clock += 61_000
        gate.cameBack()
        assertEquals(Gate.LOCKED, gate.state.value)
    }

    @Test
    fun `«сразу» запирает уже на уходе`() {
        val (gate, _) = entered(LockAfter.NOW)
        gate.wentAway()
        assertEquals(Gate.LOCKED, gate.state.value)
    }

    @Test
    fun `пять промахов пин-кода — и только паролем`() = runBlocking {
        val (gate, _) = entered(pin = "2580")
        gate.lockNow()

        repeat(4) { assertIs<Verdict.Wrong>(gate.enterWithPin("1111")) }
        assertEquals(Verdict.PinSpent, gate.enterWithPin("1111"))
        // Верный пин-код после пятого промаха тоже не принимается.
        assertEquals(Verdict.PinSpent, gate.enterWithPin("2580"))
        assertEquals(Gate.LOCKED, gate.state.value)

        assertEquals(Verdict.Right, gate.enterWithPassword(PASSWORD))
        gate.lockNow()
        assertEquals(Verdict.Right, gate.enterWithPin("2580"))
    }

    @Test
    fun `после пятого промаха пароля пауза держит даже верный`() = runBlocking {
        val (gate, _) = entered()
        gate.lockNow()

        repeat(4) { assertIs<Verdict.Wrong>(gate.enterWithPassword("не-тот-пароль")) }
        val paused = gate.enterWithPassword("не-тот-пароль")
        assertIs<Verdict.Paused>(paused)
        assertEquals(clock + 30_000, paused.until)

        assertIs<Verdict.Paused>(gate.enterWithPassword(PASSWORD))
        clock += 31_000
        assertEquals(Verdict.Right, gate.enterWithPassword(PASSWORD))
    }

    @Test
    fun `код восстановления ставит новый пароль и сгорает`() = runBlocking {
        val (gate, code) = entered()
        gate.lockNow()

        val done = gate.recover(code.lowercase(), "новый-пароль")
        assertIs<Recovery.Done>(done)
        // Экран входа ещё показывает новый код — впускает он сам.
        assertEquals(Gate.LOCKED, gate.state.value)
        gate.admitRecovered()
        assertEquals(Gate.OPEN, gate.state.value)

        gate.lockNow()
        assertIs<Recovery.Refused>(gate.recover(code, "ещё-один-пароль"))
        assertIs<Verdict.Wrong>(gate.enterWithPassword(PASSWORD))
        assertEquals(Verdict.Right, gate.enterWithPassword("новый-пароль"))
        assertTrue(done.code != null && done.code != code)
    }

    @Test
    fun `впустить без восстановления нельзя`() {
        val (gate, _) = entered()
        gate.lockNow()
        gate.admitRecovered()
        assertEquals(Gate.LOCKED, gate.state.value)
    }

    @Test
    fun `убранный аккаунт открывает Askya и после перезапуска`() = runBlocking {
        val (gate, _) = entered()
        gate.remove()
        assertEquals(Gate.OPEN, gate.state.value)
        assertEquals(Gate.OPEN, launch().settled())
    }

    private companion object {
        const val PASSWORD = "длинный-пароль"
    }
}
