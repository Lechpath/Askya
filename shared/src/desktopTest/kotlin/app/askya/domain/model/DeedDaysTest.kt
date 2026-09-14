package app.askya.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Дни повторения дела: как они пишутся в колонку, как читаются обратно и что
 * значат для даты.
 *
 * Проверяется здесь одно правило и его края — «пусто это каждый день»: на нём
 * держатся и старые дела, у которых колонки не было, и день, в который дело
 * не случается.
 */
class DeedDaysTest {

    @Test
    fun `пусто читается как каждый день`() {
        assertEquals(emptySet(), DeedDays.of(null))
        assertEquals(emptySet(), DeedDays.of(""))
        assertEquals("Каждый день", DeedDays.title(DeedDays.of(null)))
    }

    @Test
    fun `все семь дней это тоже каждый день`() {
        assertEquals(emptySet(), DeedDays.of("1,2,3,4,5,6,7"))
        assertEquals(null, DeedDays.store(DeedDays.week.toSet()))
    }

    @Test
    fun `дни пишутся порядком недели`() {
        val chosen = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertEquals("1,3,5", DeedDays.store(chosen))
        assertEquals(chosen, DeedDays.of("5,1,3"))
        assertEquals("Пн Ср Пт", DeedDays.title(chosen))
    }

    @Test
    fun `будни и выходные названы словами`() {
        assertEquals("По будням", DeedDays.title(DeedDays.of("1,2,3,4,5")))
        assertEquals("По выходным", DeedDays.title(DeedDays.of("6,7")))
    }

    @Test
    fun `мусор в колонке не отменяет дело`() {
        // Строка из будущей версии не должна оставить человека без дела вовсе:
        // непонятное сводится к «каждый день», а не к «никогда».
        assertEquals(emptySet(), DeedDays.of("восьмой день"))
        assertEquals(emptySet(), DeedDays.of("0,9"))
    }

    @Test
    fun `дело случается только в свои дни`() {
        // 24 августа 2026 — понедельник.
        val monday = LocalDate.of(2026, 8, 24)
        val tuesday = monday.plusDays(1)

        assertTrue(DeedDays.on(DeedDays.of("1"), monday))
        assertFalse(DeedDays.on(DeedDays.of("1"), tuesday))
        assertTrue(DeedDays.on(emptySet(), tuesday))
    }
}
