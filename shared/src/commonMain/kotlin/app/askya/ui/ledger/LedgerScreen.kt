package app.askya.ui.ledger

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CreditScore
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.data.entity.LedgerAccount
import app.askya.data.entity.LedgerCategory
import app.askya.data.entity.LedgerEntry
import app.askya.data.repository.AccountLine
import app.askya.data.repository.MonthBook
import app.askya.domain.model.AccountKind
import app.askya.domain.model.Currency
import app.askya.domain.model.Debt
import app.askya.domain.model.EntryKind
import app.askya.domain.model.creditLeft
import app.askya.domain.model.debtOf
import app.askya.domain.model.formatMoney
import app.askya.domain.model.limitShare
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.CardGrid
import app.askya.ui.components.DayPartTitle
import app.askya.ui.components.EmptyState
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.formatMonthTitle
import app.askya.ui.components.formatRussianDate
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Danger
import app.askya.ui.theme.Ink
import app.askya.ui.theme.ModeGreen
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge
import app.askya.ui.theme.markColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Три страницы книги, и листаются они пальцем вбок.
 *
 * Первыми — счета: книгу открывают, чтобы посмотреть, где сколько лежит.
 * «Сколько у меня денег» — вопрос, который задают каждый раз, а «на что ушло
 * за август» — раз в месяц, и второй не должен стоять перед первым. Здесь же,
 * под счетами, стоит и план погашения: долг живёт на счету, и ходить за
 * планом в другое место значило бы разлучить вопрос с ответом.
 *
 * Вторая — прожитый месяц: итог, статьи и лента записей под ними. Названа она
 * «Доход/Расход», а не «Месяц», потому что месяц — это не то, что на ней
 * делают, а то, в каких границах: делают на ней записи о приходе и расходе.
 *
 * Третья — статистика: те же записи, но за все месяцы разом. Отдельной
 * страницей, потому что вопрос у неё другой — не «что было в августе», а «как
 * я живу вообще», и ответа на него в одном месяце нет.
 *
 * ## Плашек над страницами нет
 *
 * Раньше страницы переключались плашками-вкладками во всю ширину ряда. Плашка
 * говорит «нажми меня», и, пока она была, страницы только так и переключались;
 * а книга, которую листают вбок, в кнопке не нуждается — палец здесь быстрее
 * и привычнее, чем прицеливание в слово. Осталась одна надпись — имя той
 * страницы, на которой стоишь, — и три точки рядом: сколько всего страниц и
 * которая под пальцем. Точки нажимаются: тому, кто про свайп ещё не знает,
 * нужен способ добраться до третьей страницы, не догадываясь.
 */
private enum class LedgerPage(val title: String) {
    ACCOUNTS("Счета"),
    MONTH("Доход/Расход"),
    STATS("Статистика"),
}

/**
 * Ledger — расходная книга.
 *
 * Имя английское и односложное, как у Scroll: ledger — та самая амбарная
 * книга, в которую записывают приход и расход. «Бюджет» назвал бы половину
 * дела (пределы), «Финансы» — не назвал бы ничего.
 *
 * ## Из чего книга состоит
 *
 * Три вещи, и все три нужны друг другу. **Счета** — где деньги лежат;
 * **статьи** — на что уходят и откуда приходят; **записи** — что случилось.
 * Запись всегда указывает на счёт, обычно на статью, и только этим двум
 * указаниям книга обязана тем, что из неё можно что-то узнать.
 *
 * ## Бюджет — это одна строчка у статьи
 *
 * Никаких планов на год, конвертов и «остатка на день». У статьи есть предел на
 * месяц, и на вкладке «Месяц» под ней полоска: сколько от него съедено. Вышли
 * за предел — полоска краснеет. Всё.
 *
 * Так сделано потому, что домашний бюджет ломается не о недостаток
 * подробностей, а об их избыток: расписанный по неделям план перестают вести на
 * второй месяц, а «не больше двадцати тысяч на еду» держится годами.
 *
 * ## Чего здесь нет
 *
 * Ни банков, ни импорта выписок, ни курсов валют — ничего, что требует сети:
 * Askya в сеть ходит за одной погодой. Ни повторяющихся платежей: то, что
 * повторяется, — это дело в AskyaDay с напоминанием, а сюда оно попадает
 * записью, когда случилось.
 *
 * Годовые итоги здесь всё-таки есть — страницей «Статистика», — но не отчётом
 * из десяти графиков: столбики месяцев, средние и разбор по статьям, то есть
 * ровно те числа, которые человек иначе складывал бы в уме, листая двенадцать
 * месяцев подряд.
 */
@Composable
fun LedgerScreen(onOpenMenu: () -> Unit) {
    val container = appContainer()
    val viewModel: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(container))

    val month by viewModel.month.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val accountEntries by viewModel.accountEntries.collectAsStateWithLifecycle()

    // Докуда пускать листание месяцев вперёд — см. LedgerViewModel.ahead.
    val ahead by viewModel.ahead.collectAsStateWithLifecycle()

    val pages = LedgerPage.entries
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val page = pages[pager.currentPage]

    // Открытая запись. `null` — окна нет; запись с номером 0 — новая.
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    var editingAccount by remember { mutableStateOf<LedgerAccount?>(null) }
    // Раскрытый счёт — его движение средств. Не то же, что [editingAccount]:
    // там правят название и вид, здесь смотрят, из чего сложился остаток.
    var openAccount by remember { mutableStateOf<LedgerAccount?>(null) }
    var editingCategory by remember { mutableStateOf<LedgerCategory?>(null) }
    // Раскрытая статья — её записи за открытый месяц. Не то же, что
    // [editingCategory]: там правят название и предел, здесь смотрят, из чего
    // статья сложилась.
    var openCategory by remember { mutableStateOf<LedgerCategory?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    // Словарь статей и первый счёт заводятся при первом входе — см.
    // LedgerRepository.ensureStarted.
    LaunchedEffect(Unit) { viewModel.start() }

    val open = accounts.filterNot { it.account.closed }

    ScreenScaffold(
        title = "Ledger",
        onNavigationClick = onOpenMenu,
        floatingActionButton = {
            // На статистике кнопки нет: там ничего не заводят, там смотрят.
            // Кнопка «запись» на ней означала бы, что запись попадёт куда-то
            // в статистику, — а она попадает в месяц.
            if (page != LedgerPage.STATS) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Записывать не на что — значит, сперва счёт: все счета
                        // закрыты, и запись без него упёрлась бы в неподписанный
                        // тупик, где «Готово» не нажимается и непонятно почему.
                        if (page == LedgerPage.ACCOUNTS || open.isEmpty()) {
                            editingAccount = LedgerAccount()
                        } else {
                            // Новая запись открывается в том месяце, который на
                            // экране: листали август — значит, записывают август.
                            // В нынешнем месяце это сегодня, в прошлом — его
                            // последний день.
                            editing = LedgerEntry(
                                date = dayInside(month),
                                accountId = open.firstOrNull()?.account?.id ?: 0L,
                            )
                        }
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
                        text = if (page == LedgerPage.ACCOUNTS) "счёт" else "запись",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageMark(
                titles = pages.map { it.title },
                current = pager.currentPage,
                onSelect = { index -> scope.launch { pager.animateScrollToPage(index) } },
            )

            // Вес, а не fillMaxSize: над страницами стоит надпись, и страница
            // занимает то, что от экрана осталось, а не весь экран.
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { index ->
                when (pages[index]) {
                    // Тап по счёту раскрывает его ленту, а не настройки:
                    // «сколько на карте и откуда это взялось» спрашивают
                    // каждую неделю, а название правят однажды — см.
                    // [AccountEntriesCard].
                    LedgerPage.ACCOUNTS -> AccountsTab(
                        lines = accounts,
                        onOpen = { openAccount = it },
                        onSwap = viewModel::swapAccounts,
                    )

                    // Месяц надписан над итогом и над лентой, но не над
                    // счетами: на счету лежит то, что лежит сейчас, и «август»
                    // над этим числом обещал бы остаток на конец августа,
                    // которого книга не считает.
                    LedgerPage.MONTH -> Column(modifier = Modifier.fillMaxSize()) {
                        MonthStrip(month = month, ahead = ahead, onShow = viewModel::showMonth)
                        // Записи впереди — новость, и сказать её надо там, где
                        // человек стоит: одиннадцать тапов стрелкой до августа
                        // будущего года не делает никто, а не сходится счёт
                        // из-за них уже сегодня.
                        if (ahead > YearMonth.now() && month <= YearMonth.now()) {
                            AheadNotice(ahead = ahead, onShow = { viewModel.showMonth(ahead) })
                        }
                        MonthTab(
                            book = book,
                            accounts = accounts,
                            categories = categories,
                            onCategory = { openCategory = it },
                            onOpenEntry = { editing = it },
                        )
                    }

                    LedgerPage.STATS -> StatsPage(stats = stats, categories = categories)
                }
            }
        }
    }

    editing?.let { entry ->
        MoneyCard(
            entry = entry,
            accounts = accounts,
            categories = categories,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onAddCategory = { title, kind, pick ->
                viewModel.addCategory(LedgerCategory(title = title, kind = kind), pick)
            },
            onRemove = if (entry.id == 0L) null else {
                {
                    viewModel.remove(entry.id)
                    editing = null
                }
            },
        )
    }

    // Лента раскрытого счёта спрашивается у базы отдельным потоком: она не
    // про открытый месяц, и выбрать её из уже загруженного нельзя.
    LaunchedEffect(openAccount?.id) { viewModel.showAccount(openAccount?.id ?: 0L) }

    openAccount?.let { chosen ->
        // Счёт берётся из свежего списка, а не из того, что положили в
        // состояние: правку из этого же окна иначе пришлось бы ждать до
        // закрытия.
        val line = accounts.firstOrNull { it.account.id == chosen.id }
        AccountEntriesCard(
            account = line?.account ?: chosen,
            amount = line?.amount ?: 0L,
            entries = accountEntries,
            accounts = remember(accounts) { accounts.associate { it.account.id to it.account } },
            categories = remember(categories) { categories.associateBy { it.id } },
            onOpenEntry = { entry ->
                openAccount = null
                editing = entry
            },
            onEdit = {
                openAccount = null
                editingAccount = line?.account ?: chosen
            },
            onDismiss = { openAccount = null },
        )
    }

    editingAccount?.let { account ->
        AccountCard(
            account = account,
            onDismiss = { editingAccount = null },
            onSave = {
                viewModel.saveAccount(it)
                editingAccount = null
            },
            onDelete = if (account.id == 0L) null else {
                {
                    viewModel.deleteAccount(account.id) { done ->
                        if (done) {
                            editingAccount = null
                        } else {
                            notice = "По этому счёту уже есть записи, и без него " +
                                "прошлые месяцы перестанут сходиться. Закройте его — " +
                                "он уйдёт из выбора, а история останется."
                        }
                    }
                }
            },
        )
    }

    openCategory?.let { category ->
        // Записи статьи отбираются из уже загруженного месяца, а не запросом:
        // месяц целиком и так лежит на экране, и второй поход в базу за теми
        // же строками отвечал бы на тот же вопрос дважды.
        //
        // У расхода берутся и возвраты: они той же статьи и именно они
        // объясняют, почему её сумма меньше суммы покупок (см. [EntryKind]).
        val ofCategory = book.entries.filter { entry ->
            // Валютные записи сюда не попадают: в статью они не входят вовсе
            // (см. [Currency]), и итог раскрытой статьи разошёлся бы с той же
            // суммой в строке над ней.
            entry !in book.foreign &&
                entry.categoryId == category.id &&
                when (category.kind) {
                    EntryKind.EARN -> entry.kind == EntryKind.EARN
                    else -> entry.kind == EntryKind.SPEND || entry.kind == EntryKind.BACK
                }
        }
        CategoryEntriesCard(
            category = category,
            month = month,
            entries = ofCategory,
            accounts = remember(accounts) { accounts.associate { it.account.id to it.account } },
            onOpenEntry = { entry ->
                openCategory = null
                editing = entry
            },
            onEdit = {
                openCategory = null
                editingCategory = category
            },
            onDismiss = { openCategory = null },
        )
    }

    editingCategory?.let { category ->
        CategoryCard(
            category = category,
            onDismiss = { editingCategory = null },
            onSave = {
                viewModel.saveCategory(it)
                editingCategory = null
            },
            onForget = {
                viewModel.forgetCategory(category.id)
                editingCategory = null
            },
        )
    }

    notice?.let { text ->
        AskyaNotice(
            title = "Счёт не пуст",
            text = text,
            onDismiss = { notice = null },
        )
    }
}

/** «одна запись», «три записи», «пять записей» — по числу. */
private fun entryWord(count: Int): String {
    val last = count % 10
    val hundred = count % 100
    val word = when {
        hundred in 11..14 -> "записей"
        last == 1 -> "запись"
        last in 2..4 -> "записи"
        else -> "записей"
    }
    return "$count $word"
}

/**
 * Какой день предлагать новой записи в открытом месяце.
 *
 * В нынешнем — сегодняшний: почти всегда записывают только что случившееся. В
 * прошлом — его последний день: туда заходят, чтобы дописать забытое, и первое
 * число подсказкой там было бы враньём чаще, чем последнее.
 */
private fun dayInside(month: YearMonth, today: LocalDate = LocalDate.now()): LocalDate =
    if (YearMonth.from(today) == month) today else month.atEndOfMonth()

/**
 * Имя страницы и три точки рядом.
 *
 * Имя — засечным, как заголовок дня в AskyaDay: это не кнопка, а надпись,
 * говорящая, где ты стоишь. Плашки под ней нет нарочно — см. [LedgerPage].
 *
 * Точки — единственное, что здесь нажимается, и нажимаются они ради тех, кто
 * про свайп ещё не знает: без них третья страница существовала бы только для
 * догадливых. Бегущей полоски вместо точек нет по той же причине, по какой её
 * нет у книжки: страниц три, и пересчитать их глазами дешевле, чем измерять
 * полоску.
 */
@Composable
private fun PageMark(titles: List<String>, current: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = titles.getOrElse(current) { "" },
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            letterSpacing = (-0.3).sp,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row {
            titles.forEachIndexed { index, _ ->
                val here = index == current
                // Точка мелкая, а нажимают её пальцем: сама точка в семь
                // точек, а поле вокруг неё — в двадцать восемь.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = { onSelect(index) }),
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (here) 9.dp else 7.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (here) Accent else Muted.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

/**
 * Строчка о записях, лежащих впереди нынешнего месяца.
 *
 * Запись в будущем — почти всегда описка в дате, и молчать о ней нельзя:
 * в остаток счёта и в статистику она входит наравне со всеми, а на глаза не
 * попадается — ленту листают по месяцам назад. Счёт при этом не сходится с
 * настоящей картой, и найти причину не за что зацепиться.
 *
 * Строчка не «ошибка» и не красная: записать трату будущим числом человек мог
 * и нарочно. Она говорит, где смотреть, и открывает тот месяц по тапу.
 */
@Composable
private fun AheadNotice(ahead: YearMonth, onShow: () -> Unit) {
    Text(
        text = "Есть записи позже — " + formatMonthTitle(ahead) + ". Открыть",
        style = MaterialTheme.typography.labelMedium,
        color = AccentInk,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onShow)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

/**
 * Месяц со стрелками: «‹ Август 2026 ›».
 *
 * Вперёд дальше нынешнего месяца не листается: будущих трат не бывает, а
 * пустой сентябрь, в который можно уйти без края, — это способ заблудиться.
 *
 * Кроме одного случая: если запись всё же лежит впереди — опиской в дате, —
 * край отодвигается до неё ([LedgerViewModel.ahead]). Иначе такая запись
 * входит в остаток счёта и в статистику, а достать её нельзя ничем: месяца, в
 * котором она стоит, не открыть.
 */
@Composable
private fun MonthStrip(month: YearMonth, ahead: YearMonth, onShow: (YearMonth) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .cardEdge(RoundedCornerShape(18.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Arrow(
                forward = false,
                enabled = true,
                onClick = { onShow(month.minusMonths(1)) },
            )
            Text(
                text = formatMonthTitle(month),
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Arrow(
                forward = true,
                enabled = month < ahead,
                onClick = { if (month < ahead) onShow(month.plusMonths(1)) },
            )
        }
    }
}

@Composable
private fun Arrow(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (forward) Icons.AutoMirrored.Outlined.KeyboardArrowRight
            else Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            contentDescription = if (forward) "Следующий месяц" else "Прошлый месяц",
            tint = if (enabled) Ink else Muted.copy(alpha = 0.4f),
        )
    }
}

/**
 * Месяц целиком: три числа, статьи под ними и лента записей в конце.
 *
 * Три числа — пришло, ушло, осталось, — и третье не лишнее: разность двух
 * первых человек считает в уме каждый раз, когда её не написали.
 *
 * **Статьи и лента на одной странице, а не на двух.** Они говорят об одном
 * месяце, только складывают его по-разному: статьи отвечают «на что ушло»,
 * лента — «что было в среду». Это не два места, а два взгляда на одно, и
 * переключателем между ними была когда-то вкладка, которая в пустом месяце
 * показывала слово в слово то же самое. Теперь один свиток: сверху итог, ниже
 * разбор по статьям, в конце — сами записи по дням.
 *
 * Порядок такой, потому что таков и порядок вопросов: сперва «сколько», потом
 * «на что», и только потом «а что именно я покупал».
 */
@Composable
private fun MonthTab(
    book: MonthBook,
    accounts: List<AccountLine>,
    categories: List<LedgerCategory>,
    onCategory: (LedgerCategory) -> Unit,
    onOpenEntry: (LedgerEntry) -> Unit,
) {
    if (book.entries.isEmpty()) {
        EmptyState(
            title = "В этом месяце пусто",
            hint = "Кнопкой внизу записывается трата, доход, перевод между " +
                "своими счетами или возврат — когда вернули за трату, сделанную " +
                "не на себя.",
        )
        return
    }

    val spend = categories.filter { it.kind == EntryKind.SPEND }
    val earn = categories.filter { it.kind == EntryKind.EARN }

    val today = LocalDate.now()
    // Счета и статьи целиком, а не одни их названия: строке записи нужна ещё
    // и краска, а два словаря об одном и том же разъезжались бы.
    val accountById = remember(accounts) { accounts.associate { it.account.id to it.account } }
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    FadingColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "totals") {
            // Три карточки, а не одна на троих: три числа месяца — три разных
            // ответа, и рамка вокруг них читалась бы как «это одно число,
            // разложенное на части». Ряд из трёх — тот же, каким набран день
            // в AskyaDay.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Total(title = "Пришло", value = book.earned, color = ModeGreen)
                Total(title = "Ушло", value = book.spent, color = Ink)
                Total(
                    title = "Осталось",
                    value = book.left,
                    color = if (book.left < 0) Danger else AccentInk,
                )
            }
        }

        // Валютные записи стоят в ленте месяца, но ни в один итог не входят:
        // сложить доллар с рублём книге нечем — курсов она не знает (см.
        // [Currency]). Молчать об этом нельзя: три карточки наверху иначе
        // отвечали бы не на весь месяц, ничем этого не показывая. Сколько их
        // и на каких счетах — видно в самом счёте, тапом по его карточке.
        if (book.foreign.isNotEmpty()) {
            item(key = "foreign") {
                Text(
                    text = "Ещё " + entryWord(book.foreign.size) +
                        " по валютным счетам. В итоги месяца и в статьи они не " +
                        "входят: курсов книга не знает и доллары с рублями не " +
                        "складывает. Смотреть их — в самом счёте на «Счетах».",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
        }

        // Четвёртой карточкой возврат в ряд не встал бы: четыре суммы засечным
        // в ширину экрана — это четыре обрезанных числа. Да и не четвёртое это
        // число месяца, а объяснение к третьему: «ушло» уже уменьшено на
        // столько-то, и без строчки об этом человек ищет пропавшие деньги.
        if (book.returned > 0) {
            item(key = "returned") {
                Text(
                    text = "Из «ушло» вычтено ${formatMoney(book.returned)} возвратов.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
        }

        // Статья без единой траты в этом месяце не показывается: список из
        // восьми нулей ничего не говорит о месяце. Кроме той, у которой есть
        // предел, — там ноль как раз и есть новость.
        //
        // Не «больше нуля», а «не ноль»: статья, по которой вернули больше,
        // чем в этом месяце потратили, уходит в минус, и прятать её значило бы
        // потерять возврат из виду вовсе.
        val spentRows = spend
            .map { it to (book.spentByCategory[it.id] ?: 0L) }
            .filter { (category, spent) -> spent != 0L || category.limit > 0 }
            .sortedByDescending { it.second }
        val looseSpent = book.spentByCategory[null] ?: 0L

        if (spentRows.isNotEmpty() || looseSpent != 0L) {
            item(key = "spend-title") { DayPartTitle("Расходы по статьям") }
        }

        items(spentRows, key = { "spend-${it.first.id}" }) { (category, spent) ->
            CategoryRow(
                title = category.title,
                amount = spent,
                limit = category.limit,
                onClick = { onCategory(category) },
                mark = markColor(category.color, category.title),
            )
        }

        if (looseSpent != 0L) {
            item(key = "spend-loose") {
                CategoryRow(title = "Без статьи", amount = looseSpent, limit = 0, onClick = null)
            }
        }

        val earnedRows = earn
            .map { it to (book.earnedByCategory[it.id] ?: 0L) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        val looseEarned = book.earnedByCategory[null] ?: 0L

        if (earnedRows.isNotEmpty() || looseEarned > 0) {
            item(key = "earn-title") { DayPartTitle("Доходы", modifier = Modifier.padding(top = 12.dp)) }
        }

        items(earnedRows, key = { "earn-${it.first.id}" }) { (category, earned) ->
            CategoryRow(
                title = category.title,
                amount = earned,
                limit = 0,
                accent = ModeGreen,
                onClick = { onCategory(category) },
                mark = markColor(category.color, category.title),
            )
        }

        if (looseEarned > 0) {
            item(key = "earn-loose") {
                CategoryRow(
                    title = "Без статьи",
                    amount = looseEarned,
                    limit = 0,
                    accent = ModeGreen,
                    onClick = null,
                )
            }
        }

        // Лента записей — по дням, как расписание. Сплошным списком её не
        // читают: вспоминают «что было в среду», и день отбивается заголовком.
        item(key = "entries-title") {
            DayPartTitle("Записи", modifier = Modifier.padding(top = 12.dp))
        }

        book.entries.groupBy { it.date }.forEach { (date, sameDay) ->
            item(key = "day-$date") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (date == today) "Сегодня" else formatRussianDate(date),
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted,
                        modifier = Modifier.weight(1f),
                    )
                    // Итог дня — рядом с его именем: «сколько я вчера потратил»
                    // спрашивают не реже, чем «на что». Возврат вычитается и
                    // здесь: иначе день говорил бы одно, а месяц — другое.
                    // Валютные траты в итог дня не идут по той же причине, по
                    // какой не идут в итог месяца: сложить доллар с рублём
                    // книге нечем. Сами записи в ленте стоят и читаются со
                    // своим знаком.
                    val spentThatDay = sameDay.sumOf { entry ->
                        val own = (accountById[entry.accountId]?.currency ?: Currency.RUB).main
                        when {
                            !own -> 0L
                            entry.kind == EntryKind.SPEND -> entry.amount
                            entry.kind == EntryKind.BACK -> -entry.amount
                            else -> 0L
                        }
                    }
                    if (spentThatDay != 0L) {
                        Text(
                            text = formatMoney(spentThatDay),
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                        )
                    }
                }
            }

            items(sameDay, key = { "entry-${it.id}" }) { entry ->
                EntryRow(
                    entry = entry,
                    accounts = accountById,
                    categories = categoryById,
                    onClick = { onOpenEntry(entry) },
                )
            }
        }

        item(key = "tail") { Spacer(Modifier.height(96.dp)) }
    }
}

/**
 * Число с подписью под ним: «84 300 ₽ / Ушло».
 *
 * Не приватная, потому что тремя такими набраны и итог месяца, и итог счетов,
 * и итог всей статистики: это одна и та же карточка, и разводить её по двум
 * файлам значило бы получить две слегка разные.
 */
@Composable
fun RowScope.Total(
    title: String,
    value: Long,
    color: Color,
    currency: Currency = Currency.RUB,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.weight(1f).cardEdge(RoundedCornerShape(14.dp)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatMoney(value, currency = currency),
                fontFamily = FontFamily.Serif,
                fontSize = 17.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Строка статьи: название, сумма и — если есть предел — полоска под ними.
 *
 * Полоска говорит одно: сколько от предела съедено. Переполненная краснеет
 * целиком, а не рисуется длиннее своей ширины: «на треть больше, чем хотел» —
 * это про число, а полоска отвечает на вопрос «уложился или нет».
 */
@Composable
private fun CategoryRow(
    title: String,
    amount: Long,
    limit: Long,
    onClick: (() -> Unit)?,
    accent: Color = Ink,
    mark: Color? = null,
) {
    val share = limitShare(amount, limit)
    val over = share != null && share > 1f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .cardEdge(RoundedCornerShape(14.dp))
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Краска статьи — кружком перед названием, а не буквами
                // названия: цветное слово читается хуже чёрного, а узнаётся
                // не лучше кружка.
                mark?.let { color -> Dot(color) }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (mark == null) 0.dp else 8.dp),
                )
                Text(
                    text = formatMoney(amount),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (over) Danger else accent,
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
                        "Сверх предела ${formatMoney(amount - limit)}"
                    } else {
                        "Предел ${formatMoney(limit)} · осталось ${formatMoney(limit - amount)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (over) Danger else Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Одна запись строкой.
 *
 * Слева — на что (статья, а если её нет — заметка или само слово «Расход»),
 * под ней счёт; справа — сумма со знаком. Знак и цвет говорят одно и то же
 * дважды нарочно: столбец сумм читают глазами по цвету, а не по плюсу.
 */
@Composable
private fun EntryRow(
    entry: LedgerEntry,
    accounts: Map<Long, LedgerAccount>,
    categories: Map<Long, LedgerCategory>,
    onClick: () -> Unit,
) {
    val account = accounts[entry.accountId]
    val currency = account?.currency ?: Currency.RUB
    val category = entry.categoryId?.let { categories[it] }?.title
    val from = account?.title.orEmpty()
    val to = entry.toAccountId?.let { accounts[it]?.title }.orEmpty()

    // Краска строки — статьи, а если статьи нет, то счёта. Так лента остаётся
    // цветной и у того, кто статьи не проставляет: «откуда деньги» известно
    // всегда, «на что» — не всегда.
    val mark = entry.categoryId?.let { id ->
        categories[id]?.let { markColor(it.color, it.title) }
    } ?: account?.let { markColor(it.color, it.title) }

    val title = when {
        entry.kind == EntryKind.MOVE -> "Перевод"
        // Возврат называется возвратом, даже когда статья у него есть: одна
        // «Еда» в ленте с плюсом, другая с минусом — это загадка, а не строка.
        // Статья дописывается рядом: она отвечает, за что вернули.
        entry.kind == EntryKind.BACK ->
            if (category.isNullOrBlank()) "Возврат" else "Возврат · $category"
        !category.isNullOrBlank() -> category
        entry.note.isNotBlank() -> entry.note
        else -> entry.kind.one
    }
    val under = when {
        entry.kind == EntryKind.MOVE -> listOf(from, to).filter { it.isNotBlank() }.joinToString(" → ")
        entry.kind == EntryKind.BACK ->
            listOf(from, entry.note).filter { it.isNotBlank() }.joinToString(" · ")
        entry.note.isNotBlank() && !category.isNullOrBlank() -> "$from · ${entry.note}"
        else -> from
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .cardEdge(RoundedCornerShape(14.dp))
            .fillMaxWidth(),
    ) {
        // Нажатие внутри карточки, а не на ней: иначе подсветка от него
        // квадратная (см. [app.askya.ui.scroll.BookTile]).
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            mark?.let { color -> Dot(color) }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (mark == null) 0.dp else 8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
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
                // Валюта — та, что у счёта записи: сумма без знака своей
                // валюты в ленте, где рядом стоят рублёвые и долларовые
                // строки, читалась бы неправдой.
                text = when (entry.kind) {
                    EntryKind.EARN, EntryKind.BACK ->
                        formatMoney(entry.amount, withSign = true, currency = currency)
                    EntryKind.SPEND -> formatMoney(-entry.amount, currency = currency)
                    EntryKind.MOVE -> formatMoney(entry.amount, currency = currency)
                },
                style = MaterialTheme.typography.titleSmall,
                color = when (entry.kind) {
                    EntryKind.EARN -> ModeGreen
                    // Возврат приходит с плюсом, но зелёным не красится:
                    // зелёное в этом столбце значит «заработал», а возврат —
                    // не заработок, а отменившаяся трата.
                    EntryKind.BACK -> AccentInk
                    EntryKind.SPEND -> Ink
                    EntryKind.MOVE -> Muted
                },
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/**
 * Счета и то, сколько на них.
 *
 * Сверху — сколько всего: это единственное число в книге, которое отвечает на
 * вопрос «сколько у меня денег», и складывается оно из открытых счетов вместе
 * с долгами, то есть уходит в минус, когда должен больше, чем имеешь.
 *
 * Под ним — долг, и стоит он отдельной строкой не для красоты: «всего» у
 * человека с кредитной картой отвечает на вопрос «сколько у меня своих», а
 * «сколько я должен» — это второй вопрос, который задают чаще первого и
 * которому в разности двух чисел делать нечего.
 *
 * А под самими счетами — план погашения ([PayoffBlock]), и только если есть
 * что гасить. Он стоит здесь, а не отдельной страницей, потому что вопрос
 * «сколько я должен» и вопрос «за сколько я это закрою» задают подряд, глядя
 * на одну и ту же карточку.
 */
@Composable
private fun AccountsTab(
    lines: List<AccountLine>,
    onOpen: (LedgerAccount) -> Unit,
    onSwap: (List<LedgerAccount>, LedgerAccount, LedgerAccount) -> Unit,
) {
    val open = lines.filterNot { it.account.closed }
    val closed = lines.filter { it.account.closed }

    // Итоги считаются по каждой валюте отдельно и никогда не складываются
    // между собой: курсов книга не знает — см. [Currency]. Валюта, которой в
    // книге нет, и строки себе не получает.
    val totals = Currency.entries.mapNotNull { currency ->
        val its = open.filter { it.account.currency == currency }
        if (its.isEmpty()) null else CurrencyTotal(currency = currency, lines = its)
    }
    // Ряд высотой в самую высокую свою карточку — но мерить его надо по тому,
    // что в разделе есть, а не по тому, что бывает. У кредитной карты под
    // суммой ещё полоска лимита и подпись; у долгового счёта — слово «долг»; у
    // кошелька ничего. Одна высота на всех, самая нужная из трёх: иначе ряд
    // идёт лесенкой, а книга без единого долга зияет пустотой в каждой
    // карточке.
    //
    // На треть выше, чем когда карточек в ряду было две: в трети экрана
    // название переносится на вторую строку, а «Кредитная карта» — и вовсе
    // на две.
    val kinds = lines.map { it.account }
    val height = when {
        kinds.any { it.kind == AccountKind.CREDIT && it.limit > 0 } -> 214.dp
        kinds.any { it.kind.owed } -> 186.dp
        else -> 168.dp
    }

    // fillMaxSize по той же причине, что и у прочих страниц: пейджер ставит
    // содержимое страницы по середине, и короткий список висел бы в пустоте.
    FadingColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item(key = "total") {
            Column {
                if (totals.isEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Total(title = "всего", value = 0, color = Ink)
                    }
                }
                totals.forEach { money -> CurrencyTotals(money) }

                // Черта под итогом: он не счёт, а сумма счетов, и стоящий
                // вплотную к сетке читался бы как ещё одна карточка в ней —
                // тем более что и сложен так же, карточками в ряд. Воздуха
                // одного было мало: между рядами сетки его столько же.
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                )
            }
        }

        // Счета — сеткой по трое, как дела в AskyaDay: их пять-шесть, и
        // столбик строк во всю ширину растягивал бы на весь экран то, что
        // умещается в треть.
        //
        // Одной строкой списка, а не рядом на строку: карточки перетаскивают
        // из ряда в ряд, а строка списка, уехавшая за край экрана, о своём
        // месте больше не знает — и обмен через границу рядов не состоялся бы.
        // Счетов пять-шесть, разложить их все разом дешевле, чем следить, кто
        // из них сейчас виден.
        item(key = "open") {
            AccountsGrid(
                lines = open,
                height = height,
                onOpen = onOpen,
                onSwap = { one, other -> onSwap(open.map { it.account }, one, other) },
            )
        }

        // Долги — те же счета, но взятые со своим знаком: план считает по
        // сумме долга, а не по остатку в минусе. Закрытые счета в него не
        // попадают: закрытый долг — уже не долг.
        // План погашения — только по рублёвым долгам: он складывает их в один
        // столбик и считает, чем гасить вперёд, а сложить доллар с рублём
        // книге нечем.
        val debts = open
            .filter { it.account.kind.owed && it.account.currency.main }
            .map { line ->
                Debt(
                    id = line.account.id,
                    title = line.account.title,
                    amount = debtOf(line.amount),
                    rate = line.account.rate,
                )
            }
            .filter { it.amount > 0 }

        if (debts.isNotEmpty()) {
            item(key = "payoff") { PayoffBlock(debts = debts) }
        }

        if (closed.isNotEmpty()) {
            item(key = "closed-title") {
                DayPartTitle("Закрытые", modifier = Modifier.padding(top = 12.dp))
            }
            item(key = "closed") {
                AccountsGrid(
                    lines = closed,
                    height = height,
                    onOpen = onOpen,
                    onSwap = { one, other -> onSwap(closed.map { it.account }, one, other) },
                )
            }
        }

        item(key = "tail") { Spacer(Modifier.height(96.dp)) }
    }
}

/**
 * Итоги одной валюты: сколько своих и сколько должен.
 *
 * ## «Всего» — без кредитной карты
 *
 * Раньше в «всего» складывались все счета подряд, и кредитка среди них
 * означала одно из двух: либо остаток на ней в минусе и «всего» молча
 * уменьшалось на долг, либо на карте лежало переплаченное — и книга
 * записывала банковские деньги в наличные. Ни то ни другое не отвечает на
 * вопрос, ради которого на это число смотрят: «сколько я могу потратить, не
 * влезая в долг».
 *
 * Поэтому кредитная карта в «всего» не входит вовсе — ни минусом, ни плюсом.
 * Она стоит справа, в долге: занятое у банка — это не средства, это
 * обязательство, и складывать одно с другим в единственном числе, которое
 * читают мельком, нельзя. Пояснительной строки под карточками нет — её убрали
 * по просьбе 13 сентября 2026.
 *
 * Прочий долг ([AccountKind.DEBT]) из «всего» не вынут: заняли у человека
 * наличными — деньги эти лежат в кошельке и правда доступны, а минус на
 * долговом счету — та самая поправка, которая делает «всего» правдой.
 */
private data class CurrencyTotal(val currency: Currency, val lines: List<AccountLine>) {

    /** Свои деньги: всё, кроме кредитных карт. */
    val total: Long = lines
        .filter { it.account.kind != AccountKind.CREDIT }
        .sumOf { it.amount }

    /**
     * Долг по всем счетам — и по кредитным, и по взятым у людей: «сколько я
     * должен» задают одним вопросом, и разносить ответ по двум карточкам
     * значило бы переспрашивать «а какой именно долг вы имеете в виду».
     */
    val debt: Long = lines.sumOf { debtOf(it.amount) }
}

/**
 * Строка итогов одной валюты.
 *
 * Две карточки, а не одна с двумя числами внутри: «сколько у меня» и «сколько
 * я должен» — разные вопросы, и в одной рамке второе читается как уточнение
 * первого.
 *
 * У главной валюты подписи короткие — «всего», «долг»: рубль в книге и так
 * везде. У валютной подпись несёт знак («всего, $»), потому что рядом стоит
 * такая же карточка с другим знаком, и без него они читались бы как одно
 * число, посчитанное дважды.
 */
@Composable
private fun CurrencyTotals(money: CurrencyTotal) {
    val currency = money.currency
    val mark = if (currency.main) "" else ", " + currency.sign

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Total(
            title = "всего" + mark,
            value = money.total,
            color = if (money.total < 0) Danger else Ink,
            currency = currency,
        )
        if (money.debt > 0) {
            Total(
                title = "долг" + mark,
                value = money.debt,
                color = Danger,
                currency = currency,
            )
        }
    }
}

/**
 * Сетка счетов: по трое в ряд, и карточки в ней меняются местами.
 *
 * ## Порядок раскладывает человек
 *
 * Зажать карточку и перенести на другую — счета обменяются местами. Порядок
 * этот и есть список приоритетов: первым лежит то, чем платят, дальше то, куда
 * копят, а редкий счёт уходит в хвост. Считать его приложение не берётся — ни
 * по остатку (карточки перетасовывались бы после каждой покупки хлеба), ни по
 * числу записей (счёт, на который кладут раз в год, важнее того, с которого
 * платят по мелочи каждый день).
 *
 * Перенос начинается с долгого нажатия, а не сразу: короткий проход пальцем по
 * книге — это листание страниц («Счета», «Месяц», «Записи»), и отнимать его у
 * пейджера нельзя. Тем же движением и по той же причине переставляются дела в
 * расписании — см. `PartRow` в AskyaDay.
 *
 * ## Где какая карточка лежит
 *
 * Спросить об этом больше некого: сетка сама решает, что перенести на другую
 * строку, и знает об этом только разметка. Место считается в окне целиком, а не
 * внутри сетки: список едет под пальцем, пока карточку несут, и место,
 * отмеренное от сетки, за это время успевает соврать.
 */
@Composable
private fun AccountsGrid(
    lines: List<AccountLine>,
    height: Dp,
    onOpen: (LedgerAccount) -> Unit,
    onSwap: (LedgerAccount, LedgerAccount) -> Unit,
) {
    val places = remember { mutableStateMapOf<Long, Rect>() }
    // Какую карточку несут, на сколько увели и на кого сейчас положат.
    var carried by remember { mutableStateOf<Long?>(null) }
    var shift by remember { mutableStateOf(Offset.Zero) }
    var landing by remember { mutableStateOf<Long?>(null) }

    CardGrid(lines, modifier = Modifier.padding(vertical = 4.dp)) { line, slot ->
        val id = line.account.id
        val lifted = carried == id
        // Карточка, на которую сейчас положат, отступает. Это единственный
        // способ сказать «обмен произойдёт вот с этой», не рисуя рамок и не
        // двигая всю сетку раньше времени.
        val aimed by animateFloatAsState(
            targetValue = if (landing == id) 0.92f else 1f,
            animationSpec = tween(120),
            label = "aimed",
        )

        AccountTile(
            line = line,
            height = height,
            onClick = { onOpen(line.account) },
            modifier = slot
                // Несомая карточка идёт поверх остальных: проехать под
                // соседкой она не может — её ведут рукой.
                .zIndex(if (lifted) 1f else 0f)
                .onGloballyPositioned { places[id] = it.boundsInRoot() }
                .graphicsLayer {
                    val shown = if (lifted) CARRIED_SIZE else aimed
                    scaleX = shown
                    scaleY = shown
                    translationX = if (lifted) shift.x else 0f
                    translationY = if (lifted) shift.y else 0f
                    alpha = if (lifted) 0.94f else 1f
                }
                .pointerInput(id, lines) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            carried = id
                            shift = Offset.Zero
                            landing = null
                        },
                        onDrag = { change, moved ->
                            change.consume()
                            shift += moved
                            val point = places[id]?.center?.plus(shift)
                            landing = point?.let { spot ->
                                lines.firstOrNull { other ->
                                    other.account.id != id &&
                                        places[other.account.id]?.contains(spot) == true
                                }?.account?.id
                            }
                        },
                        onDragEnd = {
                            val other = landing?.let { at ->
                                lines.firstOrNull { it.account.id == at }?.account
                            }
                            carried = null
                            shift = Offset.Zero
                            landing = null
                            if (other != null) onSwap(line.account, other)
                        },
                        onDragCancel = {
                            carried = null
                            shift = Offset.Zero
                            landing = null
                        },
                    )
                },
        )
    }
}

/** Несомая карточка приподнята: так видно, что она оторвалась от сетки. */
private const val CARRIED_SIZE = 1.06f

/**
 * Карточка счёта.
 *
 * Знак вида наверху, название под ним, сумма внизу крупно — тот же порядок,
 * что у карточки дела в AskyaDay. У долгового счёта внизу стоит сам долг и
 * слово «долг» под ним: остаток там отрицательный, и заставлять читать минус
 * ради ответа на вопрос «сколько я должен» незачем. Погашенная карта говорит
 * «погашено» зелёным — ноль здесь новость, и прятать его нечего.
 *
 * У кредитной карты с записанным лимитом под суммой полоска — та же, что у
 * статьи с пределом, и говорит она то же самое: сколько занято и сколько ещё
 * можно. Вышли за лимит — краснеет целиком.
 *
 * ## Краска
 *
 * Знак вида счёта покрашен краской-меткой ([markColor]), а сверху карточки
 * лежит её полоска. Красится знак, а не сумма и не название: сумму читают
 * цифрами, а цвет у неё уже занят смыслом — красное значит долг. Полоска же
 * видна и тогда, когда карточку не читают вовсе, — а именно так на сетку
 * счетов и смотрят, отыскивая свою карту среди шести.
 *
 * Закрытый счёт краски не получает: он серый весь, и цветная полоска на нём
 * означала бы, что им ещё пользуются.
 */
@Composable
private fun AccountTile(
    line: AccountLine,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = line.account
    val debt = debtOf(line.amount)
    val left = if (account.kind == AccountKind.CREDIT) {
        creditLeft(account.limit, line.amount)
    } else {
        null
    }
    val share = left?.let { limitShare(debt, account.limit) }
    val over = share != null && share > 1f
    val mark = markColor(account.color, account.title)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Та же тень, что у карточки книги на полке Scroll: счёт — такая же
        // крупная карточка, к которой ходят, и лежать они должны одним слоем.
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .cardEdge(RoundedCornerShape(18.dp))
            .height(height),
    ) {
        // Нажатие внутри карточки, а не на ней: иначе подсветка от него
        // квадратная (см. [app.askya.ui.scroll.BookTile]).
        Column(modifier = Modifier.fillMaxSize().clickable(onClick = onClick)) {
            // Полоска краски во всю ширину — поэтому она снаружи отступов, а всё
            // прочее внутри: краска, отбитая от края, читалась бы как ещё одна
            // строка карточки, а не как её метка.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(if (account.closed) Muted.copy(alpha = 0.3f) else mark),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            ) {
                Icon(
                    imageVector = markOf(account.kind),
                    contentDescription = null,
                    tint = if (account.closed) Muted else mark,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = account.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (account.closed) Muted else Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = account.kind.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    // Две строки: в трети экрана «Кредитная карта» в одну не
                    // встаёт, а обрезанная «Кредитн…» не говорит ничего, чего не
                    // сказал бы знак над ней.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Пустота собирается здесь, между названием и суммой, а не под
                // суммой. Высота у ряда общая — самая нужная из трёх, — и в
                // карточке кошелька, которой ни полоска лимита, ни слово «долг» не
                // нужны, лишние точки надо куда-то деть. Внизу они оставляли бы
                // карточку недописанной; собранные в середине, они выстраивают
                // суммы всего ряда на одну линию, и сетка читается поперёк — а
                // именно так на неё и смотрят, сравнивая «где сколько».
                Spacer(Modifier.weight(1f))

                Money(
                    // Долговой счёт без долга показывает ноль, а не пустоту:
                    // «карта погашена» — это новость, и её надо видеть.
                    text = formatMoney(
                        if (account.kind.owed) debt else line.amount,
                        currency = account.currency,
                    ),
                    color = when {
                        account.closed -> Muted
                        account.kind.owed -> if (debt > 0) Danger else ModeGreen
                        line.amount < 0 -> Danger
                        else -> Ink
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (account.kind.owed) {
                    Text(
                        text = if (debt > 0) "долг" else "погашено",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }

                if (share != null && left != null) {
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
                                // Полоска лимита остаётся акцентной и красной:
                                // она не метка, а сигнал, и краска счёта на ней
                                // означала бы «зелёный — значит хорошо».
                                .background(if (over) Danger else Accent),
                        )
                    }
                    Text(
                        text = if (over) {
                            "сверх лимита " + formatMoney(-left, currency = account.currency)
                        } else {
                            "доступно " + formatMoney(left, currency = account.currency)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (over) Danger else Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Сумма на карточке счёта — набранная так, чтобы поместиться целиком.
 *
 * Кегль убавляется, пока строка не встанет в ширину карточки. В трети экрана
 * «128 430 ₽» двадцать первым кеглем не помещается, а сумма, обрезанная
 * многоточием, — это не сумма: остаток счёта читают ради последних цифр не
 * меньше, чем ради первых.
 *
 * Не ниже пятнадцатого: дальше число перестаёт читаться мельком, а ради
 * миллиарда рублей ломать вид всей сетки незачем — такое всё-таки обрежется.
 *
 * Пока кегль подбирается, строка не рисуется: подбор идёт кадрами, и без
 * этого сумма при каждом открытии книги сперва моргала бы крупной.
 */
@Composable
private fun Money(text: String, color: Color, modifier: Modifier = Modifier) {
    var size by remember(text) { mutableStateOf(MONEY_SIZE) }
    var fitted by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontSize = size,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        color = color,
        modifier = modifier.drawWithContent { if (fitted) drawContent() },
        onTextLayout = { laid ->
            if (laid.hasVisualOverflow && size > MONEY_MIN) {
                size = (size.value - 1f).sp
            } else {
                fitted = true
            }
        },
    )
}

/** Кегль суммы на карточке счёта: с чего начинают и куда убавляют. */
private val MONEY_SIZE = 21.sp
private val MONEY_MIN = 15.sp

/** Знак вида счёта: по нему кошелёк отличают от карты раньше, чем читают. */
private fun markOf(kind: AccountKind): ImageVector = when (kind) {
    AccountKind.CASH -> Icons.Outlined.Payments
    AccountKind.CARD -> Icons.Outlined.CreditCard
    AccountKind.CREDIT -> Icons.Outlined.CreditScore
    AccountKind.SAVINGS -> Icons.Outlined.Savings
    AccountKind.DEBT -> Icons.Outlined.TrendingDown
}

/**
 * Кружок краски-метки перед названием.
 *
 * Девять точек в поперечнике: меньше — и краска перестаёт узнаваться, больше —
 * и кружок начинает спорить с названием, рядом с которым стоит.
 */
@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(color),
    )
}
