package app.askya.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.app.AppContainer
import app.askya.data.entity.Reminder
import app.askya.data.entity.reminderOf
import app.askya.data.sync.Uid
import app.askya.data.repository.ReminderRepository
import app.askya.reminders.ReminderClock
import app.askya.reminders.ReminderCreator
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.RemindAt
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
class RemindersViewModel(
    private val reminders: ReminderRepository,
    private val alarms: ReminderClock,
    private val creator: ReminderCreator,
) : ViewModel() {

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
            existing?.let { alarms.cancel(it.id) }

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
                uid = existing?.uid ?: Uid.new(),
            )

            if (existing == null) {
                // Новое — тем же путём, что у всех: запись, потом будильник с её номером.
                creator.create(reminder)
            } else {
                reminders.save(reminder)
                alarms.schedule(reminder)
            }
        }
    }

    /**
     * Выключить, не удаляя: напоминание о деле, которое в этот раз не нужно,
     * человек чаще хочет приглушить, чем стереть.
     */
    fun setEnabled(reminder: Reminder, enabled: Boolean) {
        viewModelScope.launch {
            val updated = reminder.copy(enabled = enabled)
            reminders.save(updated)
            if (enabled) alarms.schedule(updated) else alarms.cancel(reminder.id)
        }
    }

    fun delete(reminder: Reminder) {
        viewModelScope.launch {
            alarms.cancel(reminder.id)
            reminders.delete(reminder)
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                RemindersViewModel(container.reminderRepository, container.alarms, container.reminderCreator)
            }
        }
    }
}
