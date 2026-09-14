package app.askya.ui.noteedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.app.AppContainer
import app.askya.data.entity.Note
import app.askya.data.entity.ScrollTopic
import app.askya.data.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteEditViewModel(private val notes: NoteRepository) : ViewModel() {

    val topics: StateFlow<List<ScrollTopic>> = notes.topics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun note(id: Long): Flow<Note?> = notes.note(id)

    /**
     * Полка, на которой лежит запись, — одними номерами и по порядку. По ней
     * карточка листается смахиванием. Спрашивается разом, а не потоком: см.
     * [NoteRepository.shelfOf].
     */
    suspend fun shelfOf(id: Long): List<Long> = notes.shelfOf(id)

    fun save(note: Note) {
        viewModelScope.launch { notes.save(note) }
    }

    /**
     * Убрать запись — в корзину на сутки, а не из базы вон. Подтверждения
     * поэтому и нет: возврат отменяет ошибку, а вопрос её только перекладывал.
     */
    fun remove(note: Note) {
        viewModelScope.launch { notes.remove(note.id) }
    }

    fun restore(id: Long) {
        viewModelScope.launch { notes.restore(id) }
    }

    /**
     * Стереть совсем — так уходит карточка, которую завели и закрыли пустой.
     *
     * Не в корзину: возвращать там нечего, а «Запись убрана · Вернуть» под
     * пустой карточкой выглядело бы сообщением о потере того, чего не было.
     */
    fun discard(note: Note) {
        viewModelScope.launch { notes.delete(note) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { NoteEditViewModel(container.noteRepository) }
        }
    }
}
