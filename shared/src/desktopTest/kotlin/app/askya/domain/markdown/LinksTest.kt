package app.askya.domain.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ссылки проверяются здесь: вставленный из буфера адрес — самый частый способ
 * положить ссылку в запись, а ошибка разбора либо не открывает страницу, либо
 * уводит не туда.
 */
class LinksTest {

    @Test
    fun `голый адрес находится с начала`() {
        assertEquals("https://askya.app/дела", Links.at("https://askya.app/дела", 0))
        assertEquals("www.askya.app", Links.at("www.askya.app", 0))
    }

    @Test
    fun `точка в конце предложения не часть адреса`() {
        val text = "Смотри https://askya.app."
        assertEquals("https://askya.app", Links.at(text, text.indexOf("https")))
    }

    @Test
    fun `внутри слова адрес не начинается`() {
        val text = "почта@www.askya.app"
        assertNull(Links.at(text, text.indexOf("www")))
    }

    @Test
    fun `не адрес — не ссылка`() {
        assertNull(Links.at("просто слово", 0))
        assertNull(Links.at("https://", 0))
    }

    @Test
    fun `схема дописывается только там, где её нет`() {
        assertEquals("https://www.askya.app", Links.web("www.askya.app"))
        assertEquals("https://askya.app/%D0%B4%D0%B5%D0%BB%D0%B0", Links.web("askya.app/дела"))
        assertEquals("http://askya.app", Links.web("http://askya.app"))
        assertEquals("mailto:лист@askya.app", Links.web("mailto:лист@askya.app"))
    }

    @Test
    fun `русские буквы в адресе едут процентами`() {
        // Ссылки на jw_org и прочие сайты с локализованными разделами
        // копируются с русскими буквами в пути. Сырыми они уезжают в запрос
        // как есть, и раздача отвечает отказом — человек видит 403 на ссылке,
        // которая открывается в браузере.
        assertEquals(
            "https://www.jw.org/ru/%D0%B1%D0%B8%D0%B1%D0%BB%D0%B8%D0%BE%D1%82%D0%B5%D0%BA%D0%B0/",
            Links.web("https://www.jw.org/ru/библиотека/"),
        )
    }

    @Test
    fun `знаки адреса и уже закодированное не трогаются`() {
        assertEquals(
            "https://www.jw.org/finder?wtlocale=U&docid=1&srcid=share#p3",
            Links.web("https://www.jw.org/finder?wtlocale=U&docid=1&srcid=share#p3"),
        )
        assertEquals(
            "https://wol.jw.org/ru/wol/d/r2/lp-u/%D0%B0",
            Links.web("https://wol.jw.org/ru/wol/d/r2/lp-u/%D0%B0"),
        )
    }

    @Test
    fun `строка без домена остаётся как есть`() {
        // Подписи вроде «см. тетрадь» ссылкой не притворяются: дописать им
        // https:// значит увести человека в никуда.
        assertEquals("см. тетрадь", Links.web("см. тетрадь"))
    }
}
