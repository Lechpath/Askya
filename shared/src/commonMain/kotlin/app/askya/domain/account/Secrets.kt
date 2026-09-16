package app.askya.domain.account

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Пароль, пин-код и код восстановления — как они лежат и как сверяются.
 *
 * Сами они не хранятся нигде: лежит отпечаток PBKDF2 (HMAC-SHA256) с солью.
 * Свой, а не библиотечный: `javax.crypto` есть и на телефоне (с Android 8, а
 * ниже Askya не ставится), и в JDK компьютера, — и сверка одна у обоих.
 *
 * Отпечаток записан строкой вместе со своими числами —
 * `pbkdf2-sha256$итерации$соль$хеш`. Так число итераций можно поднять в
 * следующей версии, не ломая уже заведённых аккаунтов: старый отпечаток
 * сверяется по своим числам.
 *
 * Итераций у пароля втрое больше, чем у пин-кода. Пароль набирают раз в
 * несколько дней, и полсекунды на сверку ему не во вред. Пин-код набирают по
 * десять раз на дню, и от подбора его защищают не итерации — десять тысяч
 * вариантов перебираются при любых, — а счёт промахов ([Attempts]).
 */
object Secrets {

    const val PASSWORD_ROUNDS = 310_000
    const val PIN_ROUNDS = 100_000

    private const val SCHEME = "pbkdf2-sha256"
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256

    private val random = SecureRandom()

    /** Отпечаток для хранения. Медленно — звать не с главного потока. */
    fun seal(secret: String, rounds: Int): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(secret, salt, rounds)
        return listOf(SCHEME, rounds.toString(), b64(salt), b64(hash)).joinToString("$")
    }

    /**
     * Совпадает ли набранное с отпечатком. Испорченный или незнакомый
     * отпечаток — «не совпадает», а не падение: лучше не пустить, чем уронить
     * экран входа.
     */
    fun matches(secret: String, sealed: String): Boolean {
        val parts = sealed.split('$')
        if (parts.size != 4 || parts[0] != SCHEME) return false
        val rounds = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = runCatching { unb64(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { unb64(parts[3]) }.getOrNull() ?: return false
        // Сравнение за одинаковое время: побайтовое с выходом на первом
        // несовпадении подсказывало бы по часам, сколько байт угадано.
        return MessageDigest.isEqual(derive(secret, salt, rounds), expected)
    }

    /**
     * Код восстановления: двадцать знаков пятью четвёрками,
     * `K7QM-2XHD-9PWA-TR4N-E3FB`.
     *
     * Азбука без 0/O, 1/I/L и без кириллицы: код переписывают с экрана на
     * бумагу и обратно, и знак, который читается двояко, — это код, который не
     * подойдёт. Тридцать один знак на место, двадцать мест — без малого сто
     * бит: столько не подбирается.
     */
    fun recoveryCode(): String =
        (1..RECOVERY_LENGTH)
            .map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }
            .chunked(4) { it.joinToString("") }
            .joinToString("-")

    /**
     * Код, как его набрали, — к виду, в котором он сверяется: без дефисов и
     * пробелов, заглавными. Переписанный с бумаги код набирают как придётся.
     */
    fun normalizeCode(typed: String): String =
        typed.uppercase().filter { it.isLetterOrDigit() }

    private fun derive(secret: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, rounds, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun unb64(text: String) = Base64.getDecoder().decode(text)

    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val RECOVERY_LENGTH = 20
}
