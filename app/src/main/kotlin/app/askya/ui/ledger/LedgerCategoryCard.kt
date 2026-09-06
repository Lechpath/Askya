package app.askya.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.askya.data.entity.LedgerAccount
import app.askya.data.entity.LedgerCategory
import app.askya.data.entity.LedgerEntry
import app.askya.domain.model.EntryKind
import app.askya.domain.model.formatMoney
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogTitle
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.components.formatMonthTitle
import app.askya.ui.components.formatRussianDate
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted
import java.time.YearMonth

/**
 * Статья, развёрнутая в записи: когда и сколько по ней прошло.
 *
 * ## Зачем
 *
 * Строка «Еда — 18 400 ₽» отвечает на «сколько», но не на «откуда столько».
 * Проверить её было нечем: лента месяца лежит ниже, вперемешку со всеми
 * прочими тратами, и, чтобы собрать по ней одну статью, человек листал месяц
 * глазами и складывал в уме — то есть делал руками ровно то, ради чего книгу
 * и ведут. Теперь тап по статье раскрывает её саму: те же записи, только по
 * одной этой графе и по дням.
 *
 * Месяц тот, что открыт на странице, и назван он подписью под именем статьи:
 * окно, показывающее не тот месяц, который человек листал, врало бы молча.
 *
 * ## Возвраты стоят здесь же
 *
 * Не отдельным списком и не спрятанными: возврат — это трата со знаком минус
 * (см. [EntryKind.BACK]), и в статье он именно то, из-за чего её сумма меньше
 * суммы покупок. Строка возврата подписана словом и стоит на своей дате, а
 * итог под названием сходится с той же цифрой, что и в строке статьи, — так
 * видно движение денег целиком, а не один его конец.
 *
 * ## Правка статьи ушла под значок
 *
 * Раньше тап по статье открывал её настройки — название, предел, краску.
 * Спрашивают статью не за этим: предел ставят однажды, а «сколько и когда»
 * смотрят каждую неделю. Настройки остались на месте, только одним действием
 * дальше — значком «Статья» внизу окна.
 */
@Composable
fun CategoryEntriesCard(
    category: LedgerCategory,
    month: YearMonth,
    entries: List<LedgerEntry>,
    accounts: Map<Long, LedgerAccount>,
    onOpenEntry: (LedgerEntry) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spending = category.kind == EntryKind.SPEND
    // Тот же счёт, что и в строке статьи: возврат вычитается из траты.
    val total = entries.sumOf { entry ->
        if (entry.kind == EntryKind.BACK) -entry.amount else entry.amount
    }
    val returned = entries.filter { it.kind == EntryKind.BACK }.sumOf { it.amount }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Sell) }) {
        DialogTitle(category.title.ifBlank { "Статья" })
        DialogCaption(formatMonthTitle(month))

        Text(
            text = formatMoney(total),
            style = MaterialTheme.typography.headlineSmall,
            color = if (spending) Ink else AccentInk,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (returned > 0) {
            Text(
                text = "Из них вернули ${formatMoney(returned)} — на столько статья и уменьшилась.",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (spending && category.limit > 0) {
            Text(
                text = "Предел на месяц ${formatMoney(category.limit)}",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (entries.isEmpty()) {
            Text(
                text = "В этом месяце по этой статье ничего не прошло.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            // Список прокручивается внутри окна — как выбор дел в AskyaDay: по
            // одной статье за месяц бывает три десятка записей, и окно во весь
            // экран из-за них перестало бы быть окном. Края тают, как у всех
            // прокруток приложения (`fadingVerticalScroll`).
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .heightIn(max = 300.dp)
                    .fadingVerticalScroll(),
            ) {
                entries.forEach { entry ->
                    CategoryEntryRow(
                        entry = entry,
                        account = accounts[entry.accountId]?.title.orEmpty(),
                        onClick = { onOpenEntry(entry) },
                    )
                }
            }
        }

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Tune,
                label = "Статья",
                onClick = onEdit,
            )
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Готово",
                accent = true,
                onClick = onDismiss,
            )
        }
    }
}

/**
 * Одна запись статьи: день слева, сумма справа.
 *
 * Дата стоит первой, потому что в раскрытой статье спрашивают именно её:
 * «сколько» сказано итогом выше, а вопрос «когда» — весь оставшийся. Строка
 * нажимается и открывает саму запись: увидев в статье лишнее, человек хочет
 * поправить это здесь же, а не искать ту же трату в ленте месяца.
 */
@Composable
private fun CategoryEntryRow(entry: LedgerEntry, account: String, onClick: () -> Unit) {
    val back = entry.kind == EntryKind.BACK
    val under = listOf(account, entry.note).filter { it.isNotBlank() }.joinToString(" · ")
    val day = formatRussianDate(entry.date)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (back) "$day · Возврат" else day,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (under.isNotBlank()) {
                Text(
                    text = under,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = if (back) formatMoney(entry.amount, withSign = true) else formatMoney(entry.amount),
            style = MaterialTheme.typography.titleSmall,
            // Возврат — коралловым, как в ленте: зелёное в книге значит
            // «заработал», а возврат не заработок, а отменившаяся трата.
            color = if (back) AccentInk else Ink,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
