package app.askya.ui.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.data.entity.ThreadItem
import app.askya.data.repository.ThreadParts
import app.askya.data.repository.ThreadRow
import app.askya.domain.model.ThreadState
import app.askya.domain.model.formatMoney
import app.askya.domain.model.limitShare
import app.askya.domain.model.moneyToText
import app.askya.domain.model.parseMoney
import app.askya.domain.model.silenceWord
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DateLean
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogField
import app.askya.ui.components.DialogTitle
import app.askya.ui.components.MarkPalette
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.components.formatRussianDate
import app.askya.ui.components.formatTypedDate
import app.askya.ui.components.parseTypedDate
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Danger
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted
import app.askya.ui.theme.markColor
import java.time.LocalDate

/**
 * Нить, раскрытая карточкой: жива ли она и из чего состоит.
 *
 * ## Наверху — тишина, а не готовность
 *
 * Первое, что говорит карточка, — сколько нить не трогали. Это и есть ответ на
 * вопрос, ради которого её открыли; процент готовности на его месте был бы
 * цифрой, которую никто не может назвать честно.
 *
 * ## Ниже — срезы других разделов
 *
 * «Дела 4 из 11», «Список 6 из 19», «Траты 12 400 ₽», «Записи 3». Это не
 * содержимое нити, а то, что уже лежит в расписании, списках, книге и Scroll и
 * помечено этой нитью. Своего у неё пять полей — см. [ThreadItem].
 *
 * ## «Записать» кладёт в нужный раздел, не уводя из нити
 *
 * Второй шаг карточки: строка и два ответа — «Дело сегодня» и «Строка в
 * список». Первое ложится в расписание обычным делом с привязкой, второе — в
 * список Yet этой нити (нет списка — заводится сам). Уводить человека в другой
 * раздел ради одной строки значит потерять его по дороге.
 */
@Composable
fun ThreadCard(
    row: ThreadRow,
    parts: ThreadParts,
    onEdit: () -> Unit,
    onAddDeed: (String) -> Unit,
    onAddLine: (String) -> Unit,
    onState: (ThreadState) -> Unit,
    onDismiss: () -> Unit,
) {
    val thread = row.thread
    val today = LocalDate.now()

    // Пишут ли сейчас новое. Отдельным шагом, а не полем внизу: поле, всегда
    // висящее под карточкой, обещает, что писать сюда — обычное дело, а сюда
    // чаще приходят посмотреть.
    var writing by remember(thread.id) { mutableStateOf(false) }
    var draft by remember(thread.id) { mutableStateOf("") }

    AskyaDialog(onDismiss = onDismiss, width = 0.92f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(markColor(thread.color, thread.title)),
            )
            DialogTitle(
                text = thread.title.ifBlank { "Нить" },
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
        }
        if (thread.ending.isNotBlank()) {
            Text(
                text = thread.ending,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (writing) {
            Compose(
                draft = draft,
                onDraft = { draft = it },
                onDeed = {
                    onAddDeed(draft)
                    draft = ""
                    writing = false
                },
                onLine = {
                    onAddLine(draft)
                    draft = ""
                    writing = false
                },
                onBack = { writing = false },
            )
            return@AskyaDialog
        }

        Column(
            modifier = Modifier
                .padding(top = 14.dp)
                .heightIn(max = 400.dp)
                .fadingVerticalScroll(),
        ) {
            Pulse(row = row, today = today)

            if (parts.next.isNotBlank()) {
                DialogCaption("Что дальше")
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp, end = 10.dp)
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentSoft),
                    )
                    Text(
                        text = parts.next,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AccentInk,
                    )
                }
            }

            DialogCaption("Из чего она состоит")
            if (parts.empty) {
                Text(
                    text = "Пока ни одного дела, ни строки, ни траты. Нить тянется тем, " +
                        "что уже лежит в разделах: пометь ею дело в дне, список или запись " +
                        "в книге — или запиши прямо отсюда.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            } else {
                Part("Дела", "${parts.deedsDone} из ${parts.deeds.size}", parts.deeds.isNotEmpty())
                Part("Список", "${parts.linesDone} из ${parts.lines.size}", parts.lines.isNotEmpty())
                Money(spent = parts.money.spent, budget = thread.budget, any = parts.money.count > 0)
                Part("Записи", "${parts.notes}", parts.notes > 0)
            }

            thread.due?.let { due ->
                DialogCaption("Срок")
                val left = java.time.temporal.ChronoUnit.DAYS.between(today, due)
                Text(
                    text = formatRussianDate(due) + when {
                        left > 0 -> " · осталось ${dayWord(left.toInt())}"
                        left == 0L -> " · сегодня"
                        else -> " · прошёл"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (left < 0) Danger else Ink,
                )
            }
        }

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.EditNote,
                label = "Записать",
                onClick = { writing = true },
            )
            ActionButton(icon = Icons.Outlined.Tune, label = "Нить", onClick = onEdit)
            Spacer(Modifier.weight(1f))
            States(state = thread.state, onState = onState)
        }
    }
}

/**
 * Пульс наверху карточки: тишина крупно, полоска месяцев под ней.
 *
 * Крупная строка засечным — та же мерка, что у заголовка окна: это не подпись
 * к полоске, а сам ответ. Ни разу не тронутая нить говорит об этом словами, а
 * не нулём: ноль здесь читался бы как «ноль дней тишины», то есть наоборот.
 */
@Composable
private fun Pulse(row: ThreadRow, today: LocalDate) {
    val silence = row.pulse.silence(today)

    Text(
        text = silence?.let { silenceWord(it) } ?: "Ещё не трогали",
        fontFamily = FontFamily.Serif,
        fontSize = 24.sp,
        color = if (silence != null && silence >= 45) AccentInk else Ink,
    )
    Text(
        text = if (silence == null) "нить только завели" else "последнее касание",
        style = MaterialTheme.typography.labelMedium,
        color = Muted,
        modifier = Modifier.padding(top = 2.dp),
    )

    PulseStrip(
        pulse = row.pulse,
        dimmed = !row.thread.state.running,
        height = 48.dp,
        labels = true,
        modifier = Modifier.padding(top = 14.dp),
    )
    Text(
        text = "За полгода — ${dayWord(row.pulse.days)}, когда ты её трогал",
        style = MaterialTheme.typography.labelMedium,
        color = Muted,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Строка среза: чем нить занята в этом разделе и сколько там всего. */
@Composable
private fun Part(title: String, value: String, any: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (any) Ink else Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = if (any) AccentInk else Muted,
        )
    }
}

/**
 * Деньги нити: сколько ушло и сколько закладывали.
 *
 * Полоска — та же, что у статьи с пределом, и говорит то же самое. Сметы нет —
 * нет и полоски: рисовать её от выдуманного потолка значило бы обещать
 * человеку план, которого он не составлял.
 */
@Composable
private fun Money(spent: Long, budget: Long, any: Boolean) {
    val share = limitShare(spent, budget)
    val over = share != null && share > 1f

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Траты",
                style = MaterialTheme.typography.bodyLarge,
                color = if (any) Ink else Muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (any) formatMoney(spent) else "нет",
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    over -> Danger
                    any -> Ink
                    else -> Muted
                },
            )
        }
        if (share != null) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (over) Danger else Accent),
                )
            }
            Text(
                text = if (over) {
                    "Сверх сметы ${formatMoney(spent - budget)}"
                } else {
                    "Заложено ${formatMoney(budget)} · осталось ${formatMoney(budget - spent)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (over) Danger else Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Что сделать с нитью: отложить, оживить, бросить, закончить.
 *
 * Идущая умеет отложиться и закрыться; отложенная и закрытая — ожить. Кнопки
 * меняются вместе с состоянием, а не гаснут: погашенная кнопка занимает место
 * и заставляет гадать, чем она погашена.
 *
 * «Бросить» стоит рядом с «Закончить» и не красное: брошенная нить не потеря
 * и не ошибка, а честный конец, и пугать им незачем.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.States(
    state: ThreadState,
    onState: (ThreadState) -> Unit,
) {
    if (state.running) {
        ActionButton(
            icon = Icons.Outlined.Pause,
            label = "Отложить",
            onClick = { onState(ThreadState.PAUSED) },
        )
        ActionButton(
            icon = Icons.Outlined.Close,
            label = "Бросить",
            onClick = { onState(ThreadState.DROPPED) },
        )
        ActionButton(
            icon = Icons.Outlined.Check,
            label = "Закончить",
            accent = true,
            onClick = { onState(ThreadState.DONE) },
        )
    } else {
        ActionButton(
            icon = Icons.Outlined.PlayArrow,
            label = if (state.closed) "Открыть снова" else "Вернуть",
            accent = true,
            onClick = { onState(ThreadState.LIVE) },
        )
    }
}

/**
 * Второй шаг карточки: одна строка и два ответа, куда её положить.
 *
 * Два ответа, а не выбор раздела списком: у записанного на ходу два адреса —
 * сегодняшний день, если это дело на сегодня, и список нити, если это «когда-
 * нибудь». Третьего не бывает: трата записывается суммой, а не строкой, и ей
 * место в книге.
 */
@Composable
private fun Compose(
    draft: String,
    onDraft: (String) -> Unit,
    onDeed: () -> Unit,
    onLine: () -> Unit,
    onBack: () -> Unit,
) {
    DialogCaption("Что записать")
    DialogField(
        value = draft,
        onValueChange = onDraft,
        hint = "Вывезти старую плитку",
        autoFocus = true,
        singleLine = false,
    )
    Text(
        text = "«Дело сегодня» встанет в расписание с этой нитью. «Строка в список» ляжет " +
            "в список Yet этой нити — списка нет, он заведётся сам.",
        style = MaterialTheme.typography.bodySmall,
        color = Muted,
        modifier = Modifier.padding(top = 8.dp),
    )

    DialogButtons {
        ActionButton(icon = Icons.Outlined.Close, label = "Назад", onClick = onBack)
        Spacer(Modifier.weight(1f))
        ActionButton(
            icon = Icons.Outlined.EditNote,
            label = "Строка в список",
            enabled = draft.isNotBlank(),
            onClick = onLine,
        )
        ActionButton(
            icon = Icons.Outlined.Check,
            label = "Дело сегодня",
            accent = true,
            enabled = draft.isNotBlank(),
            onClick = onDeed,
        )
    }
}

/**
 * Новая нить и правка старой.
 *
 * Спрашивает пять полей, и первые два — имя и чем закончится. Второе не
 * обязательно, но стоит вторым нарочно: нить без описанного конца через месяц
 * не читается, а придумать его за человека нельзя.
 *
 * Срок и смета спрашиваются последними и оба пустые по умолчанию: у
 * большинства личных нитей нет ни того, ни другого, и поле, требующее ответа,
 * заставляло бы придумывать его на пустом месте.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadEditCard(
    thread: ThreadItem,
    onSave: (ThreadItem) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by remember(thread.id) { mutableStateOf(thread.title) }
    var ending by remember(thread.id) { mutableStateOf(thread.ending) }
    var color by remember(thread.id) { mutableStateOf(thread.color) }
    var due by remember(thread.id) { mutableStateOf(thread.due?.let { formatTypedDate(it) } ?: "") }
    var budget by remember(thread.id) { mutableStateOf(moneyToText(thread.budget)) }

    AskyaDialog(onDismiss = onDismiss) {
        DialogTitle(if (thread.id == 0L) "Новая нить" else "Правим нить")

        DialogCaption("Как назовём")
        DialogField(
            value = title,
            onValueChange = { title = it },
            hint = "Разобрать гараж, испанский, кухня",
            autoFocus = thread.id == 0L,
        )

        DialogCaption("Чем закончится")
        DialogField(
            value = ending,
            onValueChange = { ending = it },
            hint = "Машина встанет внутрь",
            singleLine = false,
        )
        Text(
            text = "Одна строка своими словами. Нить без описанного конца — это " +
                "настроение, а не нить: через месяц по ней не понять, чего хотели.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(top = 6.dp),
        )

        DialogCaption("Краска")
        MarkPalette(
            chosen = color,
            // Повторный тап снимает выбор — как у корешка книги и у счёта:
            // цвет тогда снова выводится из названия.
            onPick = { picked -> color = if (picked == color) null else picked },
        )

        DialogCaption("Срок")
        DialogField(
            value = due,
            onValueChange = { due = it },
            hint = "Пусто — без срока",
        )

        DialogCaption("Смета")
        DialogField(
            value = budget,
            onValueChange = { budget = it },
            hint = "Пусто — без сметы",
            keyboard = KeyboardType.Decimal,
        )
        Text(
            text = "Сколько заложено на всю нить. Из трат, помеченных ею, книга посчитает, " +
                "сколько уже ушло, и покажет полоску — ту же, что у статьи с пределом.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(top = 6.dp),
        )

        DialogButtons {
            onDelete?.let { delete ->
                ActionButton(
                    icon = Icons.Outlined.DeleteOutline,
                    label = "Стереть",
                    color = MaterialTheme.colorScheme.error,
                    onClick = delete,
                )
            }
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Готово",
                accent = true,
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        thread.copy(
                            title = title.trim(),
                            ending = ending.trim(),
                            color = color,
                            // Срок смотрит вперёд: то, что назначают,
                            // назначают в будущее — так же, как у дела и
                            // напоминания (см. [DateLean]).
                            due = parseTypedDate(due, lean = DateLean.AHEAD),
                            budget = parseMoney(budget) ?: 0L,
                        ),
                    )
                },
            )
        }
    }
}

/**
 * Выбор нити словами — для тех мест, где нить проставляют у чужой записи:
 * у траты в книге и у списка Yet.
 *
 * Тем же рядом слов, каким в книге выбирают статью и счёт: нитей три-четыре,
 * они помещаются в две строки, и выпадающий список ради них был бы прибором.
 * Повторный тап снимает выбор — «без нити» тоже ответ.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadPicker(
    threads: List<ThreadItem>,
    chosen: Long?,
    onPick: (Long?) -> Unit,
) {
    // Закрытые не предлагаются — кроме той, на которой запись уже стоит:
    // иначе, открыв прошлогоднюю трату, человек увидел бы, что она «ничья».
    val pickable = threads.filter { !it.state.closed || it.id == chosen }
    if (pickable.isEmpty()) return

    DialogCaption("Нить")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        pickable.forEach { thread ->
            val picked = chosen == thread.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (picked) AccentSoft else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPick(if (picked) null else thread.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(markColor(thread.color, thread.title)),
                )
                Text(
                    text = thread.title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (picked) AccentInk else Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
