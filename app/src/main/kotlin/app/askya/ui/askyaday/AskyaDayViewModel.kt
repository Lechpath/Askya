package app.askya.ui.askyaday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.content.Context
import app.askya.app.AppContainer
import app.askya.data.entity.DeedTask
import app.askya.data.entity.RoutineItem
import app.askya.data.entity.ScheduleItem
import app.askya.data.repository.DayRepository
import app.askya.data.repository.DeedTaskRepository
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.RoutineRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.data.entity.reminderOf
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.DayPlan
import app.askya.domain.model.RemindAt
import app.askya.domain.plan.DayLayout
import app.askya.domain.plan.sameDeed
import app.askya.reminders.ReminderAlarms
import app.askya.reminders.dropReminders
import app.askya.reminders.moveReminder
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
    private val deedTasks: DeedTaskRepository,
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
     * Списки всех дел этого дня, разложенные по делам.
     *
     * Одним потоком на день, а не потоком на дело: маленькая карточка
     * показывает «3 из 7», и подписка на каждую из полутора десятков карточек
     * означала бы полтора десятка запросов ради одной цифры.
     */
    fun tasks(date: LocalDate): Flow<Map<Long, List<DeedTask>>> =
        deedTasks.tasksOn(date).map { all -> all.groupBy { it.deedId } }

    /**
     * Одно дело по номеру. Нужно шторке: она называет номер, а не дело, и
     * искать его в потоке дня значило бы ждать, пока день соберётся.
     */
    suspend fun deed(id: Long): ScheduleItem? = schedule.get(id)

    /**
     * Дописать в список дела: одна отправка — столько строк, сколько написали
     * или вставили. Разбор разметки живёт в репозитории.
     */
    fun addTasks(deedId: Long, source: String) {
        if (deedId <= 0L || source.isBlank()) return
        viewModelScope.launch { deedTasks.addLines(deedId, source) }
    }

    fun toggleTask(task: DeedTask) {
        viewModelScope.launch { deedTasks.toggle(task) }
    }

    /** Убрать строку — в корзину на сутки, как везде. */
    fun removeTask(id: Long) {
        viewModelScope.launch { deedTasks.remove(id) }
    }

    fun clearDoneTasks(deedId: Long) {
        viewModelScope.launch { deedTasks.clearDone(deedId) }
    }

    /**
     * Разворачивает список дел в день при первом открытии — тем же сборщиком,
     * что и цветок в шапке. Что при этом происходит с прошлыми днями и с
     * пустым списком, решено в [DayRepository.ensureComposed].
     */
    fun ensureGenerated(date: LocalDate) {
        viewModelScope.launch { day.ensureComposed(date) }
    }

    /**
     * Кладёт в день выбранные дела списка — способ собрать день по одному
     * делу, между «собрать целиком» и «написать руками».
     *
     * Что уже стоит в дне, отсеивает окно выбора: правило «то же дело» ([sameDeed])
     * живёт здесь, рядом с экраном, и второй его копии в базе не нужно.
     */
    fun addFromRoutine(date: LocalDate, items: List<RoutineItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch { routine.addToDay(date, items) }
    }

    /**
     * Собрать день заново — по нажатию на цветок.
     *
     * То же самое, что происходит само при первом открытии дня
     * ([ensureGenerated]); разница только в том, что здесь не смотрят, собирали
     * этот день раньше или нет. Об этом и просят, нажимая: пересобрать уже
     * собранное.
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
     * Отметка «день собран» не снимается: без неё список дел развернулся бы
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
            dropReminders(context, reminders, ids)
        }
    }

    fun toggleDone(item: ScheduleItem) {
        viewModelScope.launch { schedule.setDone(item.id, !item.done) }
    }

    /**
     * Отметить дело сделанным по номеру — так возвращаются из-за моста.
     *
     * Не переключатель, а «сделано»: человек ответил «да» на «отметить?», и
     * снимать этим отметку с уже отмеченного дела было бы противоположным
     * тому, что он сказал.
     */
    fun markDone(id: Long) {
        viewModelScope.launch { schedule.setDone(id, true) }
    }

    /**
     * Меняет два дела местами: каждое забирает время другого.
     *
     * Так работает перетаскивание карточки в расписании. Меняется именно время,
     * а не порядок строк: в дне порядок и есть время, и переставленное дело,
     * оставшееся при своих часах, вернулось бы на прежнее место при первой же
     * перерисовке.
     *
     * Конец переезжает вместе с началом. Дело длиной в два часа, положенное на
     * получасовое, стало бы получасовым — и человек, поменявший местами обед и
     * созвон, потерял бы полтора часа, ни разу об этом не спросив.
     *
     * Напоминания едут следом ([moveReminder]): «за пятнадцать минут» — это про
     * дело, а не про час, в который оно раньше стояло.
     */
    fun swapTimes(context: Context, one: ScheduleItem, other: ScheduleItem) {
        if (one.id == other.id) return
        viewModelScope.launch {
            schedule.save(one.copy(startTime = other.startTime, endTime = other.endTime))
            schedule.save(other.copy(startTime = one.startTime, endTime = one.endTime))
            moveReminder(context, reminders, one.id, one.date, other.startTime, other.endTime)
            moveReminder(context, reminders, other.id, other.date, one.startTime, one.endTime)
        }
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
        link: String?,
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
                        link = link,
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
                        link = link,
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
            val added = RoutineItem(
                title = item.title,
                startTime = item.startTime,
                endTime = item.endTime,
                icon = item.icon,
            )
            routine.add(added)
            // И сразу в уже собранные дни впереди: «будет в каждом новом» для
            // человека значит и завтрашний день, если он в него уже заглянул.
            // В этом дне дело уже стоит — его же отсюда и взяли.
            routine.applyToDays(added)
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
        dropReminders(context, reminders, listOf(itemId))
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

    /**
     * Убрать дело — в корзину на сутки, а не из базы вон.
     *
     * Напоминание при этом снимается сразу и насовсем: убранное дело не должно
     * звонить, и ждать суток на это незачем. Возвращённое дело приходит без
     * него — так честнее, чем воскрешать будильник, о котором человек за эти
     * минуты успел забыть.
     */
    fun remove(context: Context, id: Long) {
        viewModelScope.launch {
            schedule.remove(id)
            dropReminders(context, reminders, listOf(id))
        }
    }

    /** Вернуть убранное — то, что предлагает полоска внизу экрана. */
    fun restore(id: Long) {
        viewModelScope.launch { schedule.restore(id) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                AskyaDayViewModel(
                    container.scheduleRepository,
                    container.routineRepository,
                    container.dayRepository,
                    container.reminderRepository,
                    container.deedTaskRepository,
                )
            }
        }
    }
}
