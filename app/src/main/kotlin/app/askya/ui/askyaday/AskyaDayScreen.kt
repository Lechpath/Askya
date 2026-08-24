package app.askya.ui.askyaday

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.R
import app.askya.app.appContainer
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.remindAt
import app.askya.domain.model.DayPlan
import app.askya.domain.plan.DayLayout
import app.askya.domain.plan.RoutineDayComposer
import app.askya.ui.components.AskyaAsk
import app.askya.ui.components.BlockCard
import app.askya.ui.components.CardAction
import app.askya.ui.components.CardContent
import app.askya.ui.components.CardDialog
import app.askya.ui.components.BreathingFlower
import app.askya.ui.components.CardGrid
import app.askya.ui.components.DayPart
import app.askya.ui.components.DayPartTitle
import app.askya.ui.components.EmptyState
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.blockIconOf
import app.askya.ui.components.formatRange
import app.askya.ui.components.formatRemind
import app.askya.ui.components.formatRussianDate
import app.askya.ui.theme.Accent
import app.askya.ui.theme.Ink
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.IconButton
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import app.askya.reminders.ReminderAlarms
import androidx.compose.foundation.layout.Spacer

/**
 * Дни листаются страницами вокруг сегодняшней. Диапазон конечный, но заведомо
 * больше любого осмысленного листания — пейджеру нужно число страниц, а не
 * бесконечность.
 */
private const val ANCHOR_PAGE = 10_000
private const val PAGE_COUNT = 20_001

/**
 * AskyaDay — расписание дня. Свайп влево-вправо переводит на соседний день,
 * иконка колокольчика в шапке — единственный вход в напоминания.
 */
@Composable
fun AskyaDayScreen(
    onOpenMenu: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val viewModel: AskyaDayViewModel = viewModel(factory = AskyaDayViewModel.factory(appContainer()))
    val now by viewModel.now.collectAsStateWithLifecycle()
    val hasRoutine by viewModel.hasRoutine.collectAsStateWithLifecycle()
    val composing by viewModel.composing.collectAsStateWithLifecycle()
    val composed by viewModel.composed.collectAsStateWithLifecycle()
    val composeError by viewModel.composeError.collectAsStateWithLifecycle()

    // Точка отсчёта фиксируется на время жизни экрана: если пересчитывать её
    // в полночь, страницы разъехались бы под пальцем.
    val anchor = remember { LocalDate.now() }
    val pagerState = rememberPagerState(initialPage = ANCHOR_PAGE) { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    fun dateOf(page: Int): LocalDate = anchor.plusDays((page - ANCHOR_PAGE).toLong())

    val visibleDate = dateOf(pagerState.currentPage)

    // Заполняется только тот день, на котором палец остановился: иначе
    // распорядок разворачивался бы в соседние дни при каждом подглядывании.
    val settledDate = dateOf(pagerState.settledPage)
    LaunchedEffect(settledDate) { viewModel.ensureGenerated(settledDate) }

    // null — диалога нет; Editing(null) — новое дело.
    var editing by remember { mutableStateOf<Editing?>(null) }

    // Очистка дня необратима и трогает в том числе сделанное, поэтому
    // спрашивается подтверждение — тут нет «отменить».
    var clearing by remember { mutableStateOf<LocalDate?>(null) }

    // Открытое окно «взять из списка дел». Помнит и день, и то, что в нём уже
    // стоит: окно должно отметить взятое раньше, а спрашивать об этом базу
    // второй раз незачем — день уже прочитан страницей, из которой позвали.
    var picking by remember { mutableStateOf<Picking?>(null) }

    val itemReminders by viewModel.itemReminders.collectAsStateWithLifecycle()
    // Список дел нужен раскрытой карточке: по нему видно, лежит ли это дело
    // в списке, и что предлагать — добавить или убрать.
    val routineItems by viewModel.routineItems.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Разрешение на уведомления спрашивается в момент, когда оно понадобилось,
    // а не на первом запуске: просить заранее — значит просить у человека,
    // который ещё не знает, о чём речь.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Отказали — напоминание всё равно записывается, просто не покажется. */ }

    Box(modifier = Modifier.fillMaxSize()) {

    ScreenScaffold(
        title = "AskyaDay",
        onNavigationClick = onOpenMenu,
        actions = {
            // Цветок Askya = «собери этот день». Пока собирает, он тут же
            // дышит: отдельный индикатор занял бы место, а ждать всё равно
            // нужно смотреть в ту точку, на которую нажал.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !composing) { viewModel.composeDay(visibleDate) },
                contentAlignment = Alignment.Center,
            ) {
                if (composing) {
                    BreathingFlower(size = 26.dp)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_flower),
                        contentDescription = "Собрать день",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            // Список дел живёт здесь, а не в настройках: день собирается из
            // него, и заводить его надо там же, где день смотрят.
            HeaderIcon(
                icon = Icons.Outlined.Checklist,
                contentDescription = "Список дел",
                onClick = onOpenTasks,
            )
            HeaderIcon(
                icon = Icons.Outlined.Notifications,
                contentDescription = "Напоминания",
                onClick = onOpenReminders,
            )
        },
        floatingActionButton = {
            // Кнопка подписана словом, а не одной буквой: «+ new card» говорит,
            // что произойдёт, а каллиграфическая A требовала догадки. Чёрное
            // пятно на кремовом держит угол экрана, а коралловое на нём —
            // единственное место в приложении, где акцент лежит на тёмном.
            ExtendedFloatingActionButton(
                onClick = { editing = Editing(null) },
                shape = RoundedCornerShape(percent = 50),
                containerColor = Ink,
                contentColor = Accent,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "new card",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val date = dateOf(page)
            // null значит «база ещё не ответила», а не «блоков нет». Разница
            // видна на холодном старте: без неё экран секунду уверяет, что
            // день пуст, хотя расписание есть.
            val plan by remember(date) { viewModel.plan(date) }
                .collectAsStateWithLifecycle(initialValue = null)

            DayContent(
                date = date,
                plan = plan,
                // Подсветка «сейчас» имеет смысл только у сегодняшнего дня.
                currentBlockId = if (date == now.toLocalDate()) {
                    plan?.currentBlock(now.toLocalTime())?.id
                } else {
                    null
                },
                isToday = date == now.toLocalDate(),
                hasRoutine = hasRoutine,
                // Реплика и ошибка сборки показываются только на том дне,
                // который человек и просил собрать.
                composed = composed.takeIf { date == visibleDate },
                composeError = composeError.takeIf { date == visibleDate },
                onGoToToday = { scope.launch { pagerState.animateScrollToPage(ANCHOR_PAGE) } },
                onClearDay = { clearing = date },
                onFillFromRoutine = { viewModel.fillFromRoutine(date) },
                onPickFromRoutine = { picking = Picking(date, plan?.schedule.orEmpty()) },
                onComposeDay = { viewModel.composeDay(date) },
                onDismissComposed = viewModel::dismissComposed,
                onOpen = { item -> editing = Editing(item) },
                remindedIds = itemReminders.filterValues { it.enabled }.keys,
                onAddNote = { item -> editing = Editing(item, straightToNote = true) },
                onRemind = { item ->
                    if (ReminderAlarms.needsPermission()) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // Колокольчик открывает ту же карточку, что и тап по делу,
                    // сразу на строке напоминания: время и название в ней уже
                    // написаны, и спрашивать их заново незачем.
                    editing = Editing(item, straightToRemind = true)
                },
            )
        }
    }

    clearing?.let { date ->
        AskyaAsk(
            title = "Очистить день?",
            text = "Расписание на ${formatRussianDate(date)} будет стёрто целиком, " +
                "включая отмеченное сделанным. Вернуть не получится.",
            confirm = "Очистить",
            onConfirm = {
                viewModel.clearDay(context, date)
                clearing = null
            },
            onDismiss = { clearing = null },
        )
    }

    picking?.let { pick ->
        RoutinePickCard(
            items = routineItems,
            inDay = { item -> pick.existing.any { sameDeed(item, it) } },
            onDismiss = { picking = null },
            onAdd = { chosen ->
                viewModel.addFromRoutine(pick.date, chosen)
                picking = null
            },
        )
    }

    editing?.let { current ->
        val listed = current.item?.let { item -> routineItems.any { sameDeed(it, item) } } == true
        val reminder = current.item?.let { itemReminders[it.id] }
        CardDialog(
            card = current.item?.let {
                CardContent(
                    title = it.title,
                    time = formatRange(it.startTime, it.endTime),
                    note = it.note,
                    remind = reminder?.let { set -> formatRemind(set.remindAt) }.orEmpty(),
                    silent = reminder?.silent == true,
                    sound = reminder?.sound,
                    soundTitle = reminder?.soundTitle,
                    done = it.done,
                    icon = it.icon,
                )
            },
            startAtNote = current.straightToNote,
            startAtRemind = current.straightToRemind,
            // Напоминание — строка этой же карточки: о том, когда напомнить,
            // думают там же, где о самом деле, а не отдельным разговором.
            withRemind = true,
            onDismiss = { editing = null },
            onToggleDone = current.item?.let { item ->
                {
                    viewModel.toggleDone(item)
                    editing = null
                }
            },
            onDelete = current.item?.let { item ->
                {
                    viewModel.delete(context, item.id)
                    editing = null
                }
            },
            // Дело дня можно отправить в список дел — и убрать оттуда же.
            // Действие живёт в раскрытой карточке, а не в маленькой: в неё
            // заглядывают, когда про дело думают, а не когда листают день.
            extra = current.item?.let { item ->
                CardAction(
                    icon = Icons.Outlined.Checklist,
                    label = if (listed) "Из списка" else "В список",
                    accent = listed,
                    onClick = {
                        if (listed) viewModel.removeFromRoutine(item) else viewModel.addToRoutine(item)
                    },
                )
            },
            onSave = { draft ->
                if (draft.remind != null && ReminderAlarms.needsPermission()) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.save(
                    context = context,
                    existing = current.item,
                    date = visibleDate,
                    title = draft.title,
                    start = draft.start,
                    end = draft.end,
                    note = draft.note,
                    icon = draft.icon,
                    remind = draft.remind,
                    silent = draft.silent,
                    sound = draft.sound,
                    soundTitle = draft.soundTitle,
                )
            },
        )
    }
    }
}

/** Открытое окно выбора дел: в какой день кладём и что в нём уже стоит. */
private data class Picking(val date: LocalDate, val existing: List<ScheduleItem>)

/** Открытый диалог. [item] = null — дело ещё не создано. */
private data class Editing(
    val item: ScheduleItem?,
    val straightToNote: Boolean = false,
    val straightToRemind: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayContent(
    date: LocalDate,
    plan: DayPlan?,
    currentBlockId: Long?,
    isToday: Boolean,
    hasRoutine: Boolean,
    composed: DayLayout?,
    composeError: String?,
    onGoToToday: () -> Unit,
    onClearDay: () -> Unit,
    onFillFromRoutine: () -> Unit,
    onPickFromRoutine: () -> Unit,
    onComposeDay: () -> Unit,
    onDismissComposed: () -> Unit,
    onOpen: (ScheduleItem) -> Unit,
    onAddNote: (ScheduleItem) -> Unit,
    onRemind: (ScheduleItem) -> Unit,
    remindedIds: Set<Long>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 4.dp,
            // Место под кнопку: последний блок иначе прячется под ней.
            bottom = 96.dp,
        ),
        // Свой отступ строки задаёт ритм списка; общий интервал только
        // отделяет шапку и карточку режима от расписания.
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            // Слова переносятся на вторую строку, когда не помещаются: их тут
            // до трёх, и рядом с длинной датой они в узкий телефон не встают.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = formatRussianDate(date),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                // Возврат к сегодня: пролистать назад тридцать дней пальцем — не дело.
                if (!isToday) {
                    TextButton(onClick = onGoToToday) { Text("Сегодня") }
                }
                // Взять дела из списка — способ собрать день по одному делу.
                // Стоит здесь же, у даты: собирают этот день, а не вообще.
                // На пустом дне не дублируется — там оно и так предложено.
                if (hasRoutine && plan?.schedule?.isNotEmpty() == true) {
                    TextButton(onClick = onPickFromRoutine) { Text("Взять из списка") }
                }
                // Стереть день целиком. Стоит рядом с датой, а не в шапке:
                // действие относится к этому дню, а шапка общая для всех.
                if (plan?.schedule?.isNotEmpty() == true) {
                    TextButton(onClick = onClearDay) { Text("Очистить") }
                }
            }
        }

        // Слово Askya про собранный день. Стоит под датой, а не всплывает
        // снизу: это объяснение к списку, который человек прямо сейчас читает,
        // и исчезать само оно не должно.
        composed?.let { layout ->
            item {
                ComposedNote(
                    text = layout.comment.ifBlank {
                        if (layout.source == RoutineDayComposer.SOURCE) {
                            "Собрать не вышло — день заполнен по распорядку."
                        } else {
                            "День собран."
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    onDismiss = onDismissComposed,
                )
            }
        }

        composeError?.let { message ->
            item {
                ComposedNote(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    onDismiss = onDismissComposed,
                )
            }
        }

        // Пока база не ответила, под датой не рисуется ничего: пустой экран
        // на долю секунды честнее, чем неверное «блоков нет».
        if (plan == null) return@LazyColumn

        if (plan.schedule.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        title = if (isToday) "Сегодня пока нет блоков." else "На этот день блоков нет.",
                        hint = "Добавь первый.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Разные предложения, а не одно: копия распорядка
                    // бесплатна и мгновенна, сборка тратит ключ и думает,
                    // а выбор из списка — это день, собранный по одному делу.
                    // Человек должен выбирать это сам.
                    TextButton(
                        onClick = onComposeDay,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Собрать день")
                    }
                    if (hasRoutine) {
                        TextButton(onClick = onFillFromRoutine) {
                            Text("Заполнить по распорядку")
                        }
                        TextButton(onClick = onPickFromRoutine) {
                            Text("Взять из списка")
                        }
                    }
                }
            }
        } else {
            // День разложен по частям: утро, день, вечер. Список из двадцати
            // подряд идущих карточек не отвечает на вопрос «что у меня
            // вечером» — а именно его и задают, открывая расписание.
            //
            // Внутри части карточки листаются вбок, страница — вниз: части
            // всегда на виду, а длинное утро не сдвигает вечер за экран.
            DayPart.entries.forEach { part ->
                val items = plan.schedule.filter { DayPart.of(it.startTime) == part }
                if (items.isEmpty()) return@forEach

                item(key = part.name) {
                    PartRow(
                        part = part,
                        items = items,
                        currentBlockId = currentBlockId,
                        remindedIds = remindedIds,
                        onOpen = onOpen,
                        onAddNote = onAddNote,
                        onRemind = onRemind,
                    )
                }
            }
        }
    }
}

/**
 * Реплика про собранный день. Тап по ней убирает её — крестик ради одной
 * строки был бы тяжелее самой строки.
 */
@Composable
private fun ComposedNote(text: String, color: Color, onDismiss: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
            .padding(bottom = 8.dp),
    )
}

/**
 * Часть дня: подпись и карточки под ней.
 *
 * Карточки и сетка общие со списком дел — [BlockCard] и [CardGrid]: день и
 * список, из которого он собирается, показываются одинаково.
 */
@Composable
private fun PartRow(
    part: DayPart,
    items: List<ScheduleItem>,
    currentBlockId: Long?,
    remindedIds: Set<Long>,
    onOpen: (ScheduleItem) -> Unit,
    onAddNote: (ScheduleItem) -> Unit,
    onRemind: (ScheduleItem) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        DayPartTitle(part.title)
        CardGrid(items) { item, cardModifier ->
            DayCard(
                item = item,
                isCurrent = item.id == currentBlockId,
                hasReminder = item.id in remindedIds,
                onClick = { onOpen(item) },
                onAddNote = { onAddNote(item) },
                onRemind = { onRemind(item) },
                modifier = cardModifier,
            )
        }
    }
}

/** Карточка дела в дне: общая карточка плюс заметка и напоминание снизу. */
@Composable
private fun DayCard(
    item: ScheduleItem,
    isCurrent: Boolean,
    hasReminder: Boolean,
    onClick: () -> Unit,
    onAddNote: () -> Unit,
    onRemind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        item.done -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onBackground
    }
    val metaColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    BlockCard(
        icon = blockIconOf(item.title, item.icon),
        time = formatRange(item.startTime, item.endTime),
        title = item.title,
        titleColor = titleColor,
        metaColor = metaColor,
        onClick = onClick,
        modifier = modifier,
    ) {
        if (item.done) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Сделано",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // Плюс уже заполненной заметки не заводит новую, а открывает
        // её: иначе второе нажатие молча затирало бы написанное.
        IconButton(onClick = onAddNote, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = if (item.note.isBlank()) "Добавить заметку" else "Заметка",
                tint = if (item.note.isBlank()) metaColor else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        // Поставленное напоминание видно по залитому колокольчику:
        // иначе узнать, стоит ли оно, можно только нажав.
        IconButton(onClick = onRemind, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (hasReminder) {
                    Icons.Outlined.Notifications
                } else {
                    Icons.Outlined.NotificationsNone
                },
                contentDescription = if (hasReminder) "Напоминание стоит" else "Напомнить",
                tint = if (hasReminder) MaterialTheme.colorScheme.primary else metaColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
