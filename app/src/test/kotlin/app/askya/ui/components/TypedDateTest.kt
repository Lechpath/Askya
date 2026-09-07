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

    /**
     * Запись расходной книги смотрит назад: её пишут о случившемся, и «31.08»,
     * напечатанная третьего сентября, означает позавчера. Прежде она означала
     * будущий август, и запись уезжала на год вперёд молча — в ленте месяца её
     * не видно, а в остатке счёта она есть.
     */
    @Test
    fun `назад — прошлый год, а не будущий`() {
        val september = LocalDate.of(2026, 9, 3)
        assertEquals(
            LocalDate.of(2026, 8, 31),
            parseTypedDate("31.08", september, DateLean.BEHIND),
        )
        assertEquals(
            LocalDate.of(2026, 8, 31),
            parseTypedDate("31 августа", september, DateLean.BEHIND),
        )
        // Тот же сентябрь вперёд — будущий август: дело назначают, а не
        // записывают.
        assertEquals(
            LocalDate.of(2027, 8, 31),
            parseTypedDate("31.08", september, DateLean.AHEAD),
        )
    }

    @Test
    fun `назад — этот же год, если дата ещё не прошла`() {
        val december = LocalDate.of(2026, 12, 20)
        assertEquals(
            LocalDate.of(2026, 8, 31),
            parseTypedDate("31.08", december, DateLean.BEHIND),
        )
        assertEquals(december, parseTypedDate("20.12", december, DateLean.BEHIND))
    }

    @Test
    fun `одно число назад — ближайшее прошедшее`() {
        assertEquals(LocalDate.of(2026, 8, 15), parseTypedDate("15", today, DateLean.BEHIND))
        assertEquals(today, parseTypedDate("20", today, DateLean.BEHIND))
        assertEquals(LocalDate.of(2026, 7, 25), parseTypedDate("25", today, DateLean.BEHIND))
    }

    /** Названный год сильнее направления: написали — значит, знают. */
    @Test
    fun `названный год не переставляется`() {
        assertEquals(
            LocalDate.of(2027, 3, 1),
            parseTypedDate("1.03.2027", today, DateLean.BEHIND),
        )
    }

    /**
     * Год другой — он и написан. Без него запись, уехавшая в будущий август,
     * выглядела ровно как позавчерашняя, и найти её было нечем.
     */
    @Test
    fun `чужой год виден и читается обратно`() {
        val far = LocalDate.of(2027, 8, 31)
        assertEquals("Вторник, 31 августа 2027", formatTypedDate(far, today))
        assertEquals(far, parseTypedDate(formatTypedDate(far, today), today))
        assertEquals(far, parseTypedDate(formatTypedDate(far, today), today, DateLean.BEHIND))
    }

    @Test
    fun `пусто и непонятное`() {
        assertNull(parseTypedDate("", today))
        assertNull(parseTypedDate("как-нибудь", today))
        assertNull(parseTypedDate("32.01", today))
        assertNull(parseTypedDate("3 сортября", today))
    }
}
