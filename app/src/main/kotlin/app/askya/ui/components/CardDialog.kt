package app.askya.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.DeedDays
import app.askya.domain.model.Priority
import app.askya.domain.model.RemindAt
import app.askya.reminders.ReminderSound
import app.askya.reminders.ReminderSoundPreview
import app.askya.reminders.ReminderSounds
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.cardEdge
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Шаг правки. `VIEW` — карточка просто раскрыта; дальше по порядку: дата,
 * время, событие, дни недели, заметка, напоминание.
 */
private enum class CardStep { VIEW, DATE, TIME, TITLE, DAYS, NOTE, REMIND }

/**
 * Что показывает раскрытая карточка.
 *
 * Дата, время и напоминание приходят уже строками — теми самыми, которые
 * человек и увидит. Обратно они возвращаются разобранными, в [CardDraft]:
 * карточка знает, как читается напечатанное, а экран — что с этим делать.
 */
data class CardContent(
    val title: String,
    val time: String,
    val date: String = "",
    val note: String = "",
    val remind: String = "",
    val silent: Boolean = false,
    val sound: String? = null,
    val soundTitle: String? = null,
    val done: Boolean = false,
    val icon: BlockIcon? = null,
    val priority: Priority = Priority.NORMAL,
    /** Чем дело делается — как записано в колонке (`book:12`). */
    val link: String? = null,
    /** По каким дням недели дело повторяется. Пусто — каждый день. */
    val days: Set<DayOfWeek> = emptySet(),
)

/** Что человек написал и выбрал в карточке. */
data class CardDraft(
    val title: String,
    val start: LocalTime,
    val end: LocalTime?,
    val note: String,
    val icon: BlockIcon?,
    val priority: Priority,
    val date: LocalDate? = null,
    /** Пусто — напоминания нет; было — значит, его сняли. */
    val remind: RemindAt? = null,
    val silent: Boolean = false,
    /** Ссылка на мелодию. Пусто — обычный звук напоминания. */
    val sound: String? = null,
    val soundTitle: String? = null,
    /** Чем дело делается. Пусто — привязки нет или её сняли. */
    val link: String? = null,
    /** По каким дням недели дело повторяется. Пусто — каждый день. */
    val days: Set<DayOfWeek> = emptySet(),
)

/**
 * Ещё одно действие в раскрытой карточке — своё у каждого экрана. В дне это
 * «в список дел» и обратно; списку дел добавлять нечего.
 */
data class CardAction(
    val icon: ImageVector,
    val label: String,
    val accent: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Карточка дела, раскрытая поверх экрана.
 *
 * Не разговор с вопросами, как раньше, а та же карточка — только крупная. Тап
 * по делу должен показывать это дело, а не уводить в другой экран: человек
 * нажал на «Подъём», и «Подъём» перед ним, просто больше.
 *
 * Значки внизу — что с делом можно сделать: править, отметить сделанным,
 * убрать. Правка идёт по местам, а не сразу по всему: подсвечивается то, что
 * правится сейчас, остальное приглушено. Порядок — дата, время, событие, дни
 * недели, заметка, напоминание: так дело и думается, «когда и что», а дни,
 * заметка и напоминание нужны не всегда — и показываются только там, где
 * бывают.
 *
 * Знак дела в правку не входит: он угадывается по названию, и спрашивать про
 * него каждый раз — лишнее решение на каждое дело. Но догадка иногда мимо,
 * поэтому знак нажимается и открывает выбор.
 *
 * [card] = null означает новое дело: тогда карточка сразу открывается на
 * первой правке, а «сделано» и «удалить» не показываются — отмечать и убирать
 * ещё нечего.
 *
 * Диалог общий для расписания, списка дел и напоминаний. Что делу доступно,
 * решают необязательные части: [withNote] = false убирает заметку из правки
 * вовсе (у дела в списке её нет), [withPriority] = true добавляет выбор
 * важности, [withDate] = true — дату (у дела в дне она уже есть, а у
 * напоминания её надо назвать), [withRemind] = true — строку напоминания,
 * [onToggleDone], [onDelete] и [extra] = null убирают своё действие.
 *
 * Привязка — «чем делается дело» — приходит списком готовых строк
 * ([linkChoices]), а не запросом в базу: карточка о базе ничего не знает и не
 * должна, иначе один и тот же диалог пришлось бы учить пяти разделам. Пустой
 * список убирает строку привязки вовсе — у напоминания её нет.
 */
@Composable
fun CardDialog(
    card: CardContent?,
    onDismiss: () -> Unit,
    onSave: (CardDraft) -> Unit,
    startAtNote: Boolean = false,
    startAtRemind: Boolean = false,
    startAtList: Boolean = false,
    withNote: Boolean = true,
    withPriority: Boolean = false,
    withDays: Boolean = false,
    withDate: Boolean = false,
    withRemind: Boolean = false,
    onToggleDone: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    extra: CardAction? = null,
    linkChoices: List<LinkChoice> = emptyList(),
    onOpenLink: ((String) -> Unit)? = null,
    /**
     * Список задач внутри дела. `null` — «у этого экрана списков нет»: у дела
     * в распорядке и у напоминания их и не бывает. Пустой список — это «список
     * есть, но в нём пусто», и строка [TaskLine] в карточке уже стоит.
     */
    tasks: List<CardTask>? = null,
    onToggleTask: (CardTask) -> Unit = {},
    onRemoveTask: (CardTask) -> Unit = {},
    onClearDoneTasks: () -> Unit = {},
    onAddTasks: (String) -> Unit = {},
    /**
     * Карточка занимает весь экран.
     *
     * Так открывается дело со списком: список — это то, ради чего в дело и
     * заходят, и показывать его в трети экрана значит просить открыть его ещё
     * раз. [onCollapse] сворачивает карточку обратно к расписанию; пусто —
     * сворачивать некуда, и действие не показывается.
     */
    fullScreen: Boolean = false,
    onCollapse: (() -> Unit)? = null,
) {
    // Первое место правки: у напоминания это дата, у дела в дне — время.
    val firstStep = if (withDate) CardStep.DATE else CardStep.TIME

    var step by remember {
        mutableStateOf(
            when {
                card == null -> firstStep
                startAtNote && withNote -> CardStep.NOTE
                startAtRemind && withRemind -> CardStep.REMIND
                else -> CardStep.VIEW
            },
        )
    }
    // У новой карточки дата уже проставлена сегодняшняя: её имеют в виду чаще
    // всего, и заставлять писать «сегодня» руками не за что.
    var dateText by remember {
        mutableStateOf(card?.date?.ifBlank { null } ?: formatTypedDate(LocalDate.now()))
    }
    var timeText by remember { mutableStateOf(card?.time.orEmpty()) }
    var title by remember { mutableStateOf(card?.title.orEmpty()) }
    var note by remember { mutableStateOf(card?.note.orEmpty()) }
    var remindText by remember { mutableStateOf(card?.remind.orEmpty()) }
    var silent by remember { mutableStateOf(card?.silent == true) }
    var sound by remember { mutableStateOf(card?.sound) }
    var soundTitle by remember { mutableStateOf(card?.soundTitle) }
    var icon by remember { mutableStateOf(card?.icon) }
    var priority by remember { mutableStateOf(card?.priority ?: Priority.NORMAL) }
    var link by remember { mutableStateOf(card?.link) }
    var days by remember { mutableStateOf(card?.days ?: emptySet()) }

    // Выбор знака — не шаг правки, а отступление в сторону: он занимает
    // карточку целиком и возвращает обратно туда же, откуда его открыли.
    var picking by remember { mutableStateOf(false) }

    // Выбор мелодии — такое же отступление: список песен на телефоне длинный,
    // и строкой в карточке он не помещается.
    var pickingSound by remember { mutableStateOf(false) }

    // И выбор привязки: книг и заметок бывает под сотню.
    var pickingLink by remember { mutableStateOf(false) }

    // Открыт ли список задач. Он занимает карточку так же, как выбор знака, —
    // и по той же причине: строки в нём отмечают и дописывают, и делать это в
    // щель под заметкой было бы работой в замочную скважину.
    //
    // На весь экран карточку открывают ради него, поэтому там он открыт сразу.
    var listing by remember { mutableStateOf(tasks != null && (startAtList || fullScreen)) }

    BackHandler(
        onBack = {
            when {
                pickingSound -> pickingSound = false
                pickingLink -> pickingLink = false
                picking -> picking = false
                // На весь экран карточку открыли ради списка: закрывать в ней
                // сперва список, а потом карточку значило бы два «назад» там,
                // где человек ждёт одного.
                listing && !fullScreen -> listing = false
                onCollapse != null -> onCollapse()
                else -> onDismiss()
            }
        },
    )

    // Появление: без переключения флага animateFloatAsState стартовал бы уже
    // в цели и не анимировал ничего.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrim by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(200),
        label = "scrim",
    )
    // Карточка вырастает, а не проявляется: тап был по маленькой карточке, и
    // движение от неё к большой связывает одно с другим. Пружина с недолётом
    // даёт лёгкий доводчик в конце — рост читается как движение, а не как
    // подмена картинки.
    val grow by animateFloatAsState(
        targetValue = if (shown) 1f else 0.84f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
        label = "grow",
    )

    val range = parseTypedRange(timeText)
    val date = parseTypedDate(dateText)
    val remind = parseTypedRemind(remindText)
    // Пустая строка напоминания — это «не напоминать», а непонятная — ошибка:
    // «за 15 мнут» не должно молча превратиться в «никогда».
    val remindReady = remindText.isBlank() || remind != null
    val canFinish = range != null && title.isNotBlank() &&
        (!withDate || date != null) && (!withRemind || remindReady)

    fun save() {
        val parsed = parseTypedRange(timeText) ?: return
        onSave(
            CardDraft(
                title = title.trim(),
                start = parsed.start,
                end = parsed.end,
                note = note.trim(),
                icon = icon,
                priority = priority,
                date = date,
                remind = remind,
                silent = silent,
                sound = sound,
                soundTitle = soundTitle,
                link = link,
                days = days,
            ),
        )
    }

    /**
     * Записать выбор, сделанный в просмотре.
     *
     * Знак и важность выбираются одним тапом и не входят в правку — «готово»
     * там нажимать негде, и без записи на месте выбор пропал бы при закрытии
     * карточки. Посреди правки, наоборот, не пишем: там всё сохранит «готово»,
     * а запись на каждый тап складывала бы в базу недописанное. У нового дела
     * записывать ещё нечего — его выборы уедут вместе с первым сохранением.
     */
    fun keepChoice() {
        if (card != null && step == CardStep.VIEW && canFinish) save()
    }

    /** Записать и вернуться к просмотру. Новое дело закрывается сразу. */
    fun finish() {
        if (!canFinish) return
        save()
        // Держать человека в карточке, которую он только что завёл, незачем.
        if (card == null) onDismiss() else step = CardStep.VIEW
    }

    /** Шаг вперёд. С последнего места — закончить. */
    fun next() {
        when (step) {
            CardStep.DATE -> if (date != null) step = CardStep.TIME
            CardStep.TIME -> if (range != null) step = CardStep.TITLE
            CardStep.TITLE -> when {
                title.isBlank() -> Unit
                withDays -> step = CardStep.DAYS
                withNote -> step = CardStep.NOTE
                withRemind -> step = CardStep.REMIND
                else -> finish()
            }

            CardStep.DAYS -> when {
                withNote -> step = CardStep.NOTE
                withRemind -> step = CardStep.REMIND
                else -> finish()
            }

            CardStep.NOTE -> if (withRemind) step = CardStep.REMIND else finish()
            CardStep.REMIND -> finish()
            CardStep.VIEW -> step = firstStep
        }
    }

    /** Последнее место правки: дальше только «готово». */
    val lastStep = when {
        withRemind -> step == CardStep.REMIND
        withNote -> step == CardStep.NOTE
        withDays -> step == CardStep.DAYS
        else -> step == CardStep.TITLE
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(scrim)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            // Тап мимо карточки — закрыть. Без indication: рябь во весь экран
            // выглядела бы дико.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        // Во весь экран карточка со списком становится страницей, и углы у неё
        // прямые: скруглённый прямоугольник, упирающийся в края экрана,
        // читается как неудачно растянутая карточка, а не как раскрытая.
        val corners = RoundedCornerShape(if (fullScreen) 0.dp else 28.dp)
        Card(
            shape = corners,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (fullScreen) 0.dp else 6.dp),
            modifier = Modifier
                .then(
                    if (fullScreen) {
                        Modifier.fillMaxSize()
                    } else {
                        // Вытянутая вниз, но не во весь экран: карточка должна
                        // читаться как поднятая над расписанием, а не как
                        // отдельная страница. Полтора к одному — те же
                        // пропорции, что у маленькой карточки в дне.
                        Modifier
                            .fillMaxWidth(0.82f)
                            // Со строкой напоминания карточка выше: иначе она
                            // отъедала бы высоту у названия, а название в
                            // карточке главное. Со списком — тоже: строки в
                            // щель на две штуки не читаются.
                            .fillMaxHeight(
                                when {
                                    listing -> 0.72f
                                    withRemind -> 0.60f
                                    // Со строкой дней недели — тоже выше: под
                                    // названием прибавились неделя и слово
                                    // под ней, и на прежней высоте они
                                    // отъедали бы место у действий внизу.
                                    withDays -> 0.58f
                                    else -> 0.52f
                                },
                            )
                    },
                )
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .cardEdge(corners)
                // Тап по самой карточке не закрывает её: иначе правка
                // обрывалась бы от промаха мимо строки.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Во весь экран карточка кладётся под часы и под кнопки
                    // системы, и это верно: лист должен доходить до краёв.
                    // Отступ берёт содержимое — иначе знак дела встал бы
                    // ровно на час в углу.
                    .then(if (fullScreen) Modifier.systemBarsPadding() else Modifier)
                    .padding(24.dp),
            ) {
                // Знак нажимается всегда — и в просмотре, и посреди правки:
                // промах догадки виден сразу, как только написано название.
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { picking = !picking }
                        .padding(4.dp),
                ) {
                    Icon(
                        imageVector = blockIconOf(title, icon),
                        contentDescription = "Знак дела",
                        tint = if (picking) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(46.dp),
                    )
                }

                if (picking) {
                    IconPalette(
                        chosen = icon,
                        onPick = {
                            icon = it
                            picking = false
                            keepChoice()
                        },
                        onGuess = {
                            icon = null
                            picking = false
                            keepChoice()
                        },
                        modifier = Modifier.weight(1f).padding(top = 12.dp),
                    )
                    return@Column
                }

                if (pickingLink) {
                    LinkPalette(
                        chosen = link,
                        choices = linkChoices,
                        onPick = { picked ->
                            link = picked
                            pickingLink = false
                            keepChoice()
                        },
                        modifier = Modifier.weight(1f).padding(top = 12.dp),
                    )
                    return@Column
                }

                if (pickingSound) {
                    SoundPalette(
                        chosen = sound,
                        silent = silent,
                        onPick = { picked ->
                            silent = picked == null
                            sound = picked?.uri
                            soundTitle = picked?.title
                        },
                        onDone = {
                            pickingSound = false
                            keepChoice()
                        },
                        modifier = Modifier.weight(1f).padding(top = 12.dp),
                    )
                    return@Column
                }

                if (withDate) {
                    EditableLine(
                        value = dateText,
                        onValueChange = { dateText = it },
                        active = step == CardStep.DATE,
                        dimmed = step != CardStep.VIEW && step != CardStep.DATE,
                        hint = "Сегодня",
                        fontSize = 19.sp,
                        weight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onDone = ::next,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                EditableLine(
                    value = timeText,
                    onValueChange = { timeText = it },
                    active = step == CardStep.TIME,
                    dimmed = step != CardStep.VIEW && step != CardStep.TIME,
                    hint = "20:45 – 22:45",
                    fontSize = 22.sp,
                    weight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    onDone = ::next,
                    modifier = Modifier.padding(top = 14.dp),
                )

                EditableLine(
                    value = title,
                    onValueChange = { title = it },
                    active = step == CardStep.TITLE,
                    dimmed = step != CardStep.VIEW && step != CardStep.TITLE,
                    hint = "Что за дело?",
                    fontSize = 30.sp,
                    weight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    onDone = ::next,
                    modifier = Modifier.padding(top = 10.dp),
                )

                // Дни недели — место правки, а не пометка на полях: правка
                // доходит до них и останавливается ([CardStep.DAYS]).
                //
                // Раньше их не спрашивали вовсе — «у дела и так есть значение
                // по умолчанию». Но у нового дела правка на названии и
                // кончалась: «Готово» записывало дело и закрывало карточку, и
                // человек, заводивший дело ради «по вторникам и пятницам»,
                // до недели не доходил ни разу. Спрашивать про повторение
                // после того, как дело уже заведено, — значит не спрашивать.
                //
                // Под названием, а не над временем: сперва читается, что за
                // дело и в котором часу, и только потом — как часто.
                if (withDays) {
                    DaysLine(
                        chosen = days,
                        onChoose = {
                            days = it
                            keepChoice()
                        },
                        dimmed = step != CardStep.VIEW && step != CardStep.DAYS,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                // Важность выбирается в один тап и не занимает очереди в
                // правке: у дела всегда есть значение по умолчанию, и
                // спрашивать про него отдельным шагом не за что.
                if (withPriority) {
                    PriorityLine(
                        chosen = priority,
                        onChoose = {
                            priority = it
                            keepChoice()
                        },
                        dimmed = step != CardStep.VIEW,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                // Список занимает карточку целиком — как выбор знака и выбор
                // привязки: строки в нём отмечают и дописывают, и делать это в
                // щель под заметкой было бы работой в замочную скважину.
                //
                // Заметка, привязка и напоминание на это время уходят: они
                // никуда не денутся, а показанные вместе со списком превратили
                // бы карточку дела в анкету.
                val listed = listing && tasks != null
                val viewActions: @Composable () -> Unit = {
                    ViewActions(
                        done = card?.done == true,
                        // Правка закрывает список: её строки — дата, время,
                        // название, заметка, — а список на их месте показывал
                        // бы правку, в которой половины правимого не видно.
                        onEdit = {
                            listing = false
                            step = CardStep.TIME
                        },
                        onToggleDone = onToggleDone,
                        onDelete = onDelete,
                        extra = extra,
                        // Выход из списка — первым действием, слева: это то,
                        // чем из него и выходят, и искать его среди «удалить»
                        // человек не должен.
                        leading = when {
                            fullScreen && onCollapse != null -> CardAction(
                                icon = Icons.Outlined.ExpandMore,
                                label = "Свернуть",
                                onClick = onCollapse,
                            )

                            listing -> CardAction(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                label = "К делу",
                                onClick = { listing = false },
                            )

                            else -> null
                        },
                    )
                }

                if (listed) {
                    // Действия встают над строкой ввода, а не под ней: строку
                    // дописывают чаще, чем уходят из списка, и ей место внизу —
                    // под пальцем и прямо над клавиатурой.
                    CardTaskList(
                        tasks = tasks.orEmpty(),
                        onToggle = onToggleTask,
                        onRemove = onRemoveTask,
                        onClearDone = onClearDoneTasks,
                        onAdd = onAddTasks,
                        actions = if (step == CardStep.VIEW) viewActions else null,
                        modifier = Modifier.weight(1f).padding(top = 12.dp),
                    )
                } else {
                    // Заметка показывается, когда она есть или когда до неё дошли:
                    // пустая строка под каждым делом только занимала бы место.
                    if (withNote && (note.isNotBlank() || step == CardStep.NOTE)) {
                        EditableLine(
                            value = note,
                            onValueChange = { note = it },
                            active = step == CardStep.NOTE,
                            dimmed = step != CardStep.VIEW && step != CardStep.NOTE,
                            hint = "Заметка, если нужна",
                            fontSize = 19.sp,
                            weight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            multiline = true,
                            onDone = ::next,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }

                    // Привязка — под названием и над заметкой: «чем делается» —
                    // это про само дело, а заметка и напоминание уже про то, как
                    // с ним обойтись.
                    if (linkChoices.isNotEmpty()) {
                        LinkLine(
                            label = linkChoices.firstOrNull { it.value == link }?.title.orEmpty(),
                            dimmed = step != CardStep.VIEW,
                            onPick = { pickingLink = true },
                            onOpen = link
                                ?.takeIf { chosen -> onOpenLink != null && linkChoices.any { it.value == chosen } }
                                ?.let { chosen -> { onOpenLink?.invoke(chosen) } },
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }

                    // Напоминание — такая же строка карточки, как время и заметка,
                    // а не отдельный разговор поверх экрана: о том, когда напомнить,
                    // думают там же, где о самом деле.
                    if (withRemind && (remindText.isNotBlank() || step == CardStep.REMIND)) {
                        RemindLine(
                            value = remindText,
                            onValueChange = { remindText = it },
                            active = step == CardStep.REMIND,
                            dimmed = step != CardStep.VIEW && step != CardStep.REMIND,
                            set = remind != null,
                            silent = silent,
                            soundTitle = soundTitle,
                            onPickSound = { pickingSound = true },
                            onDone = ::next,
                        )
                    }

                    // Список — рядом с заметкой и привязкой: это всё «что у
                    // этого дела есть». Показывается только там, где списки
                    // вообще бывают, — в дне; у дела в распорядке и у
                    // напоминания [tasks] пусто.
                    if (tasks != null) {
                        TaskLine(
                            tasks = tasks,
                            dimmed = step != CardStep.VIEW,
                            onOpen = { listing = true },
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    // Действия прижаты к низу карточки: они относятся ко всему
                    // делу, а не к последней строке над ними.
                    Spacer(modifier = Modifier.weight(1f))
                }


                if (step == CardStep.VIEW) {
                    // Со списком действия уже стоят внутри него, над строкой ввода.
                    if (!listed) viewActions()
                } else {
                    // Одна галочка на всю правку: она и переводит на следующее
                    // место, и заканчивает — отдельная кнопка «дальше» рядом с
                    // «готово» заставляла бы выбирать между ними на каждом шаге.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        val enabled = when {
                            lastStep -> canFinish
                            step == CardStep.DATE -> date != null
                            step == CardStep.TIME -> range != null
                            step == CardStep.NOTE -> true
                            else -> title.isNotBlank()
                        }
                        ActionButton(
                            icon = Icons.Outlined.Check,
                            label = if (lastStep) "Готово" else "Дальше",
                            accent = enabled,
                            enabled = enabled,
                            onClick = ::next,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Строка напоминания: колокольчик, время и способ.
 *
 * Одной строкой, потому что это одна мысль: «напомнить за пятнадцать минут,
 * молча». Час или промежуток — как написали, так и прочтётся ([parseTypedRemind]).
 *
 * Способ стоит словом рядом, а не отдельным шагом правки: у напоминания всегда
 * есть звук по умолчанию, и спрашивать про него каждый раз — лишнее решение.
 * Слово показывает выбранное сейчас — «Молча» или имя мелодии, — а тап по нему
 * открывает выбор, где и то и другое лежит рядом.
 */
@Composable
private fun RemindLine(
    value: String,
    onValueChange: (String) -> Unit,
    active: Boolean,
    dimmed: Boolean,
    set: Boolean,
    silent: Boolean,
    soundTitle: String?,
    onPickSound: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (silent) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
            contentDescription = "Напоминание",
            tint = if (set) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .alpha(if (dimmed) 0.35f else 1f)
                .size(22.dp),
        )
        EditableLine(
            value = value,
            onValueChange = onValueChange,
            active = active,
            dimmed = dimmed,
            hint = "19:00 или за 15 мин",
            fontSize = 19.sp,
            weight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onDone = onDone,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        if (set) {
            Text(
                text = if (silent) "Молча" else soundTitle ?: "Со звуком",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    // Имя песни бывает длиннее самой строки напоминания, а час
                    // звонка важнее названия мелодии: место в первую очередь ему.
                    .widthIn(max = 120.dp)
                    .alpha(if (dimmed) 0.35f else 1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onPickSound)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Выбор мелодии вместо содержимого карточки.
 *
 * Сверху «Молча» и обычный звук — два ответа, которых чаще всего и хватает;
 * ниже мелодии телефона и своя музыка. Своим списком, а не системным экраном
 * звуков: это такой же выбор, как знак дела, и уводить ради него из карточки,
 * которую человек правит, незачем.
 *
 * Тап по строке и выбирает мелодию, и тут же её проигрывает: как звучит
 * «Sunrise», по названию не знает никто. Проба идёт будильничьим звуком — тем
 * самым, которым зазвучит напоминание, и потому слышна в беззвучном режиме.
 */
@Composable
private fun SoundPalette(
    chosen: String?,
    silent: Boolean,
    onPick: (ReminderSound?) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var granted by remember { mutableStateOf(ReminderSounds.hasMusicAccess(context)) }
    var melodies by remember { mutableStateOf(emptyList<ReminderSound>()) }
    var music by remember { mutableStateOf(emptyList<ReminderSound>()) }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed -> granted = allowed }

    LaunchedEffect(Unit) { melodies = ReminderSounds.system(context) }
    LaunchedEffect(granted) { if (granted) music = ReminderSounds.music(context) }

    // Уходя из выбора, обрываем пробу: песня, доигрывающая поверх расписания, —
    // это уже не выбор мелодии, а забытый плеер.
    DisposableEffect(Unit) { onDispose { ReminderSoundPreview.stop() } }

    Column(modifier = modifier) {
        FadingColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                SoundRow(
                    title = "Молча",
                    icon = Icons.Outlined.NotificationsOff,
                    picked = silent,
                    onClick = {
                        ReminderSoundPreview.stop()
                        onPick(null)
                    },
                )
                SoundRow(
                    title = ReminderSounds.Default.title,
                    icon = Icons.Outlined.Notifications,
                    picked = !silent && chosen == null,
                    onClick = {
                        onPick(ReminderSounds.Default)
                        ReminderSoundPreview.play(context, null)
                    },
                )
            }

            soundGroup("Мелодии телефона", melodies, chosen, silent, Icons.Outlined.MusicNote) {
                onPick(it)
                ReminderSoundPreview.play(context, it.uri)
            }

            if (granted) {
                soundGroup("Моя музыка", music, chosen, silent, Icons.Outlined.LibraryMusic) {
                    onPick(it)
                    ReminderSoundPreview.play(context, it.uri)
                }
            } else {
                // Музыку читаем только с разрешения, и просим его здесь, а не
                // на входе в день: до выбора мелодии она не нужна ни разу.
                item {
                    SoundRow(
                        title = "Взять из своей музыки",
                        icon = Icons.Outlined.LibraryMusic,
                        picked = false,
                        onClick = { ask.launch(ReminderSounds.musicPermission()) },
                    )
                }
            }
        }

        // «Готово» закреплено под списком: песен бывают сотни, и возврат в
        // карточку не должен зависеть от того, докрутил ли человек до низа.
        Text(
            text = "Готово",
            style = MaterialTheme.typography.bodyMedium,
            color = Accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onDone)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

/** Кучка мелодий с подписью. Пустая не показывается — подписывать нечего. */
private fun LazyListScope.soundGroup(
    label: String,
    sounds: List<ReminderSound>,
    chosen: String?,
    silent: Boolean,
    icon: ImageVector,
    onPick: (ReminderSound) -> Unit,
) {
    if (sounds.isEmpty()) return

    item {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    // Ключ с подписью кучки: один и тот же файл может стоять и мелодией
    // телефона, и песней в музыке, а два одинаковых ключа в списке — падение.
    items(sounds, key = { "$label:${it.uri}" }) { sound ->
        SoundRow(
            title = sound.title,
            icon = icon,
            picked = !silent && sound.uri == chosen,
            onClick = { onPick(sound) },
        )
    }
}

/** Строка выбора: значок, название, выбранное — цветом. */
@Composable
private fun SoundRow(
    title: String,
    icon: ImageVector,
    picked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (picked) AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (picked) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (picked) Accent else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * Действия раскрытой карточки.
 *
 * Переносятся на вторую строку, когда не помещаются: подписи под значками
 * длиннее самих значков, и четыре действия в строку
 * не встают. «Удалить» стоит последним и потому уходит вниз первым — от
 * остальных его отделяет уже перенос.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewActions(
    done: Boolean,
    onEdit: () -> Unit,
    onToggleDone: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    extra: CardAction?,
    /** Выход из того, что сейчас открыто, — свернуть карточку или закрыть список. */
    leading: CardAction? = null,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = 3,
    ) {
        leading?.let { action ->
            ActionButton(
                icon = action.icon,
                label = action.label,
                accent = action.accent,
                onClick = action.onClick,
            )
        }
        ActionButton(
            icon = Icons.Outlined.EditNote,
            label = "Редактировать",
            onClick = onEdit,
        )
        onToggleDone?.let { toggle ->
            ActionButton(
                icon = Icons.Outlined.Check,
                label = if (done) "Не выполнено" else "Выполнено",
                accent = done,
                onClick = toggle,
            )
        }
        extra?.let { action ->
            ActionButton(
                icon = action.icon,
                label = action.label,
                accent = action.accent,
                onClick = action.onClick,
            )
        }
        onDelete?.let { delete ->
            ActionButton(
                icon = Icons.Outlined.DeleteOutline,
                label = "Удалить",
                color = MaterialTheme.colorScheme.error,
                onClick = delete,
            )
        }
    }
}

/**
 * Выбор знака вместо содержимого карточки.
 *
 * Без подписей: знаки выбирают глазами, а не по названию, и подпись под каждым
 * растянула бы сетку на два экрана. Идут они кучками по смыслу — порядок задан
 * в [BlockIcon]. В карточку помещаются не все, поэтому сетка прокручивается, а
 * возврат к догадке по названию закреплён под ней: иначе выбранный однажды знак
 * нельзя было бы отменить, не докрутив до низа.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPalette(
    chosen: BlockIcon?,
    onPick: (BlockIcon) -> Unit,
    onGuess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 5,
            modifier = Modifier
                // fill = false: пока знаки помещаются, сетка занимает своё, а
                // не растягивается на всю карточку.
                .weight(1f, fill = false)
                .fadingVerticalScroll()
                .fillMaxWidth(),
        ) {
            BlockIcon.entries.forEach { option ->
                val picked = option == chosen
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (picked) AccentSoft else Color.Transparent)
                        .clickable { onPick(option) },
                ) {
                    Icon(
                        imageVector = blockIcon(option),
                        contentDescription = option.name,
                        tint = if (picked) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }

        if (chosen != null) {
            Text(
                text = "Угадывать по названию",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onGuess)
                    .padding(vertical = 10.dp),
            )
        }
    }
}

/**
 * Важность строкой: три слова, выбранное — цветом.
 *
 * Не чипы: карточка набрана строчками текста, и рамки Material в ней читались
 * бы как кусок формы, попавший не туда.
 */
/**
 * Строка дней недели: семь букв, и нажатая горит.
 *
 * Повторение выбирается пальцем по самим дням, а не списком «ежедневно /
 * еженедельно / по будням»: список отвечает словом, которое потом надо
 * разворачивать в дни, а семь букв и есть ответ — «Пн Ср Пт» видно целиком,
 * не открывая ничего.
 *
 * Ничего не выбрано и выбраны все семь — одно и то же, «каждый день» (см.
 * [DeedDays]), и горят при этом все семь: погашенная неделя читалась бы как
 * «дело не случается никогда», а такого у дела не бывает — для этого есть
 * переключатель на карточке. Поэтому и снятый последний день возвращает
 * неделю целиком: человек снимал день, а не отменял дело.
 *
 * Слово под буквами — то же, что стоит на карточке в списке: «По будням»
 * короче пяти сокращений, и, увидев его здесь, человек узнает его там.
 */
@Composable
private fun DaysLine(
    chosen: Set<DayOfWeek>,
    onChoose: (Set<DayOfWeek>) -> Unit,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    // Пусто — это «каждый день», и показывается оно всей неделей.
    val lit = chosen.ifEmpty { DeedDays.week.toSet() }

    Column(modifier = modifier.alpha(if (dimmed) 0.35f else 1f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            DeedDays.week.forEach { day ->
                val picked = day in lit
                Text(
                    text = DeedDays.short(day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (picked) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (picked) AccentSoft else Color.Transparent)
                        .clickable {
                            val next = if (picked) lit - day else lit + day
                            onChoose(if (next.size == 7) emptySet() else next.ifEmpty { emptySet() })
                        }
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                )
            }
        }
        Text(
            text = DeedDays.title(chosen),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
    }
}

/**
 * Важность дела списка — и то, что она теперь делает.
 *
 * Раньше она не значила ничего: осталась от разбора рассказа о себе, где
 * решала, куда поставить дело без названного часа. Теперь важное дело само
 * встаёт в расписание дня — в сегодняшний и в каждый будущий свой день, — и
 * выбирать его каждое утро из списка не нужно (см.
 * [app.askya.data.repository.DayRepository.ensureStanding]).
 *
 * Сказано это строкой под выбором, а не спрятано в справку: слово «Важно»,
 * которое молча меняет поведение приложения, — это ловушка, а не пометка. И
 * сказано только у выбранного «Важно»: подпись под каждым из трёх слов
 * перестают читать на второй карточке.
 */
@Composable
private fun PriorityLine(
    chosen: Priority,
    onChoose: (Priority) -> Unit,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.alpha(if (dimmed) 0.35f else 1f)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Priority.entries.forEach { option ->
                val picked = option == chosen
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (picked) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onChoose(option) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        if (chosen == Priority.HIGH) {
            Text(
                text = "Важное дело само встаёт в расписание — в сегодняшний день и в " +
                    "каждый выбранный выше. Брать его из списка руками не нужно; " +
                    "убранное из одного дня обратно не возвращается.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 6.dp, end = 6.dp),
            )
        }
    }
}

/**
 * Строка карточки: показывает значение, а на своём шаге правки становится
 * полем ввода и подсвечивается.
 *
 * Подсветка — плашка `AccentSoft` под строкой: она говорит «правится вот это»
 * без единого слова. Остальные строки в это время приглушены — так видно, что
 * очередь не их.
 */
@Composable
internal fun EditableLine(
    value: String,
    onValueChange: (String) -> Unit,
    active: Boolean,
    dimmed: Boolean,
    hint: String,
    fontSize: TextUnit,
    weight: FontWeight,
    color: Color,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(active) { if (active) focus.requestFocus() }

    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSize,
        lineHeight = fontSize * 1.3f,
        fontWeight = weight,
        color = color,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) AccentSoft else Color.Transparent)
            .padding(horizontal = if (active) 10.dp else 0.dp, vertical = if (active) 8.dp else 0.dp)
            .alpha(if (dimmed) 0.35f else 1f),
    ) {
        if (active) {
            if (value.isEmpty()) {
                Text(text = hint, style = style.copy(color = color.copy(alpha = 0.4f)))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = style,
                cursorBrush = SolidColor(Accent),
                singleLine = !multiline,
                keyboardOptions = KeyboardOptions(
                    imeAction = if (multiline) ImeAction.Default else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
                    .focusRequester(focus),
            )
        } else {
            Text(text = value.ifBlank { hint }, style = style)
        }
    }
}

/** Значок действия с подписью под ним: значок один не всегда узнаётся. */
@Composable
internal fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        accent -> Accent
        else -> color
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
