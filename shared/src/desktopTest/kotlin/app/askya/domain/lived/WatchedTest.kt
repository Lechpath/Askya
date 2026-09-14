package app.askya.domain.lived

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Счёт по делам, которые человек выбрал сам. На телефоне его не проверить: год
 * записей с заранее известным ответом руками не набрать.
 */
class WatchedTest {

    private val today = LocalDate.of(2026, 8, 26)
    private val start = LocalDate.of(2026, 3, 1)

    private fun row(title: String, day: Int, done: Boolean = true) =
        LivedRow(title, start.plusDays(day.toLong()), done)

    private fun watch(rows: List<LivedRow>, vararg titles: String) =
        watched(rows, titles.toList(), today)

    @Test
    fun `считается только выбранное`() {
        val rows = listOf(row("Бег", 0), row("Завтрак", 0), row("Завтрак", 1))
        val deeds = watch(rows, "Бег")
        assertEquals(1, deeds.size)
        assertEquals("Бег", deeds.single().title)
        assertEquals(1, deeds.single().planned)
    }

    @Test
    fun `поставленное и сделанное считаются порознь`() {
        val rows = (0 until 10).map { row("Бег", it, done = it % 3 == 0) }
        val deed = watch(rows, "Бег").single()
        assertEquals(10, deed.planned)
        assertEquals(4, deed.done)
        assertEquals("Стояло 10 раз, отмечено 4", deed.tally())
    }

    @Test
    fun `дело, ни разу не отмеченное, так и сказано`() {
        val rows = (0 until 5).map { row("Бег", it, done = false) }
        val deed = watch(rows, "Бег").single()
        assertEquals("Стояло 5 раз, ни разу не отмечено", deed.tally())
        assertNull(deed.lastDone)
    }

    @Test
    fun `выбранное дело, которого за год не было, из счёта не выпадает`() {
        val deeds = watch(listOf(row("Завтрак", 0)), "Чтение Библии")
        assertEquals(1, deeds.size)
        assertEquals("Чтение Библии", deeds.single().title)
        assertEquals("За год ни разу не стояло в дне", deeds.single().tally())
    }

    @Test
    fun `написание не мешает счёту, а показывается последнее`() {
        val rows = listOf(row("бег ", 0), row("Бег", 1), row("БЕГ", 2))
        val deed = watch(rows, " БеГ ").single()
        assertEquals(3, deed.planned)
        assertEquals("БЕГ", deed.title)
    }

    @Test
    fun `порядок остаётся тем, в котором выбирали`() {
        val rows = listOf(row("Бег", 0), row("Чтение Библии", 0), row("Чтение Библии", 1))
        val deeds = watch(rows, "Бег", "Чтение Библии")
        assertEquals(listOf("Бег", "Чтение Библии"), deeds.map { it.title })
    }

    @Test
    fun `год — двенадцать месяцев, последний нынешний`() {
        val deed = watch(listOf(row("Бег", 0)), "Бег").single()
        assertEquals(12, deed.months.size)
        assertEquals(YearMonth.of(2026, 8), deed.months.last().month)
        assertEquals(YearMonth.of(2025, 9), deed.months.first().month)
    }

    @Test
    fun `месяц знает и поставленное, и сделанное`() {
        // Март: пять раз стояло, два отмечено. Апрель: один раз и не сделан.
        val rows = (0 until 5).map { row("Бег", it, done = it < 2) } +
            listOf(LivedRow("Бег", LocalDate.of(2026, 4, 3), false))
        val months = watch(rows, "Бег").single().months.associateBy { it.month }

        assertEquals(MonthTally(YearMonth.of(2026, 3), 5, 2), months.getValue(YearMonth.of(2026, 3)))
        assertEquals(MonthTally(YearMonth.of(2026, 4), 1, 0), months.getValue(YearMonth.of(2026, 4)))
    }

    @Test
    fun `пустой месяц остаётся в ряду нулём`() {
        val deed = watch(listOf(row("Бег", 0)), "Бег").single()
        val may = deed.months.single { it.month == YearMonth.of(2026, 5) }
        assertEquals(0, may.planned)
        assertEquals(0, may.done)
    }

    @Test
    fun `дважды за день — две строки в счёте, но один день в череде`() {
        val rows = listOf(row("Чтение Библии", 0), row("Чтение Библии", 0))
        val deed = watch(rows, "Чтение Библии").single()
        assertEquals(2, deed.planned)
        assertEquals(1, deed.streak)
    }

    @Test
    fun `череда считается по отмеченным дням подряд`() {
        val rows = (0 until 6).map { row("Чтение Библии", it) } +
            (10 until 14).map { row("Чтение Библии", it) }
        val deed = watch(rows, "Чтение Библии").single()
        assertEquals(6, deed.streak)
        assertEquals("дольше всего — 6 дней подряд", deed.streakWord())
    }

    @Test
    fun `два дня подряд чередой не называются`() {
        val rows = listOf(row("Бег", 0), row("Бег", 1))
        assertNull(watch(rows, "Бег").single().streakWord())
    }

    @Test
    fun `неотмеченные дни череду не удлиняют`() {
        val rows = listOf(row("Бег", 0), row("Бег", 1, done = false), row("Бег", 2))
        assertEquals(1, watch(rows, "Бег").single().streak)
    }

    @Test
    fun `последний отмеченный день — самый поздний`() {
        val rows = listOf(row("Бег", 0), row("Бег", 5), row("Бег", 9, done = false))
        assertEquals(start.plusDays(5), watch(rows, "Бег").single().lastDone)
    }

    @Test
    fun `выбирать предлагается частое, и каждое название по разу`() {
        val rows = (0 until 4).map { row("Завтрак", it) } +
            (0 until 2).map { row("Бег", it) } +
            listOf(row("бег", 7))
        val names = deedNames(rows)
        assertEquals(listOf("Завтрак", "бег"), names.map { it.title })
        assertEquals(4, names.first().planned)
        assertEquals(3, names.last().planned)
    }

    @Test
    fun `без выбора не считается ничего`() {
        assertTrue(watched(listOf(row("Бег", 0)), emptyList(), today).isEmpty())
    }
}
