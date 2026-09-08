package app.askya.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate
import java.time.YearMonth

/**
 * Пульс нити считается арифметикой, которую на телефоне не проверить: чтобы
 * увидеть «за полгода 19 дней», нужны полгода записей, а чтобы поймать ошибку
 * в счёте — вторые полгода с известным ответом. То же рассуждение, по которому
 * тестами покрыт счёт «Прожитого».
 */
class ThreadPulseTest {

    private val through = YearMonth.of(2026, 9)

    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test
    fun `считаются дни, а не события`() {
        // Один день, в который отметили дело и записали трату, — это один день
        // работы, а не два: иначе нить хвалили бы за подряд поставленные
        // галочки.
        val pulse = pulseOf(
            touches = listOf(day(9, 1), day(9, 1), day(9, 1), day(9, 2)),
            through = through,
        )
        assertEquals(2, pulse.days)
    }

    @Test
    fun `полоска всегда из шести месяцев, включая пустые`() {
        val pulse = pulseOf(touches = listOf(day(9, 5)), through = through)

        assertEquals(PULSE_MONTHS, pulse.months.size)
        assertEquals(YearMonth.of(2026, 4), pulse.months.first().month)
        assertEquals(YearMonth.of(2026, 9), pulse.months.last().month)
        // Пустой месяц — это и есть новость, и он должен стоять клеткой.
        assertEquals(0, pulse.months.first().days)
        assertEquals(1, pulse.months.last().days)
    }

    @Test
    fun `последнее касание берётся за всю жизнь нити, а не за полоску`() {
        // Нить, брошенная год назад, должна уметь сказать про тот год: полоска
        // показывает полгода, а вопрос «когда трогали» шире неё.
        val pulse = pulseOf(touches = listOf(LocalDate.of(2025, 3, 4)), through = through)

        assertEquals(LocalDate.of(2025, 3, 4), pulse.last)
        // В счёт полугода старое касание при этом не идёт.
        assertEquals(0, pulse.days)
    }

    @Test
    fun `нетронутая нить молчит, а не показывает ноль дней тишины`() {
        val pulse = pulseOf(touches = emptyList(), through = through)

        assertNull(pulse.last)
        assertNull(pulse.silence(day(9, 8)))
        assertEquals(0, pulse.days)
        assertEquals(PULSE_MONTHS, pulse.months.size)
    }

    @Test
    fun `тишина считается днями от последнего касания`() {
        val pulse = pulseOf(touches = listOf(day(8, 1)), through = through)

        assertEquals(38, pulse.silence(day(9, 8)))
        // Касание сегодняшним днём — ноль, а не минус: день из будущего
        // (описка в дате) не должен давать отрицательную тишину.
        val ahead = pulseOf(touches = listOf(day(9, 20)), through = through)
        assertEquals(0, ahead.silence(day(9, 8)))
    }

    @Test
    fun `спрашивает только у идущей и только после полутора месяцев`() {
        val today = day(9, 8)
        val quiet = pulseOf(touches = listOf(day(7, 1)), through = through)
        val fresh = pulseOf(touches = listOf(day(9, 1)), through = through)

        assertTrue(asksAbout(ThreadState.LIVE, quiet, today))
        assertFalse(asksAbout(ThreadState.LIVE, fresh, today))

        // У отложенной тишина и есть её состояние, а закрытую спрашивать не о
        // чем: обе молчат нарочно.
        assertFalse(asksAbout(ThreadState.PAUSED, quiet, today))
        assertFalse(asksAbout(ThreadState.DONE, quiet, today))
        assertFalse(asksAbout(ThreadState.DROPPED, quiet, today))

        // Нетронутая нить не считается замолчавшей: её ещё не начинали.
        assertFalse(asksAbout(ThreadState.LIVE, pulseOf(emptyList(), through), today))
    }

    @Test
    fun `тишина называется словами, а после двух месяцев — месяцами`() {
        assertEquals("сегодня", silenceWord(0))
        assertEquals("вчера", silenceWord(1))
        assertEquals("3 дня", silenceWord(3))
        assertEquals("47 дней", silenceWord(47))
        assertEquals("11 дней", silenceWord(11))
        assertEquals("21 день", silenceWord(21))
        // Дальше двух месяцев дни человек всё равно переводит в уме.
        assertEquals("2 месяца", silenceWord(70))
        assertEquals("5 месяцев", silenceWord(160))
    }

    @Test
    fun `состояние нити читается из строки, а неизвестное — как идущая`() {
        assertEquals(ThreadState.DROPPED, ThreadState.of("DROPPED"))
        assertEquals(ThreadState.LIVE, ThreadState.of(null))
        assertEquals(ThreadState.LIVE, ThreadState.of("ЧТО-ТО"))

        assertTrue(ThreadState.LIVE.running)
        assertFalse(ThreadState.PAUSED.running)
        assertTrue(ThreadState.DONE.closed)
        assertTrue(ThreadState.DROPPED.closed)
        assertFalse(ThreadState.PAUSED.closed)
    }
}
