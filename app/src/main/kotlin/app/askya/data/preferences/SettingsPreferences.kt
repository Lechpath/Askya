package app.askya.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Настройки приложения. Экран настроек читает и пишет их через этот класс. */
class SettingsPreferences(private val context: Context) {

    /** Начинать ли неделю с понедельника — влияет на счётчики практик. */
    val weekStartsMonday: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_WEEK_STARTS_MONDAY] ?: true }

    suspend fun setWeekStartsMonday(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WEEK_STARTS_MONDAY] = enabled }
    }

    /**
     * Рассказ о себе и своём дне. Хранится целиком, а не только разобранные из
     * него дела: текст — это то, что человек сказал, и его нужно уметь показать
     * и перечитать, а разбор всегда можно повторить.
     */
    val aboutMe: Flow<String> = context.dataStore.data.map { it[KEY_ABOUT_ME] ?: "" }

    suspend fun setAboutMe(text: String) {
        context.dataStore.edit { it[KEY_ABOUT_ME] = text }
    }

    /**
     * Ключ Claude для разбора рассказа о себе. Пустая строка — разбор идёт
     * правилами на телефоне, и приложение в сеть не выходит вовсе.
     *
     * Хранится в DataStore как есть, без шифрования: файл лежит в приватном
     * каталоге приложения и другим программам недоступен, но человек с
     * разблокированным телефоном или root его прочитает. Для личного ключа
     * с оплатой по счётчику это приемлемо; ключ от рабочего аккаунта сюда
     * заводить не стоит.
     */
    val claudeKey: Flow<String> = context.dataStore.data.map { it[KEY_CLAUDE] ?: "" }

    suspend fun setClaudeKey(value: String) {
        context.dataStore.edit { it[KEY_CLAUDE] = value.trim() }
    }

    private companion object {
        val KEY_WEEK_STARTS_MONDAY = booleanPreferencesKey("week_starts_monday")
        val KEY_ABOUT_ME = stringPreferencesKey("about_me")
        val KEY_CLAUDE = stringPreferencesKey("claude_key")
    }
}
