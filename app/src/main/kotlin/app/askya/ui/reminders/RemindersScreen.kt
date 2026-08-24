package app.askya.ui.reminders

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.data.entity.Reminder
import app.askya.data.entity.remindAt
import app.askya.domain.model.RemindAt
import app.askya.reminders.ReminderAlarms
import app.askya.ui.components.BlockCard
import app.askya.ui.components.CardContent
import app.askya.ui.components.CardDialog
import app.askya.ui.components.CardGrid
import app.askya.ui.components.DayPartTitle
import app.askya.ui.components.EmptyState
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.blockIconOf
import app.askya.ui.components.formatRange
import app.askya.ui.components.formatRemind
import app.askya.ui.components.formatTime
import app.askya.ui.components.formatTypedDate
import app.askya.ui.theme.Accent
import app.askya.ui.theme.Ink
import java.time.LocalDate

/**
 * Напоминания. Экрана нет в боковом меню: на него ведёт один вход —
 * колокольчик в шапке AskyaDay, — поэтому кнопка навигации здесь всегда
 * «назад».
 *
 * Показывается ровно так же, как расписание дня: те же карточки, та же сетка
 * по трое, та же раскрытая карточка на правку. Напоминание — такое же дело со
 * временем, только с приписанным к нему часом звонка, и разная вёрстка
 * заставляла бы сверять день и напоминания о нём глазами.
 *
 * В списке всё подряд — и заведённое здесь, и поставленное на дело дня:
 * человек спрашивает «о чём мне напомнят», а не «о чём мне напомнят из этого
 * раздела».
 */
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.factory(appContainer()))
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // null — диалога нет; Editing(null) — новое напоминание.
    var editing by remember { mutableStateOf<Editing?>(null) }

    // Разрешение спрашивается тогда, когда понадобилось: просить заранее —
    // значит просить у человека, который ещё не знает, о чём речь.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Отказали — напоминание всё равно записывается, просто не покажется. */ }

    fun askIfNeeded() {
        if (ReminderAlarms.needsPermission()) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val today = remember { LocalDate.now() }

    ScreenScaffold(
        title = "Напоминания",
        onNavigationClick = onBack,
        navigationIsBack = true,
        floatingActionButton = {
            // Та же чёрная «таблетка» с коралловым плюсом, что в AskyaDay и в
            // списке дел: жест один и тот же — «завести новую карточку».
            ExtendedFloatingActionButton(
                onClick = {
                    askIfNeeded()
                    editing = Editing(null)
                },
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
                    text = "new",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        title = "Напоминаний пока нет",
                        hint = "Заведи карточку: о чём напомнить, во сколько это будет и когда сказать.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                    )
                }
            } else {
                // Днями, а не одной лентой: напоминания живут по дням так же,
                // как дела, и «что у меня в четверг» — тот же вопрос.
                items.groupBy { it.eventDate }.forEach { (date, dayItems) ->
                    item(key = date.toString()) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            DayPartTitle(formatTypedDate(date, today))
                            CardGrid(dayItems) { reminder, cardModifier ->
                                ReminderCard(
                                    reminder = reminder,
                                    onClick = { editing = Editing(reminder) },
                                    onToggle = {
                                        if (!reminder.enabled) askIfNeeded()
                                        viewModel.setEnabled(context, reminder, !reminder.enabled)
                                    },
                                    modifier = cardModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { current ->
        // Та же раскрытая карточка, что в дне, плюс дата: у дела в дне она уже
        // есть, а напоминание заводят вне какого-либо дня. Заметки нет — всё,
        // что нужно сказать, помещается в название события.
        CardDialog(
            card = current.reminder?.let { reminder ->
                CardContent(
                    title = reminder.title,
                    time = reminder.eventStart
                        ?.let { formatRange(it, reminder.eventEnd) }
                        ?: formatTime(reminder.time),
                    date = formatTypedDate(reminder.eventDate, today),
                    remind = formatRemind(reminder.remindAt),
                    silent = reminder.silent,
                    sound = reminder.sound,
                    soundTitle = reminder.soundTitle,
                    icon = reminder.icon,
                )
            },
            withNote = false,
            withDate = true,
            withRemind = true,
            onDismiss = { editing = null },
            onDelete = current.reminder?.let { reminder ->
                {
                    viewModel.delete(context, reminder)
                    editing = null
                }
            },
            onSave = { draft ->
                askIfNeeded()
                viewModel.save(
                    context = context,
                    existing = current.reminder,
                    title = draft.title,
                    date = draft.date ?: today,
                    start = draft.start,
                    end = draft.end,
                    icon = draft.icon,
                    // Строку напоминания оставили пустой — напомнить к началу
                    // события: ради чего ещё его заводили.
                    remind = draft.remind ?: RemindAt.Exact(draft.start),
                    silent = draft.silent,
                    sound = draft.sound,
                    soundTitle = draft.soundTitle,
                )
            },
        )
    }
}

/** Открытый диалог. [reminder] = null — напоминание ещё не создано. */
private data class Editing(val reminder: Reminder?)

/**
 * Карточка напоминания: общая карточка плюс колокольчик и час звонка снизу.
 *
 * Время наверху — время события, как у дела в дне: карточка отвечает на «что и
 * когда», а «когда напомнить» — приписка к ней. Колокольчик выключает
 * напоминание, не удаляя: то, что в этот раз не нужно, чаще хотят приглушить,
 * чем стереть.
 */
@Composable
private fun ReminderCard(
    reminder: Reminder,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant

    BlockCard(
        icon = blockIconOf(reminder.title, reminder.icon),
        time = reminder.eventStart
            ?.let { formatRange(it, reminder.eventEnd) }
            ?: formatTime(reminder.time),
        title = reminder.title,
        titleColor = if (reminder.enabled) MaterialTheme.colorScheme.onBackground else metaColor,
        metaColor = metaColor,
        onClick = onClick,
        modifier = modifier,
    ) {
        IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (reminder.silent) {
                    Icons.Outlined.NotificationsOff
                } else {
                    Icons.Outlined.Notifications
                },
                contentDescription = if (reminder.enabled) "Напоминание стоит" else "Напоминание выключено",
                tint = if (reminder.enabled) MaterialTheme.colorScheme.primary else metaColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = formatRemind(reminder.remindAt),
            style = MaterialTheme.typography.labelSmall,
            color = if (reminder.enabled) MaterialTheme.colorScheme.primary else metaColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.size(4.dp))
    }
}
