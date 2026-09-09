package app.askya.ui.yet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.app.AppContainer
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList
import app.askya.data.repository.YetRepository
import app.askya.domain.model.ListMark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class YetViewModel(private val yet: YetRepository) : ViewModel() {

    val lists = yet.lists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val remaining = yet.remaining()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val sizes = yet.sizes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun list(id: Long): Flow<YetList?> = yet.list(id)

    fun items(id: Long): Flow<List<YetItem>> = yet.items(id)

    /**
     * Заводит список и отдаёт его номер: сразу после создания он открывается,
     * иначе человек возвращался бы к оглавлению и тыкал в только что заведённое.
     */
    fun addList(title: String, mark: ListMark, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(yet.addList(title, mark)) }
    }

    fun updateList(list: YetList, title: String, mark: ListMark) {
        viewModelScope.launch { yet.updateList(list, title, mark) }
    }

    fun deleteList(id: Long) {
        viewModelScope.launch { yet.deleteList(id) }
    }

    fun addLines(listId: Long, source: String) {
        if (source.isBlank()) return
        viewModelScope.launch { yet.addLines(listId, source) }
    }

    fun toggle(item: YetItem) {
        viewModelScope.launch { yet.toggle(item) }
    }

    /**
     * Убрать строку — в корзину на сутки, а не из базы вон. Подтверждения
     * поэтому и нет: возврат отменяет ошибку, а вопрос её только перекладывал.
     */
    fun removeItem(id: Long) {
        viewModelScope.launch { yet.removeItem(id) }
    }

    fun restoreItem(id: Long) {
        viewModelScope.launch { yet.restoreItem(id) }
    }

    fun clearDone(listId: Long) {
        viewModelScope.launch { yet.clearDone(listId) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { YetViewModel(container.yetRepository) }
        }
    }
}
