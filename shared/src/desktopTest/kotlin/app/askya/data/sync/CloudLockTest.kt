package app.askya.data.sync

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Замок облака и то, из чего сложена порция: шифр, запись и разбор.
 *
 * Витков PBKDF2 здесь мало нарочно: настоящие шестьсот тысяч считаются
 * полсекунды, и два десятка проверок стояли бы минуту. Проверяется здесь не
 * скорость перебора, а то, что запертое отпирается своим и не отпирается чужим.
 */
class CloudLockTest {

    private val rounds = 1_000

    @Test
    fun `закрытое открывается своим ключом`() {
        val key = Crypt.key()
        val plain = "хлеб, молоко".toByteArray()

        val sealed = Crypt.seal(key, plain, "devices/phone/000001.pack")

        assertTrue(sealed.size > plain.size, "подпись и nonce должны быть на месте")
        assertContentEquals(plain, Crypt.open(key, sealed, "devices/phone/000001.pack"))
    }

    @Test
    fun `чужим ключом не открывается`() {
        val sealed = Crypt.seal(Crypt.key(), "тайна".toByteArray(), "p")
        assertNull(Crypt.open(Crypt.key(), sealed, "p"))
    }

    @Test
    fun `переложенное в другое место не открывается`() {
        val key = Crypt.key()
        val sealed = Crypt.seal(key, "тайна".toByteArray(), "devices/phone/000001.pack")

        // Тот же ключ, те же байты — но путь входит в подпись.
        assertNull(Crypt.open(key, sealed, "devices/computer/000001.pack"))
    }

    @Test
    fun `тронутый байт всё портит`() {
        val key = Crypt.key()
        val sealed = Crypt.seal(key, "хлеб".toByteArray(), "p")

        for (at in sealed.indices) {
            val spoiled = sealed.copyOf()
            spoiled[at] = (spoiled[at] + 1).toByte()
            assertNull(Crypt.open(key, spoiled, "p"), "байт $at прошёл незамеченным")
        }
    }

    @Test
    fun `nonce не повторяется`() {
        val key = Crypt.key()
        val nonces = (1..200).map { Crypt.seal(key, "одно и то же".toByteArray(), "p").take(12) }
        assertEquals(200, nonces.toSet().size, "повторённый nonce раскрывает оба сообщения")
    }

    @Test
    fun `сжатое разжимается`() {
        val text = "дело дня ".repeat(500)
        val small = Crypt.squeeze(text.toByteArray())

        assertTrue(small.size < text.toByteArray().size / 5, "порция должна ужиматься")
        assertEquals(text, String(Crypt.unsqueeze(small)))
    }

    @Test
    fun `пароль отпирает ключ данных, чужой — нет`() {
        val (account, dataKey) = CloudAccount.made("восемь знаков", recovery = null, rounds = rounds)

        assertContentEquals(dataKey, account.open("восемь знаков"))
        assertNull(account.open("восемь знаков "))
        assertNull(account.open("другой пароль"))
    }

    @Test
    fun `код восстановления отпирает тот же ключ`() {
        val code = "K7QM-2XHD-9PWA-TR4N-E3FB"
        val (account, dataKey) = CloudAccount.made("восемь знаков", code, rounds)

        assertContentEquals(dataKey, account.openRecovered(code))
        // Переписанный с бумажки — как придётся: без дефисов и строчными.
        assertContentEquals(dataKey, account.openRecovered("k7qm 2xhd 9pwa tr4n e3fb"))
        assertNull(account.openRecovered("K7QM-2XHD-9PWA-TR4N-E3FC"))
    }

    @Test
    fun `смена пароля не трогает записи`() {
        val (account, dataKey) = CloudAccount.made("старый пароль", recovery = null, rounds = rounds)
        val sealed = Crypt.seal(dataKey, "запись".toByteArray(), "p")

        val changed = account.withPassword(dataKey, "новый пароль", rounds)

        assertNull(changed.open("старый пароль"))
        assertContentEquals(dataKey, changed.open("новый пароль"))
        // Главное: ключ данных прежний, и всё, что лежит в облаке, читается.
        assertContentEquals("запись".toByteArray(), Crypt.open(assertNotNull(changed.open("новый пароль")), sealed, "p"))
    }

    @Test
    fun `account json читается обратно`() {
        val (account, dataKey) = CloudAccount.made("восемь знаков", "K7QM-2XHD", rounds)

        val read = assertNotNull(CloudAccount.of(account.json()))

        assertEquals(account.id, read.id)
        assertContentEquals(dataKey, read.open("восемь знаков"))
        assertContentEquals(dataKey, read.openRecovered("K7QM-2XHD"))
        assertNull(CloudAccount.of("{}"), "чужой файл не притворится аккаунтом")
        assertNull(CloudAccount.of("не json вовсе"))
    }

    @Test
    fun `порция доезжает такой же, какой уехала`() {
        val pack = Pack(
            schema = 48,
            device = "7f3a",
            from = "с телефона",
            made = 1_789_234_567_000,
            rows = listOf(
                Pack.Row(
                    table = "notes",
                    uid = Uid.new(),
                    hlc = 7_330_121_877_209_088,
                    base = 42,
                    values = mapOf(
                        "title" to "Запись \"в кавычках\"",
                        "body" to "первая строка\nвторая\tс табуляцией\\и слэшем",
                        "topicId" to null,
                        "durationMs" to 1234L,
                        "isImage" to 0L,
                    ),
                ),
                Pack.Row(table = "notes", uid = "мёртвая", hlc = 7, dead = true),
            ),
        )

        val there = assertNotNull(Pack.of(pack.bytes()))

        assertEquals(pack, there)
    }

    @Test
    fun `порция из мусора не разбирается`() {
        assertNull(Pack.of("не порция".toByteArray()))
        assertNull(Pack.of(Crypt.squeeze("{\"pack\":99}".toByteArray())))
    }

    @Test
    fun `json пишет и читает то же самое`() {
        val value = mapOf(
            "строка" to "перевод\nстроки, кавычка \" и слэш \\",
            "число" to 42L,
            "дробь" to 1.5,
            "пусто" to null,
            "правда" to true,
            "список" to listOf(1L, "два", null),
            "внутри" to mapOf("ещё" to "глубже"),
        )

        assertEquals(value, Json.read(Json.write(value)))
    }

    @Test
    fun `испорченный json не разбирается наполовину`() {
        assertFailsWith<IllegalArgumentException> { Json.read("{\"а\": 1") }
        assertFailsWith<IllegalArgumentException> { Json.read("{\"а\": 1} лишнее") }
        assertFailsWith<IllegalArgumentException> { Json.read("") }
    }
}
