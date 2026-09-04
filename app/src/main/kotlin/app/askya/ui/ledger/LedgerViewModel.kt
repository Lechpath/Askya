package app.askya.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.app.AppContainer
import app.askya.data.entity.LedgerAccount
import app.askya.data.entity.LedgerCategory
import app.askya.data.entity.LedgerEntry
import app.askya.data.repository.AccountLine
import app.askya.data.repository.LedgerRepository
import app.askya.data.repository.LedgerStats
import app.askya.data.repository.MonthBook
import app.askya.data.repository.Trash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * Ledger: месяц под пальцем, счета и статьи.
 *
 * Месяц живёт здесь, а не в разметке: страницу «Доход/Расход» пролистывают
 * вбок к счетам и обратно, и вернувшийся должен попасть в тот же август, а не
 * в нынешний месяц.
 */
class LedgerViewModel(private val ledger: LedgerRepository, private val trash: Trash) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val accounts: StateFlow<List<AccountLine>> = ledger.wealth()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<LedgerCategory>> = ledger.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Вся книга числами — для страницы «Статистика».
     *
     * Отдельным потоком от [book]: месяц под пальцем листают, а статистика
     * смотрит на все годы разом, и пересобирать её при каждом перелистывании
     * августа незачем.
     */
    val stats: StateFlow<LedgerStats> = ledger.stats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerStats())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val book: StateFlow<MonthBook> = _month
        .flatMapLatest { ledger.month(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthBook())

    /** Словарь статей и первый счёт — при первом входе в раздел. */
    fun start() {
        viewModelScope.launch { ledger.ensureStarted() }
    }

    fun showMonth(month: YearMonth) {
        _month.value = month
    }

    fun save(entry: LedgerEntry) {
        viewModelScope.launch { ledger.save(entry) }
    }

    /**
     * Убрать запись — в корзину на сутки, как дело, заметку и строку Yet.
     * Подтверждения поэтому и нет: возврат отменяет ошибку, а вопрос её только
     * перекладывал.
     */
    fun remove(id: Long) {
        viewModelScope.launch {
            ledger.remove(id)
            trash.remembered(Trash.Kind.MONEY, id)
        }
    }

    fun saveAccount(account: LedgerAccount) {
        viewModelScope.launch { ledger.saveAccount(account) }
    }

    /**
     * Поменять два счёта местами в сетке.
     *
     * Наружу уходит весь список в новом порядке, а не два номера: порядок
     * пересчитывается от нуля по тому, что человек видит на экране, — см.
     * [LedgerRepository.reorderAccounts]. Порядок берётся из сетки, а не из
     * базы, потому что это одно и то же: сетка показывает счета ровно тем
     * порядком, которым их отдал запрос.
     */
    fun swapAccounts(shown: List<LedgerAccount>, one: LedgerAccount, other: LedgerAccount) {
        val from = shown.indexOfFirst { it.id == one.id }
        val to = shown.indexOfFirst { it.id == other.id }
        if (from < 0 || to < 0 || from == to) return

        val swapped = shown.toMutableList()
        swapped[from] = other
        swapped[to] = one
        viewModelScope.launch { ledger.reorderAccounts(swapped) }
    }

    /**
     * Стереть счёт. Отвечает [onDone] тем, вышло ли: счёт, по которому что-то
     * прошло, не стирается — на нём висит история, и его закрывают.
     */
    fun deleteAccount(id: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(ledger.deleteAccount(id)) }
    }

    fun saveCategory(category: LedgerCategory) {
        viewModelScope.launch { ledger.saveCategory(category) }
    }

    /** Завести статью прямо из записи и тут же её выбрать. */
    fun addCategory(category: LedgerCategory, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(ledger.saveCategory(category)) }
    }

    fun forgetCategory(id: Long) {
        viewModelScope.launch { ledger.forgetCategory(id) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LedgerViewModel(container.ledgerRepository, container.trash) }
        }
    }
}
