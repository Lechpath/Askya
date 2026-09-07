package app.askya.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Check
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
import app.askya.domain.model.AccountKind
import app.askya.domain.model.Currency
import app.askya.domain.model.EntryKind
import app.askya.domain.model.creditLeft
import app.askya.domain.model.debtOf
import app.askya.domain.model.formatMoney
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogTitle
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.components.formatRussianDate
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.Danger
import app.askya.ui.theme.Ink
import app.askya.ui.theme.ModeGreen
import app.askya.ui.theme.Muted
import java.time.LocalDate

/**
 * Счёт, развёрнутый в записи: движение средств по нему.
 *
 * ## Зачем
 *
 * Тап по карточке счёта открывал её настройки — название, вид, краску. Но
 * смотрят на счёт не за этим. Настройки правят однажды, а вопрос «почему на
 * карте не то, что я думал» задают каждую неделю, и до сих пор отвечать на
 * него приходилось листанием общей ленты месяца: все траты вперемешку, свою
 * карту в них надо было отыскивать глазами строка за строкой.
 *
 * Теперь тап раскрывает сам счёт: его остаток, из чего он сложился, и лента —
 * только по нему. Настройки остались на месте, одним действием дальше —
 * значком «Счёт» внизу окна. Тот же порядок, что у статьи
 * ([CategoryEntriesCard]): раскрытое показывает, а правка стоит за отдельной
 * кнопкой.
 *
 * ## Лента не по месяцу
 *
 * У статьи лента месячная — статья и живёт месяцем, у неё месячный предел. У
 * счёта месяца нет: на нём лежит то, что лежит сейчас, и «куда делись деньги»
 * не кончается первым числом. Поэтому здесь всё подряд, свежим сверху, с
 * потолком в три сотни строк (`LedgerRepository.onAccount`).
 *
 * ## Перевод виден с той стороны, с какой он случился
 *
 * Одна и та же запись для двух счетов значит разное: с карты ушло, в кошелёк
 * пришло. Знак поэтому считается не по виду записи, а по тому, чей это счёт, —
 * см. [movementOf].
 */
@Composable
fun AccountEntriesCard(
    account: LedgerAccount,
    amount: Long,
    entries: List<LedgerEntry>,
    accounts: Map<Long, LedgerAccount>,
    categories: Map<Long, LedgerCategory>,
    onOpenEntry: (LedgerEntry) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currency = account.currency
    val owed = account.kind.owed
    val debt = debtOf(amount)

    AskyaDialog(
        onDismiss = onDismiss,
        badge = { DialogBadge(Icons.Outlined.AccountBalanceWallet) },
    ) {
        DialogTitle(account.title.ifBlank { "Счёт" })
        DialogCaption(
            listOfNotNull(
                account.kind.title,
                // Валюта подписывается только у валютного счёта: рубль и так
                // виден в каждой сумме, а строка «Рубли» под кошельком
                // сообщала бы новость всем, у кого её нет.
                currency.title.takeIf { !currency.main },
                "закрыт".takeIf { account.closed },
            ).joinToString(" · "),
        )

        // Наверху — то самое число, за которым сюда пришли. У долгового счёта
        // это долг без минуса: спрашивают у него «сколько я должен».
        Text(
            text = formatMoney(if (owed) debt else amount, currency = currency),
            style = MaterialTheme.typography.headlineSmall,
            color = when {
                owed && debt > 0 -> Danger
                amount < 0 -> Danger
                else -> Ink
            },
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = if (owed) "Долг сейчас" else "На счету сейчас",
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
        )

        if (account.kind == AccountKind.CREDIT) {
            creditLeft(account.limit, amount)?.let { left ->
                Text(
                    text = if (left >= 0) {
                        "До лимита осталось " + formatMoney(left, currency = currency)
                    } else {
                        "Сверх лимита " + formatMoney(-left, currency = currency)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (left >= 0) Muted else Danger,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = "По этому счёту пока ничего не прошло. Остаток на нём — тот, " +
                    "с которым его завели.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            DialogCaption("Движение")
            // Прокрутка внутри окна — как в раскрытой статье: по счёту за год
            // набегают сотни строк, и окно во весь экран из-за них перестало
            // бы быть окном.
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .heightIn(max = 320.dp)
                    .fadingVerticalScroll(),
            ) {
                // День надписан один раз над своими записями — как в ленте
                // месяца: без этого лента счёта читается сплошной кашей, а
                // «когда» здесь спрашивают не реже, чем «сколько».
                var day: LocalDate? = null
                entries.forEach { entry ->
                    if (entry.date != day) {
                        day = entry.date
                        Text(
                            text = formatRussianDate(entry.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    AccountEntryRow(
                        entry = entry,
                        accountId = account.id,
                        currency = currency,
                        title = entryTitle(entry, account.id, accounts, categories),
                        onClick = { onOpenEntry(entry) },
                    )
                }
            }
        }

        DialogButtons {
            ActionButton(icon = Icons.Outlined.Tune, label = "Счёт", onClick = onEdit)
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
 * Что написать в строке: статью, а у перевода — второй его конец.
 *
 * У перевода название говорит, откуда или куда: «Перевод» без второго счёта в
 * ленте одного счёта — это загадка, а не строка.
 */
private fun entryTitle(
    entry: LedgerEntry,
    accountId: Long,
    accounts: Map<Long, LedgerAccount>,
    categories: Map<Long, LedgerCategory>,
): String {
    val category = entry.categoryId?.let { categories[it]?.title }
    return when {
        entry.kind == EntryKind.MOVE -> {
            val incoming = entry.toAccountId == accountId
            val other = if (incoming) {
                accounts[entry.accountId]
            } else {
                entry.toAccountId?.let { accounts[it] }
            }
            val name = other?.title.orEmpty().ifBlank { "другой счёт" }
            if (incoming) "Перевод из «" + name + "»" else "Перевод в «" + name + "»"
        }
        entry.kind == EntryKind.BACK ->
            if (category.isNullOrBlank()) "Возврат" else "Возврат · " + category
        !category.isNullOrBlank() -> category
        entry.note.isNotBlank() -> entry.note
        else -> entry.kind.one
    }
}

/**
 * Сколько эта запись прибавила счёту или отняла у него.
 *
 * Считается ровно так же, как остаток в базе (`LedgerDao.observeDeltas`), и
 * разойтись с ним не может: доход и возврат прибавляют, расход отнимает, а
 * перевод смотрит, с какой он стороны.
 */
private fun movementOf(entry: LedgerEntry, accountId: Long): Long = when (entry.kind) {
    EntryKind.EARN, EntryKind.BACK -> entry.amount
    EntryKind.SPEND -> -entry.amount
    EntryKind.MOVE -> if (entry.toAccountId == accountId) entry.amount else -entry.amount
}

/** Одна строка ленты счёта: что было слева, сколько прибавилось справа. */
@Composable
private fun AccountEntryRow(
    entry: LedgerEntry,
    accountId: Long,
    currency: Currency,
    title: String,
    onClick: () -> Unit,
) {
    val moved = movementOf(entry, accountId)

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
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.note.isNotBlank()) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = formatMoney(moved, withSign = true, currency = currency),
            style = MaterialTheme.typography.titleSmall,
            // Те же три цвета, что в ленте месяца: зелёное — заработал,
            // коралловое — вернулось, обычное — ушло или переложено.
            color = when {
                entry.kind == EntryKind.EARN -> ModeGreen
                entry.kind == EntryKind.BACK -> AccentInk
                else -> Ink
            },
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
