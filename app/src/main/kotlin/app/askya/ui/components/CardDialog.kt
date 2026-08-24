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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.outlined.EditNote
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
import app.askya.domain.model.Priority
import app.askya.domain.model.RemindAt
import app.askya.reminders.ReminderSound
import app.askya.reminders.ReminderSoundPreview
import app.askya.reminders.ReminderSounds
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentSoft
import java.time.LocalDate
import java.time.LocalTime

/**
 * Шаг правки. `VIEW` — карточка просто раскрыта; дальше по порядку: дата,
 * время, событие, заметка, напоминание.
 */
private enum class CardStep { VIEW, DATE, TIME, TITLE, NOTE, REMIND }

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
 * правится сейчас, остальное приглушено. Порядок — дата, время, событие,
 * заметка, напоминание: так дело и думается, «когда и что», а заметка с
 * напоминанием нужны не всегда.
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
 */
@Composable
fun CardDialog(
    card: CardContent?,
    onDismiss: () -> Unit,
    onSave: (CardDraft) -> Unit,
    startAtNote: Boolean = false,
    startAtRemind: Boolean = false,
    withNote: Boolean = true,
    withPriority: Boolean = false,
    withDate: Boolean = false,
    withRemind: Boolean = false,
    onToggleDone: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    extra: CardAction? = null,
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

    // Выбор знака — не шаг правки, а отступление в сторону: он занимает
    // карточку целиком и возвращает обратно туда же, откуда его открыли.
    var picking by remember { mutableStateOf(false) }

    // Выбор мелодии — такое же отступление: список песен на телефоне длинный,
    // и строкой в карточке он не помещается.
    var pickingSound by remember { mutableStateOf(false) }

    BackHandler(
        onBack = {
            when {
                pickingSound -> pickingSound = false
                picking -> picking = false
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
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                // Вытянутая вниз, но не во весь экран: карточка должна
                // читаться как поднятая над расписанием, а не как отдельная
                // страница. Полтора к одному — те же пропорции, что у
                // маленькой карточки в дне.
                .fillMaxWidth(0.82f)
                // Со строкой напоминания карточка выше: иначе она отъедала бы
                // высоту у названия, а название в карточке главное.
                .fillMaxHeight(if (withRemind) 0.60f else 0.52f)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                // Тап по самой карточке не закрывает её: иначе правка
                // обрывалась бы от промаха мимо строки.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
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

                // Действия прижаты к низу карточки: они относятся ко всему
                // делу, а не к последней строке над ними.
                Spacer(modifier = Modifier.weight(1f))

                if (step == CardStep.VIEW) {
                    ViewActions(
                        done = card?.done == true,
                        onEdit = { step = CardStep.TIME },
                        onToggleDone = onToggleDone,
                        onDelete = onDelete,
                        extra = extra,
                    )
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
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = 3,
    ) {
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
                .verticalScroll(rememberScrollState())
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
@Composable
private fun PriorityLine(
    chosen: Priority,
    onChoose: (Priority) -> Unit,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.alpha(if (dimmed) 0.35f else 1f),
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
