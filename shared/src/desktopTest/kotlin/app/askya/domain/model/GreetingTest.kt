package app.askya.domain.model

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GreetingTest {

    // Границы пор суток. Оборот не задан — значит, нулевой, а на нулевом
    // обороте каждая пора говорит само приветствие.

    @Test
    fun `утро начинается в пять`() {
        assertEquals("Good night", Greeting.of(LocalTime.of(4, 59)))
        assertEquals("Good morning", Greeting.of(LocalTime.of(5, 0)))
        assertEquals("Good morning", Greeting.of(LocalTime.of(11, 59)))
    }

    @Test
    fun `день с полудня до пяти`() {
        assertEquals("Good afternoon", Greeting.of(LocalTime.of(12, 0)))
        assertEquals("Good afternoon", Greeting.of(LocalTime.of(16, 59)))
    }

    @Test
    fun `вечер с пяти до десяти`() {
        assertEquals("Good evening", Greeting.of(LocalTime.of(17, 0)))
        assertEquals("Good evening", Greeting.of(LocalTime.of(21, 59)))
    }

    @Test
    fun `ночь с десяти вечера`() {
        assertEquals("Good night", Greeting.of(LocalTime.of(22, 0)))
        assertEquals("Good night", Greeting.of(LocalTime.of(0, 0)))
        assertEquals("Good night", Greeting.of(LocalTime.of(3, 30)))
    }

    @Test
    fun `набор перебирается по кругу и возвращается к началу`() {
        val morning = LocalTime.of(8, 0)
        val pool = Greeting.phrases(Greeting.Part.MORNING)

        pool.forEachIndexed { turn, phrase ->
            assertEquals(phrase, Greeting.of(morning, turn))
        }
        // Круг замкнулся: следующий оборот начинает набор заново.
        assertEquals(pool.first(), Greeting.of(morning, pool.size))
        assertEquals(pool[1], Greeting.of(morning, pool.size + 1))
    }

    @Test
    fun `переполнение счётчика не роняет заставку`() {
        val evening = LocalTime.of(19, 0)
        val pool = Greeting.phrases(Greeting.Part.EVENING)

        assertTrue(Greeting.of(evening, Int.MIN_VALUE) in pool)
        assertTrue(Greeting.of(evening, -1) in pool)
        assertTrue(Greeting.of(evening, Int.MAX_VALUE) in pool)
    }

    @Test
    fun `в каждой поре первой стоит само приветствие`() {
        assertEquals("Good morning", Greeting.phrases(Greeting.Part.MORNING).first())
        assertEquals("Good afternoon", Greeting.phrases(Greeting.Part.AFTERNOON).first())
        assertEquals("Good evening", Greeting.phrases(Greeting.Part.EVENING).first())
        assertEquals("Good night", Greeting.phrases(Greeting.Part.NIGHT).first())
    }

    @Test
    fun `фразы не повторяются внутри поры и написаны по-английски`() {
        Greeting.Part.entries.forEach { part ->
            val pool = Greeting.phrases(part)
            assertTrue(pool.size > 1, "у поры $part должно быть из чего выбирать")
            assertEquals(pool.size, pool.toSet().size, "в поре $part повторяется фраза")
            pool.forEach { phrase ->
                assertTrue(phrase.isNotBlank(), "пустая фраза в поре $part")
                assertTrue(
                    phrase.none { it in 'а'..'я' || it in 'А'..'Я' },
                    "фраза «$phrase» в поре $part не по-английски",
                )
            }
        }
    }
}
