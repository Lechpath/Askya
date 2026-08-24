package app.askya.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Каким светом набрана страница книги. */
enum class ReaderPage {
    /** Кремовая — та же бумага, что во всей Askya. */
    CREAM,

    /** Сепия: теплее и темнее, для долгого чтения при лампе. */
    SEPIA,

    /** Ночь — чёрная страница, как в Echo. */
    NIGHT,
}

/**
 * Как человек читает книги: кегль, страница, шрифт.
 *
 * Настройка одна на все книги, а не на каждую: зрение у читающего одно, и
 * подбирать кегль заново к каждой книге — работа, которой читалка и должна
 * избавить.
 */
data class ReaderStyle(
    /** Кегль в пунктах: 14…30. */
    val fontSize: Int = 18,
    val page: ReaderPage = ReaderPage.CREAM,
    /** Засечки. Книгу набирают ими, но с плохого экрана они хуже читаются. */
    val serif: Boolean = true,
)

/** Где человек остановился в книге: глава и абзац в ней. */
data class ReaderSpot(val chapter: Int = 0, val block: Int = 0)

private val Context.readerStore: DataStore<Preferences> by preferencesDataStore(name = "reader")

/**
 * Хранилище читалки: как набрана страница и где человек остановился в каждой
 * книге.
 *
 * Своё, отдельно от настроек приложения: кегль двигают прямо посреди чтения, а
 * место в книге записывается на каждом перевороте, — складывать это в один
 * файл с рассказом о себе значило бы переписывать его сотнями раз за вечер.
 *
 * Место помнится по ссылке на файл, а не по номеру записи в Scroll: одну и ту
 * же книгу открывают и из «Библиотеки», и из книги, и заново добавленной, — а
 * остановились в ней один раз.
 *
 * Ключом идёт не сама ссылка, а её отпечаток: ссылки на документы бывают
 * длиной в сотни знаков, а хранилище настроек — не место для них.
 */
class ReaderPreferences(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val style: Flow<ReaderStyle> = context.readerStore.data.map { it.toStyle() }

    val state: StateFlow<ReaderStyle> = style.stateIn(scope, SharingStarted.Eagerly, ReaderStyle())

    fun setFontSize(value: Int) = put { it[KEY_FONT] = value.coerceIn(14, 30) }

    fun setPage(page: ReaderPage) = put { it[KEY_PAGE] = page.name }

    fun setSerif(value: Boolean) = put { it[KEY_SERIF] = value }

    /** Где остановились в этой книге. */
    fun spot(uri: String): Flow<ReaderSpot> = context.readerStore.data.map { preferences ->
        val saved = preferences[spotKey(uri)] ?: return@map ReaderSpot()
        val parts = saved.split(':')
        ReaderSpot(
            chapter = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            block = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        )
    }

    /**
     * Запомнить место. Зовётся на каждой смене главы и на остановке прокрутки:
     * человек закрывает книгу как придётся — кнопкой «домой», звонком, — и
     * «сохранить чтение» отдельным действием он не нажмёт никогда.
     */
    fun remember(uri: String, chapter: Int, block: Int) = put {
        it[spotKey(uri)] = "$chapter:$block"
    }

    private fun put(edit: (MutablePreferences) -> Unit) {
        scope.launch { context.readerStore.edit(edit) }
    }

    private fun Preferences.toStyle() = ReaderStyle(
        fontSize = this[KEY_FONT] ?: 18,
        page = this[KEY_PAGE]?.let { name -> ReaderPage.entries.firstOrNull { it.name == name } }
            ?: ReaderPage.CREAM,
        serif = this[KEY_SERIF] ?: true,
    )

    private fun spotKey(uri: String) =
        stringPreferencesKey("at_" + Integer.toHexString(uri.hashCode()))

    private companion object {
        val KEY_FONT = intPreferencesKey("font_size")
        val KEY_PAGE = stringPreferencesKey("page")
        val KEY_SERIF = booleanPreferencesKey("serif")
    }
}
