package app.askya.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.concurrent.Volatile

/**
 * Что показывает экран настроек агента. Самого ключа здесь нет — только
 * последние знаки, чтобы узнать, какой стоит.
 */
data class AgentSettings(
    /** Последние знаки ключа; `null` — ключа нет. */
    val keyTail: String?,
    /** Согласие этого устройства на облачную модель. Без ключа всегда `false`. */
    val allowCloud: Boolean,
    /**
     * Номер сохранённого ключа — случайный и ничего о ключе не говорящий. Новый
     * ключ — новый номер: по нему экран понимает, что клиент уже другой.
     */
    val keyId: String? = null,
) {
    val hasKey: Boolean get() = keyTail != null
}

/**
 * Настройки агента: ключ Claude и согласие на облачную модель.
 *
 * Своё хранилище ([STORE]), а не строчки в общих настройках, — по той же
 * причине, что у аккаунта: «Слепок» возит настройки файлами, и ни ключ, ни
 * согласие не должны переезжать с ним на другое устройство. Согласие — решение
 * человека об этом телефоне (второй может быть чужим), а ключ — секрет. В
 * синхронизацию хранилище не входит вовсе: она возит только таблицы базы.
 *
 * **Согласие без ключа не бывает.** Включить облако без ключа нельзя, удаление
 * ключа выключает облако, а если в файле всё же окажется «разрешено» без
 * ключа, это читается как «запрещено».
 *
 * **[cloudAllowed] — сейчас и без ожидания диска**: политика агента
 * спрашивает его перед каждой отправкой. Пока файл не прочитан, согласия нет.
 * Выключение действует раньше записи на диск, включение — только после неё:
 * ошибиться можно лишь в сторону «не отправлять».
 */
class AgentPreferences(
    private val store: DataStore<Preferences>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val lock = Mutex()

    @Volatile private var consent = false
    private var loaded = false

    /** Разрешено ли сейчас отправлять разговор облачной модели. */
    val cloudAllowed: Boolean get() = consent

    val settings: Flow<AgentSettings> = store.data.map { prefs ->
        val key = prefs.key()
        AgentSettings(
            keyTail = key?.let(::tailOf),
            allowCloud = prefs.allowed(),
            keyId = if (key != null) prefs[KEY_ID] else null,
        )
    }

    private val loading = scope.launch {
        val first = store.data.first()
        lock.withLock {
            // Запись, случившаяся раньше чтения, новее прочитанного.
            if (!loaded) {
                consent = first.allowed()
                loaded = true
            }
        }
    }

    /** Дождаться первого чтения файла — для тех, кому нужно точное значение сразу. */
    internal suspend fun ready() = loading.join()

    /** Ключ — только для того, кто создаёт клиента. На экран он не отдаётся. */
    suspend fun apiKey(): String? = store.data.first().key()

    /** Сохранить или заменить ключ. Пустой не сохраняется. Согласие не меняется. */
    suspend fun setKey(key: String): Boolean {
        val clean = key.trim()
        if (clean.isEmpty() || clean.any { it.isWhitespace() }) return false
        lock.withLock {
            val saved = store.edit {
                it[KEY_API] = clean
                it[KEY_ID] = UUID.randomUUID().toString()
            }
            consent = saved.allowed()
            loaded = true
        }
        return true
    }

    /** Удалить ключ — и вместе с ним согласие. */
    suspend fun removeKey() = lock.withLock {
        consent = false
        store.edit {
            it.remove(KEY_API)
            it.remove(KEY_ID)
            it[KEY_ALLOW_CLOUD] = false
        }
        loaded = true
    }

    /**
     * Включить или выключить облачную модель. Без ключа не включается.
     * Возвращает, что стало на самом деле.
     */
    suspend fun setAllowCloud(allow: Boolean): Boolean = lock.withLock {
        if (!allow) consent = false
        val saved = store.edit { prefs ->
            prefs[KEY_ALLOW_CLOUD] = allow && prefs.key() != null
        }
        consent = saved.allowed()
        loaded = true
        consent
    }

    override fun toString(): String = "AgentPreferences"

    private fun Preferences.key(): String? = this[KEY_API]?.takeIf { it.isNotBlank() }

    private fun Preferences.allowed(): Boolean = this[KEY_ALLOW_CLOUD] == true && key() != null

    companion object {
        /** Имя хранилища — и файла. В Слепок не входит. */
        const val STORE = "agent"

        private val KEY_API = stringPreferencesKey("api_key")
        private val KEY_ALLOW_CLOUD = booleanPreferencesKey("allow_cloud")
        private val KEY_ID = stringPreferencesKey("key_id")

        /** Сколько знаков ключа видно на экране — и то лишь у длинного ключа. */
        private const val TAIL = 4

        private fun tailOf(key: String): String = if (key.length > TAIL * 3) key.takeLast(TAIL) else ""
    }
}
