package app.askya.ui.components

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Дату в карточке напоминания печатают, а не выбирают в календаре, поэтому
 * принимать надо все привычные формы. «Сегодня» берётся из аргумента, а не из
 * часов: тест не должен зависеть от дня, в который его запустили.
 */
class TypedDateTest {

    private val today = LocalDate.of(2026, 8, 20)

    @Test
    fun `словами`() {
        assertEquals(today, parseTypedDate("сегодня", today))
        assertEquals(LocalDate.of(2026, 8, 21), parseTypedDate("Завтра", today))
        assertEquals(LocalDate.of(2026, 8, 22), parseTypedDate("послезавтра", today))
        assertEquals(LocalDate.of(2026, 8, 19), parseTypedDate("вчера", today))
    }

    @Test
    fun `числом и месяцем`() {
        assertEquals(LocalDate.of(2026, 9, 3), parseTypedDate("3 сентября", today))
        assertEquals(LocalDate.of(2026, 9, 3), parseTypedDate("3 сен", today))
        assertEquals(LocalDate.of(2026, 12, 31), parseTypedDate("31.12", today))
        assertEquals(LocalDate.of(2027, 3, 1), parseTypedDate("1.03.2027", today))
    }

    @Test
    fun `названный месяц уже прошёл — значит, будущий год`() {
        assertEquals(LocalDate.of(2027, 3, 1), parseTypedDate("1 марта", today))
    }

    @Test
    fun `одно число — ближайшее такое`() {
        assertEquals(LocalDate.of(2026, 8, 25), parseTypedDate("25", today))
        assertEquals(LocalDate.of(2026, 9, 5), parseTypedDate("5", today))
    }

    @Test
    fun `показанное читается обратно`() {
        listOf(today, today.plusDays(1), today.plusDays(9), LocalDate.of(2026, 11, 4)).forEach { date ->
            assertEquals(date, parseTypedDate(formatTypedDate(date, today), today))
        }
    }

    @Test
    fun `пусто и непонятное`() {
        assertNull(parseTypedDate("", today))
        assertNull(parseTypedDate("как-нибудь", today))
        assertNull(parseTypedDate("32.01", today))
        assertNull(parseTypedDate("3 сортября", today))
    }
}
