package app.askya.ui.components

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Время в диалоге печатают, а не выбирают из готового, поэтому формы ввода
 * разные — и каждую надо принять. Проверяется тестом: перебрать их тапами
 * по телефону нереально.
 */
class TypedTimeTest {

    @Test
    fun `с двоеточием`() {
        assertEquals(LocalTime.of(7, 30), parseTypedTime("7:30"))
        assertEquals(LocalTime.of(19, 0), parseTypedTime("19:00"))
        assertEquals(LocalTime.of(7, 5), parseTypedTime("07:05"))
    }

    @Test
    fun `через точку и пробел`() {
        assertEquals(LocalTime.of(21, 40), parseTypedTime("21.40"))
        assertEquals(LocalTime.of(9, 15), parseTypedTime("9 15"))
    }

    @Test
    fun `одними цифрами`() {
        assertEquals(LocalTime.of(7, 0), parseTypedTime("7"))
        assertEquals(LocalTime.of(19, 0), parseTypedTime("19"))
        assertEquals(LocalTime.of(7, 30), parseTypedTime("730"))
        assertEquals(LocalTime.of(19, 45), parseTypedTime("1945"))
    }

    @Test
    fun `лишние пробелы по краям не мешают`() {
        assertEquals(LocalTime.of(8, 0), parseTypedTime("  8:00 "))
    }

    @Test
    fun `бессмыслица не превращается во время`() {
        assertNull(parseTypedTime(""))
        assertNull(parseTypedTime("   "))
        assertNull(parseTypedTime("утром"))
        assertNull(parseTypedTime("25:00"))
        assertNull(parseTypedTime("7:75"))
        assertNull(parseTypedTime("123456"))
    }

    @Test
    fun `промежуток с началом и концом`() {
        assertEquals(
            TypedRange(LocalTime.of(20, 45), LocalTime.of(22, 45)),
            parseTypedRange("20:45-22:45"),
        )
        assertEquals(
            TypedRange(LocalTime.of(9, 0), LocalTime.of(10, 30)),
            parseTypedRange("9 – 10.30"),
        )
        assertEquals(
            TypedRange(LocalTime.of(7, 0), LocalTime.of(8, 15)),
            parseTypedRange("700 — 815"),
        )
    }

    @Test
    fun `конец необязателен`() {
        assertEquals(TypedRange(LocalTime.of(7, 30), null), parseTypedRange("7:30"))
        assertEquals(TypedRange(LocalTime.of(19, 0), null), parseTypedRange("19"))
    }

    @Test
    fun `тире между часами и минутами осталось прежним`() {
        // «7-30» — это половина восьмого, а не «с семи до тридцати»: тире у нас
        // и разделитель часов с минутами. Промежутком запись считается только
        // тогда, когда разбираются обе половины.
        assertEquals(TypedRange(LocalTime.of(7, 30), null), parseTypedRange("7-30"))
    }

    @Test
    fun `промежуток через полночь принимается`() {
        // Смена с вечера на ночь — обычное дело; обрезать её здесь значило бы
        // не дать записать то, что человек живёт.
        assertEquals(
            TypedRange(LocalTime.of(22, 0), LocalTime.of(2, 0)),
            parseTypedRange("22:00 - 2:00"),
        )
    }

    @Test
    fun `недописанный промежуток временем не считается`() {
        assertNull(parseTypedRange("20:45 -"))
        assertNull(parseTypedRange("- 22:45"))
        assertNull(parseTypedRange(""))
    }
}
