package app.askya.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {

    // Неразрывный пробел: им отбиты и тысячи, и знак рубля. В ожидаемых
    // строках он должен стоять буквально, иначе проверка ничего не проверяет.
    private val nbsp = ' '

    @Test
    fun `рубли без копеек пишутся без хвоста`() {
        assertEquals("300${nbsp}₽", formatMoney(30_000))
        assertEquals("0${nbsp}₽", formatMoney(0))
    }

    @Test
    fun `копейки показываются только когда они есть`() {
        assertEquals("12,50${nbsp}₽", formatMoney(1_250))
        assertEquals("12,05${nbsp}₽", formatMoney(1_205))
    }

    @Test
    fun `тысячи отбиты неразрывным пробелом`() {
        assertEquals("1${nbsp}234${nbsp}₽", formatMoney(123_400))
        assertEquals("1${nbsp}234${nbsp}567${nbsp}₽", formatMoney(123_456_700))
    }

    @Test
    fun `минус типографский, плюс только когда его просят`() {
        assertEquals("−500${nbsp}₽", formatMoney(-50_000))
        assertEquals("+500${nbsp}₽", formatMoney(50_000, withSign = true))
        assertEquals("500${nbsp}₽", formatMoney(50_000))
        // Минус у нуля не появляется: нуля со знаком не бывает.
        assertEquals("0${nbsp}₽", formatMoney(0, withSign = true))
    }

    @Test
    fun `запятая и точка — одно и то же`() {
        assertEquals(123_450L, parseMoney("1234,5"))
        assertEquals(123_450L, parseMoney("1234.5"))
    }

    @Test
    fun `лишнее в строке отбрасывается`() {
        assertEquals(70_000L, parseMoney("700 ₽"))
        assertEquals(123_400L, parseMoney("1 234"))
        assertEquals(45_000L, parseMoney("около 450 рублей"))
    }

    @Test
    fun `пустое — это не ноль`() {
        assertNull(parseMoney(""))
        assertNull(parseMoney("   "))
        assertNull(parseMoney("сколько-то"))
        assertEquals(0L, parseMoney("0"))
    }

    @Test
    fun `третий знак после запятой режется, а не округляется`() {
        assertEquals(1_234L, parseMoney("12,345"))
        assertEquals(1_239L, parseMoney("12,39"))
    }

    @Test
    fun `описка в двадцать нулей не становится суммой`() {
        assertNull(parseMoney("99999999999999"))
    }

    @Test
    fun `написанное читается обратно`() {
        listOf(0L, 1L, 50L, 1_00L, 123_45L, 1_000_00L).forEach { kopecks ->
            val text = moneyToText(kopecks)
            val back = parseMoney(text) ?: 0L
            assertEquals(kopecks, back, "«$text» прочиталось не тем же числом")
        }
    }

    @Test
    fun `доля предела считается, а без предела её нет`() {
        assertNull(limitShare(spent = 500, limit = 0))
        assertEquals(0.5f, limitShare(spent = 500, limit = 1000))
        assertTrue(limitShare(spent = 1500, limit = 1000)!! > 1f)
    }

    @Test
    fun `долг — это минус остатка, а плюс долгом не считается`() {
        assertEquals(20_000_00L, debtOf(-20_000_00L))
        assertEquals(0L, debtOf(0L))
        // Переплатили по карте: должны в этот момент не вы.
        assertEquals(0L, debtOf(1_500_00L))
    }

    @Test
    fun `по кредитной карте доступно столько, сколько осталось от лимита`() {
        assertEquals(80_000_00L, creditLeft(limit = 100_000_00L, amount = -20_000_00L))
        assertEquals(100_000_00L, creditLeft(limit = 100_000_00L, amount = 0L))
        // Вышли за лимит — «доступно» уходит в минус, а не притворяется нулём:
        // ноль и перерасход — разные новости.
        assertEquals(-5_000_00L, creditLeft(limit = 100_000_00L, amount = -105_000_00L))
    }

    @Test
    fun `без записанного лимита считать нечего`() {
        assertNull(creditLeft(limit = 0L, amount = -20_000_00L))
    }

    @Test
    fun `долговой счёт спрашивает про долг, а не про остаток`() {
        assertTrue(AccountKind.CREDIT.owed)
        assertTrue(AccountKind.DEBT.owed)
        assertFalse(AccountKind.CARD.owed)
        assertFalse(AccountKind.CASH.owed)
        assertFalse(AccountKind.SAVINGS.owed)
    }
}
