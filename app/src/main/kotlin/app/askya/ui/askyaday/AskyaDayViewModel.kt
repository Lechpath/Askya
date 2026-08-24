package app.askya.ui.askyaday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.content.Context
import app.askya.app.AppContainer
import app.askya.data.entity.RoutineItem
import app.askya.data.entity.ScheduleItem
import app.askya.data.repository.DayRepository
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.RoutineRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.data.entity.reminderOf
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.DayPlan
import app.askya.domain.model.RemindAt
import app.askya.domain.plan.DayLayout
import app.askya.reminders.ReminderAlarms
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime

class AskyaDayViewModel(
    private val schedule: ScheduleRepository,
    private val routine: RoutineRepository,
    private val day: DayRepository,
    private val reminders: ReminderRepository,
) : ViewModel() {

    /** Идёт сборка дня — на это время в шапке дышит цветок. */
    private val _composing = MutableStateFlow(false)
    val composing: StateFlow<Boolean> = _composing.asStateFlow()

    /**
     * Что Askya сказала про собранный день, и кто его собрал. Живёт до
     * следующей сборки или до закрытия: это реплика, а не состояние дня.
     */
    private val _composed = MutableStateFlow<DayLayout?>(null)
    val composed: StateFlow<DayLayout?> = _composed.asStateFlow()

    private val _composeError = MutableStateFlow<String?>(null)
    val composeError: StateFlow<String?> = _composeError.asStateFlow()

    /**
     * Часы экрана: по ним подсвечивается текущий блок. Просыпаются в начале
     * минуты, а не через 60 секунд от запуска, иначе подсветка отстаёт на
     * случайный сдвиг. Пока экран не виден, поток остановлен.
     */
    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            val moment = LocalDateTime.now()
            emit(moment)
            delay(60_000L - moment.second * 1_000L - moment.nano / 1_000_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    /**
     * Список дел целиком. Раскрытой карточке дня по нему видно, лежит ли это
     * дело в списке, — от этого зависит, что предложить: добавить или убрать.
     */
    val routineItems: StateFlow<List<RoutineItem>> = routine.items()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Есть ли что разворачивать — от этого зависит предложение заполнить день. */
    val hasRoutine: StateFlow<Boolean> = routineItems
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** План одного дня. Экран листает дни, поэтому поток строится на дату. */
    fun plan(date: LocalDate): Flow<DayPlan> =
        schedule.itemsOn(date).map { items -> DayPlan(date, items) }

    /**
     * Разворачивает распорядок в день при первом открытии.
     *
     * Прошлые дни не заполняются намеренно: расписание на позавчера — это запись
     * о том, что было, и дорисовывать её задним числом значило бы врать.
     */
    fun ensureGenerated(date: LocalDate) {
        if (date.isBefore(LocalDate.now())) return
        viewModelScope.launch { routine.ensureGenerated(date) }
    }

    /** Заполнение по кнопке — работает и для дня, который уже разворачивали. */
    fun fillFromRoutine(date: LocalDate) {
        viewModelScope.launch { routine.fillFromRoutine(date) }
    }

    /**
     * Кладёт в день выбранные дела списка — третий способ собрать расписание,
     * между «заполнить целиком» и «написать руками».
     *
     * Что уже стоит в дне, отсеивает окно выбора: правило «то же дело» ([sameDeed])
     * живёт здесь, рядом с экраном, и второй его копии в базе не нужно.
     */
    fun addFromRoutine(date: LocalDate, items: List<RoutineItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch { routine.addToDay(date, items) }
    }

    /**
     * Собрать день заново — с профилем, самочувствием и днём недели.
     *
     * Только по нажатию: это запрос к модели, то есть деньги человека и
     * перетасованный день. Делать это молча при каждом открытии нельзя.
     */
    fun composeDay(date: LocalDate) {
        if (_composing.value) return
        viewModelScope.launch {
            _composing.value = true
            _composeError.value = null
            try {
                _composed.value = day.recompose(date)
            } catch (e: Exception) {
                _composeError.value = e.message ?: "Не удалось собрать день"
            } finally {
                _composing.value = false
            }
        }
    }

    /** Реплика прочитана. */
    fun dismissComposed() {
        _composed.value = null
        _composeError.value = null
    }

    /**
     * Стереть день целиком.
     *
     * Отметка «день заполнен» не снимается: без неё распорядок развернулся бы
     * сюда снова при следующем открытии, и очистка не пережила бы даже свайпа
     * на соседний день и обратно.
     */
    fun clearDay(context: Context, date: LocalDate) {
        viewModelScope.launch {
            // Номера дел собираются до очистки: после неё спросить уже не у
            // кого, а напоминания о стёртых делах прозвучали бы как ни в чём
            // не бывало.
            val ids = schedule.itemsOnce(date).map { it.id }
            schedule.clearDay(date)
            dropReminders(context, ids)
        }
    }

    fun toggleDone(item: ScheduleItem) {
        viewModelScope.launch { schedule.setDone(item.id, !item.done) }
    }

    /**
     * Сохраняет ответы диалога. У нового дела [existing] пусто; у правки
     * сохраняются конец и отметка «сделано» — диалог про них не спрашивает,
     * и терять их из-за этого нельзя.
     */
    fun save(
        context: Context,
        existing: ScheduleItem?,
        date: LocalDate,
        title: String,
        start: LocalTime,
        end: LocalTime?,
        note: String,
        icon: BlockIcon?,
        remind: RemindAt?,
        silent: Boolean,
        sound: String?,
        soundTitle: String?,
    ) {
        viewModelScope.launch {
            val id = if (existing == null) {
                schedule.add(
                    ScheduleItem(
                        date = date,
                        startTime = start,
                        endTime = end,
                        title = title,
                        note = note,
                        icon = icon,
                    )
                )
            } else {
                schedule.save(
                    existing.copy(
                        startTime = start,
                        endTime = end,
                        title = title,
                        note = note,
                        icon = icon,
                    )
                )
                existing.id
            }
            // Напоминание переписывается вместе с делом: у него в карточке
            // названы и час, и название, и они должны совпадать с делом, а
            // «за 15 минут» — сдвинуться вслед за перенесённым началом.
            applyRemind(context, id, title, date, start, end, icon, remind, silent, sound, soundTitle)
        }
    }

    /**
     * Кладёт дело дня в список дел — с этого дня оно будет в каждом новом.
     *
     * Заметка и отметка «сделано» не переносятся: они про один конкретный день,
     * а в списке лежит правило, по которому день собирается.
     */
    fun addToRoutine(item: ScheduleItem) {
        viewModelScope.launch {
            if (routineItems.value.any { sameDeed(it, item) }) return@launch
            routine.add(
                RoutineItem(
                    title = item.title,
                    startTime = item.startTime,
                    endTime = item.endTime,
                    icon = item.icon,
                )
            )
        }
    }

    /**
     * Убирает дело из списка дел. Сам сегодняшний блок остаётся: человек просил
     * не собирать это дело впредь, а не стереть то, что уже стоит в дне.
     */
    fun removeFromRoutine(item: ScheduleItem) {
        viewModelScope.launch {
            routineItems.value.filter { sameDeed(it, item) }.forEach { routine.delete(it.id) }
        }
    }

    /**
     * Напоминания дел: колокольчик в карточке должен знать, что уже стоит.
     * Ключ — номер дела, потому что напоминание у дела одно: второе к тому же
     * делу — это правка первого, а не ещё одно уведомление.
     */
    val itemReminders = reminders.forItems()
        .map { list -> list.associateBy { requireNotNull(it.itemId) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Ставит напоминание о деле и заводит будильник. Прежнее у этого дела
     * снимается: напоминание у дела одно, второе к тому же делу — правка
     * первого, а не ещё одно уведомление. Пустое [remind] снимает и не ставит
     * ничего: строку напоминания стёрли — значит, напоминать не надо.
     */
    private suspend fun applyRemind(
        context: Context,
        itemId: Long,
        title: String,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime?,
        icon: BlockIcon?,
        remind: RemindAt?,
        silent: Boolean,
        sound: String?,
        soundTitle: String?,
    ) {
        dropReminders(context, listOf(itemId))
        if (remind == null) return

        val reminder = reminderOf(
            title = title,
            eventDate = date,
            eventStart = start,
            eventEnd = end,
            remind = remind,
            icon = icon,
            silent = silent,
            sound = sound,
            soundTitle = soundTitle,
            itemId = itemId,
        )
        val id = reminders.add(reminder)
        ReminderAlarms.schedule(context, reminder.copy(id = id))
    }

    fun delete(context: Context, id: Long) {
        viewModelScope.launch {
            schedule.delete(id)
            dropReminders(context, listOf(id))
        }
    }

    /**
     * Снимает напоминания о названных делах — вместе с будильниками.
     *
     * Читается из базы, а не из [itemReminders]: тот поток жив, только пока на
     * экран смотрят, а дело могут удалить и в тот же миг уйти назад.
     */
    private suspend fun dropReminders(context: Context, itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
        reminders.forItems(itemIds).forEach { old ->
            ReminderAlarms.cancel(context, old.id)
            reminders.delete(old)
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                AskyaDayViewModel(
                    container.scheduleRepository,
                    container.routineRepository,
                    container.dayRepository,
                    container.reminderRepository,
                )
            }
        }
    }
}
/**
 * Дело списка и дело дня — одно и то же, когда совпали название и время начала.
 *
 * Номера, связывающего копию с источником, у дел нет и заводить его не стоит:
 * день и список живут порознь, правки в одном другого не касаются, и такая
 * связь пережила бы смысл, который в неё вкладывают. Название со временем —
 * ровно то, чем дело в списке и является.
 */
internal fun sameDeed(routine: RoutineItem, item: ScheduleItem): Boolean =
    routine.startTime == item.startTime &&
        routine.title.trim().equals(item.title.trim(), ignoreCase = true)
