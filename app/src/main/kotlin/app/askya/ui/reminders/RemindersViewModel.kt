package app.askya.ui.reminders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.app.AppContainer
import app.askya.data.entity.Reminder
import app.askya.data.entity.reminderOf
import app.askya.data.repository.ReminderRepository
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.RemindAt
import app.askya.reminders.ReminderAlarms
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Напоминания — все, и заведённые здесь, и поставленные на дело дня.
 *
 * Список один намеренно: человек спрашивает «о чём мне напомнят», а не «о чём
 * мне напомнят из раздела напоминаний». Связь с делом ([Reminder.itemId]) при
 * правке сохраняется — по ней колокольчик в дне и знает, что напоминание
 * стоит.
 */
class RemindersViewModel(private val reminders: ReminderRepository) : ViewModel() {

    val items: StateFlow<List<Reminder>> = reminders.reminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Сохраняет карточку напоминания — и новую, и правку.
     *
     * Будильник перезаводится всегда: поменяться могло что угодно — час,
     * начало события, промежуток до него, — и старый будильник после этого
     * прозвучал бы не вовремя.
     */
    fun save(
        context: Context,
        existing: Reminder?,
        title: String,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime?,
        icon: BlockIcon?,
        remind: RemindAt,
        silent: Boolean,
        sound: String?,
        soundTitle: String?,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            existing?.let { ReminderAlarms.cancel(context, it.id) }

            val reminder = reminderOf(
                id = existing?.id ?: 0,
                title = title.trim(),
                eventDate = date,
                eventStart = start,
                eventEnd = end,
                remind = remind,
                icon = icon,
                enabled = existing?.enabled != false,
                silent = silent,
                sound = sound,
                soundTitle = soundTitle,
                itemId = existing?.itemId,
            )

            val id = if (existing == null) reminders.add(reminder) else {
                reminders.save(reminder)
                reminder.id
            }
            ReminderAlarms.schedule(context, reminder.copy(id = id))
        }
    }

    /**
     * Выключить, не удаляя: напоминание о деле, которое в этот раз не нужно,
     * человек чаще хочет приглушить, чем стереть.
     */
    fun setEnabled(context: Context, reminder: Reminder, enabled: Boolean) {
        viewModelScope.launch {
            val updated = reminder.copy(enabled = enabled)
            reminders.save(updated)
            if (enabled) ReminderAlarms.schedule(context, updated) else ReminderAlarms.cancel(context, reminder.id)
        }
    }

    fun delete(context: Context, reminder: Reminder) {
        viewModelScope.launch {
            ReminderAlarms.cancel(context, reminder.id)
            reminders.delete(reminder)
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RemindersViewModel(container.reminderRepository) }
        }
    }
}
