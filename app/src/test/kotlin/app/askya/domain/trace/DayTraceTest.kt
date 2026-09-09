package app.askya.domain.trace

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayTraceTest {

    private fun event(
        at: LocalTime?,
        part: TracePart = TracePart.DAY,
        gist: String = "что-то",
    ) = DayEvent(at = at, part = part, gist = gist)

    // ——— Порядок ———

    @Test
    fun `часовое идёт по часам`() {
        val order = orderedTrace(
            listOf(
                event(LocalTime.of(18, 0), gist = "вечер"),
                event(LocalTime.of(7, 30), gist = "утро"),
                event(LocalTime.of(12, 15), gist = "полдень"),
            ),
        )
        assertEquals(listOf("утро", "полдень", "вечер"), order.map { it.gist })
    }

    @Test
    fun `бесчасовое уходит вниз, а не в полночь`() {
        val order = orderedTrace(
            listOf(
                event(null, TracePart.YET, "список"),
                event(LocalTime.of(0, 5), gist = "ночью"),
            ),
        )
        assertEquals(listOf("ночью", "список"), order.map { it.gist })
    }

    @Test
    fun `в один час порядок разделов постоянный`() {
        // Список подрагивал бы на каждом обновлении базы, если бы два события
        // одного часа переставлялись местами.
        val noon = LocalTime.of(12, 0)
        val first = orderedTrace(
            listOf(
                event(noon, TracePart.LEDGER, "обед"),
                event(noon, TracePart.DAY, "дело"),
            ),
        )
        val second = orderedTrace(
            listOf(
                event(noon, TracePart.DAY, "дело"),
                event(noon, TracePart.LEDGER, "обед"),
            ),
        )
        assertEquals(first, second)
        assertEquals(listOf("дело", "обед"), first.map { it.gist })
    }

    @Test
    fun `пустой день остаётся пустым`() {
        assertTrue(orderedTrace(emptyList()).isEmpty())
    }

    // ——— Суть одной строкой ———

    @Test
    fun `берётся первая непустая строка`() {
        assertEquals("Позвонить", gistOf("\n\n  Позвонить  \nи ещё что-то"))
    }

    @Test
    fun `пустое становится запасным словом`() {
        assertEquals("Запись", gistOf("   \n \n ", fallback = "Запись"))
        assertEquals("Запись", gistOf("", fallback = "Запись"))
    }

    @Test
    fun `короткое не трогается`() {
        assertEquals("Хлеб", gistOf("Хлеб"))
    }

    @Test
    fun `длинное обрывается по слову`() {
        val long = "Позвонить Алёне и договориться о встрече на следующей неделе"
        val short = gistOf(long, limit = 20)
        assertTrue(short.endsWith("…"), "ожидалось многоточие, а вышло: $short")
        assertTrue(short.length <= 21, "слишком длинно: $short")
        // Обрыв по слову, а не по букве: «Позвонить Ал…» читается хуже.
        assertTrue(!short.dropLast(1).endsWith(" "), "хвостовой пробел: $short")
        assertEquals("Позвонить Алёне и", short.dropLast(1))
    }

    @Test
    fun `слово без пробелов обрывается по букве`() {
        // Одно длинное слово нечем резать по смыслу — режется как есть, иначе
        // от него не осталось бы ничего.
        val short = gistOf("Пооооооооооооооооооочень", limit = 10)
        assertEquals("Пооооооооо…", short)
    }
}
