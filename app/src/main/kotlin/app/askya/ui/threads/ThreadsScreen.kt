package app.askya.ui.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.data.entity.ThreadItem
import app.askya.data.repository.ThreadRow
import app.askya.domain.model.PULSE_MONTHS
import app.askya.domain.model.ThreadPulse
import app.askya.domain.model.ThreadState
import app.askya.domain.model.asksAbout
import app.askya.domain.model.silenceWord
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.DayPartTitle
import app.askya.ui.components.EmptyState
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.formatRussianDate
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge
import app.askya.ui.theme.markColor
import java.time.LocalDate

/**
 * Threads — нити: то, что тянется неделями через все разделы.
 *
 * ## Почему это раздел, а не метка
 *
 * Метка отвечает «покажи всё с этим ярлыком»; раздел отвечает «как оно идёт».
 * Второго вопроса в Askya задать было нечем: расписание знает про свой день,
 * книга про свои деньги, списки про свои строки, и ни один не складывает их в
 * одно начинание. Нить складывает — и ничего при этом не хранит: дела остаются
 * делами дня, траты тратами, строки строками.
 *
 * ## Что показывает лента
 *
 * У каждой нити три вещи, и все три — ответ на «жива ли она»: когда трогали в
 * последний раз, полоска месяцев и ближайший незакрытый шаг. Процента
 * готовности здесь нет и не будет: его нельзя назвать честно, а «47 дней
 * назад» — факт, который лежит в базе.
 *
 * Полоска — та же арифметика, что в «Прожитом»: считаются дни, в которые
 * что-то происходило, а не события. День, в который отметили дело и записали
 * трату, это один день работы.
 *
 * ## Тихий вопрос
 *
 * У нити, замолчавшей на полтора месяца, под полоской встаёт строка: «Полтора
 * месяца тишины — отложить или бросить?». Не уведомление, не значок и не
 * красное: Askya не канючит. Один раз сказать правду там, где на неё и так
 * смотрят, — этого достаточно.
 */
@Composable
fun ThreadsScreen(onOpenMenu: () -> Unit) {
    val container = appContainer()
    val viewModel: ThreadsViewModel = viewModel(factory = ThreadsViewModel.factory(container))

    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val parts by viewModel.parts.collectAsStateWithLifecycle()

    // Раскрытая нить и та, что правится. Разные вещи: в первой смотрят, как
    // идёт, во второй меняют имя и краску.
    var opened by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<ThreadItem?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(opened) { viewModel.show(opened ?: 0L) }

    val today = LocalDate.now()
    val live = rows.filter { it.thread.state == ThreadState.LIVE }
    val paused = rows.filter { it.thread.state == ThreadState.PAUSED }
    val closed = rows.filter { it.thread.state.closed }

    ScreenScaffold(
        title = "Threads",
        onNavigationClick = onOpenMenu,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = ThreadItem() },
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
                    text = "нить",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) {
        if (rows.isEmpty()) {
            EmptyState(
                title = "Нитей пока нет",
                hint = "Нить — это то, что тянется неделями и умирает не от провала, а от " +
                    "тишины: выучить язык, доделать ремонт, дописать книгу. Askya не " +
                    "считает проценты готовности — их нельзя назвать честно. Она " +
                    "показывает, когда ты трогал нить в последний раз, и тянет к ней " +
                    "дела, списки и траты, которые у неё уже есть.",
            )
        } else {
            FadingColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                part("Идут", live, today, onOpen = { opened = it })
                part("Отложены", paused, today, onOpen = { opened = it })
                part("Закрыты", closed, today, onOpen = { opened = it })

                item(key = "tail") { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    opened?.let { id ->
        // Нить берётся из живого списка: правка из этой же карточки иначе
        // ждала бы её закрытия.
        val row = rows.firstOrNull { it.thread.id == id }
        if (row == null) {
            opened = null
        } else {
            ThreadCard(
                row = row,
                parts = parts,
                onEdit = { editing = row.thread },
                onAddDeed = { title -> viewModel.addDeed(id, title) },
                onAddLine = { text -> viewModel.addLine(row.thread, text) },
                onState = { state ->
                    if (state == ThreadState.LIVE) viewModel.revive(row.thread)
                    else viewModel.close(row.thread, state)
                },
                onDismiss = { opened = null },
            )
        }
    }

    editing?.let { thread ->
        ThreadEditCard(
            thread = thread,
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = if (thread.id == 0L) null else {
                {
                    viewModel.delete(thread.id) { done ->
                        if (done) {
                            editing = null
                            opened = null
                        } else {
                            notice = "По этой нити уже что-то прошло — дела, строки, " +
                                "траты. Стереть её значит стереть месяцы, в которые она " +
                                "шла. Брось её: она уйдёт вниз, а история останется."
                        }
                    }
                }
            },
            onDismiss = { editing = null },
        )
    }

    notice?.let { text ->
        AskyaNotice(
            title = "Нить не пуста",
            text = text,
            onDismiss = { notice = null },
        )
    }
}

/**
 * Кучка ленты: заголовок и нити под ним.
 *
 * Пустая кучка не показывается вовсе — заголовок «Отложены» над пустотой
 * рассказывал бы о том, чего нет.
 */
private fun LazyListScope.part(
    title: String,
    rows: List<ThreadRow>,
    today: LocalDate,
    onOpen: (Long) -> Unit,
) {
    if (rows.isEmpty()) return

    item(key = "title-$title") {
        DayPartTitle(title, modifier = Modifier.padding(top = 10.dp))
    }
    items(rows, key = { row -> "thread-${row.thread.id}" }) { row ->
        ThreadTile(row = row, today = today, onClick = { onOpen(row.thread.id) })
    }
}

/**
 * Нить в ленте — карточка во всю ширину, а не плитка по трое.
 *
 * Счета и дела лежат в сетке по трое, но им нечего показывать, кроме числа. У
 * нити под именем полоска месяцев, и в трети экрана она превратилась бы в
 * шесть волосков. Ширина здесь не украшение, а то, ради чего в раздел заходят.
 */
@Composable
private fun ThreadTile(row: ThreadRow, today: LocalDate, onClick: () -> Unit) {
    val thread = row.thread
    val mark = markColor(thread.color, thread.title)
    val quiet = thread.state.closed || thread.state == ThreadState.PAUSED
    val asks = asksAbout(thread.state, row.pulse, today)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .cardEdge(RoundedCornerShape(20.dp))
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (quiet) Muted.copy(alpha = 0.45f) else mark),
                )
                Text(
                    text = thread.title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (quiet) Muted else Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                Text(
                    text = corner(row, today),
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                )
            }

            // Закрытая нить полоски не получает: пульс — это про то, жива ли
            // она, а у закрытой ответ уже дан словом.
            if (!thread.state.closed) {
                if (row.next.isNotBlank()) {
                    Text(
                        text = "Дальше — ${row.next}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                PulseStrip(
                    pulse = row.pulse,
                    dimmed = quiet,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "полгода · ${dayWord(row.pulse.days)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (asks) {
                val silence = row.pulse.silence(today) ?: 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentSoft)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${silenceWord(silence)} тишины",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentInk,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Отложить или бросить?",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentInk,
                    )
                }
            }
        }
    }
}

/**
 * Что стоит в правом верхнем углу карточки.
 *
 * У идущей — тишина: «вчера», «47 дней». У отложенной и закрытой числа нет,
 * там слово: спрашивать у отложенной нити, давно ли её трогали, незачем — её
 * отложили нарочно.
 */
private fun corner(row: ThreadRow, today: LocalDate): String = when {
    row.thread.state == ThreadState.PAUSED -> row.thread.due
        ?.let { "до ${formatRussianDate(it)}" }
        ?: ThreadState.PAUSED.title.lowercase()

    row.thread.state.closed -> row.thread.closedAt
        ?.let { formatRussianDate(it.toLocalDate()) }
        ?: row.thread.state.title.lowercase()

    else -> row.pulse.silence(today)?.let { silenceWord(it) } ?: "ещё не трогали"
}

/**
 * Полоска месяцев: шесть столбиков, пустой — огрызок в цвет подложки.
 *
 * Огрызок, а не пустое место: столбик нулевой высоты читается как пропущенный
 * месяц, а месяц, в который ничего не было, — это и есть новость. И не
 * приглушённый столбик в полроста: он выглядел бы работой, сделанной другим
 * цветом.
 *
 * Высота столбика — доля от самого высокого месяца, а не от какого-нибудь
 * «нормального» числа дней в месяц. Нормы у нити нет: одну ведут каждый день,
 * другую по субботам, и обе живы.
 */
@Composable
fun PulseStrip(
    pulse: ThreadPulse,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 28.dp,
    labels: Boolean = false,
) {
    val months = pulse.months.takeIf { it.isNotEmpty() }
        ?: List(PULSE_MONTHS) { app.askya.domain.model.ThreadMonth(java.time.YearMonth.now(), 0) }
    val tallest = months.maxOf { it.days }.coerceAtLeast(1)
    val ink = if (dimmed) Muted.copy(alpha = 0.4f) else Accent

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            months.forEach { month ->
                val share = month.days.toFloat() / tallest
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (month.days == 0) EMPTY_BAR else height * share)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (month.days == 0) MaterialTheme.colorScheme.surfaceVariant else ink,
                        ),
                )
            }
        }
        if (labels) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                months.forEach { month ->
                    Text(
                        text = MONTHS[month.month.monthValue - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Огрызок пустого месяца. Ниже уже не видно, выше читается как работа. */
private val EMPTY_BAR = 4.dp

/**
 * Месяцы тремя буквами. Своим списком, а не через `Locale`: язык приложения
 * всегда русский, а локаль устройства бывает любой — то же правило, что у дат
 * в AskyaDay.
 */
private val MONTHS = listOf(
    "янв", "фев", "мар", "апр", "май", "июн",
    "июл", "авг", "сен", "окт", "ноя", "дек",
)

/** «один день», «три дня», «пять дней» — по числу. */
internal fun dayWord(count: Int): String {
    val last = count % 10
    val hundred = count % 100
    val word = when {
        hundred in 11..14 -> "дней"
        last == 1 -> "день"
        last in 2..4 -> "дня"
        else -> "дней"
    }
    return "$count $word"
}
