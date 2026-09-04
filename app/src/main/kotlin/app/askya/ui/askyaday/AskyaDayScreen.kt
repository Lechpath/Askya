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
import androidx.compose.material.icons.outlined.CalendarMonth
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.askya.data.entity.DeedTask
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.remindAt
import app.askya.domain.model.DayPlan
import app.askya.domain.plan.DayLayout
import app.askya.ui.components.AskyaAsk
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.askya.bridges.BridgeApps
import app.askya.bridges.DeedTarget
import app.askya.bridges.deedTarget
import app.askya.data.entity.Bridge
import app.askya.data.repository.Trash
import app.askya.domain.model.DeedLink
import app.askya.domain.model.LinkKind
import app.askya.ui.components.AskyaAsk
import app.askya.ui.components.BlockCard
import app.askya.ui.components.LinkChoice
import app.askya.ui.components.rememberLinkChoices
import app.askya.ui.components.CardAction
import app.askya.ui.components.CardContent
import app.askya.ui.components.CardTask
import app.askya.ui.components.CardDialog
import app.askya.ui.components.BreathingIcon
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import app.askya.ui.components.CardHeight
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Дни листаются страницами вокруг сегодняшней. Диапазон конечный, но заведомо
 * больше любого осмысленного листания — пейджеру нужно число страниц, а не
 * бесконечность.
 */
private const val ANCHOR_PAGE = 10_000
private const val PAGE_COUNT = 20_001

/**
 * Страница пейджера для этой даты.
 *
 * Больше двадцати семи лет в каждую сторону пейджер не держит, и календарь не
 * должен уводить палец за край: дальний край даёт крайнюю страницу, а не
 * прыжок в никуда.
 */
private fun pageOf(anchor: LocalDate, date: LocalDate): Int =
    (ANCHOR_PAGE + java.time.temporal.ChronoUnit.DAYS.between(anchor, date))
        .coerceIn(0L, (PAGE_COUNT - 1).toLong())
        .toInt()

/**
 * AskyaDay — расписание дня. Свайп влево-вправо переводит на соседний день,
 * иконка колокольчика в шапке — единственный вход в напоминания.
 */
@Composable
fun AskyaDayScreen(
    onOpenMenu: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenLink: (String) -> Unit = {},
    onOpenLived: () -> Unit = {},
    /** Дело, которое просят раскрыть на весь экран, — из шторки уведомлений. */
    openDeed: Long? = null,
    onDeedOpened: () -> Unit = {},
) {
    val container = appContainer()
    val context = LocalContext.current
    val viewModel: AskyaDayViewModel = viewModel(factory = AskyaDayViewModel.factory(container))
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

    // Собирается только тот день, на котором палец остановился: иначе список
    // дел разворачивался бы в соседние дни при каждом подглядывании.
    val settledDate = dateOf(pagerState.settledPage)
    // Разворачивать список дел в новый день — настройка: тот, кто ведёт день
    // руками, не должен каждый раз стирать развёрнутое.
    val autoFill by appContainer().settings.settings
        .collectAsStateWithLifecycle(initialValue = appContainer().settings.state.value)
    LaunchedEffect(settledDate, autoFill.autoFillDay) {
        if (autoFill.autoFillDay) viewModel.ensureGenerated(settledDate)
    }

    // null — диалога нет; Editing(null) — новое дело.
    var editing by remember { mutableStateOf<Editing?>(null) }

    // Свернули ли карточку со списком руками.
    //
    // Забывается при уходе с экрана, но переживает поворот: «свернуть» — это
    // «сейчас мне нужно расписание», а не настройка, и завтра оно должно
    // раскрыться снова. Само правило, раскрывать ли, — настройка
    // (`AppSettings.deedFullScreen`), и живёт она в другом месте по той же
    // причине: это два разных ответа на два разных вопроса.
    var collapsed by rememberSaveable { mutableStateOf(false) }

    // Списки дел этого дня. Отдельно от страницы пейджера: раскрытая карточка
    // живёт над ним и переживает свайп, а спрашивать у неё, какая страница
    // сейчас под ней, было бы кружным путём к тому же дню.
    val dayTasks by remember(visibleDate) { viewModel.tasks(visibleDate) }
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    // Расписание того дня, на котором палец остановился, — и по той же
    // причине, что и списки: раскрытая карточка живёт над пейджером.
    val visiblePlan by remember(settledDate) { viewModel.plan(settledDate) }
        .collectAsStateWithLifecycle(initialValue = null)

    // Открыта ли карточка месяца. Свайп доводит до соседних дней, а до «через
    // месяц» — нет, и календарь здесь ровно за этим.
    var calendar by remember { mutableStateOf(false) }

    // Выехали ли знаки действий из-за скобки в шапке. Забывается при уходе с
    // экрана намеренно: шапка должна открываться одинаково каждый раз, а не
    // помнить, чем человек занимался в прошлый вторник.
    var tools by remember { mutableStateOf(false) }

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
    val linkChoices = rememberLinkChoices()
    val bridges by container.bridgeRepository.bridges()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Ушли по мосту — запоминаем, куда и когда: вернувшись, Askya предложит
    // отметить дело и сама скажет, сколько его делали. Ничего в фоне при этом
    // не измеряется и никаких служб не поднимается — только два числа в памяти
    // экрана. Приложение убили, пока человек читал, — предложения не будет:
    // спрашивать про дело, о котором Askya уже забыла, было бы гаданием.
    var crossed by remember { mutableStateOf<Crossed?>(null) }
    var returning by remember { mutableStateOf<Crossed?>(null) }


    /**
     * Чем делается это дело.
     *
     * Своя привязка дела главнее: её поставили этому дню и этому делу. Нет
     * своей — берётся привязка одноимённой строки списка дел: повторяющееся
     * дело делается одним и тем же, и сказать это один раз в списке должно
     * хватить. По названию, а не через сборку дня: пересборка дня переписывает
     * дела заново, и привязка, протащенная через неё, терялась бы при каждой.
     */
    fun linkOf(item: ScheduleItem): String? =
        item.link ?: routineItems.firstOrNull { sameDeed(it, item) }?.link

    /** Чем делается это дело — внутри Askya или за её пределами. */
    fun targetOf(item: ScheduleItem): DeedTarget? = deedTarget(
        bridges = bridges,
        link = item.link,
        routineLink = routineItems.firstOrNull { sameDeed(it, item) }?.link,
        title = item.title,
        icon = item.icon,
    )

    /**
     * Уйти туда, чем дело делается.
     *
     * Внутрь — обычным переходом. Наружу — намерением системе; и только в этом
     * случае запоминается, что мы ушли: вернувшись из книги Askya не спросит
     * «отметить?», потому что из книги человек и не уходил.
     */
    fun cross(item: ScheduleItem) {
        when (val target = targetOf(item)) {
            null -> Unit
            is DeedTarget.Inside -> onOpenLink(target.link.store())
            is DeedTarget.Outside -> {
                if (BridgeApps.cross(context, target.bridge)) {
                    crossed = Crossed(item.id, item.title, System.currentTimeMillis())
                    scope.launch { container.bridgeRepository.markUsed(target.bridge.id) }
                }
            }
        }
    }

    // Askya снова на переднем плане. Ушли по мосту и вернулись — предложить
    // отметить дело. Меньше минуты не в счёт: столько занимает промах пальцем,
    // а не дело.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val left = crossed ?: return@LifecycleEventEffect
        crossed = null
        if (System.currentTimeMillis() - left.at >= MIN_AWAY_MS) returning = left
    }

    // Разрешение на уведомления спрашивается в момент, когда оно понадобилось,
    // а не на первом запуске: просить заранее — значит просить у человека,
    // который ещё не знает, о чём речь.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Отказали — напоминание всё равно записывается, просто не покажется. */ }

    /**
     * Дело со списком, которое стоит показать на весь экран.
     *
     * Идущее сейчас — первым: список заводят к делу, которым заняты, и открыть
     * вечерний, пока идёт утреннее, значило бы показать не то. Нет идущего —
     * ближайшее из тех, где ещё осталось несделанное; отмеченные дела и
     * дописанные до конца списки не в счёт, показывать в них нечего.
     */
    fun listedDeed(): ScheduleItem? {
        val moment = now.toLocalTime()
        val open = visiblePlan?.schedule.orEmpty()
            .filterNot { it.done }
            .filter { item -> dayTasks[item.id].orEmpty().any { !it.done } }
        if (open.isEmpty()) return null
        return open.firstOrNull { !it.startTime.isAfter(moment) && endOf(it) > moment }
            ?: open.minByOrNull { it.startTime }
    }

    // Позвали из шторки — раскрываем названное дело, чем бы человек ни был
    // занят до этого: он нажал на конкретный список и ждёт именно его.
    // «Свернули» при этом снимается: просьба свежее прошлого решения.
    LaunchedEffect(openDeed) {
        val id = openDeed ?: return@LaunchedEffect
        val item = viewModel.deed(id) ?: run {
            onDeedOpened()
            return@LaunchedEffect
        }
        collapsed = false
        editing = Editing(item, straightToList = true, full = true)
        onDeedOpened()
    }

    /**
     * Дело со списком открывается на весь экран само.
     *
     * Только сегодняшнее и только пока карточку не свернули: вчерашние списки
     * — это запись о том, что было, а свёрнутая карточка означает «сейчас мне
     * нужно расписание». Поверх уже раскрытой карточки ничего не открывается:
     * человек чем-то занят, и подменять открытое им — худшее, что можно
     * сделать.
     */
    LaunchedEffect(settledDate, dayTasks, visiblePlan, collapsed, autoFill.deedFullScreen) {
        if (!autoFill.deedFullScreen) return@LaunchedEffect
        if (collapsed || editing != null) return@LaunchedEffect
        if (settledDate != now.toLocalDate()) return@LaunchedEffect
        val item = listedDeed() ?: return@LaunchedEffect
        editing = Editing(item, straightToList = true, full = true)
    }

    Box(modifier = Modifier.fillMaxSize()) {

    ScreenScaffold(
        title = "AskyaDay",
        onNavigationClick = onOpenMenu,
        actions = {
            // Четыре знака шапки сложены за одну скобку. Порознь они значат
            // разное — собрать день, список дел, календарь, напоминания, — но
            // нужны все четверо изредка, а место в шапке занимали всегда.
            // Скобка отдаёт его обратно дате и делу: то, чем человек занят
            // каждый день, не должно тесниться рядом с тем, чем он занят раз в
            // неделю.
            //
            // Знаки выезжают из-под неё, а не всплывают списком: они и раньше
            // стояли в этом ряду, и человек, нажавший скобку, находит их там
            // же, где привык.
            AnimatedVisibility(
                visible = tools,
                enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Восход = «собери этот день». Единственный способ собрать его:
                    // прежде рядом стояли ещё две кнопки — «Собрать день» и «Заполнить
                    // по распорядку», — и обе делали ровно то же самое.
                    //
                    // Знак был цветком, пока цветок не стал открывать меню на всех
                    // экранах (см. ScreenHeader). Два цветка в одной шапке значили бы
                    // разное — слева лицо приложения, справа действие, — и знак
                    // перестал бы значить что-либо вовсе. Восход остался за действием:
                    // кнопка начинает день, которого ещё нет.
                    //
                    // Пока собирает, тот же восход тут же дышит: отдельный индикатор
                    // занял бы место, а ждать всё равно нужно смотреть в ту точку, на
                    // которую нажал.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !composing) { viewModel.composeDay(visibleDate) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (composing) {
                            BreathingIcon(
                                painter = painterResource(R.drawable.ic_sunrise),
                                contentDescription = "Собираю день",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_sunrise),
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
                        icon = Icons.Outlined.CalendarMonth,
                        contentDescription = "Календарь",
                        onClick = { calendar = true },
                    )
                    HeaderIcon(
                        icon = Icons.Outlined.Notifications,
                        contentDescription = "Напоминания",
                        onClick = onOpenReminders,
                    )
                }
            }

            // Скобка смотрит налево, пока знаки спрятаны, и разворачивается,
            // когда они выехали: по ней видно, куда они уедут обратно.
            val turn by animateFloatAsState(
                targetValue = if (tools) 180f else 0f,
                animationSpec = tween(220),
                label = "tools",
            )
            HeaderIcon(
                icon = Icons.Outlined.ChevronLeft,
                contentDescription = if (tools) "Спрятать действия" else "Действия",
                onClick = { tools = !tools },
                modifier = Modifier.rotate(turn),
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

            // Списки дел этой страницы. Своей подпиской, а не той, что у
            // раскрытой карточки: страниц под пальцем три, и цифра «3 из 7»
            // должна стоять на соседнем дне ещё до того, как палец на нём
            // остановится.
            val pageTasks by remember(date) { viewModel.tasks(date) }
                .collectAsStateWithLifecycle(initialValue = emptyMap())

            DayContent(
                date = date,
                plan = plan,
                // Подсветка «сейчас» имеет смысл только у сегодняшнего дня.
                currentBlockId = if (date == now.toLocalDate()) {
                    plan?.currentBlock(now.toLocalTime())?.id
                } else {
                    null
                },
                // Час нужен, чтобы понять, что уже позади: такие дела
                // карточка показывает ужатыми. У вчерашнего и завтрашнего дня
                // часа нет и быть не может — «позади» там либо всё, либо
                // ничего, и ужимать нечего.
                nowTime = if (date == now.toLocalDate()) now.toLocalTime() else null,
                isToday = date == now.toLocalDate(),
                hasRoutine = hasRoutine,
                // Реплика и ошибка сборки показываются только на том дне,
                // который человек и просил собрать.
                composed = composed.takeIf { date == visibleDate },
                composeError = composeError.takeIf { date == visibleDate },
                onGoToToday = { scope.launch { pagerState.animateScrollToPage(ANCHOR_PAGE) } },
                onClearDay = { clearing = date },
                onPickFromRoutine = { picking = Picking(date, plan?.schedule.orEmpty()) },
                onDismissComposed = viewModel::dismissComposed,
                // Сколько в списке дела сделано — цифрой на самой карточке.
                // Пусто у дел без списка: у большинства дел его и нет.
                taskCount = { id ->
                    pageTasks[id]?.takeIf { it.isNotEmpty() }
                        ?.let { rows -> "${rows.count { it.done }}/${rows.size}" }
                },
                onOpen = { item -> editing = Editing(item) },
                onToggleDone = viewModel::toggleDone,
                onSwap = { one, other -> viewModel.swapTimes(context, one, other) },
                hasTarget = { item -> targetOf(item) != null },
                onCross = ::cross,
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

    if (calendar) {
        MonthCard(
            today = now.toLocalDate(),
            initial = visibleDate,
            onOpenDay = { date ->
                calendar = false
                scope.launch { pagerState.animateScrollToPage(pageOf(anchor, date)) }
            },
            onLived = {
                calendar = false
                onOpenLived()
            },
            onNewCard = { date ->
                calendar = false
                scope.launch {
                    // Без анимации: карточка дела откроется сразу за прыжком, и
                    // пролистывать под ней полгода незачем. Сам прыжок нужен
                    // всё равно — новое дело записывается в тот день, который
                    // на экране (`date = visibleDate` в onSave).
                    pagerState.scrollToPage(pageOf(anchor, date))
                    editing = Editing(null)
                }
            },
            onDismiss = { calendar = false },
        )
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

    returning?.let { back ->
        AskyaAsk(
            title = back.title,
            text = away(System.currentTimeMillis() - back.at) + ". Отметить сделанным?",
            confirm = "Отметить",
            icon = Icons.Outlined.Check,
            danger = false,
            onConfirm = {
                viewModel.markDone(back.id)
                returning = null
            },
            onDismiss = { returning = null },
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
                    link = linkOf(it),
                )
            },
            linkChoices = linkChoices + bridgeChoices(bridges),
            onOpenLink = { chosen ->
                val target = current.item?.let { targetOf(it) }
                    ?: DeedLink.of(chosen)?.let { DeedTarget.Inside(it) }
                when (target) {
                    null -> Unit
                    is DeedTarget.Inside -> onOpenLink(target.link.store())
                    is DeedTarget.Outside -> current.item?.let { editing = null; cross(it) }
                }
            },
            startAtNote = current.straightToNote,
            startAtRemind = current.straightToRemind,
            startAtList = current.straightToList,
            fullScreen = current.full,
            // Свернуть — не то же самое, что закрыть: закрытая карточка
            // откроется снова сама, а свёрнутая уступает место расписанию до
            // конца захода.
            onCollapse = if (current.full) {
                {
                    collapsed = true
                    editing = null
                }
            } else {
                null
            },
            // Списки бывают только у дела дня: у нового дела ещё нет номера,
            // к которому их привязать, и строка списка в нём не показывается
            // вовсе — заведут дело, тогда и список.
            tasks = current.item?.let { item -> dayTasks[item.id].orEmpty().map(::cardTask) },
            onToggleTask = { row ->
                dayTasks[current.item?.id]
                    ?.firstOrNull { it.id == row.id }
                    ?.let(viewModel::toggleTask)
            },
            onRemoveTask = { row ->
                viewModel.removeTask(row.id)
                container.trash.remembered(Trash.Kind.DEED_ROW, row.id)
            },
            onClearDoneTasks = { current.item?.let { viewModel.clearDoneTasks(it.id) } },
            onAddTasks = { text -> current.item?.let { viewModel.addTasks(it.id, text) } },
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
                    viewModel.remove(context, item.id)
                    container.trash.remembered(Trash.Kind.DEED, item.id)
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
                    link = draft.link,
                )
            },
        )
    }
    }
}

/** Строка списка дела — тем видом, каким её знает карточка. */
private fun cardTask(task: DeedTask) = CardTask(id = task.id, text = task.text, done = task.done)

/**
 * Ушли по мосту: какое дело, как называется и когда ушли.
 *
 * Живёт в памяти экрана, а не в базе: это не запись о дне, а вопрос, который
 * задаётся один раз при возвращении. Приложение убили, пока человек читал, —
 * вопроса не будет, и это честнее, чем спрашивать про дело наугад.
 */
private data class Crossed(val id: Long, val title: String, val at: Long)

/** Меньше минуты — это промах пальцем, а не сделанное дело. */
private const val MIN_AWAY_MS = 60_000L

/** Открытое окно выбора дел: в какой день кладём и что в нём уже стоит. */
private data class Picking(val date: LocalDate, val existing: List<ScheduleItem>)

/**
 * Открытый диалог. [item] = null — дело ещё не создано.
 *
 * [full] — карточка занимает весь экран. Так открывается дело со списком: за
 * ним и заходят, а карточка в треть экрана заставила бы открыть его ещё раз.
 */
private data class Editing(
    val item: ScheduleItem?,
    val straightToNote: Boolean = false,
    val straightToRemind: Boolean = false,
    val straightToList: Boolean = false,
    val full: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayContent(
    date: LocalDate,
    plan: DayPlan?,
    currentBlockId: Long?,
    nowTime: LocalTime?,
    isToday: Boolean,
    hasRoutine: Boolean,
    composed: DayLayout?,
    composeError: String?,
    /** Сколько в списке дела сделано и сколько всего — «3/7». Нет списка — пусто. */
    taskCount: (Long) -> String?,
    onGoToToday: () -> Unit,
    onClearDay: () -> Unit,
    onPickFromRoutine: () -> Unit,
    onDismissComposed: () -> Unit,
    onOpen: (ScheduleItem) -> Unit,
    onAddNote: (ScheduleItem) -> Unit,
    onRemind: (ScheduleItem) -> Unit,
    onToggleDone: (ScheduleItem) -> Unit,
    onSwap: (ScheduleItem, ScheduleItem) -> Unit,
    remindedIds: Set<Long>,
    hasTarget: (ScheduleItem) -> Boolean,
    onCross: (ScheduleItem) -> Unit,
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
                    text = layout.comment.ifBlank { "День собран из списка дел." },
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
                        hint = if (hasRoutine) {
                            "Цветок в шапке соберёт день из списка дел."
                        } else {
                            "Добавь первый."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Под пустым днём — только «взять из списка»: собрать день
                    // целиком просит цветок в шапке, и второй кнопки о том же
                    // здесь нет намеренно. Одно действие — одно место.
                    if (hasRoutine) {
                        TextButton(
                            onClick = onPickFromRoutine,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
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
            // Что уже позади: время дела кончилось, а текущим оно не
            // считается. Такие карточки ужимаются — см. [PartRow].
            val passedIds = if (nowTime == null) {
                emptySet()
            } else {
                plan.schedule
                    .filter { it.id != currentBlockId && endOf(it) <= nowTime }
                    .mapTo(HashSet()) { it.id }
            }

            DayPart.entries.forEach { part ->
                val items = plan.schedule.filter { DayPart.of(it.startTime) == part }
                if (items.isEmpty()) return@forEach

                item(key = part.name) {
                    PartRow(
                        part = part,
                        items = items,
                        currentBlockId = currentBlockId,
                        passedIds = passedIds,
                        remindedIds = remindedIds,
                        onOpen = onOpen,
                        onAddNote = onAddNote,
                        onRemind = onRemind,
                        onToggleDone = onToggleDone,
                        onSwap = onSwap,
                        hasTarget = hasTarget,
                        onCross = onCross,
                        taskCount = taskCount,
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
 *
 * ## Размер говорит о времени
 *
 * Прошедшее и отмеченное сделанным ужато на треть; дело, которое идёт прямо
 * сейчас, чуть крупнее прочих, и по его краю ходит огонёк. Расписание от этого
 * читается не по одной карточке, а целиком: крупное — то, чем человек занят,
 * мелкое — то, что позади, обычное — то, что ещё будет.
 *
 * Прибавка нарочно маленькая. Карточка, полезшая на соседей, сломала бы строку
 * ради того же, что говорит и без этого: рядом с ней всё остальное меньше.
 *
 * ## Карточки меняются местами
 *
 * Зажать и перетащить на другую — дела обменяются временем. Именно временем, а
 * не местом в списке: расписание не список, порядок в нём и есть часы, и
 * «переставить, оставив время» означало бы не переставить вовсе.
 */
@Composable
private fun PartRow(
    part: DayPart,
    items: List<ScheduleItem>,
    currentBlockId: Long?,
    passedIds: Set<Long>,
    remindedIds: Set<Long>,
    onOpen: (ScheduleItem) -> Unit,
    onAddNote: (ScheduleItem) -> Unit,
    onRemind: (ScheduleItem) -> Unit,
    onToggleDone: (ScheduleItem) -> Unit,
    onSwap: (ScheduleItem, ScheduleItem) -> Unit,
    hasTarget: (ScheduleItem) -> Boolean,
    onCross: (ScheduleItem) -> Unit,
    taskCount: (Long) -> String?,
) {
    // Где какая карточка лежит. Спросить об этом больше некого: сетка сама
    // решает, что перенести на другую строку, и знает об этом только разметка.
    // Место считается в окне целиком, а не внутри сетки: список едет под
    // пальцем, пока карточку несут, и место, отмеренное от сетки, за это время
    // успевает соврать.
    val places = remember { mutableStateMapOf<Long, Rect>() }
    // Какую карточку несут, на сколько увели и на кого сейчас положат.
    var carried by remember { mutableStateOf<Long?>(null) }
    var shift by remember { mutableStateOf(Offset.Zero) }
    var landing by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.padding(top = 10.dp)) {
        DayPartTitle(part.title, part = part)
        CardGrid(items) { item, slot ->
            val current = item.id == currentBlockId
            val behind = item.done || item.id in passedIds
            val lifted = carried == item.id

            // Размер меняется на глазах, а не скачком: по тому, как карточка
            // ужимается, и видно, что галочка сработала. Дело, законченное
            // раньше времени, уходит к сделанным этим же движением.
            val size by animateFloatAsState(
                targetValue = when {
                    behind -> PASSED_SIZE
                    current -> CURRENT_SIZE
                    else -> 1f
                },
                animationSpec = tween(280),
                label = "size",
            )
            // Карточка, на которую сейчас положат, отступает. Это единственный
            // способ сказать «обмен произойдёт вот с этой», не рисуя рамок и не
            // двигая всю строку раньше времени.
            val aimed by animateFloatAsState(
                targetValue = if (landing == item.id) 0.92f else 1f,
                animationSpec = tween(120),
                label = "aimed",
            )

            DeedSlot(
                scale = size * aimed,
                lifted = lifted,
                shift = if (lifted) shift else Offset.Zero,
                modifier = slot
                    // Несомая карточка идёт поверх остальных: проехать под
                    // соседкой она не может — её ведут рукой.
                    .zIndex(if (lifted) 1f else 0f)
                    .onGloballyPositioned { places[item.id] = it.boundsInRoot() }
                    .pointerInput(item.id, items) {
                        // После долгого нажатия, а не сразу: короткий проход
                        // пальцем по расписанию — это листание дней, и отнимать
                        // его у пейджера нельзя.
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                carried = item.id
                                shift = Offset.Zero
                                landing = null
                            },
                            onDrag = { change, moved ->
                                change.consume()
                                shift += moved
                                val point = places[item.id]?.center?.plus(shift)
                                landing = point?.let { spot ->
                                    items.firstOrNull { other ->
                                        other.id != item.id &&
                                            places[other.id]?.contains(spot) == true
                                    }?.id
                                }
                            },
                            onDragEnd = {
                                val other = landing?.let { id -> items.firstOrNull { it.id == id } }
                                carried = null
                                shift = Offset.Zero
                                landing = null
                                if (other != null) onSwap(item, other)
                            },
                            onDragCancel = {
                                carried = null
                                shift = Offset.Zero
                                landing = null
                            },
                        )
                    },
            ) { cardModifier ->
                DayCard(
                    item = item,
                    isCurrent = current,
                    hasReminder = item.id in remindedIds,
                    onClick = { onOpen(item) },
                    onAddNote = { onAddNote(item) },
                    onRemind = { onRemind(item) },
                    onToggleDone = { onToggleDone(item) },
                    onOpenLink = if (hasTarget(item)) ({ onCross(item) }) else null,
                    tasks = taskCount(item.id),
                    modifier = if (current) {
                        cardModifier.runningGlow(MaterialTheme.colorScheme.primary)
                    } else {
                        cardModifier
                    },
                )
            }
        }
    }
}

/** Прошедшее дело ужато на треть, текущее — чуть крупнее прочих. */
private const val PASSED_SIZE = 0.67f
private const val CURRENT_SIZE = 1.06f

/** Несомая карточка приподнята: так видно, что она оторвалась от строки. */
private const val CARRIED_SIZE = 1.06f

/**
 * Место карточки в сетке и то, во что она в этом месте сжата.
 *
 * Размер меняется рисованием, а не пересборкой: карточка, заново разложенная
 * под меньшую высоту, стала бы другой карточкой — с другим числом строк в
 * названии и другими промежутками между кнопками. Ужать её целиком честнее:
 * это та же карточка, просто дальше от глаза.
 *
 * Место под ней ужимается вместе с ней — иначе прошедшее утро оставляло бы
 * после себя полосу пустоты в полную высоту.
 */
@Composable
private fun DeedSlot(
    scale: Float,
    lifted: Boolean,
    shift: Offset,
    modifier: Modifier,
    card: @Composable (Modifier) -> Unit,
) {
    card(
        modifier
            // Ширина приходит от сетки, высота задаётся здесь. Своей ширины
            // тут не назначается намеренно: сетка спрашивает у карточки, какой
            // ширины та хочет быть, и на этот вопрос приходит без границ —
            // жёстко взятая ширина оборачивалась бы в такую минуту
            // бесконечностью.
            .layout { measurable, constraints ->
                val height = CardHeight.roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(minHeight = height, maxHeight = height),
                )
                val room = (placeable.height * scale).roundToInt()
                layout(placeable.width, room) {
                    placeable.place(0, (room - placeable.height) / 2)
                }
            }
            .graphicsLayer {
                val shown = if (lifted) scale * CARRIED_SIZE else scale
                scaleX = shown
                scaleY = shown
                translationX = shift.x
                translationY = shift.y
                alpha = if (lifted) 0.94f else 1f
            },
    )
}

/**
 * Полоска света, идущая по краю карточки. Так помечено дело, которое идёт
 * сейчас.
 *
 * Краской его не пометить: краска уже занята — ею набраны время и название
 * текущего дела, и залив ею же карточку, мы получили бы третье значение одного
 * цвета. Движение не занято ничем и видно боковым зрением: карточка не кричит,
 * но её находишь, не читая.
 *
 * Полоска короткая и без краёв: она набрана из отрезков, гаснущих к обоим
 * концам, — начала и конца не видно, виден только светлый мазок. Карточка,
 * обведённая светом по всему периметру, — это уведомление, а здесь сказано
 * всего лишь «вот здесь ты сейчас».
 *
 * Ход и всплытие разведены по разным часам, и часы эти не кратны: полоска
 * выныривает то на одной стороне карточки, то на другой, и глаз не успевает
 * выучить место. Между всплытиями край тёмный — свет, горящий не переставая,
 * перестаёт значить хоть что-то.
 *
 * Всё это читается в отрисовке, а не в разметке: иначе экран пересобирался бы
 * шестьдесят раз в секунду ради одной светлой полоски.
 */
@Composable
private fun Modifier.runningGlow(color: Color): Modifier {
    val circling = rememberInfiniteTransition(label = "glow")
    val at = circling.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GLOW_CYCLE_MS, easing = LinearEasing)),
        label = "run",
    )
    val tide = circling.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GLOW_TIDE_MS, easing = LinearEasing)),
        label = "tide",
    )
    return drawWithCache {
        val gap = GLOW_GAP.toPx()
        val edge = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(-gap, -gap, size.width + gap, size.height + gap),
                    cornerRadius = CornerRadius(CARD_CORNER.toPx() + gap),
                ),
            )
        }
        val ride = PathMeasure().apply { setPath(edge, true) }
        val length = ride.length
        // Полоска не должна доставать сама себе до хвоста на короткой карточке.
        val trail = minOf(GLOW_TRAIL.toPx(), length / 3f)
        val step = trail / GLOW_STEPS
        val piece = Path()
        val halo = Stroke(width = GLOW_HALO.toPx(), cap = StrokeCap.Round)
        val core = Stroke(width = GLOW_CORE.toPx(), cap = StrokeCap.Round)
        onDrawWithContent {
            drawContent()
            val surfaced = surfacing(tide.value)
            if (surfaced <= 0.01f) return@onDrawWithContent
            val head = at.value * length
            repeat(GLOW_STEPS) { index ->
                val shade = surfaced * tapering((index + 0.5f) / GLOW_STEPS)
                if (shade <= 0.01f) return@repeat
                val from = (head - step * (index + 1)).mod(length)
                val to = from + step
                piece.reset()
                if (to <= length) {
                    ride.getSegment(from, to, piece, true)
                } else {
                    // Полоска перешла через ноль пути: два куска вместо одного.
                    ride.getSegment(from, length, piece, true)
                    ride.getSegment(0f, to - length, piece, true)
                }
                drawPath(piece, color.copy(alpha = shade * 0.16f), style = halo)
                drawPath(piece, color.copy(alpha = shade * 0.7f), style = core)
            }
        }
    }
}

/**
 * Яркость отрезка полоски: [along] — доля пути от головы (0) к хвосту (1).
 *
 * Синус гасит оба конца до нуля, степень отодвигает свет к середине. Резкого
 * края нет ни спереди, ни сзади: полоска не заканчивается, а истаивает.
 */
private fun tapering(along: Float): Float =
    sin(PI * along).toFloat().let { it * it }

/**
 * Насколько полоска сейчас над водой: [phase] — доля цикла всплытия.
 *
 * Волна поднимается и уходит, степень удлиняет тёмную часть: полоска дольше
 * отсутствует, чем светит, и каждое появление снова заметно.
 */
private fun surfacing(phase: Float): Float =
    ((sin(2 * PI * phase) * 0.5 + 0.5).toFloat()).let { it * it * it }

/** Тот же угол, что у карточки ([BlockCard]): полоска идёт по её краю. */
private val CARD_CORNER = 20.dp

/** Насколько полоска вынесена за край карточки, какой длины и толщины. */
private val GLOW_GAP = 3.dp
private val GLOW_TRAIL = 132.dp
private val GLOW_CORE = 1.5.dp
private val GLOW_HALO = 5.dp

/**
 * На сколько отрезков разбита полоска. Отрезок должен остаться коротким —
 * иначе видны ступеньки яркости; лишние отрезки — работа на каждый кадр.
 */
private const val GLOW_STEPS = 36

/** Круг за пять секунд: быстрее — мельтешит, медленнее — не читается. */
private const val GLOW_CYCLE_MS = 5200

/**
 * Всплытие короче круга и не кратно ему: место, где полоска появляется,
 * каждый раз другое.
 */
private const val GLOW_TIDE_MS = 3300

/**
 * Когда дело кончилось.
 *
 * Конца может не быть — тогда час от начала, тем же правилом, что у подсветки
 * текущего дела ([DayPlan.currentBlock]). Час, перешагнувший полночь, оказался
 * бы раньше начала: такое дело считаем идущим до конца суток.
 */
private fun endOf(item: ScheduleItem): LocalTime {
    val end = item.endTime ?: item.startTime.plusHours(1)
    return if (end <= item.startTime) LocalTime.MAX else end
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
    onToggleDone: () -> Unit,
    onOpenLink: (() -> Unit)? = null,
    /** «3/7» — сколько в списке дела сделано. Пусто у дел без списка. */
    tasks: String? = null,
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
        onIconClick = onOpenLink,
        // Знак дела с привязкой горит краской: по нему видно, что за делом
        // что-то стоит, ещё до того, как в него ткнули. Только знак — время
        // той же краской означало бы «это дело идёт сейчас».
        iconTint = if (onOpenLink != null) MaterialTheme.colorScheme.primary else null,
        // Галочка прямо на карточке, а не только в раскрытой.
        //
        // Дело чаще всего кончают раньше срока, и отмечать это надо там же,
        // где на него смотрят: путь «открыть карточку — нажать — закрыть» ради
        // одного касания человек не проходит, и день остаётся неотмеченным.
        // Нажатая галочка ужимает карточку до размера сделанных — по этому
        // движению и видно, что дело ушло к ним.
        //
        // В углу, а не в нижнем ряду: карточка шириной в треть экрана держит
        // внизу две кнопки, третья туда не встаёт.
        corner = {
            IconButton(onClick = onToggleDone, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = if (item.done) "Сделано" else "Отметить сделанным",
                    tint = if (item.done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        metaColor.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        modifier = modifier,
    ) {
        // Список — цифрой, а не третьей кнопкой: в карточке шириной в треть
        // экрана третья кнопка не встаёт, а «3/7» говорит ровно то, ради чего
        // на список и смотрят издали. Нажимать её отдельно не надо — карточка
        // и так открывается тапом, а список в ней стоит строкой.
        if (tasks != null) {
            Text(
                text = tasks,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
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

/**
 * Мосты строками выбора привязки — в той же кучке, что книги и списки.
 *
 * Отдельной группой «Мосты»: они ведут наружу, и человек должен видеть это до
 * нажатия, а не после того, как телефон переключился в другое приложение.
 */
private fun bridgeChoices(bridges: List<Bridge>): List<LinkChoice> = bridges.map { bridge ->
    LinkChoice(
        value = DeedLink(LinkKind.BRIDGE, bridge.id).store(),
        title = bridge.name.ifBlank { "Без названия" },
        group = "Мосты",
    )
}

/**
 * Сколько человека не было — словами, а не «00:47:13».
 *
 * Округляется до минут и часов: точность до секунды тут не значит ничего, а
 * читается хуже.
 */
private fun away(millis: Long): String {
    val minutes = (millis / 60_000L).toInt()
    if (minutes < 60) return "$minutes " + minuteWord(minutes)
    val hours = minutes / 60
    val rest = minutes % 60
    val head = "$hours " + hourWord(hours)
    return if (rest == 0) head else head + " $rest " + minuteWord(rest)
}

private fun minuteWord(value: Int): String = word(value, "минута", "минуты", "минут")

private fun hourWord(value: Int): String = word(value, "час", "часа", "часов")

/** Русский счёт: одна минута, две минуты, пять минут. */
private fun word(value: Int, one: String, few: String, many: String): String {
    val tail = value % 100
    if (tail in 11..14) return many
    return when (value % 10) {
        1 -> one
        2, 3, 4 -> few
        else -> many
    }
}
