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

    fun save(note: Note) {
        viewModelScope.launch { notes.save(note) }
    }

    fun delete(note: Note) {
        viewModelScope.launch { notes.delete(note) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { NoteEditViewModel(container.noteRepository) }
        }
    }
}
