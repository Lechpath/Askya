package app.askya.data.sync

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифр облака: всё, что уезжает наружу, закрыто здесь.
 *
 * ## Два ключа, а не один
 *
 * Записи закрыты **ключом данных** — случайными 256 битами, которые заводятся
 * один раз при подключении облака. Сам пароль записи не шифрует: иначе смена
 * пароля означала бы перешифровать всё, что уже лежит в облаке, а забытый
 * пароль — потерять его целиком. Пароль запирает только ключ данных
 * ([CloudAccount]), и смена пароля перезапирает двадцать восемь байт.
 *
 * ## Почему AES-GCM
 *
 * GCM не только прячет, но и подписывает: изменённый байт в файле не
 * расшифруется вовсе, а не превратится в мусор, который кто-то попробует
 * применить к базе. Подписью накрывается и **путь файла** ([seal] берёт его
 * добавкой): порцию нельзя переложить из папки одного устройства в папку
 * другого и выдать за чужую.
 *
 * Свой случайный `nonce` у каждого файла: у GCM повторённый nonce с тем же
 * ключом раскрывает оба сообщения разом, и это единственное, чего в GCM нельзя
 * делать никогда. Двенадцать байт от [SecureRandom] на каждую порцию —
 * совпадение невозможно раньше, чем кончатся все порции всех устройств.
 *
 * ## Из пароля в ключ
 *
 * PBKDF2-HMAC-SHA256, 600 000 витков — впятеро больше, чем у замка на
 * устройстве ([app.askya.domain.account.Secrets]), и по другой причине.
 * Отпечаток замка лежит на телефоне, до которого ещё надо добраться; ключ
 * облака перебирают на чужой видеокарте по файлу, скачанному с Диска, и
 * времени у неё сколько угодно. Отсюда же правило «пароль от восьми знаков».
 *
 * `javax.crypto` есть и на телефоне (с Android 8, ниже Askya не ставится), и в
 * JDK компьютера — новой зависимости не нужно, а шифр один у обеих систем.
 */
object Crypt {

    /** Витков PBKDF2 у ключа облака. Лежит в `account.json`: поднять можно. */
    const val ROUNDS = 600_000

    private const val KEY_BITS = 256
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    /** Ключ данных — случайные 256 бит. Заводится один раз на аккаунт. */
    fun key(): ByteArray = ByteArray(KEY_BITS / 8).also(random::nextBytes)

    fun salt(): ByteArray = ByteArray(16).also(random::nextBytes)

    /** Ключ из пароля. Медленно — звать не с главного потока. */
    fun fromSecret(secret: String, salt: ByteArray, rounds: Int = ROUNDS): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, rounds, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Закрыть. Отдаётся `nonce` и следом закрытое с подписью; [path] в самих
     * байтах не лежит, но входит в подпись.
     */
    fun seal(key: ByteArray, plain: ByteArray, path: String): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(path.toByteArray())
        return nonce + cipher.doFinal(plain)
    }

    /**
     * Открыть. `null` — не тот ключ, тронутые байты или чужой путь: всё это
     * одно и то же «применять нельзя», и разбирать, что именно, не нужно —
     * порция просто не берётся.
     */
    fun open(key: ByteArray, sealed: ByteArray, path: String): ByteArray? {
        if (sealed.size <= NONCE_BYTES) return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val nonce = sealed.copyOf(NONCE_BYTES)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(path.toByteArray())
            cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
        }.getOrNull()
    }

    /**
     * Сжатие перед шифром, а не после: шифрованное не сжимается вовсе — оно
     * неотличимо от случайного. Порция это текст, и ужимается он раз в десять.
     */
    fun squeeze(plain: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(plain) }
        return out.toByteArray()
    }

    fun unsqueeze(packed: ByteArray): ByteArray =
        GZIPInputStream(packed.inputStream()).use { it.readBytes() }

    fun b64(bytes: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(bytes)

    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
}
