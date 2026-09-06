package app.askya.ui.routine

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.app.AppContainer
import app.askya.data.entity.RoutineItem
import app.askya.data.repository.DeedTrace
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.RoutineRepository
import app.askya.reminders.dropReminders
import app.askya.reminders.moveReminder
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
class RoutineViewModel(
    private val repository: RoutineRepository,
    private val reminders: ReminderRepository,
) : ViewModel() {

    val items: StateFlow<List<RoutineItem>> = repository.items()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Сохраняет и новое дело, и правку существующего: у нового id равен нулю.
     *
     * И тут же доводит сохранённое до уже собранных дней
     * ([RoutineRepository.applyToDays]): дело, заведённое в списке, должно
     * стоять в сегодняшнем дне сразу, а не с завтрашнего утра. Прежний вид
     * дела читается до записи — по нему в дне находится уже стоящая копия,
     * которую надо подтянуть, а не задвоить.
     */
    fun save(context: Context, item: RoutineItem) {
        if (item.title.isBlank()) return
        val trimmed = item.copy(title = item.title.trim())
        viewModelScope.launch {
            val before = if (trimmed.id == 0L) null else repository.get(trimmed.id)
            if (trimmed.id == 0L) repository.add(trimmed) else repository.save(trimmed)
            follow(context, trimmed, repository.applyToDays(trimmed, before))
        }
    }

    /**
     * Переключатель «участвует ли дело в сборке» — тоже правка правила, и
     * доводится он так же: включённое встаёт в сегодняшний день, выключенное
     * уходит из него и из дней впереди. Иначе выключить дело было бы нечем —
     * поставил его переключатель, а убрать его уже не может.
     */
    fun setEnabled(context: Context, item: RoutineItem, enabled: Boolean) {
        viewModelScope.launch {
            val updated = item.copy(enabled = enabled)
            repository.save(updated)
            follow(context, updated, repository.applyToDays(updated, item))
        }
    }

    /**
     * Ведёт напоминания вслед за днями, которые поправила доводка: у убранного
     * дела напоминание снимается, у переехавшего — переезжает.
     */
    private suspend fun follow(context: Context, item: RoutineItem, trace: DeedTrace) {
        dropReminders(context, reminders, trace.taken)
        trace.moved.forEach { move ->
            moveReminder(context, reminders, move.itemId, move.date, item.startTime, item.endTime)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                RoutineViewModel(container.routineRepository, container.reminderRepository)
            }
        }
    }
}
