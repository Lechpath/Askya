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
import app.askya.domain.model.suggestState
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
 * Threads — нити: замыслы, которые растут неделями и тянутся через разделы.
 *
 * ## Раздел из двух слоёв
 *
 * Верхний — эта лента: все нити разом и один вопрос к каждой, «жива ли она».
 * Нижний — карта замысла (`ThreadMapScreen`), куда лента ведёт по касанию: там
 * искры, пути, подводные камни и шаги, и там думают.
 *
 * Слоя два, потому что вопросов два, и задают их в разное время. «Как оно
 * вообще идёт» спрашивают походя, на бегу, глядя в список; «а что если пойти
 * другим путём» — сев и открыв одну нить. Свалив оба в один экран, получаешь
 * либо список, в котором нельзя думать, либо карту, по которой нельзя окинуть
 * взглядом всё сразу.
 *
 * ## Что говорит лента
 *
 * Состояние словом, последнее касание, полоска месяцев и ближайший
 * несделанный шаг. Процента готовности здесь нет и не будет: его нельзя
 * назвать честно, а «47 дней назад» — факт, который лежит в базе.
 *
 * Полоска — та же арифметика, что в «Прожитом»: считаются дни, в которые
 * что-то происходило, а не события. День, в который отметили дело, записали
 * трату и разложили три узла, — один день работы.
 *
 * ## Тихая догадка
 *
 * Под полоской иногда встаёт строка: «похоже, нить тлеет — сменить?». Это
 * догадка по карте и пульсу ([suggestState]), а не решение: состояние нити —
 * отношение человека к замыслу, и переставлять его за него приложение не
 * вправе. Не уведомление, не значок и не красное: Askya не канючит.
 */
@Composable
fun ThreadsScreen(onOpenMenu: () -> Unit, onOpenThread: (Long) -> Unit) {
    val container = appContainer()
    val viewModel: ThreadsViewModel = viewModel(factory = ThreadsViewModel.factory(container))

    val rows by viewModel.rows.collectAsStateWithLifecycle()

    // Правится нить или заводится новая. Раскрытой карточки у ленты больше нет:
    // касание по нити ведёт в её карту, а «как оно идёт» карта показывает сама.
    var editing by remember { mutableStateOf<ThreadItem?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val today = LocalDate.now()
    val live = rows.filter { it.thread.state.running }
    val quiet = rows.filter { it.thread.state.quiet }
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
                hint = "Нить — это замысел, который растёт неделями: выучить язык, " +
                    "доделать ремонт, записать альбом. Он начинается с искры, обрастает " +
                    "путями, спотыкается о подводные камни и понемногу превращается в " +
                    "шаги — всё это раскладывается картой внутри нити. Askya не считает " +
                    "проценты готовности: их нельзя назвать честно. Она показывает, когда " +
                    "ты трогал нить в последний раз, и тянет к ней дела, списки и траты.",
            )
        } else {
            FadingColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                part("Идут", live, today, onOpen = onOpenThread)
                part("Затихли", quiet, today, onOpen = onOpenThread)
                part("Закрыты", closed, today, onOpen = onOpenThread)

                item(key = "tail") { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    editing?.let { thread ->
        ThreadEditCard(
            thread = thread,
            onSave = { made ->
                val fresh = made.id == 0L
                viewModel.save(made) { id ->
                    // Заведённая нить открывается сразу: у неё пустая карта и
                    // одна кнопка — «первый узел», а возвращать человека в
                    // ленту значило бы заставить его искать глазами то, что он
                    // только что назвал.
                    if (fresh && id > 0) onOpenThread(id)
                }
                editing = null
            },
            onDelete = if (thread.id == 0L) null else {
                {
                    viewModel.delete(thread.id) { done ->
                        if (done) {
                            editing = null
                        } else {
                            notice = "По этой нити уже что-то прошло — узлы карты, дела, " +
                                "строки, траты. Стереть её значит стереть месяцы, в " +
                                "которые она шла. Брось её: она уйдёт вниз, а история " +
                                "останется."
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
 * Кучки три, а состояний семь: горящее, растущее и плетущееся стоят вместе под
 * «идут», тлеющее и спящее — под «затихли». Семь заголовков разрезали бы ленту
 * из четырёх нитей на семь кусков по одной.
 *
 * Пустая кучка не показывается вовсе — заголовок «Затихли» над пустотой
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
    val dim = thread.state.closed || thread.state.quiet
    val guess = suggestState(thread.state, row.pulse, row.signs, today)
    val asks = guess == null && asksAbout(thread.state, row.pulse, today)

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
                // Краска нити и значок состояния стоят рядом и говорят разное:
                // первая отвечает «которая из них», второй — «что с ней».
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (dim) Muted.copy(alpha = 0.45f) else mark),
                )
                Text(
                    text = thread.title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (dim) Muted else Ink,
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Icon(
                    imageVector = stateIcon(thread.state),
                    contentDescription = null,
                    tint = if (dim) Muted else stateColor(thread.state),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = thread.state.title + nodeWord(row.signs.nodes),
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                    modifier = Modifier.padding(start = 6.dp),
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
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                PulseStrip(
                    pulse = row.pulse,
                    dimmed = dim,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "полгода · ${dayWord(row.pulse.days)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (guess != null) {
                Quiet(
                    left = "Похоже, нить ${guess.title.lowercase()}",
                    right = "Открыть",
                )
            } else if (asks) {
                val silence = row.pulse.silence(today) ?: 0
                Quiet(
                    left = "${silenceWord(silence)} тишины",
                    right = "Отложить или бросить?",
                )
            }
        }
    }
}

/**
 * Тихая строка под полоской: догадка о состоянии или вопрос о тишине.
 *
 * Плашка приглушённого цвета акцента, обычным кеглем, без восклицаний. Нажатие
 * по ней ничего не делает отдельно: вся карточка ведёт в нить, а состояние
 * меняют там, где на него смотрят.
 */
@Composable
private fun Quiet(left: String, right: String) {
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
            text = left,
            style = MaterialTheme.typography.bodySmall,
            color = AccentInk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = right,
            style = MaterialTheme.typography.labelMedium,
            color = AccentInk,
        )
    }
}

/**
 * Что стоит в правом верхнем углу карточки.
 *
 * У идущей — тишина: «вчера», «47 дней». У затихшей и закрытой числа нет, там
 * слово или день: спрашивать у спящей нити, давно ли её трогали, незачем — её
 * отложили нарочно.
 */
private fun corner(row: ThreadRow, today: LocalDate): String = when {
    row.thread.state == ThreadState.SLEEPING -> row.thread.due
        ?.let { "до ${formatRussianDate(it)}" }
        ?: "спит"

    row.thread.state.closed -> row.thread.closedAt
        ?.let { formatRussianDate(it.toLocalDate()) }
        ?: row.thread.state.title.lowercase()

    else -> row.pulse.silence(today)?.let { silenceWord(it) } ?: "ещё не трогали"
}

/** «· 14 узлов» — или ничего, если карта пуста. */
private fun nodeWord(count: Int): String {
    if (count <= 0) return ""
    val last = count % 10
    val hundred = count % 100
    val word = when {
        hundred in 11..14 -> "узлов"
        last == 1 -> "узел"
        last in 2..4 -> "узла"
        else -> "узлов"
    }
    return " · $count $word"
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
