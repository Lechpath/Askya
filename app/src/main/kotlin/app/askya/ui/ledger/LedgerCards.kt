package app.askya.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Sell
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.askya.data.entity.LedgerAccount
import app.askya.data.entity.LedgerCategory
import app.askya.data.entity.LedgerEntry
import app.askya.data.repository.AccountLine
import app.askya.domain.model.AccountKind
import app.askya.domain.model.Currency
import app.askya.domain.model.EntryKind
import app.askya.domain.model.MarkColor
import app.askya.domain.model.moneyToText
import app.askya.domain.model.formatMoney
import app.askya.domain.model.parseMoney
import app.askya.domain.model.parseMoneySum
import app.askya.domain.model.parseMoneyTerms
import app.askya.domain.model.parseRate
import app.askya.domain.model.rateToText
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DateLean
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogField
import app.askya.ui.components.DialogTitle
import app.askya.ui.components.formatTypedDate
import app.askya.ui.components.parseTypedDate
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted
import app.askya.ui.theme.markColor

/**
 * Запись книги: сколько, откуда, на что.
 *
 * Одно окно на все четыре вида записи, но спрашивает оно у каждого своё: у
 * перевода вместо статьи — второй счёт, потому что перевод и есть «отсюда
 * туда». Четыре отдельных окна отличались бы одной строкой, а вид записи
 * человек нередко меняет на ходу («это не трата, это я снял с карты»).
 *
 * У возврата спрашивается ровно то же, что у траты, — и статья та же,
 * расходная: возвращают за трату, и своего словаря у возврата нет
 * ([EntryKind.categoryKind]). Поэтому и выбранная статья при переключении
 * «Расход» ↔ «Возврат» не сбрасывается: она и там и там своя.
 *
 * Вид стоит первым и переключается словом: от него зависит всё остальное, и
 * спрашивать его последним значило бы перебирать поля заново.
 *
 * Сумма — единственное, без чего запись не запишется. Ни статья, ни заметка не
 * обязательны: у кассы человек хочет записать «700 с карты», а не заполнять
 * анкету. Статья потом дописывается тапом по строке в ленте.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyCard(
    entry: LedgerEntry,
    accounts: List<AccountLine>,
    categories: List<LedgerCategory>,
    onDismiss: () -> Unit,
    onSave: (LedgerEntry) -> Unit,
    onAddCategory: (String, EntryKind, (Long) -> Unit) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var kind by remember(entry.id) { mutableStateOf(entry.kind) }
    var amount by remember(entry.id) { mutableStateOf(moneyToText(entry.amount)) }
    var day by remember(entry.id) { mutableStateOf(formatTypedDate(entry.date)) }
    var accountId by remember(entry.id) { mutableStateOf(entry.accountId) }
    var toAccountId by remember(entry.id) { mutableStateOf(entry.toAccountId) }
    var categoryId by remember(entry.id) { mutableStateOf(entry.categoryId) }
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    // Заводится ли сейчас новая статья: окно на это время подменяется, а
    // набранное в записи остаётся на месте — оно живёт здесь, а не в окне.
    var naming by remember(entry.id) { mutableStateOf(false) }

    // Закрытый счёт в выборе не показывается — кроме того, на котором запись
    // уже стоит: иначе, открыв прошлогоднюю трату, человек увидел бы, что она
    // «ниоткуда».
    val pickable = accounts.filter { !it.account.closed || it.account.id == accountId }
    val forKind = categories.filter { it.kind == kind.categoryKind }

    // Валюта записи — та, что у выбранного счёта: своей у записи нет и быть не
    // должно (см. [app.askya.domain.model.Currency]).
    val currency = pickable.firstOrNull { it.account.id == accountId }?.account?.currency
        ?: Currency.RUB

    // Поле суммы складывает столбик: «120+340+56». Слагаемые нужны и отдельно
    // — чтобы показать итог только тогда, когда их правда несколько, а не
    // подписывать «= 700» под одинокой семисоткой.
    val terms = parseMoneyTerms(amount)
    val money = parseMoneySum(amount)
    // У перевода второй счёт обязан быть той же валюты: одно число на два
    // счёта разных валют означало бы курс, которого у книги нет.
    val target = toAccountId?.let { id -> pickable.firstOrNull { it.account.id == id } }
    val ready = money != null && money > 0 && accountId != 0L &&
        (
            kind != EntryKind.MOVE ||
                (target != null && target.account.id != accountId &&
                    target.account.currency == currency)
            )

    if (naming) {
        NewCategoryCard(
            kind = kind,
            onDismiss = { naming = false },
            onAdd = { title ->
                // Заведённая из возврата статья — расходная: своих статей у
                // возврата нет (см. [EntryKind.categoryKind]).
                onAddCategory(title, kind.categoryKind) { id -> categoryId = id }
                naming = false
            },
        )
        return
    }

    AskyaDialog(onDismiss = onDismiss, width = 0.92f, badge = { DialogBadge(Icons.Outlined.Payments) }) {
        DialogTitle(if (entry.id == 0L) "Запись" else "Правим запись")

        DialogCaption("Что случилось")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EntryKind.entries.forEach { option ->
                Word(
                    text = option.one,
                    picked = kind == option,
                    onClick = {
                        // Статья у перевода не бывает, а выбранная от прошлого
                        // вида статья — чужая: расходная у дохода не к месту.
                        // У расхода с возвратом словарь один и тот же, и
                        // сбрасывать выбранное между ними значило бы заставить
                        // выбрать «Еду» дважды подряд.
                        if (option.categoryKind != kind.categoryKind) categoryId = null
                        kind = option
                    },
                )
            }
        }

        // Что случится с деньгами — словами и сразу, а не по факту.
        //
        // Возврат единственный из четырёх видов идёт в две стороны: счёту
        // прибавляет, а месяцу убавляет, — и, не сказав этого, книга получает
        // человека, который ищет пропавшую тысячу. Остальные три объяснять
        // нечего: расход, доход и перевод делают ровно то, что называют.
        if (kind == EntryKind.BACK) {
            Text(
                text = "Деньги придут на счёт, а из «ушло» за месяц вычтутся: " +
                    "возврат — это отменившаяся трата, а не доход. В ленте месяца " +
                    "он стоит своей строкой «Возврат» с плюсом.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        DialogField(
            value = amount,
            onValueChange = { amount = it },
            hint = "Сколько",
            keyboard = KeyboardType.Decimal,
            autoFocus = entry.id == 0L,
        )

        // Столбик прямо в поле суммы: четыре чека из одного магазина
        // записываются одной записью, и складывать их в уме, а потом
        // проверять по калькулятору телефона, больше не нужно.
        //
        // Кнопки «+» и «−» стоят под полем, потому что цифровая клавиатура их
        // не даёт: тянуться за плюсом на буквенную раскладку посреди набора
        // суммы — работа, которой не должно быть.
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Word(text = "+", picked = false, onClick = { amount = plus(amount, "+") })
            Word(text = "−", picked = false, onClick = { amount = plus(amount, "−") })
            // Итог показывается, только когда слагаемых больше одного: под
            // одиноким числом он повторял бы его же.
            if (terms.size > 1 && money != null) {
                Text(
                    text = "= " + formatMoney(money, currency = currency) +
                        " · " + terms.size + " шт.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentInk,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        DialogField(
            value = day,
            onValueChange = { day = it },
            hint = "Когда: сегодня, вчера, 20.08",
        )

        DialogCaption(
            when (kind) {
                EntryKind.MOVE -> "Откуда"
                // У возврата деньги приходят, и «с какого счёта» спрашивало бы
                // ровно наоборот тому, что случилось.
                EntryKind.BACK -> "На какой счёт"
                else -> "С какого счёта"
            },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            pickable.forEach { line ->
                Word(
                    text = line.account.title,
                    picked = accountId == line.account.id,
                    onClick = {
                        accountId = line.account.id
                        // Второй конец перевода сбрасывается и тогда, когда он
                        // остался в другой валюте: рубли на долларовый счёт
                        // книга переложить не может.
                        val other = toAccountId?.let { id ->
                            pickable.firstOrNull { it.account.id == id }?.account
                        }
                        if (other == null || other.id == line.account.id ||
                            other.currency != line.account.currency
                        ) {
                            toAccountId = null
                        }
                    },
                    mark = markColor(line.account.color, line.account.title),
                )
            }
        }

        if (kind == EntryKind.MOVE) {
            DialogCaption("Куда")
            // Только счета той же валюты: перевод — это одно число на два
            // счёта, и, чтобы положить рубли на долларовый счёт, книге нужен
            // курс, которого у неё нет (см. [Currency]). Обмен валюты
            // записывается двумя записями — расходом там и доходом здесь, — и
            // курс в них человек считает сам.
            val others = pickable.filter {
                it.account.id != accountId && it.account.currency == currency
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                others.forEach { line ->
                    Word(
                        text = line.account.title,
                        picked = toAccountId == line.account.id,
                        onClick = { toAccountId = line.account.id },
                        mark = markColor(line.account.color, line.account.title),
                    )
                }
            }
            // Счёт всего один — переводить некуда, и «Готово» не нажимается.
            // Сказать об этом словами дешевле, чем оставить человека гадать,
            // почему окно не закрывается.
            if (others.isEmpty()) {
                Text(
                    text = if (pickable.count { it.account.id != accountId } > 0) {
                        "Перевести можно только на счёт той же валюты: курса, чтобы " +
                            "пересчитать сумму, у книги нет. Обмен записывается двумя " +
                            "записями — расходом там и доходом здесь."
                    } else {
                        "Перевести можно только на другой свой счёт, а он пока один. " +
                            "Заведите второй на вкладке «Счета»."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        } else {
            DialogCaption("Статья")
            // У возврата статья говорит не «на что», а «за что вернули»: из
            // неё возврат и вычтется. Сказать это словами дешевле, чем ждать,
            // пока человек догадается по уменьшившейся «Еде».
            if (kind == EntryKind.BACK) {
                Text(
                    text = "Та, по которой прошла трата: возврат её и уменьшит.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                forKind.forEach { category ->
                    Word(
                        text = category.title,
                        picked = categoryId == category.id,
                        // Повторное нажатие снимает выбор: «без статьи» — тоже
                        // ответ, и вернуться к нему должно быть можно.
                        onClick = {
                            categoryId = if (categoryId == category.id) null else category.id
                        },
                        mark = markColor(category.color, category.title),
                    )
                }
                Word(text = "Новая…", picked = false, onClick = { naming = true })
            }
        }

        Spacer(Modifier.height(12.dp))
        DialogField(
            value = note,
            onValueChange = { note = it },
            hint = "Заметка",
            singleLine = false,
        )

        DialogButtons {
            onRemove?.let { remove ->
                ActionButton(
                    icon = Icons.Outlined.Delete,
                    label = "Убрать",
                    color = MaterialTheme.colorScheme.error,
                    onClick = remove,
                )
            }
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Готово",
                accent = true,
                enabled = ready,
                onClick = {
                    val sum = parseMoneySum(amount) ?: return@ActionButton
                    onSave(
                        entry.copy(
                            kind = kind,
                            amount = sum,
                            // Не разобранная дата остаётся прежней: «20 abc» —
                            // это описка, и терять из-за неё запись незачем.
                            //
                            // Год у ненаписанного года берётся назад
                            // ([DateLean.BEHIND]): книга ведётся о
                            // случившемся, и «31.08», напечатанная в сентябре,
                            // означает позавчера, а не будущий август.
                            date = parseTypedDate(day, lean = DateLean.BEHIND) ?: entry.date,
                            accountId = accountId,
                            toAccountId = toAccountId,
                            categoryId = categoryId,
                            note = note,
                        ),
                    )
                },
            )
        }
    }
}

/**
 * Дописать знак к набранной сумме.
 *
 * Второй знак подряд заменяет первый, а не встаёт рядом: «120+−» не значит
 * ничего, а промахнуться мимо соседней кнопки легко. Знак в пустом поле имеет
 * смысл только у минуса — с плюса сумма и так начинается.
 */
private fun plus(amount: String, sign: String): String {
    val written = amount.trimEnd()
    if (written.isEmpty()) return if (sign == "+") "" else sign
    val last = written.last()
    return if (last == '+' || last == '−' || last == '-') {
        written.dropLast(1) + sign
    } else {
        written + sign
    }
}

/**
 * Новая статья — одно поле и ничего больше.
 *
 * Вид статьи не спрашивается: её заводят, стоя в записи, и она наследует вид
 * записи — а у возврата расходный ([EntryKind.categoryKind]). Спрашивать «это
 * расходная или доходная?» у человека, который только что выбрал «Расход»,
 * значит переспрашивать.
 */
@Composable
private fun NewCategoryCard(kind: EntryKind, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var title by remember { mutableStateOf("") }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Sell) }) {
        DialogTitle("Новая статья")
        DialogCaption(if (kind == EntryKind.EARN) "Откуда приходит" else "На что уходит")

        DialogField(
            value = title,
            onValueChange = { title = it },
            hint = "Название",
            autoFocus = true,
            onDone = { if (title.isNotBlank()) onAdd(title) },
        )

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Завести",
                accent = true,
                enabled = title.isNotBlank(),
                onClick = { onAdd(title) },
            )
        }
    }
}

/**
 * Статья: название и предел на месяц.
 *
 * Предел — это и есть весь бюджет Askya. Пусто или ноль означает «без
 * предела»: статья без него — обычное дело, и отдельного выключателя ей не
 * нужно.
 *
 * «Убрать» стирает статью, но не записи по ней: они становятся «без статьи» и
 * остаются в итогах месяца. Человек убирал графу, а не прошлогодние траты.
 */
@Composable
fun CategoryCard(
    category: LedgerCategory,
    onDismiss: () -> Unit,
    onSave: (LedgerCategory) -> Unit,
    onForget: () -> Unit,
) {
    var title by remember(category.id) { mutableStateOf(category.title) }
    var limit by remember(category.id) { mutableStateOf(moneyToText(category.limit)) }
    var color by remember(category.id) { mutableStateOf(category.color) }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Sell) }) {
        DialogTitle(category.title.ifBlank { "Статья" })

        DialogCaption("Название")
        DialogField(value = title, onValueChange = { title = it }, hint = "Название")

        if (category.kind == EntryKind.SPEND) {
            DialogCaption("Предел на месяц")
            DialogField(
                value = limit,
                onValueChange = { limit = it },
                hint = "Пусто — без предела",
                keyboard = KeyboardType.Decimal,
            )
            Text(
                text = "Под статьёй появится полоска: сколько от предела съедено. " +
                    "Вышли за него — покраснеет.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        DialogCaption("Краска")
        MarkPalette(
            chosen = color,
            onPick = { picked -> color = if (picked == color) null else picked },
        )

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.DeleteOutline,
                label = "Убрать",
                color = MaterialTheme.colorScheme.error,
                onClick = onForget,
            )
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Готово",
                accent = true,
                onClick = {
                    onSave(
                        category.copy(
                            title = title,
                            limit = if (category.kind == EntryKind.SPEND) {
                                parseMoney(limit) ?: 0L
                            } else {
                                0L
                            },
                            color = color,
                        ),
                    )
                },
            )
        }
    }
}

/**
 * Счёт: название, вид и сколько на нём было в самом начале.
 *
 * Начальный остаток спрашивается один раз и потом почти не трогается: книгу
 * заводят не с нуля, а с того, что уже лежит в кошельке. Нынешний остаток тут
 * не правится вовсе — он складывается из записей, и «подогнать» его значило бы
 * соврать самому себе, а книге потом не сойтись.
 *
 * ## Долг спрашивается положительным числом
 *
 * У кредитной карты и у долга остаток отрицательный: должен — значит минус.
 * Но набирать минус человек не должен: поле у них подписано «Сколько должны
 * сейчас», в него пишут двадцать тысяч, а книга кладёт их в остаток со своим
 * знаком. Минус в поле суммы и слово «долг» рядом — это два отрицания на одно
 * число, и второе из них человек рано или поздно потеряет.
 *
 * У кредитной карты спрашивается ещё лимит — тот потолок, до которого банк
 * даёт занимать. Из него и долга считается, сколько ещё можно потратить;
 * пустой лимит просто убирает эту строчку, а не запрещает карту.
 *
 * У долговых счетов спрашивается ещё ставка — проценты годовых. Она нужна
 * одному плану погашения на странице «Счета»: без неё план считает платёж и
 * честно молчит про переплату. Спрашивается она последней и необязательна:
 * человек, записывающий долг другу, про проценты слышать не должен.
 *
 * Краска — восемь кружков внизу окна. Выбирать её не обязательно: не выбрали —
 * выводится из названия, и счёт всё равно узнаётся в сетке по цвету.
 *
 * Закрытие вместо удаления: на счёте висит история — см.
 * [app.askya.data.repository.LedgerRepository.deleteAccount].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountCard(
    account: LedgerAccount,
    onDismiss: () -> Unit,
    onSave: (LedgerAccount) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember(account.id) { mutableStateOf(account.title) }
    var kind by remember(account.id) { mutableStateOf(account.kind) }
    var currency by remember(account.id) { mutableStateOf(account.currency) }
    // Знак в поле не показывается: у долговых счетов оно спрашивает «сколько
    // должны», а не «какой остаток», — см. рассуждение выше.
    var opening by remember(account.id) {
        mutableStateOf(moneyToText(kotlin.math.abs(account.opening)))
    }
    var limit by remember(account.id) { mutableStateOf(moneyToText(account.limit)) }
    var rate by remember(account.id) { mutableStateOf(rateToText(account.rate)) }
    var color by remember(account.id) { mutableStateOf(account.color) }
    var closed by remember(account.id) { mutableStateOf(account.closed) }

    AskyaDialog(
        onDismiss = onDismiss,
        badge = { DialogBadge(Icons.Outlined.AccountBalanceWallet) },
    ) {
        DialogTitle(if (account.id == 0L) "Новый счёт" else account.title.ifBlank { "Счёт" })

        DialogCaption("Название")
        DialogField(
            value = title,
            onValueChange = { title = it },
            hint = "Кошелёк, карта, копилка",
            autoFocus = account.id == 0L,
        )

        DialogCaption("Что это")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AccountKind.entries.forEach { option ->
                Word(text = option.title, picked = kind == option, onClick = { kind = option })
            }
        }

        DialogCaption("В чём считать")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Currency.entries.forEach { option ->
                Word(
                    text = option.title,
                    picked = currency == option,
                    onClick = { currency = option },
                )
            }
        }
        if (!currency.main) {
            Text(
                text = "Валютный счёт живёт сам по себе: его остаток и его записи " +
                    "считаются в " + currency.sign + " и ни в итоги месяца, ни в статьи, " +
                    "ни в статистику не входят. Курсов книга не знает и складывать " +
                    "разные валюты не берётся — на «Счетах» у каждой свой итог.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (account.id != 0L && currency != account.currency) {
            Text(
                text = "Уже записанное не пересчитывается: цифры остаются те же, " +
                    "меняется только знак валюты. Курса, по которому их пересчитать, " +
                    "у книги нет.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        DialogCaption(
            if (kind.owed) "Сколько должны сейчас" else "Сколько было, когда завели",
        )
        DialogField(
            value = opening,
            onValueChange = { opening = it },
            hint = "0",
            keyboard = KeyboardType.Decimal,
        )
        if (kind.owed) {
            Text(
                text = "Число пишется без минуса — знак книга ставит сама. Дальше долг " +
                    "растёт от трат по этому счёту и убывает от переводов на него.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else if (account.id != 0L) {
            Text(
                text = "Нынешний остаток складывается из записей и здесь не правится.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // Лимит — только у кредитной карты: у долга потолка нет, а у кошелька
        // строчка «сколько мне разрешено» означала бы не то, что написано.
        if (kind == AccountKind.CREDIT) {
            DialogCaption("Кредитный лимит")
            DialogField(
                value = limit,
                onValueChange = { limit = it },
                hint = "Пусто — без лимита",
                keyboard = KeyboardType.Decimal,
            )
            Text(
                text = "На счетах появится полоска: сколько лимита занято и сколько " +
                    "ещё можно потратить. Вышли за него — покраснеет.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // Ставка — только у того, кому должны: у кошелька процентов не бывает,
        // а у накоплений они есть, но книга их не начисляет и обещать не
        // станет — это уже вклад с капитализацией, а не расходная книга.
        if (kind.owed) {
            DialogCaption("Ставка")
            DialogField(
                value = rate,
                onValueChange = { rate = it },
                hint = "Пусто — без процентов",
                keyboard = KeyboardType.Decimal,
            )
            Text(
                text = "Процентов годовых. Из них план погашения на странице «Счета» " +
                    "считает переплату: без ставки он покажет платёж, но промолчит " +
                    "о том, сколько сверху уйдёт банку.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        DialogCaption("Краска")
        MarkPalette(
            chosen = color,
            // Повторный тап по выбранной краске снимает выбор — как у корешка
            // книги: цвет тогда снова выводится из названия.
            onPick = { picked -> color = if (picked == color) null else picked },
        )

        if (account.id != 0L) {
            DialogCaption("Пользуемся ли")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Word(text = "Открыт", picked = !closed, onClick = { closed = false })
                Word(text = "Закрыт", picked = closed, onClick = { closed = true })
            }
            Text(
                text = "Закрытый счёт уходит из выбора, а история по нему остаётся " +
                    "и продолжает сходиться.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

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
                    val written = parseMoney(opening) ?: 0L
                    onSave(
                        account.copy(
                            title = title,
                            kind = kind,
                            currency = currency,
                            // Долг ложится в остаток минусом: спросили «сколько
                            // должны», а хранится то же самое остатком счёта.
                            opening = if (kind.owed) -written else written,
                            limit = if (kind == AccountKind.CREDIT) parseMoney(limit) ?: 0L else 0L,
                            // Ставка стирается вместе с видом счёта: осталась
                            // бы от кредитки, переделанной в кошелёк, и всплыла
                            // бы потом в чужом плане погашения.
                            rate = if (kind.owed) parseRate(rate) ?: 0 else 0,
                            color = color,
                            closed = closed,
                        ),
                    )
                },
            )
        }
    }
}

/**
 * Слово-выбор: тем же, чем выбирают область упражнения и самочувствие.
 *
 * У статьи перед словом стоит кружок её краски: в записи статью выбирают из
 * дюжины слов подряд, и своя краска находит нужное быстрее, чем чтение.
 * Красить само слово нельзя — выбранное слово красится акцентом, и две краски
 * на одной надписи спорили бы за то, что она значит.
 */
@Composable
private fun Word(text: String, picked: Boolean, onClick: () -> Unit, mark: Color? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (picked) AccentSoft else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        mark?.let { color ->
            Box(
                modifier = Modifier
                    .padding(end = 7.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (picked) AccentInk else Accent,
        )
    }
}

/**
 * Палитра красок: восемь кружков, выбранный — с галочкой.
 *
 * Без подписей: краску выбирают глазами, и слово «сливовая» не помогает её
 * узнать. Галочка внутри кружка, а не обводка вокруг: на тёмных красках
 * обводка почти не видна, а светлый знак виден на всех восьми. Ровно та же
 * палитра, что у корешков книг в Scroll, — см. [MarkColor].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkPalette(chosen: MarkColor?, onPick: (MarkColor) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 4,
        modifier = Modifier.fillMaxWidth(),
    ) {
        MarkColor.entries.forEach { option ->
            val picked = option == chosen
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(markColor(option))
                    .border(
                        width = if (picked) 2.dp else 0.dp,
                        color = if (picked) Ink else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onPick(option) },
            ) {
                if (picked) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Выбрано",
                        tint = Cream,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
