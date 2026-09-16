package app.askya.data.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Аккаунт Askya — имя и то, чем в неё входят.
 *
 * Аккаунт здесь — не учётная запись на чужом сервере, а замок на этом
 * устройстве: сервера у Askya нет, и имя с паролем никуда не уходят. Лежат не
 * сами пароль и пин-код, а их отпечатки ([app.askya.domain.account.Secrets]).
 *
 * Своё хранилище (`account`), а не строчки в общих настройках, и по двум
 * причинам. Первая — «Слепок»: он возит настройки файлами как есть, а замок
 * не должен переезжать вместе с записями — прочитавший чужой слепок получил
 * бы чужой пароль на свою Askya, а вернувший старый — забытый. Вторая —
 * счётчики промахов: они пишутся на каждой ошибке, и общим настройкам это ни к
 * чему.
 */
data class Account(
    val name: String,
    /** Отпечаток пароля. */
    val password: String,
    /** Отпечаток пин-кода; `null` — пин-кода нет, вход только паролем. */
    val pin: String?,
    /** Сколько цифр в пин-коде: набор принимается, как только их столько. */
    val pinLength: Int,
    /** Отпечаток кода восстановления — на случай забытого пароля. */
    val recovery: String,
    val lockAfter: LockAfter,
    /** Промахи пин-кода подряд — см. [app.askya.domain.account.Attempts]. */
    val pinMisses: Int,
    /** Промахи пароля и кода восстановления подряд. */
    val passwordMisses: Int,
    /** До какого мгновения пароль не принимается, в миллисекундах эпохи. */
    val pausedUntil: Long,
) {
    /** Можно ли сейчас входить пин-кодом. */
    val pinUsable: Boolean
        get() = pin != null && pinMisses < app.askya.domain.account.Attempts.PIN_TRIES
}

/**
 * Через сколько запирается Askya, оставленная в фоне.
 *
 * «Сразу» есть, но не стоит умолчанием: из Askya выходят на минуту — выбрать
 * файл, ответить на звонок, — и пароль после каждого такого выхода быстро
 * становится тем, что отключают. Минута закрывает то, от чего замок и
 * заводят: телефон, оставленный на столе.
 */
enum class LockAfter(val title: String, val millis: Long) {
    NOW("Сразу", 0L),
    MINUTE("Через минуту", 60_000L),
    FIVE("Через 5 минут", 5 * 60_000L),
    HALF_HOUR("Через полчаса", 30 * 60_000L),
}

class AccountPreferences(private val store: DataStore<Preferences>) {

    /** Аккаунт, если он заведён; `null` — Askya открыта всем, как прежде. */
    val account: Flow<Account?> = store.data.map { it.toAccount() }

    suspend fun current(): Account? = account.first()

    /** Завести аккаунт. Отпечатки готовит вызывающий — это медленно. */
    suspend fun create(name: String, password: String, recovery: String) = edit {
        it.clear()
        it[KEY_NAME] = name
        it[KEY_PASSWORD] = password
        it[KEY_RECOVERY] = recovery
        it[KEY_LOCK_AFTER] = LockAfter.MINUTE.name
    }

    suspend fun setName(name: String) = edit { it[KEY_NAME] = name }

    suspend fun setPassword(password: String) = edit { it[KEY_PASSWORD] = password }

    suspend fun setRecovery(recovery: String) = edit { it[KEY_RECOVERY] = recovery }

    /** Поставить пин-код или убрать его (`null`). Счёт промахов — с нуля. */
    suspend fun setPin(pin: String?, length: Int) = edit {
        if (pin == null) {
            it.remove(KEY_PIN)
            it.remove(KEY_PIN_LENGTH)
        } else {
            it[KEY_PIN] = pin
            it[KEY_PIN_LENGTH] = length
        }
        it.remove(KEY_PIN_MISSES)
    }

    suspend fun setLockAfter(value: LockAfter) = edit { it[KEY_LOCK_AFTER] = value.name }

    /** Промах пин-кода; возвращает, сколько их теперь подряд. */
    suspend fun missPin(): Int =
        store.edit { it[KEY_PIN_MISSES] = (it[KEY_PIN_MISSES] ?: 0) + 1 }[KEY_PIN_MISSES] ?: 0

    /** Промах пароля — и пауза до следующей попытки, если она положена. */
    suspend fun missPassword(pauseUntil: (misses: Int) -> Long): Int {
        val saved = store.edit {
            val misses = (it[KEY_PASSWORD_MISSES] ?: 0) + 1
            it[KEY_PASSWORD_MISSES] = misses
            it[KEY_PAUSED_UNTIL] = pauseUntil(misses)
        }
        return saved[KEY_PASSWORD_MISSES] ?: 0
    }

    /** Вошли паролем: сбрасываются все промахи, и пин-код снова в деле. */
    suspend fun clearMisses() = edit {
        it.remove(KEY_PIN_MISSES)
        it.remove(KEY_PASSWORD_MISSES)
        it.remove(KEY_PAUSED_UNTIL)
    }

    /** Вошли пин-кодом: сбрасываются только его промахи. */
    suspend fun clearPinMisses() = edit { it.remove(KEY_PIN_MISSES) }

    /** Убрать аккаунт. Записи Askya не трогаются: они не принадлежали ему. */
    suspend fun remove() = edit { it.clear() }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit(block)
    }

    private fun Preferences.toAccount(): Account? {
        val password = this[KEY_PASSWORD] ?: return null
        return Account(
            name = this[KEY_NAME].orEmpty(),
            password = password,
            pin = this[KEY_PIN],
            pinLength = this[KEY_PIN_LENGTH] ?: 0,
            recovery = this[KEY_RECOVERY].orEmpty(),
            lockAfter = this[KEY_LOCK_AFTER]
                ?.let { name -> LockAfter.entries.firstOrNull { it.name == name } }
                ?: LockAfter.MINUTE,
            pinMisses = this[KEY_PIN_MISSES] ?: 0,
            passwordMisses = this[KEY_PASSWORD_MISSES] ?: 0,
            pausedUntil = this[KEY_PAUSED_UNTIL] ?: 0L,
        )
    }

    private companion object {
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_PIN = stringPreferencesKey("pin")
        val KEY_PIN_LENGTH = intPreferencesKey("pin_length")
        val KEY_RECOVERY = stringPreferencesKey("recovery")
        val KEY_LOCK_AFTER = stringPreferencesKey("lock_after")
        val KEY_PIN_MISSES = intPreferencesKey("pin_misses")
        val KEY_PASSWORD_MISSES = intPreferencesKey("password_misses")
        val KEY_PAUSED_UNTIL = longPreferencesKey("paused_until")
    }
}
