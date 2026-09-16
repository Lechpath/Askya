package app.askya.domain.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Правила аккаунта: что принимается паролем и пин-кодом, сколько промахов
 * прощается и как растёт пауза. На телефоне часы не подкрутить, а ошибка
 * здесь — это хозяин, запертый снаружи своей же Askya.
 */
class AccountRulesTest {

    @Test
    fun `пароль короче восьми знаков не принимается`() {
        assertNotNull(AccountRules.passwordProblem("1234567", "1234567"))
        assertNull(AccountRules.passwordProblem("12345678", "12345678"))
    }

    @Test
    fun `пароль должен совпасть с повтором`() {
        assertEquals("Пароли не совпадают", AccountRules.passwordProblem("длинный-пароль", "длинный-парол"))
    }

    @Test
    fun `пин-код от четырёх до шести цифр`() {
        assertNotNull(AccountRules.pinProblem("123", "123"))
        assertNull(AccountRules.pinProblem("1234", "1234"))
        assertNull(AccountRules.pinProblem("123456", "123456"))
        assertNotNull(AccountRules.pinProblem("1234567", "1234567"))
    }

    @Test
    fun `в пин-коде только арабские цифры`() {
        assertNotNull(AccountRules.pinProblem("12a4", "12a4"))
        // Цифры других письменностей Char.isDigit считает цифрами, а
        // клавиатура пин-кода их не набирает — такой код не ввести.
        assertNotNull(AccountRules.pinProblem("١٢٣٤", "١٢٣٤"))
    }

    @Test
    fun `одна цифра подряд не годится`() {
        assertNotNull(AccountRules.pinProblem("0000", "0000"))
    }

    @Test
    fun `имя нужно и не длиннее сорока`() {
        assertNotNull(AccountRules.nameProblem("   "))
        assertNotNull(AccountRules.nameProblem("я".repeat(41)))
        assertNull(AccountRules.nameProblem("Лёша"))
    }

    @Test
    fun `пин-коду пять попыток`() {
        assertEquals(5, Attempts.pinLeft(0))
        assertEquals(1, Attempts.pinLeft(4))
        assertEquals(0, Attempts.pinLeft(5))
        assertEquals(0, Attempts.pinLeft(9))
    }

    @Test
    fun `пароль прощает пять промахов, дальше пауза удваивается до часа`() {
        assertEquals(0L, Attempts.pauseAfter(4))
        assertEquals(30_000L, Attempts.pauseAfter(5))
        assertEquals(60_000L, Attempts.pauseAfter(6))
        assertEquals(120_000L, Attempts.pauseAfter(7))
        assertEquals(3_600_000L, Attempts.pauseAfter(12))
        assertEquals(3_600_000L, Attempts.pauseAfter(1_000))
    }

    @Test
    fun `отпечаток сверяется с тем же и не сверяется с другим`() {
        val sealed = Secrets.seal("1234", rounds = 1_000)
        assertTrue(Secrets.matches("1234", sealed))
        assertTrue(!Secrets.matches("1235", sealed))
    }

    @Test
    fun `у одинаковых паролей разные отпечатки`() {
        // Соль своя у каждого: по двум одинаковым отпечаткам нельзя понять,
        // что пароли одинаковые.
        assertNotEquals(Secrets.seal("пароль-один", 1_000), Secrets.seal("пароль-один", 1_000))
    }

    @Test
    fun `испорченный отпечаток не пускает и не роняет`() {
        assertTrue(!Secrets.matches("1234", ""))
        assertTrue(!Secrets.matches("1234", "pbkdf2-sha256\$abc\$\$"))
        assertTrue(!Secrets.matches("1234", "md5\$1\$AA\$AA"))
    }

    @Test
    fun `код восстановления читается однозначно и набирается как угодно`() {
        val code = Secrets.recoveryCode()
        assertTrue(Regex("[A-Z2-9]{4}(-[A-Z2-9]{4}){4}").matches(code), code)
        assertTrue(code.none { it in "01OIL" }, code)
        assertEquals(code.replace("-", ""), Secrets.normalizeCode(" " + code.lowercase().replace("-", " ") + " "))
    }
}
