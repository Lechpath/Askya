package app.askya.data.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/*
 * Хранилище аккаунта телефона — файл `account` рядом с настройками. В «Слепок»
 * он не входит: см. [AccountPreferences].
 */
private val Context.accountStore: DataStore<Preferences> by preferencesDataStore(name = "account")

fun AccountPreferences(context: Context) = AccountPreferences(context.accountStore)
