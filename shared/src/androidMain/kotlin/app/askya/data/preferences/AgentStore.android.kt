package app.askya.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/*
 * Хранилище агента телефона — файл `agent` рядом с настройками. Ни в «Слепок»,
 * ни в синхронизацию он не входит: см. [AgentPreferences].
 */
private val Context.agentStore: DataStore<Preferences> by preferencesDataStore(name = AgentPreferences.STORE)

fun AgentPreferences(context: Context) = AgentPreferences(context.agentStore)
