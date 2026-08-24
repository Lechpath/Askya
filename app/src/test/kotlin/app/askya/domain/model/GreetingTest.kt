package app.askya.domain.model

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {

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
}
