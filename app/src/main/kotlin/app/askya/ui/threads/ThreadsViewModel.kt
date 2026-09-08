package app.askya.ui.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.app.AppContainer
import app.askya.data.entity.ThreadItem
import app.askya.data.entity.ThreadNode
import app.askya.data.repository.ThreadParts
import app.askya.data.repository.ThreadRepository
import app.askya.data.repository.ThreadRow
import app.askya.data.repository.ThreadWeb
import app.askya.domain.model.ThreadNodeKind
import app.askya.domain.model.ThreadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Threads: лента нитей, раскрытая нить и её карта замысла.
 *
 * Раскрытая нить живёт здесь, а не в разметке: её части спрашиваются у базы
 * отдельными потоками, и держать подписку на все нити разом значило бы читать
 * дела, строки и траты всего раздела ради одной открытой карточки.
 *
 * Карта отдаётся потоком по номеру, а не полем: у неё свой экран, который свой
 * номер знает, — тот же приём, что у списка Yet.
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

    /**
     * Завести нить или поправить её. Отдаёт номер: только что заведённая
     * открывается сразу — искать её глазами в ленте человек не должен.
     */
    fun save(thread: ThreadItem, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch { onSaved(threads.save(thread)) }
    }

    /** Сменить состояние нити — любое на любое: гореть и тлеть равно можно. */
    fun setState(thread: ThreadItem, state: ThreadState) {
        viewModelScope.launch { threads.setState(thread, state) }
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

    // ---- Карта замысла ----

    /**
     * Карта одной нити. Потоком по номеру, а не полем: карту открывают с
     * экрана, который знает свой номер, — так же, как список Yet.
     */
    fun web(id: Long): Flow<ThreadWeb> = threads.web(id)

    fun thread(id: Long): Flow<ThreadItem?> = threads.thread(id)

    /**
     * Завести узел — сам по себе или выросшим из другого. Отдаёт его номер:
     * заведённый узел тут же открывается, иначе человек искал бы его глазами.
     */
    fun addNode(
        threadId: Long,
        kind: ThreadNodeKind,
        title: String,
        note: String = "",
        from: ThreadNode? = null,
        onMade: (Long) -> Unit = {},
    ) {
        viewModelScope.launch { onMade(threads.addNode(threadId, kind, title, note, from)) }
    }

    fun saveNode(node: ThreadNode) {
        viewModelScope.launch { threads.saveNode(node) }
    }

    /** Узел отпустили на новом месте. Пока тянут, база не трогается. */
    fun moveNode(id: Long, x: Float, y: Float) {
        viewModelScope.launch { threads.moveNode(id, x, y) }
    }

    fun deleteNode(id: Long) {
        viewModelScope.launch { threads.deleteNode(id) }
    }

    fun tie(threadId: Long, from: Long, to: Long) {
        viewModelScope.launch { threads.tie(threadId, from, to) }
    }

    fun untie(edgeId: Long) {
        viewModelScope.launch { threads.untie(edgeId) }
    }

    fun markNode(node: ThreadNode, done: Boolean) {
        viewModelScope.launch { threads.markNode(node, done) }
    }

    /** Поставить шаг делом на сегодня. Второй раз то же дело не заведётся. */
    fun stepToDeed(node: ThreadNode) {
        viewModelScope.launch { threads.stepToDeed(node) }
    }

    fun stepToLine(thread: ThreadItem, node: ThreadNode) {
        viewModelScope.launch { threads.stepToLine(thread, node) }
    }

    fun linkNode(node: ThreadNode, link: String?) {
        viewModelScope.launch { threads.linkNode(node, link) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ThreadsViewModel(container.threadRepository) }
        }
    }
}
