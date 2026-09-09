package app.askya.data.repository

import app.askya.data.db.dao.LedgerDao
import app.askya.data.entity.LedgerAccount
import app.askya.data.entity.LedgerCategory
import app.askya.data.entity.LedgerEntry
import app.askya.domain.model.AccountKind
import app.askya.domain.model.Currency
import app.askya.domain.model.EntryKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Расходная книга: счета, статьи и записи.
 *
 * ## Остаток нигде не хранится
 *
 * Ни у счёта, ни у месяца нет колонки «сколько». Всё складывается из записей
 * при каждом взгляде: остаток счёта — начальный плюс всё, что по нему прошло;
 * итог месяца — сумма его записей. Хранить посчитанное значило бы завести
 * второй источник правды, и первая же правка задним числом развела бы их.
 *
 * ## Перевод — не доход и не расход
 *
 * Переложенное с карты в кошелёк не меняет ни того, сколько человек получил, ни
 * того, сколько потратил: меняется только, где деньги лежат. Поэтому перевод
 * есть в остатках счетов и его нет в итогах месяца — см. [MonthBook].
 *
 * ## Возврат — расход со знаком минус
 *
 * Вернувшееся за чужую трату не доход: месяц, в котором «пришло» вырастает от
 * того, что за вас отдали за такси, врёт про заработок. Возврат вычитается из
 * расхода — из «ушло» и из той статьи, по которой трата прошла, — и так трата,
 * сделанная не на свои, из бюджета уходит. См. [EntryKind.BACK].
 */
class LedgerRepository(private val dao: LedgerDao) {

    fun accounts(): Flow<List<LedgerAccount>> = dao.observeAccounts()

    /**
     * Счета вместе с тем, сколько на них сейчас.
     *
     * Двумя потоками, сведёнными в один: список счетов меняется редко, а
     * остатки — с каждой записью, и держать их одним запросом значило бы
     * перечитывать названия счетов при каждой покупке хлеба.
     */
    fun wealth(): Flow<List<AccountLine>> =
        combine(dao.observeAccounts(), dao.observeDeltas()) { accounts, deltas ->
            val byId = deltas.associate { it.id to it.delta }
            accounts.map { account ->
                AccountLine(account, account.opening + (byId[account.id] ?: 0L))
            }
        }

    fun categories(): Flow<List<LedgerCategory>> = dao.observeCategories()

    /** Записи одного дня — «Что было» в AskyaDay. */
    fun entriesOn(date: LocalDate): Flow<List<LedgerEntry>> = dao.observeIn(date, date)

    /**
     * Самый поздний месяц, в котором есть запись, — граница листания вперёд.
     *
     * `null` означает «дальше нынешнего месяца ничего нет», и это обычный
     * ответ: книга пишется про прошлое. Нужна граница ради того редкого
     * случая, когда запись всё же оказалась впереди, — см.
     * [LedgerDao.observeLastDate].
     */
    fun edge(): Flow<YearMonth?> = dao.observeLastDate().map { date -> date?.let(YearMonth::from) }

    /**
     * Месяц целиком: его записи и всё, что из них считается.
     *
     * Со списком счетов, а не с одними записями: у счёта своя валюта, а курсов
     * книга не знает (см. [Currency]). В ленту месяца попадает всё, что за
     * месяц было, — но складываются в итоги и разносятся по статьям только
     * записи главной валюты: «Еда», в которой рубли сложены с долларами, — не
     * сумма, а описка. Сколько валютных записей осталось за итогом, лежит в
     * [MonthBook.foreign], и месяц говорит об этом строкой.
     */
    fun month(month: YearMonth): Flow<MonthBook> =
        combine(
            dao.observeIn(month.atDay(1), month.atEndOfMonth()),
            dao.observeAccounts(),
        ) { entries, accounts ->
            val currencyOf = accounts.associate { it.id to it.currency }
            val (own, foreign) = entries.partition { entry ->
                (currencyOf[entry.accountId] ?: Currency.RUB).main
            }

            // Возврат считается расходом со знаком минус — и в общем «ушло», и
            // в статье, по которой прошла трата. Складываются они поэтому
            // вместе, одной кучей: врозь пришлось бы вычитать одну карту из
            // другой, помня, что в первой есть статьи, которых нет во второй.
            val outgoing = own.filter {
                it.kind == EntryKind.SPEND || it.kind == EntryKind.BACK
            }
            val returned = own.filter { it.kind == EntryKind.BACK }.sumOf { it.amount }
            MonthBook(
                month = month,
                entries = entries,
                foreign = foreign,
                earned = own.filter { it.kind == EntryKind.EARN }.sumOf { it.amount },
                spent = outgoing.sumOf { it.signed },
                returned = returned,
                spentByCategory = outgoing
                    .groupBy { it.categoryId }
                    .mapValues { (_, rows) -> rows.sumOf { it.signed } },
                earnedByCategory = own
                    .filter { it.kind == EntryKind.EARN }
                    .groupBy { it.categoryId }
                    .mapValues { (_, rows) -> rows.sumOf { it.amount } },
            )
        }

    /**
     * Движение средств по одному счёту — то, из чего сложился его остаток.
     *
     * Карточка счёта отвечала на «сколько на нём сейчас», и это был тупик:
     * увидев на карте не то число, человек шёл искать её траты в общей ленте
     * месяца, вперемешку со всеми прочими. Теперь тап по счёту раскрывает его
     * собственную ленту.
     *
     * Переводы приходят с обоих концов — см. [LedgerDao.observeOnAccount].
     */
    fun onAccount(accountId: Long): Flow<List<LedgerEntry>> =
        dao.observeOnAccount(accountId, ACCOUNT_ENTRIES)

    /**
     * Вся книга, сложенная по месяцам и статьям, — для страницы «Статистика».
     *
     * Одним потоком на всё: срезы по годам и полугодиям, средние, статьи и
     * столбики месяцев считаются из одних и тех же клеток уже в разметке.
     * Спрашивать базу заново на каждый выбранный период значило бы гонять по
     * запросу на каждый тап, притом за теми же числами.
     */
    fun stats(): Flow<LedgerStats> = dao.observeTotals().map { rows ->
        LedgerStats(
            cells = rows.mapNotNull { row ->
                // Месяц пришёл строкой «2026-08». Неразобранная клетка
                // выбрасывается, а не роняет страницу: такой в базе взяться
                // неоткуда, но статистика — не то место, ради которого стоит
                // падать.
                val month = runCatching { YearMonth.parse(row.month) }.getOrNull()
                month?.let { StatCell(it, row.categoryId, row.kind, row.amount) }
            },
        )
    }

    /**
     * Первый вход в раздел: завести словарь статей и первый счёт.
     *
     * Здесь, а не в миграции: обновление приложения не должно вписывать «Еду» и
     * «Кошелёк» тому, кто книгу вести не собирался. Заводится ровно один раз —
     * пустота таблицы и есть признак «ещё не начинали»; кто стёр всё сам,
     * получит словарь заново, и это лучше пустого экрана без объяснений.
     *
     * Счёт один и называется «Кошелёк»: без счёта первая же запись упирается в
     * вопрос «а откуда деньги?», а спрашивать его до первой траты — значит
     * встречать человека анкетой. Переименовать или закрыть его можно тут же.
     */
    suspend fun ensureStarted() {
        if (dao.countAccounts() == 0) {
            dao.insertAccount(LedgerAccount(title = "Кошелёк", kind = AccountKind.CASH))
        }
        if (dao.countCategories() == 0) {
            STARTER_SPEND.forEach { title ->
                dao.insertCategory(LedgerCategory(title = title, kind = EntryKind.SPEND))
            }
            STARTER_EARN.forEach { title ->
                dao.insertCategory(LedgerCategory(title = title, kind = EntryKind.EARN))
            }
        }
    }

    /**
     * Записать. Новая запись отличается от правки только тем, что у неё нет
     * номера, — отдельного «добавить» здесь не заводится.
     *
     * Лишнее у записи стирается на входе, а не на экране: у перевода не бывает
     * статьи, у расхода, дохода и возврата — второго счёта. Поле, оставшееся
     * от передуманного вида записи, потом всплыло бы в итогах месяца.
     */
    suspend fun save(entry: LedgerEntry): Long {
        val clean = entry.copy(
            note = entry.note.trim(),
            toAccountId = if (entry.kind == EntryKind.MOVE) entry.toAccountId else null,
            categoryId = if (entry.kind == EntryKind.MOVE) null else entry.categoryId,
        )
        if (clean.id == 0L) return dao.insertEntry(clean)
        dao.updateEntry(clean)
        return clean.id
    }

    /** Убрать запись — в корзину на сутки. См. [ScheduleRepository.remove]. */
    suspend fun remove(id: Long) = dao.setEntryRemoved(id, LocalDateTime.now())

    suspend fun restore(id: Long) = dao.setEntryRemoved(id, null)

    suspend fun purgeTrash() = dao.purgeEntries(LocalDateTime.now().minusDays(1))

    suspend fun saveAccount(account: LedgerAccount): Long {
        val clean = account.copy(title = account.title.trim().ifBlank { "Счёт" })
        // Новый счёт встаёт последним, а не первым: человек разложил сетку
        // руками, и вклинивать в неё новичка перед его картой — значит ломать
        // разложенное. Дойдёт очередь — переставит сам.
        if (clean.id == 0L) return dao.insertAccount(clean.copy(position = dao.nextAccountPosition()))
        dao.updateAccount(clean)
        return clean.id
    }

    /**
     * Переписать порядок счетов целиком — в том виде, в каком его показали.
     *
     * Списком, а не перестановкой двух номеров, по той же причине, что и в
     * плейлисте ([EchoRepository.reorder]): номера в базе бывают с повторами —
     * у всех счетов, заведённых до того, как порядок появился, он один и тот
     * же, — и обмен двух одинаковых номеров не меняет ничего. Пересчёт от нуля
     * по списку с экрана разойтись с тем, что человек видит, не может.
     */
    suspend fun reorderAccounts(accounts: List<LedgerAccount>) {
        accounts.forEachIndexed { at, account -> dao.setAccountPosition(account.id, at) }
    }

    /**
     * Стереть счёт совсем — и только пустой.
     *
     * На счёте, по которому что-то прошло, висит история: сотрёшь его — и
     * прошлогодний месяц перестанет сходиться. Такой счёт закрывают
     * ([LedgerAccount.closed]), а не удаляют. Ответ `false` означает «не стёрли,
     * потому что не пустой», и экран говорит об этом словами.
     */
    suspend fun deleteAccount(id: Long): Boolean {
        if (dao.countEntriesOn(id) > 0) return false
        dao.deleteAccount(id)
        return true
    }

    suspend fun saveCategory(category: LedgerCategory): Long {
        val clean = category.copy(title = category.title.trim().ifBlank { "Статья" })
        if (clean.id == 0L) return dao.insertCategory(clean)
        dao.updateCategory(clean)
        return clean.id
    }

    /**
     * Убрать статью. Записи по ней остаются и становятся «без статьи»: человек
     * убирал графу, а не прошлогодние траты.
     */
    suspend fun forgetCategory(id: Long) {
        dao.detachFromCategory(id)
        dao.deleteCategory(id)
    }
}

/**
 * Расход со своим знаком: у возврата он отрицательный, а всё прочее — не
 * расход вовсе и весит ноль.
 *
 * Одно правило на всю книгу, где возврат превращается в «минус расход»: и
 * месяц, и статистика складывают траты им, и разъехаться они не могут. Ноль
 * у дохода и перевода — не отговорка, а то самое «в расход не входит», из-за
 * которого их иначе пришлось бы отфильтровывать в каждом месте отдельно.
 */
private val LedgerEntry.signed: Long
    get() = when (kind) {
        EntryKind.SPEND -> amount
        EntryKind.BACK -> -amount
        else -> 0
    }

/** То же правило для клетки статистики — см. [signed] у записи. */
private val StatCell.signed: Long
    get() = when (kind) {
        EntryKind.SPEND -> amount
        EntryKind.BACK -> -amount
        else -> 0
    }

/**
 * Докуда раскрывается лента счёта. Три сотни строк — это больше года частых
 * трат: столько прокручивают до конца разве что нарочно, а держать в окне всю
 * пятилетнюю книгу незачем.
 */
private const val ACCOUNT_ENTRIES = 300

/** Счёт вместе с тем, сколько на нём сейчас. */
data class AccountLine(val account: LedgerAccount, val amount: Long)

/**
 * Месяц книги: записи и всё, что из них следует.
 *
 * Переводов в [earned] и [spent] нет намеренно — см. [EntryKind.MOVE]. В
 * разбивке по статьям ключ `null` означает «без статьи»: такие траты стоят
 * своей строкой, а не растворяются в «Прочем».
 *
 * [spent] и [spentByCategory] — уже за вычетом возвратов, и потому бывают
 * отрицательными: вернули в сентябре за августовскую трату — сентябрь по этой
 * статье уходит в минус (см. [EntryKind.BACK]). Сколько именно вернули,
 * лежит в [returned] — не чтобы вычесть его ещё раз, а чтобы месяц мог
 * сказать словами, откуда взялось уменьшившееся «ушло».
 */
data class MonthBook(
    val month: YearMonth = YearMonth.now(),
    val entries: List<LedgerEntry> = emptyList(),
    /**
     * Записи месяца по валютным счетам. Они есть в [entries] и стоят в ленте,
     * но ни в один итог и ни в одну статью не входят: складывать доллары с
     * рублями книга не берётся — см. [Currency].
     */
    val foreign: List<LedgerEntry> = emptyList(),
    val earned: Long = 0,
    val spent: Long = 0,
    /** Сколько за месяц вернули. Из [spent] уже вычтено. */
    val returned: Long = 0,
    val spentByCategory: Map<Long?, Long> = emptyMap(),
    val earnedByCategory: Map<Long?, Long> = emptyMap(),
) {
    /** Сколько осталось от полученного за месяц. Бывает и отрицательным. */
    val left: Long get() = earned - spent
}

/**
 * Словарь, с которого начинают.
 *
 * Не список дозволенного: свою статью заводят прямо в записи, а ненужную
 * убирают. Восемь и четыре — это не «все статьи мира», а те, по которым в
 * первый же месяц раскладывается почти всё; пустой список вместо них заставил
 * бы придумывать графы до первой траты.
 */
private val STARTER_SPEND = listOf(
    "Еда",
    "Дом",
    "Транспорт",
    "Здоровье",
    "Связь",
    "Одежда",
    "Отдых",
    "Прочее",
)

private val STARTER_EARN = listOf(
    "Зарплата",
    "Подработка",
    "Подарок",
    "Прочее",
)

/**
 * Клетка статистики: сколько в таком-то месяце прошло по такой-то статье.
 *
 * Ровно то же, что вернул SQLite ([app.askya.data.db.dao.LedgerTotal]), только
 * месяц уже разобран. Переводов среди клеток нет — см. [EntryKind.MOVE], — а
 * возвраты лежат своим видом и вычитаются из расхода уже здесь, в [LedgerStats].
 */
data class StatCell(
    val month: YearMonth,
    val categoryId: Long?,
    val kind: EntryKind,
    val amount: Long,
)

/** Месяц одной строкой: сколько пришло и сколько ушло. */
data class MonthTotal(val month: YearMonth, val earned: Long, val spent: Long) {
    val left: Long get() = earned - spent
}

/**
 * Статистика книги за все времена — и за любой её срез.
 *
 * Внутри лежат те же клетки, что вернула база; всё остальное складывается из
 * них по требованию. Средние, столбики месяцев и разбор по статьям — это не
 * разные данные, а разные взгляды на одни и те же числа, и хранить их
 * посчитанными значило бы завести четыре источника правды вместо одного.
 *
 * Срез ([since]) отрезает старые месяцы и возвращает такую же статистику — так
 * страница «Статистика» переключает годы, ни о чём не спрашивая базу.
 *
 * Пустые месяцы в [months] не появляются: месяц, в котором не было ни одной
 * записи, — это не ноль, а «книгу тогда не вели», и столбик в ноль на графике
 * врал бы про месяц, которого не было. Считается по этой же причине и среднее:
 * делится на прожитые месяцы, а не на длину периода.
 */
data class LedgerStats(val cells: List<StatCell> = emptyList()) {

    /** Месяцы по возрастанию — в том порядке, в каком они идут столбиками. */
    val months: List<MonthTotal> by lazy {
        cells.groupBy { it.month }
            .map { (month, rows) ->
                MonthTotal(
                    month = month,
                    earned = rows.filter { it.kind == EntryKind.EARN }.sumOf { it.amount },
                    spent = rows.sumOf { it.signed },
                )
            }
            .sortedBy { it.month }
    }

    val earned: Long by lazy { cells.filter { it.kind == EntryKind.EARN }.sumOf { it.amount } }
    val spent: Long by lazy { cells.sumOf { it.signed } }

    /** Сколько за срок вернули. Из [spent] уже вычтено — см. [EntryKind.BACK]. */
    val returned: Long by lazy {
        cells.filter { it.kind == EntryKind.BACK }.sumOf { it.amount }
    }

    val left: Long get() = earned - spent

    /** Сколько месяцев книгу вели. Ноль — не вели вовсе. */
    val livedMonths: Int get() = months.size

    val averageEarned: Long get() = if (livedMonths == 0) 0 else earned / livedMonths
    val averageSpent: Long get() = if (livedMonths == 0) 0 else spent / livedMonths

    /** Самый расточительный месяц и самый скромный. `null` — месяцев нет. */
    val richest: MonthTotal? get() = months.maxByOrNull { it.spent }
    val leanest: MonthTotal? get() = months.filter { it.spent > 0 }.minByOrNull { it.spent }

    /**
     * Куда уходило и откуда приходило: ключ `null` — «без статьи».
     *
     * У расхода из статьи вычтены возвраты по ней: спрашивают «сколько ушло на
     * еду», а ушло на неё столько, сколько не вернулось. Отдельного разбора по
     * [EntryKind.BACK] нет и не нужно — возврат живёт в той же статье, что и
     * трата.
     */
    fun byCategory(kind: EntryKind): Map<Long?, Long> =
        if (kind == EntryKind.SPEND) {
            cells.filter { it.kind == EntryKind.SPEND || it.kind == EntryKind.BACK }
                .groupBy { it.categoryId }
                .mapValues { (_, rows) -> rows.sumOf { it.signed } }
        } else {
            cells.filter { it.kind == kind }
                .groupBy { it.categoryId }
                .mapValues { (_, rows) -> rows.sumOf { it.amount } }
        }

    /** Срез с такого-то месяца включительно. `null` — за все времена. */
    fun since(from: YearMonth?): LedgerStats =
        if (from == null) this else LedgerStats(cells.filter { it.month >= from })

    /** Есть ли что показывать вообще. */
    val empty: Boolean get() = cells.isEmpty()
}
