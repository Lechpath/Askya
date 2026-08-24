package app.askya.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.app.AppContainer
import app.askya.data.entity.RoutineItem
import app.askya.data.repository.RoutineRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Список дел — то, из чего собирается день.
 *
 * Раньше сюда же приходил рассказ о себе: человек надиктовывал, каким бывает
 * его день, модель разбирала текст на дела, планировщик расставлял их по
 * времени. Разговор с моделью из приложения убран, поэтому и разбор ушёл: дела
 * заводят руками, у каждого названо время, и раскладывать нечего.
 */
class RoutineViewModel(private val repository: RoutineRepository) : ViewModel() {

    val items: StateFlow<List<RoutineItem>> = repository.items()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Сохраняет и новое дело, и правку существующего: у нового id равен нулю. */
    fun save(item: RoutineItem) {
        if (item.title.isBlank()) return
        val trimmed = item.copy(title = item.title.trim())
        viewModelScope.launch {
            if (trimmed.id == 0L) repository.add(trimmed) else repository.save(trimmed)
        }
    }

    fun setEnabled(item: RoutineItem, enabled: Boolean) {
        viewModelScope.launch { repository.save(item.copy(enabled = enabled)) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RoutineViewModel(container.routineRepository) }
        }
    }
}
