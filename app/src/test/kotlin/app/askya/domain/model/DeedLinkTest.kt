package app.askya.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Разбор привязки — из тех мест, что ошибаются молча: строка приходит из базы,
 * её никто не видит, и неверно разобранная она просто уводит не туда.
 */
class DeedLinkTest {

    @Test
    fun `вид с адресом разбирается и записывается обратно тем же`() {
        val link = DeedLink.of("book:12")
        assertEquals(DeedLink(LinkKind.BOOK, 12), link)
        assertEquals("book:12", link?.store())
    }

    @Test
    fun `у безадресного вида адреса в строке нет`() {
        assertEquals("echo", DeedLink(LinkKind.ECHO).store())
        assertEquals(DeedLink(LinkKind.ECHO), DeedLink.of("echo"))
    }

    @Test
    fun `безадресный вид читается и с приписанным адресом`() {
        // Так выглядела бы строка, записанная версией, у которой у плеера
        // появился вход в конкретный плейлист. Сегодня адрес игнорируется, но
        // дело всё равно ведёт в AskyaEcho — это лучше, чем никуда.
        assertEquals(DeedLink(LinkKind.ECHO), DeedLink.of("echo:5"))
    }

    @Test
    fun `мост разбирается как обычный вид с адресом`() {
        assertEquals(DeedLink(LinkKind.BRIDGE, 3), DeedLink.of("bridge:3"))
    }

    @Test
    fun `неизвестный вид означает отсутствие привязки`() {
        assertNull(DeedLink.of("gramophone:3"))
    }

    @Test
    fun `мусор и пустота не роняют чтение`() {
        assertNull(DeedLink.of(null))
        assertNull(DeedLink.of(""))
        assertNull(DeedLink.of("   "))
        assertNull(DeedLink.of("book"))
        assertNull(DeedLink.of("book:"))
        assertNull(DeedLink.of("book:абв"))
        assertNull(DeedLink.of("book:0"))
        assertNull(DeedLink.of("book:-4"))
    }
}
