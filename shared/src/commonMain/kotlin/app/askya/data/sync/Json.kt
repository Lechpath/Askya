package app.askya.data.sync

/**
 * JSON — свой, на полсотни строк.
 *
 * На телефоне `org.json` лежит в самой системе, на компьютере его нет, и общему
 * коду он недоступен: метаданные `commonMain` собираются без библиотек
 * платформы. Тянуть ради порции kotlinx-serialization — это плагин, кодогенерация
 * и версия, за которой надо следить; а нужно здесь ровно то, что описано ниже.
 *
 * Читается и пишется только то, что бывает в порции: объект, список, строка,
 * целое, дробное, `true`/`false` и `null`. Целые приходят [Long], дробные
 * [Double] — так же, как их отдаёт SQLite.
 *
 * Разбор строгий: мусор в конце или незакрытая скобка — исключение, а не
 * догадка. Порция приходит из облака расшифрованной; если она разобралась
 * наполовину, значит, случилось что-то, чего не должно быть, и молча применять
 * половину нельзя.
 */
internal object Json {

    fun write(value: Any?): String = StringBuilder().also { put(it, value) }.toString()

    /** Разобрать целиком. Всё, что не JSON, — [IllegalArgumentException]. */
    fun read(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.end()
        return value
    }

    private fun put(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> quote(out, value)
            is Boolean -> out.append(value.toString())
            is Int, is Long -> out.append(value.toString())
            is Double, is Float -> out.append(value.toString())
            is Map<*, *> -> {
                out.append('{')
                var first = true
                value.forEach { (key, item) ->
                    if (!first) out.append(',')
                    first = false
                    quote(out, key.toString())
                    out.append(':')
                    put(out, item)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                value.forEach { item ->
                    if (!first) out.append(',')
                    first = false
                    put(out, item)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("нечего писать: " + value::class)
        }
    }

    /**
     * Строка в кавычках. Управляющие знаки уезжают шестнадцатеричной записью:
     * в записях Askya попадается всякое — перевод строки в заметке, табуляция
     * из вставленного текста, — и всё это должно доехать тем же, чем уехало.
     */
    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        text.forEach { symbol ->
            when {
                symbol == '"' -> out.append("\\\"")
                symbol == '\\' -> out.append("\\\\")
                symbol == '\n' -> out.append("\\n")
                symbol == '\r' -> out.append("\\r")
                symbol == '\t' -> out.append("\\t")
                symbol < ' ' -> out.append("\\u").append(symbol.code.toString(16).padStart(4, '0'))
                else -> out.append(symbol)
            }
        }
        out.append('"')
    }

    private class Reader(private val text: String) {

        private var at = 0

        fun value(): Any? {
            skip()
            return when (val symbol = peek()) {
                '{' -> obj()
                '[' -> list()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (symbol == '-' || symbol.isDigit()) number() else fail("нежданный знак $symbol")
            }
        }

        fun end() {
            skip()
            if (at != text.length) fail("лишнее после значения")
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            at++
            skip()
            if (peek() == '}') { at++; return map }
            while (true) {
                skip()
                val key = string()
                skip()
                if (peek() != ':') fail("нет двоеточия")
                at++
                map[key] = value()
                skip()
                when (peek()) {
                    ',' -> at++
                    '}' -> { at++; return map }
                    else -> fail("нет запятой")
                }
            }
        }

        private fun list(): List<Any?> {
            val items = mutableListOf<Any?>()
            at++
            skip()
            if (peek() == ']') { at++; return items }
            while (true) {
                items += value()
                skip()
                when (peek()) {
                    ',' -> at++
                    ']' -> { at++; return items }
                    else -> fail("нет запятой")
                }
            }
        }

        private fun string(): String {
            if (peek() != '"') fail("нет кавычки")
            at++
            val out = StringBuilder()
            while (true) {
                if (at >= text.length) fail("строка не закрыта")
                when (val symbol = text[at++]) {
                    '"' -> return out.toString()
                    '\\' -> out.append(escaped())
                    else -> out.append(symbol)
                }
            }
        }

        private fun escaped(): Char {
            if (at >= text.length) fail("строка не закрыта")
            return when (val symbol = text[at++]) {
                '"', '\\', '/' -> symbol
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'b' -> '\b'
                'f' -> ''
                'u' -> {
                    val code = text.substring(at, (at + 4).coerceAtMost(text.length))
                    at += 4
                    code.toIntOrNull(16)?.toChar() ?: fail("испорчен \\u")
                }
                else -> fail("неизвестный знак после \\")
            }
        }

        private fun number(): Any {
            val from = at
            if (peek() == '-') at++
            while (at < text.length && (text[at].isDigit() || text[at] in ".eE+-")) at++
            val raw = text.substring(from, at)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: fail("не число: $raw")
        }

        private fun <T> word(expected: String, value: T): T {
            if (!text.startsWith(expected, at)) fail("не $expected")
            at += expected.length
            return value
        }

        private fun peek(): Char = if (at < text.length) text[at] else fail("строка кончилась")

        private fun skip() {
            while (at < text.length && text[at].isWhitespace()) at++
        }

        private fun fail(why: String): Nothing =
            throw IllegalArgumentException("JSON, знак $at: $why")
    }
}
