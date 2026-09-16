package app.askya.data.sync

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate

/**
 * Имя строки, общее для всех устройств.
 *
 * Номер строки (`id` из Room) для этого не годится: он раздаётся счётчиком
 * таблицы, и на двух устройствах седьмым делом окажутся разные дела. Поэтому у
 * каждой строки, которая едет в облако, есть **uid** — тридцать два знака
 * шестнадцатеричных, случайных настолько, что совпадение не случается.
 *
 * Ставится он в Kotlin, а не триггером базы: триггер, дописывающий строку в
 * свою же таблицу, будит второй триггер, и запрет рекурсии в SQLite — не то
 * основание, на котором держат целостность.
 */
object Uid {

    private val random = SecureRandom()

    /** Новое имя строки. */
    fun new(): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        return hex(bytes)
    }

    /**
     * Имя дела, развёрнутого в день из списка дел, — **не случайное**.
     *
     * Это главная ловушка синхронизации: заполнение дня работает на каждом
     * устройстве само, и телефон с компьютером развернут один и тот же
     * распорядок в один и тот же день порознь. Со случайными именами день
     * съехался бы вдвойне — каждое дело дважды.
     *
     * Имя считается из даты и имени строки распорядка, поэтому у обоих
     * устройств оно совпадает, и при слиянии это одно и то же дело.
     */
    fun ofRoutineDay(date: LocalDate, routineUid: String): String =
        digest("day|$date|$routineUid")

    /**
     * Имя отметки «день заполнен» — из самой даты, и нарочно читаемое:
     * `generated-2026-09-16`.
     *
     * У этой таблицы ключ природный — дата, — и отметка одного дня одна на
     * всех устройствах. Случайное имя развело бы две записи об одном и том же
     * дне, а сойтись им по ключу было бы негде. Читаемое, а не хеш, потому что
     * то же имя выписывает миграция, а SHA-256 в SQLite нет.
     */
    fun ofGeneratedDay(date: LocalDate): String = "generated-$date"

    /** Первые шестнадцать байт SHA-256 — столько же, сколько у случайного. */
    private fun digest(source: String): String {
        val full = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        return hex(full.copyOf(16))
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
}
