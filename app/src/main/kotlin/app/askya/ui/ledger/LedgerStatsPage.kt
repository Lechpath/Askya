package app.askya.ui.ledger

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.data.entity.LedgerCategory
import app.askya.data.repository.LedgerStats
import app.askya.data.repository.MonthTotal
import app.askya.domain.model.EntryKind
import app.askya.domain.model.formatMoney
import app.askya.ui.components.DayPartTitle
import app.askya.ui.components.EmptyState
import app.askya.ui.components.formatMonthShort
import app.askya.ui.components.formatMonthTitle
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Danger
import app.askya.ui.theme.Ink
import app.askya.ui.theme.ModeGreen
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge
import app.askya.ui.theme.markColor
import java.time.YearMonth

/**
 * Статистика: то же, что на странице месяца, но за все прожитые месяцы разом.
 *
 * ## Зачем третья страница
 *
 * «Доход/Расход» отвечает про один месяц и листается по одному. Вопросы
 * «сколько я вообще трачу», «стало ли лучше», «куда уходит из года в год»
 * в неё не помещаются вовсе: чтобы ответить на них, пришлось бы пролистать
 * двенадцать месяцев и сложить их в уме. Здесь они сложены.
 *
 * ## Срок выбирается словом, а не листается
 *
 * Полгода, год, всё время. Не «с такого-то по такое-то»: две даты — это уже
 * запрос к базе, который человек составляет руками, а спрашивают почти всегда
 * одно из трёх. Срок отрезает старые месяцы у уже посчитанного
 * ([LedgerStats.since]), поэтому переключается он мгновенно и без похода в
 * базу.
 *
 * ## Средние считаются по прожитым месяцам
 *
 * Не по длине срока. Тот, кто ведёт книгу третий месяц, выбрав «год», увидит
 * среднее за свои три, а не своё же, делённое на двенадцать. Пустой месяц —
 * это «книгу тогда не вели», а не «ничего не потратил», и столбиком в ноль он
 * тоже не рисуется.
 */
@Composable
fun StatsPage(stats: LedgerStats, categories: List<LedgerCategory>) {
    var span by remember { mutableStateOf(StatsSpan.YEAR) }

    if (stats.empty) {
        EmptyState(
            title = "Считать пока нечего",
            hint = "Статистика собирается из записей. Заведите первую на странице " +
                "«Доход/Расход» — и здесь появятся месяцы, средние и статьи.",
        )
        return
    }

    val slice = remember(stats, span) { stats.since(span.since()) }
    val names = remember(categories) { categories.associate { it.id to it } }

    // fillMaxSize, а не по содержимому: страница пейджера ставит содержимое
    // по середине, и короткий свиток повисал бы посреди пустого экрана.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "span") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatsSpan.entries.forEach { option ->
                    SpanWord(
                        title = option.title,
                        picked = span == option,
                        onClick = { span = option },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Срок выбран, а месяцев в нём нет: книгу вели раньше и бросили.
        // Сказать это словами честнее, чем показать три нуля.
        if (slice.empty) {
            item(key = "empty-span") {
                Text(
                    text = "За этот срок записей нет. Выберите «Всё время» — " +
                        "прошлые месяцы никуда не делись.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            return@LazyColumn
        }

        item(key = "totals") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Total(title = "Пришло", value = slice.earned, color = ModeGreen)
                Total(title = "Ушло", value = slice.spent, color = Ink)
                Total(
                    title = "Осталось",
                    value = slice.left,
                    color = if (slice.left < 0) Danger else AccentInk,
                )
            }
        }

        // Та же строчка, что и на странице месяца: «ушло» здесь уже без
        // возвратов, и сказать об этом надо там же, где показано число.
        if (slice.returned > 0) {
            item(key = "returned") {
                Text(
                    text = "Из «ушло» вычтено " + formatMoney(slice.returned) + " возвратов.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        item(key = "average") {
            AverageCard(
                months = slice.livedMonths,
                earned = slice.averageEarned,
                spent = slice.averageSpent,
            )
        }

        // Столбики нужны, когда месяцев хотя бы два: один столбик не с чем
        // сравнивать, а сравнение — единственное, ради чего их рисуют.
        if (slice.months.size > 1) {
            item(key = "chart-title") {
                DayPartTitle("По месяцам", modifier = Modifier.padding(top = 12.dp))
            }
            item(key = "chart") { MonthsChart(months = slice.months) }
        }

        val spent = slice.byCategory(EntryKind.SPEND)
            .toList()
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        if (spent.isNotEmpty()) {
            item(key = "spend-title") {
                DayPartTitle("Куда уходит", modifier = Modifier.padding(top = 12.dp))
            }
            val most = spent.first().second
            items(spent, key = { "spend-" + (it.first ?: 0L) }) { (id, amount) ->
                val category = id?.let { names[it] }
                ShareRow(
                    title = category?.title ?: "Без статьи",
                    amount = amount,
                    share = amount.toFloat() / most.toFloat(),
                    part = if (slice.spent > 0) amount.toFloat() / slice.spent.toFloat() else 0f,
                    color = markColor(category?.color, category?.title ?: "Без статьи"),
                )
            }
        }

        val earned = slice.byCategory(EntryKind.EARN)
            .toList()
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        if (earned.isNotEmpty()) {
            item(key = "earn-title") {
                DayPartTitle("Откуда приходит", modifier = Modifier.padding(top = 12.dp))
            }
            val most = earned.first().second
            items(earned, key = { "earn-" + (it.first ?: 0L) }) { (id, amount) ->
                val category = id?.let { names[it] }
                ShareRow(
                    title = category?.title ?: "Без статьи",
                    amount = amount,
                    share = amount.toFloat() / most.toFloat(),
                    part = if (slice.earned > 0) amount.toFloat() / slice.earned.toFloat() else 0f,
                    color = markColor(category?.color, category?.title ?: "Без статьи"),
                )
            }
        }

        // Крайние месяцы — только когда их есть из чего выбирать: у одного
        // месяца «самый дорогой» и «самый скромный» — это он же.
        val richest = slice.richest
        val leanest = slice.leanest
        if (slice.months.size > 2 && richest != null && leanest != null && richest != leanest) {
            item(key = "edges-title") {
                DayPartTitle("Края", modifier = Modifier.padding(top = 12.dp))
            }
            item(key = "edges") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EdgeCard(
                        title = "Дороже всех",
                        month = richest,
                        color = Danger,
                        modifier = Modifier.weight(1f),
                    )
                    EdgeCard(
                        title = "Скромнее всех",
                        month = leanest,
                        color = ModeGreen,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item(key = "tail") { Spacer(Modifier.height(96.dp)) }
    }
}

/**
 * За какой срок смотрим.
 *
 * Три ответа, и «всё время» среди них не запасное: книга, которую ведут третий
 * год, отвечает на «куда уходит» совсем не так, как её же последние полгода.
 */
private enum class StatsSpan(val title: String, val months: Long?) {
    HALF("Полгода", 6),
    YEAR("Год", 12),
    ALL("Всё время", null),
    ;

    /** С какого месяца считать. `null` — с самого начала книги. */
    fun since(): YearMonth? = months?.let { YearMonth.now().minusMonths(it - 1) }
}

/** Слово-выбор срока: во всю свою треть ряда, как вкладка. */
@Composable
private fun SpanWord(
    title: String,
    picked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (picked) AccentInk else Muted,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (picked) AccentSoft else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
    )
}

/**
 * «В среднем в месяц» — два числа и то, по скольким месяцам они посчитаны.
 *
 * Число месяцев подписано нарочно: среднее за три месяца и среднее за три года
 * — разной крепости утверждения, и по одному числу их не отличить.
 */
@Composable
private fun AverageCard(months: Int, earned: Long, spent: Long) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth().cardEdge(RoundedCornerShape(18.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = "В среднем в месяц",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatMoney(earned),
                        fontFamily = FontFamily.Serif,
                        fontSize = 19.sp,
                        color = ModeGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "приходит",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatMoney(spent),
                        fontFamily = FontFamily.Serif,
                        fontSize = 19.sp,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "уходит",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }
            Text(
                text = "Посчитано по " + countOfMonths(months),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** «трём месяцам», «одиннадцати месяцам» — без числительных словами. */
private fun countOfMonths(months: Int): String {
    val hundred = months % 100
    val ten = months % 10
    val word = when {
        hundred in 11..14 -> "месяцам"
        ten == 1 -> "месяцу"
        else -> "месяцам"
    }
    return "$months $word"
}

/**
 * Столбики месяцев: пришло и ушло рядом, месяц за месяцем.
 *
 * Два столбика на месяц, а не один со знаком: доход и расход — разные числа, и
 * их разность («осталось») человек видит по тому, который выше, не читая
 * подписей. Все столбики меряются от самого высокого числа во всём ряду, иначе
 * месяцы нельзя было бы сравнивать глазами — а больше их сравнивать нечем.
 *
 * Ряд прокручивается вбок и начинается с последнего месяца — с того, который
 * спрашивают. Года над столбиками нет: он написан под рядом, одной строкой на
 * весь срок.
 */
@Composable
private fun MonthsChart(months: List<MonthTotal>) {
    val most = months.maxOf { maxOf(it.earned, it.spent) }.coerceAtLeast(1L)
    // Ряд открывается на последних месяцах: спрашивают про них, а старые
    // ждут слева, если за ними полезут.
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (months.size - 6).coerceAtLeast(0),
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth().cardEdge(RoundedCornerShape(18.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(months, key = { it.month.toString() }) { total ->
                    MonthColumn(total = total, most = most)
                }
            }

            Text(
                text = span(months),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp),
            )
        }
    }
}

/** Подпись под рядом: от какого месяца до какого он идёт. */
private fun span(months: List<MonthTotal>): String {
    val first = months.first().month
    val last = months.last().month
    return if (first == last) {
        formatMonthTitle(first)
    } else {
        formatMonthTitle(first) + " — " + formatMonthTitle(last)
    }
}

/** Один месяц: два столбика и три буквы под ними. */
@Composable
private fun MonthColumn(total: MonthTotal, most: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.height(96.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Bar(value = total.earned, most = most, color = ModeGreen)
            Bar(value = total.spent, most = most, color = Ink)
        }
        Text(
            text = formatMonthShort(total.month),
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Столбик. Пустой месяц не рисуется вовсе, а ненулевой — не тоньше двух точек:
 * тысяча рублей рядом с сотней тысяч всё-таки была, и в ноль ей сжиматься
 * нельзя.
 */
@Composable
private fun Bar(value: Long, most: Long, color: Color) {
    val share = (value.toFloat() / most.toFloat()).coerceIn(0f, 1f)
    val height = if (value <= 0) 0.dp else (96 * share).dp.coerceAtLeast(2.dp)

    Box(
        modifier = Modifier
            .width(11.dp)
            .height(height)
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .background(color),
    )
}

/**
 * Строка статьи в статистике: краска, название, доля от всего и сумма.
 *
 * Доля процентами, а не одной полоской: «еда — треть всего» человек помнит
 * дольше, чем длину полоски, а полоска под ней даёт ту же мысль без чтения.
 * Полоска мерится от самой большой статьи, а не от всей суммы: иначе у того,
 * кто тратит ровно, все полоски были бы одинаково короткими.
 */
@Composable
private fun ShareRow(
    title: String,
    amount: Long,
    share: Float,
    part: Float,
    color: Color,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth().cardEdge(RoundedCornerShape(14.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                Text(
                    text = formatMoney(amount),
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                )
            }

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
                        .background(color),
                )
            }
            Text(
                text = percent(part) + " от всего",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Доля процентами. Меньше половины процента пишется «меньше 1 %», а не «0 %»:
 * ноль означал бы, что этой траты не было вовсе.
 */
private fun percent(part: Float): String {
    val value = (part * 100).toInt()
    return if (value < 1) "меньше 1 %" else "$value %"
}

/** Крайний месяц: как называется и сколько в нём ушло. */
@Composable
private fun EdgeCard(
    title: String,
    month: MonthTotal,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier.cardEdge(RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
            Text(
                text = formatMonthTitle(month.month),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = formatMoney(month.spent),
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
