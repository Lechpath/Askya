package app.askya.ui.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.app.AppContainer
import app.askya.data.entity.ThreadItem
import app.askya.data.repository.ThreadParts
import app.askya.data.repository.ThreadRepository
import app.askya.data.repository.ThreadRow
import app.askya.domain.model.ThreadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Threads: лента нитей и та, что раскрыта.
 *
 * Раскрытая нить живёт здесь, а не в разметке: её части спрашиваются у базы
 * отдельными потоками, и держать подписку на все нити разом значило бы читать
 * дела, строки и траты всего раздела ради одной открытой карточки.
 */
class ThreadsViewModel(private val threads: ThreadRepository) : ViewModel() {

    val rows: StateFlow<List<ThreadRow>> = threads.threads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Раскрытая нить. 0 — не раскрыта ничья. */
    private val _shown = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val parts: StateFlow<ThreadParts> = _shown
        .flatMapLatest { id -> if (id == 0L) flowOf(ThreadParts()) else threads.parts(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadParts())

    fun show(id: Long) {
        _shown.value = id
    }

    fun save(thread: ThreadItem) {
        viewModelScope.launch { threads.save(thread) }
    }

    fun close(thread: ThreadItem, state: ThreadState) {
        viewModelScope.launch { threads.close(thread, state) }
    }

    fun revive(thread: ThreadItem) {
        viewModelScope.launch { threads.revive(thread) }
    }

    /**
     * Стереть нить. Отвечает [onDone] тем, вышло ли: нить, через которую
     * что-то прошло, не стирается — её бросают.
     */
    fun delete(id: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(threads.delete(id)) }
    }

    /** Поставить дело этой нити в сегодняшний день, не уходя из неё. */
    fun addDeed(threadId: Long, title: String) {
        viewModelScope.launch { threads.addDeed(threadId, title) }
    }

    /** Дописать строку в список нити; списка нет — он заведётся сам. */
    fun addLine(thread: ThreadItem, text: String) {
        viewModelScope.launch { threads.addLine(thread, text) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ThreadsViewModel(container.threadRepository) }
        }
    }
}
