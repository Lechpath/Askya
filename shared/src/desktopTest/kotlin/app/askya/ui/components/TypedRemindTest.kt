package app.askya.ui.components

import app.askya.domain.model.RemindAt
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Напоминание в карточке печатают одной строкой, и сказать его можно
 * по-разному: «в семь», «за пятнадцать минут», «за час». Проверяется тестом —
 * перебрать формы тапами по телефону нереально, а ошибка разбора здесь
 * означает молчащий будильник.
 */
class TypedRemindTest {

    @Test
    fun `названный час`() {
        assertEquals(RemindAt.Exact(LocalTime.of(19, 0)), parseTypedRemind("19:00"))
        assertEquals(RemindAt.Exact(LocalTime.of(7, 30)), parseTypedRemind("730"))
        assertEquals(RemindAt.Exact(LocalTime.of(15, 0)), parseTypedRemind("15"))
    }

    @Test
    fun `промежуток после приставки`() {
        assertEquals(RemindAt.Before(15), parseTypedRemind("за 15"))
        assertEquals(RemindAt.Before(15), parseTypedRemind("за 15 мин"))
        assertEquals(RemindAt.Before(15), parseTypedRemind("за 15 минут"))
        assertEquals(RemindAt.Before(90), parseTypedRemind("за 1:30"))
        assertEquals(RemindAt.Before(10), parseTypedRemind("-10"))
    }

    @Test
    fun `часы словом`() {
        assertEquals(RemindAt.Before(60), parseTypedRemind("за час"))
        assertEquals(RemindAt.Before(60), parseTypedRemind("за 1 ч"))
        assertEquals(RemindAt.Before(120), parseTypedRemind("за 2 часа"))
        assertEquals(RemindAt.Before(90), parseTypedRemind("за 1 ч 30 мин"))
        assertEquals(RemindAt.Before(30), parseTypedRemind("полчаса"))
    }

    @Test
    fun `единица измерения делает промежутком и без приставки`() {
        assertEquals(RemindAt.Before(20), parseTypedRemind("20 мин"))
        assertEquals(RemindAt.Before(120), parseTypedRemind("2 часа"))
    }

    @Test
    fun `голое число без приставки — это час, а не промежуток`() {
        assertEquals(RemindAt.Exact(LocalTime.of(20, 0)), parseTypedRemind("20"))
    }

    @Test
    fun `пусто и непонятное`() {
        assertNull(parseTypedRemind(""))
        assertNull(parseTypedRemind("   "))
        assertNull(parseTypedRemind("когда-нибудь"))
        assertNull(parseTypedRemind("за когда-нибудь"))
        assertNull(parseTypedRemind("за 15 мнут"))
        assertNull(parseTypedRemind("25:00"))
    }

    @Test
    fun `показанное читается обратно`() {
        listOf(
            RemindAt.Exact(LocalTime.of(19, 0)),
            RemindAt.Before(15),
            RemindAt.Before(60),
            RemindAt.Before(90),
        ).forEach { remind ->
            assertEquals(remind, parseTypedRemind(formatRemind(remind)))
        }
    }
}
