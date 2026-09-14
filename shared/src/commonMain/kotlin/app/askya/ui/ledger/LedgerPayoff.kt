package app.askya.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.domain.model.Debt
import app.askya.domain.model.PayoffOrder
import app.askya.domain.model.PayoffTerm
import app.askya.domain.model.formatMoney
import app.askya.domain.model.formatRate
import app.askya.domain.model.payoff
import app.askya.domain.model.payoffQueue
import app.askya.ui.components.DayPartTitle
import app.askya.ui.components.formatMonthUntil
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Danger
import app.askya.ui.theme.Ink
import app.askya.ui.theme.ModeGreen
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge
import java.time.YearMonth

/**
 * «Как закрыть долг» — шесть сроков карточками.
 *
 * Стоит на странице «Счета» под самими счетами и появляется только тогда,
 * когда есть что гасить: у человека без долгов эта половина экрана была бы
 * упрёком ни за что. Отдельной страницей не сделан нарочно: долг живёт на
 * счету, и ходить за планом в другое место значило бы разлучить вопрос с
 * ответом.
 *
 * Считает [app.askya.domain.model.payoff] — там же написано, почему сроками, а
 * не платежами, и почему аннуитет.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayoffBlock(debts: List<Debt>, modifier: Modifier = Modifier) {
    if (debts.isEmpty()) return

    // Который долг считаем. `null` — все разом: у человека с картой и займом
    // вопрос «сколько платить в месяц» обычно про обе суммы сразу.
    var chosen by remember(debts.map { it.id }) {
        mutableStateOf(if (debts.size == 1) debts.first().id else null)
    }
    var order by remember { mutableStateOf(PayoffOrder.SNOWBALL) }

    val picked = debts.firstOrNull { it.id == chosen }
    val amount = picked?.amount ?: debts.sumOf { it.amount }
    val rate = picked?.rate ?: blendedRate(debts)
    // Отсчёт от месяца, в котором открыли экран: пересчитывать его в полночь
    // незачем, а карточки от этого не переедут под пальцем.
    val from = remember { YearMonth.now() }

    Column(modifier = modifier.fillMaxWidth()) {
        DayPartTitle("Как закрыть долг", modifier = Modifier.padding(top = 16.dp))

        if (debts.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                PickWord(text = "Все долги", picked = chosen == null, onClick = { chosen = null })
                debts.forEach { debt ->
                    PickWord(
                        text = debt.title,
                        picked = chosen == debt.id,
                        onClick = { chosen = debt.id },
                    )
                }
            }
        }

        Text(
            text = buildString {
                append("Гасим ")
                append(formatMoney(amount))
                if (rate > 0) {
                    append(" под ")
                    append(formatRate(rate))
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Карточки по двое в ряд — той же сеткой, что и счета над ними: это
        // такие же карточки, к которым ходят глазами, а не строки списка.
        PayoffTerm.entries.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { term ->
                    val plan = payoff(amount, rate, term.months, from)
                    if (plan == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        PayoffTile(
                            title = term.title,
                            monthly = plan.monthly,
                            overpay = plan.overpay,
                            until = plan.until,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Text(
            text = if (rate > 0) {
                "Платёж считается как в банке: равными долями, проценты — на остаток. " +
                    "Последний месяц обычно выходит чуть меньше прочих."
            } else {
                "Ставка у этого счёта не записана, и план считает без процентов: " +
                    "это долг, поделённый на месяцы. Впишите ставку в карточке счёта — " +
                    "и здесь появится переплата."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Порядок нужен только тому, у кого долгов больше одного: с
        // единственным выбирать не из чего.
        if (debts.size > 1) {
            DayPartTitle("С какого начать", modifier = Modifier.padding(top = 20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PayoffOrder.entries.forEach { option ->
                    OrderTile(
                        title = option.title,
                        picked = order == option,
                        onClick = { order = option },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Text(
                text = order.about,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )

            payoffQueue(debts, order).forEachIndexed { index, debt ->
                QueueRow(place = index + 1, debt = debt)
            }

            Text(
                text = "По всем долгам платят обязательный минимум, а всё, что сверх него, " +
                    "кладут на первый в очереди. Закрылся — тем же весом наваливаются " +
                    "на следующий.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Средняя ставка по нескольким долгам — взвешенная суммой, а не простая.
 *
 * Сто тысяч под 25 % и тысяча под нулём — это не «12,5 % годовых»: маленький
 * долг почти ничего не решает, и простое среднее занизило бы переплату вдвое.
 */
private fun blendedRate(debts: List<Debt>): Int {
    val total = debts.sumOf { it.amount }
    if (total <= 0) return 0
    val weighted = debts.sumOf { it.amount * it.rate.toLong() }
    return (weighted / total).toInt()
}

/**
 * Карточка срока: платёж крупно, переплата и последний месяц под ним.
 *
 * Платёж — самое крупное на карточке, потому что решение принимают по нему:
 * «столько в месяц я потяну, а столько нет». Переплата стоит рядом и красным:
 * это второе, что спрашивают, и единственное, чем быстрый план отличается от
 * медленного, кроме самого платежа.
 */
@Composable
private fun PayoffTile(
    title: String,
    monthly: Long,
    overpay: Long,
    until: YearMonth,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier.cardEdge(RoundedCornerShape(18.dp)).height(150.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = AccentInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMoney(monthly),
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = "в месяц",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (overpay > 0) "сверху ${formatMoney(overpay)}" else "без процентов",
                style = MaterialTheme.typography.labelSmall,
                color = if (overpay > 0) Danger else ModeGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMonthUntil(until),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Карточка способа: имя способа и то, выбран ли он сейчас. */
@Composable
private fun OrderTile(
    title: String,
    picked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (picked) AccentSoft else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .cardEdge(RoundedCornerShape(16.dp)),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (picked) AccentInk else Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Нажатие внутри карточки, а не на ней: иначе подсветка от него
            // квадратная (см. [app.askya.ui.scroll.BookTile]).
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        )
    }
}

/** Строка очереди: место, имя долга и сколько по нему висит. */
@Composable
private fun QueueRow(place: Int, debt: Debt) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "$place",
            style = MaterialTheme.typography.labelLarge,
            color = AccentInk,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AccentSoft)
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
        Text(
            text = debt.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (debt.rate > 0) {
                formatMoney(debt.amount) + " · " + formatRate(debt.rate)
            } else {
                formatMoney(debt.amount)
            },
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            maxLines = 1,
        )
    }
}

/** Слово-выбор — то же, каким выбирают счёт в записи. */
@Composable
private fun PickWord(text: String, picked: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (picked) AccentInk else Accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (picked) AccentSoft else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
