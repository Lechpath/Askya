package app.askya.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/*
 * Хранилища телефона — те же файлы, что и до Windows-версии: имя хранилища и
 * есть имя файла, и сменить его значило бы потерять настройки у всех.
 *
 * Сами классы настроек теперь общие и берут готовое хранилище, а здесь —
 * прежний способ завести его из `Context`. Функции названы как классы нарочно:
 * всё, что на телефоне писало `SettingsPreferences(context)`, пишет так и
 * дальше, и получает то же самое единственное на процесс хранилище.
 */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.readerStore: DataStore<Preferences> by preferencesDataStore(name = "reader")
private val Context.weatherStore: DataStore<Preferences> by preferencesDataStore(name = "weather")

fun SettingsPreferences(context: Context) = SettingsPreferences(context.settingsStore)

fun ReaderPreferences(context: Context) = ReaderPreferences(context.readerStore)

fun WeatherPreferences(context: Context) = WeatherPreferences(context.weatherStore)
