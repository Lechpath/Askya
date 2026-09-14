package app.askya.ui.askyaday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.MONTHS
import app.askya.ui.components.formatMonthTitle
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentSoft
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import java.time.LocalDate
import java.time.YearMonth

/**
 * Карточка месяца: посмотреть далеко вперёд и назначить дело туда.
 *
 * До неё в AskyaDay был один способ добраться до дня — свайпом, по дню за раз.
 * Для «завтра» и «послезавтра» этого хватает, а для «через месяц у Кати
 * защита» — нет: тридцать свайпов человек не сделает, и дело останется
 * незаписанным.
 *
 * Карточка, а не отдельный экран: месяц смотрят мельком, чтобы ткнуть в число
 * и вернуться. Экран потребовал бы ухода из дня и возвращения в него, а это
 * ровно то, от чего календарь и должен избавить.
 *
 * ## Что видно
 *
 * Числа месяца сеткой, неделя с понедельника. Под числами, в которых что-то
 * записано, стоит точка — по ней видно занятость месяца, не открывая ни одного
 * дня. Сегодняшнее число обведено, выбранное залито.
 *
 * Точки считаются одним запросом на весь показанный месяц
 * (`ScheduleDao.observeDatesBetween`), а не чтением каждого дня по отдельности.
 *
 * ## Что делается
 *
 * Два ответа, и оба — про выбранное число: открыть этот день или сразу
 * записать в него дело. Открыть — чтобы посмотреть, записать — чтобы не
 * смотреть вовсе; второе и есть то, ради чего календарь заводят.
 *
 * Нажатие по числу само по себе ничего не открывает: оно выбирает. Иначе
 * ткнувший мимо оказывался бы в чужом дне вместо того, чтобы поправить выбор.
 */
@Composable
fun MonthCard(
    today: LocalDate,
    initial: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
    onNewCard: (LocalDate) -> Unit,
    onLived: () -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var chosen by remember { mutableStateOf(initial) }

    val schedule = appContainer().scheduleRepository
    // Границы берутся по показанной сетке, а не по месяцу: в первой и
    // последней строке стоят числа соседних месяцев, и точки им нужны тоже.
    val from = month.atDay(1).minusDays(WEEK.toLong())
    val to = month.atEndOfMonth().plusDays(WEEK.toLong())
    val busy by remember(from, to) { schedule.busyDates(from, to) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val busyDays = remember(busy) { busy.toSet() }

    AskyaDialog(
        onDismiss = onDismiss,
        width = 0.92f,
        badge = { app.askya.ui.components.DialogBadge(Icons.Outlined.CalendarMonth) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIcon(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "Прошлый месяц",
                onClick = { month = month.minusMonths(1) },
            )
            Text(
                text = formatMonthTitle(month),
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            HeaderIcon(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Следующий месяц",
                onClick = { month = month.plusMonths(1) },
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Сетка строится от понедельника той недели, в которую попало первое
        // число: у месяца, начавшегося в четверг, первая строка неполная, и
        // считать её отступами значило бы рисовать пустоту руками.
        val first = month.atDay(1)
        val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
        val weeks = ((month.lengthOfMonth() + (first.dayOfWeek.value - 1) + WEEK - 1) / WEEK)

        repeat(weeks) { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(WEEK) { weekday ->
                    val date = start.plusDays((week * WEEK + weekday).toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        today = date == today,
                        chosen = date == chosen,
                        busy = date in busyDays,
                        onClick = { chosen = date },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text(
            text = "Выбрано: ${chosen.dayOfMonth} ${MONTHS[chosen.monthValue - 1]}" +
                if (chosen.year != today.year) " ${chosen.year}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            textAlign = TextAlign.Center,
        )

        // Вход в «Прожитое» — отсюда, а не из шапки дня: календарь и есть то
        // место, откуда смотрят назад, а пятая кнопка в шапке AskyaDay уже не
        // помещается и не читается.
        Text(
            text = "Прожитое — дела, за которыми слежу",
            style = MaterialTheme.typography.labelLarge,
            color = Accent,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onLived)
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
        )

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.CalendarMonth,
                label = "Открыть день",
                onClick = { onOpenDay(chosen) },
            )
            ActionButton(
                icon = Icons.Outlined.Add,
                label = "Записать дело",
                accent = true,
                onClick = { onNewCard(chosen) },
            )
        }
    }
}

/**
 * Одно число в сетке.
 *
 * Числа соседних месяцев показаны бледными, а не спрятаны: неделя не
 * кончается вместе с месяцем, и «понедельник» в последней строке должен
 * читаться понедельником, а не пустой клеткой.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    today: Boolean,
    chosen: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = when {
        chosen -> MaterialTheme.colorScheme.onPrimaryContainer
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.onBackground
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .then(if (chosen) Modifier.background(AccentSoft) else Modifier)
            .then(
                if (today && !chosen) Modifier.border(1.5.dp, Accent, CircleShape) else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (today || chosen) FontWeight.SemiBold else FontWeight.Normal,
                color = ink,
            )
            // Точка — про занятость, а не про количество: три дела и тридцать
            // выглядят одинаково, потому что в календаре это одно и то же —
            // «день не пустой».
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (busy) Accent else androidx.compose.ui.graphics.Color.Transparent),
            )
        }
    }
}

private val WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

private const val WEEK = 7
