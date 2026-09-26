package app.askya.agent

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Разбор аргументов вызова инструмента — один на все инструменты.
 *
 * Это не проверка JSON Schema. Схема уходит модели как подсказка (и провайдер
 * может ей следовать строго), а здесь — проверка того, что пришло на самом
 * деле: модель могла ошибиться, провайдер — не проверить, а вызов — прийти не
 * от модели вовсе. Поэтому правила простые и явные: какие ключи бывают,
 * какого они вида, обязательны ли, в каких пределах.
 *
 * Аргументы приходят словарём из разбора JSON (`app.askya.data.sync.Json.read`):
 * строки, `Long`, `Double`, `Boolean`, `null`, списки и словари. `null` и
 * отсутствие ключа — одно и то же: «не передано».
 *
 * Ошибки — словами, которые можно отдать модели как есть: в них имя аргумента
 * и то, чего от него ждали, но не его значение — значение могло быть взято из
 * записей человека.
 *
 * ```
 * val args = when (val read = readArgs(input, KEYS) { Request(date("date"), boolean("includeDone")) }) {
 *     is ArgsResult.Bad -> return ToolResult.Failed(read.reason)
 *     is ArgsResult.Ok -> read.value
 * }
 * ```
 */
class ToolArgs internal constructor(private val input: Map<String, Any?>) {

    /** Строка или `null`, если не передана. Пустая строка — строка, а не «не передано». */
    fun string(name: String): String? = when (val raw = input[name]) {
        null -> null
        is String -> raw
        else -> bad("$name должен быть строкой")
    }

    /** Обязательная строка; пустая или из пробелов — тоже «нет». */
    fun requireString(name: String): String =
        string(name)?.takeIf { it.isNotBlank() } ?: bad("$name обязателен")

    /** Флаг; не передан — [default]. */
    fun boolean(name: String, default: Boolean = false): Boolean = when (val raw = input[name]) {
        null -> default
        is Boolean -> raw
        else -> bad("$name должен быть true или false")
    }

    /**
     * Целое; не передано — [default]. Дробное, даже `3.0`, не целое: модель,
     * приславшая дробь там, где просили число строк, ошиблась, и молча
     * округлять за неё нельзя.
     */
    fun int(name: String, default: Int, range: IntRange? = null): Int {
        val value = when (val raw = input[name]) {
            null -> return default
            is Int -> raw
            is Long -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
                ?: bad("$name вне допустимых пределов")
            else -> bad("$name должен быть целым числом")
        }
        if (range != null && value !in range) bad("$name должен быть от ${range.first} до ${range.last}")
        return value
    }

    /**
     * Дата ГГГГ-ММ-ДД или `null`, если не передана. Только ISO: формат модель
     * получает в схеме, а «завтра» и «пт» разбирает человеку `parseTypedDate`,
     * а не инструменту.
     */
    fun date(name: String): LocalDate? {
        val raw = input[name] ?: return null
        if (raw !is String) bad("$name должна быть строкой ГГГГ-ММ-ДД")
        return try {
            LocalDate.parse(raw.trim())
        } catch (_: DateTimeParseException) {
            bad("$name должна быть в виде ГГГГ-ММ-ДД")
        }
    }

    /**
     * Отказать своей причиной — для проверок, которых здесь нет (курсор, чужой
     * формат). Причина уходит модели: значения аргумента в неё не вписывать.
     */
    fun reject(reason: String): Nothing = bad(reason)

    private fun bad(reason: String): Nothing = throw BadArgument(reason)
}

/** Чем кончился разбор: готовые значения или причина отказа. */
sealed interface ArgsResult<out T> {
    data class Ok<T>(val value: T) : ArgsResult<T>
    data class Bad(val reason: String) : ArgsResult<Nothing>
}

/**
 * Разобрать аргументы: сначала неизвестные ключи, потом то, что прочтёт [read].
 *
 * [allowed] — все ключи, какие инструмент знает. Лишний ключ — отказ, а не
 * пропуск: модель, приславшая `delete: true` инструменту чтения, чего-то не
 * поняла, и сказать ей об этом полезнее, чем молча сделать другое.
 */
fun <T> readArgs(input: Map<String, Any?>, allowed: Set<String>, read: ToolArgs.() -> T): ArgsResult<T> {
    val unknown = input.keys - allowed
    if (unknown.isNotEmpty()) return ArgsResult.Bad("неизвестные аргументы: ${unknown.sorted().joinToString()}")
    return try {
        ArgsResult.Ok(ToolArgs(input).read())
    } catch (bad: BadArgument) {
        ArgsResult.Bad(bad.reason)
    }
}

/** Внутренний сигнал разбора; наружу из [readArgs] не выходит. */
private class BadArgument(val reason: String) : Exception(reason)
