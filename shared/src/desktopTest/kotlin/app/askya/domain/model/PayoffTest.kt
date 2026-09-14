package app.askya.domain.model

import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayoffTest {

    private val from = YearMonth.of(2026, 8)

    @Test
    fun `без процентов долг просто делится на месяцы`() {
        val plan = payoff(debt = 120_000_00, rate = 0, months = 12, from = from)!!
        assertEquals(10_000_00, plan.monthly)
        assertEquals(120_000_00, plan.total)
        assertEquals(0, plan.overpay)
    }

    @Test
    fun `неделящийся долг округляется вверх, и последний платёж меньше`() {
        // Сто рублей на три месяца: 33,34 + 33,34 + 33,32.
        val plan = payoff(debt = 100_00, rate = 0, months = 3, from = from)!!
        assertEquals(33_34, plan.monthly)
        // Долг закрыт ровно, а не с хвостом в две копейки сверху.
        assertEquals(100_00, plan.total)
        assertEquals(0, plan.overpay)
    }

    @Test
    fun `под процент платёж больше, а переплата положительна`() {
        val free = payoff(debt = 100_000_00, rate = 0, months = 12, from = from)!!
        val paid = payoff(debt = 100_000_00, rate = 2490, months = 12, from = from)!!

        assertTrue(paid.monthly > free.monthly)
        assertTrue(paid.overpay > 0)
        // Аннуитет на год под 24,9 % — около 9 490 ₽ в месяц; проверяется
        // порядок, а не копейка: формула здесь та же, что у банка, и подгонять
        // тест под её собственный ответ значило бы ничего не проверить.
        assertTrue(paid.monthly in 9_400_00..9_600_00, "вышло ${paid.monthly}")
    }

    @Test
    fun `чем дольше срок, тем меньше платёж и больше переплата`() {
        val plans = payoffPlans(debt = 300_000_00, rate = 1800, from = from)

        assertEquals(6, plans.size)
        plans.zipWithNext { sooner, later ->
            assertTrue(later.monthly < sooner.monthly)
            assertTrue(later.overpay > sooner.overpay)
        }
    }

    @Test
    fun `срок кончается тем месяцем, в который платят последний раз`() {
        assertEquals(from, payoff(10_000_00, 0, 1, from)!!.until)
        assertEquals(YearMonth.of(2028, 7), payoff(10_000_00, 0, 24, from)!!.until)
    }

    @Test
    fun `нечего гасить — нечего и считать`() {
        assertNull(payoff(debt = 0, rate = 1800, months = 12, from = from))
        assertNull(payoff(debt = -100, rate = 1800, months = 12, from = from))
        assertTrue(payoffPlans(debt = 0, rate = 0, from = from).isEmpty())
    }

    @Test
    fun `снежный ком начинает с маленького, лавина — с дорогого`() {
        val card = Debt(1, "Карта", 80_000_00, 2490)
        val loan = Debt(2, "Заём", 10_000_00, 900)
        val debts = listOf(card, loan)

        assertEquals(listOf(loan, card), payoffQueue(debts, PayoffOrder.SNOWBALL))
        assertEquals(listOf(card, loan), payoffQueue(debts, PayoffOrder.AVALANCHE))
    }

    @Test
    fun `погашенный долг в очередь не встаёт`() {
        val debts = listOf(Debt(1, "Карта", 0, 2490), Debt(2, "Заём", 500_00, 0))
        assertEquals(listOf(2L), payoffQueue(debts, PayoffOrder.SNOWBALL).map { it.id })
    }

    @Test
    fun `ставка читается с запятой и точкой одинаково`() {
        assertEquals(2490, parseRate("24,9"))
        assertEquals(2490, parseRate("24.9"))
        assertEquals(2490, parseRate("24,90 %"))
        assertEquals(1800, parseRate("18"))
        assertNull(parseRate(""))
        assertNull(parseRate("годовых"))
        // Лишний ноль — описка, а не ставка.
        assertNull(parseRate("2490"))
    }

    @Test
    fun `ставка пишется так же, как её набирали`() {
        assertEquals("24,9", rateToText(2490))
        assertEquals("18", rateToText(1800))
        assertEquals("7,55", rateToText(755))
        assertEquals("", rateToText(0))
        assertEquals("24,9 % годовых", formatRate(2490))
        assertEquals("", formatRate(0))
    }
}
