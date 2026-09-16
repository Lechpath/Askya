package app.askya.data.sync

/**
 * Журнал правок в самой базе: что изменилось, когда и кем ещё не отправлено.
 *
 * ## Зачем журнал, а не «посмотрим, что поменялось»
 *
 * Синхронизация должна знать не «как выглядит база сейчас», а «что в ней
 * изменилось с прошлого раза». Без журнала это выяснялось бы сравнением всей
 * базы с её копией — то есть второй базой рядом.
 *
 * ## Почему триггерами, а не в репозиториях
 *
 * Строку в Askya пишут из десятка мест: репозитории, окна, корзина на сутки,
 * чтение Слепка, сборка дня. Дописать «отметь правку» в каждое — значит
 * однажды забыть, и забытое место тихо перестанет уезжать в облако. Триггер
 * стоит у таблицы, и мимо него не пройдёт ни один `INSERT`.
 *
 * ## Часы
 *
 * У правки должно быть время, по которому две версии одной строки сравнивают.
 * Часы телефона для этого не годятся: сбитые на день назад, они сделали бы
 * свежую правку старой навсегда. Поэтому часы **гибридные** ([SyncClock]):
 * миллисекунды, сдвинутые на 12 бит, плюс счётчик в младших битах. Новая
 * отметка — это `MAX(прошлая + 1, сейчас)`, то есть время идёт вперёд даже у
 * телефона, у которого оно пошло назад. Принимая чужие правки, часы
 * подтягивают к самой поздней из них — так порядок остаётся общим у всех
 * устройств.
 *
 * ## Чужие правки не уезжают обратно
 *
 * Применяя пришедшее из облака, синхронизация ставит `applying = 1`, и триггеры
 * молчат: иначе каждая принятая правка немедленно считалась бы своей и уезжала
 * назад — бесконечное эхо на двоих.
 */
object SyncSchema {

    /**
     * Таблицы, которые едут в облако.
     *
     * Здесь нет `bridges` (мост ведёт в приложение телефона, на компьютере его
     * не открыть), таблиц Echo и AskyaV (фонотека у каждого устройства своя) и
     * настроек — они не в базе вовсе.
     */
    val TABLES = listOf(
        "notes",
        "scroll_topics",
        "image_albums",
        "schedule_items",
        "routine_items",
        "deed_tasks",
        "generated_days",
        "reminders",
        "yet_lists",
        "yet_items",
        "ledger_accounts",
        "ledger_categories",
        "ledger_entries",
    )

    /** Часы и журнал — как их создаёт Room; migration повторяет это слово в слово. */
    val CLOCK_TABLE =
        "CREATE TABLE IF NOT EXISTS `sync_clock` (`id` INTEGER NOT NULL, `hlc` INTEGER NOT NULL, " +
            "`applying` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    /**
     * Журнал, каким его завела 47-я версия. Колонка `base` появилась в 48-й
     * ([migrate47to48]) и дописана здесь нарочно не будет: переход 46 → 47
     * должен оставлять базу ровно такой, какой её оставляла та версия, иначе
     * следующий переход попробует добавить колонку дважды.
     */
    val STATE_TABLE =
        "CREATE TABLE IF NOT EXISTS `sync_state` (`tbl` TEXT NOT NULL, `uid` TEXT NOT NULL, " +
            "`hlc` INTEGER NOT NULL, `dead` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, " +
            "PRIMARY KEY(`tbl`, `uid`))"

    val STATE_INDEX =
        "CREATE INDEX IF NOT EXISTS `index_sync_state_dirty` ON `sync_state` (`dirty`)"

    /** Единственная строка часов. Её заводят и на новой базе, и при миграции. */
    val CLOCK_ROW = "INSERT OR IGNORE INTO `sync_clock` (`id`, `hlc`, `applying`) VALUES (0, 0, 0)"

    /**
     * Что выполняется на **новой** базе: Room создаёт таблицы сам, но о
     * триггерах не знает — их нет в его схеме, и создавать их приходится
     * рядом с ней.
     */
    fun onCreate(): List<String> = listOf(CLOCK_ROW) + triggers()

    /** Все триггеры журнала — по три на таблицу. */
    fun triggers(): List<String> = TABLES.flatMap { table ->
        listOf(
            trigger(table, "ins", "INSERT", "NEW", dead = 0),
            trigger(table, "upd", "UPDATE", "NEW", dead = 0),
            trigger(table, "del", "DELETE", "OLD", dead = 1),
        )
    }

    /**
     * Один триггер.
     *
     * Условие `applying = 0` — молчание при приёме чужого. Удаление не стирает
     * строку журнала, а помечает её мёртвой: иначе другое устройство,
     * не знающее об удалении, вернуло бы строку обратно при первой же встрече.
     *
     * Отметка ставится в два шага — «поправить, а если нечего, дописать», — и
     * это не многословие вместо `INSERT OR REPLACE`. Внутри триггера SQLite
     * берёт способ разрешать конфликт не у него, а у той команды, которая
     * триггер вызвала: Room обновляет строки через `UPDATE OR ABORT`, и
     * `OR REPLACE` в триггере превращался в `OR ABORT` — правка записи падала
     * с ошибкой уникальности. `INSERT … ON CONFLICT DO UPDATE` тоже не годится:
     * его нет в SQLite Android 8, а Askya ставится с восьмого.
     */
    private fun trigger(table: String, suffix: String, event: String, row: String, dead: Int): String =
        "CREATE TRIGGER IF NOT EXISTS `sync_${table}_$suffix` AFTER $event ON `$table` FOR EACH ROW " +
            "WHEN (SELECT `applying` FROM `sync_clock` WHERE `id` = 0) = 0 " +
            "BEGIN " +
            "UPDATE `sync_clock` SET `hlc` = MAX(`hlc` + 1, " +
            "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) * 4096) WHERE `id` = 0; " +
            "UPDATE `sync_state` SET " +
            "`hlc` = (SELECT `hlc` FROM `sync_clock` WHERE `id` = 0), `dead` = $dead, `dirty` = 1 " +
            "WHERE `tbl` = '$table' AND `uid` = $row.`uid`; " +
            "INSERT INTO `sync_state` (`tbl`, `uid`, `hlc`, `dead`, `dirty`) " +
            "SELECT '$table', $row.`uid`, (SELECT `hlc` FROM `sync_clock` WHERE `id` = 0), $dead, 1 " +
            "WHERE NOT EXISTS (" +
            "SELECT 1 FROM `sync_state` WHERE `tbl` = '$table' AND `uid` = $row.`uid`); " +
            "END"

    /**
     * Переход 46 → 47: имя у каждой строки, часы, журнал и триггеры.
     *
     * Уже записанные строки получают случайные имена — все, кроме отметок
     * заполненного дня: у тех имя считается из самой даты и потому совпадает у
     * двух устройств, которые завели такую отметку порознь. С делами,
     * развёрнутыми из распорядка, так поступить нельзя — их имя считается из
     * двух значений хешем, а SHA-256 в SQLite нет. Для прошлого это и не нужно:
     * второе устройство наполняется из облака с чистого листа, а дубли
     * возможны только у дней, заполненных двумя устройствами порознь, — то
     * есть начиная с этого дня, когда имена уже ставит [Uid.ofRoutineDay].
     *
     * Все строки помечаются `dirty = 1`: первая синхронизация должна увезти
     * всё, что накопилось до неё. Отметка времени у них нулевая — «было до
     * часов»; любая настоящая правка позже окажется свежее.
     */
    fun migrate46to47(): List<String> = buildList {
        add(CLOCK_TABLE)
        add(STATE_TABLE)
        add(STATE_INDEX)
        add(CLOCK_ROW)
        TABLES.forEach { table ->
            add("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
            if (table == "generated_days") {
                add("UPDATE `generated_days` SET `uid` = 'generated-' || `date`")
            } else {
                add("UPDATE `$table` SET `uid` = lower(hex(randomblob(16)))")
            }
            add("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uid` ON `$table` (`uid`)")
            add(
                "INSERT OR REPLACE INTO `sync_state` (`tbl`, `uid`, `hlc`, `dead`, `dirty`) " +
                    "SELECT '$table', `uid`, 0, 0, 1 FROM `$table`",
            )
        }
        addAll(triggers())
    }

    /**
     * Переход 47 → 48: у строки журнала появилась отметка, на чём основана её
     * правка, — `base` (см. [app.askya.data.entity.SyncState.base]).
     *
     * Ради неё и заведена: без неё «оставить обе версии» работало бы на
     * догадке. Своя правка успевает уехать в облако раньше, чем придёт чужая, и
     * к тому времени отметка «неотправленное» уже снята — спор выглядел бы как
     * обычная свежая правка, и написанное на одном устройстве молча стёрлось бы
     * написанным на другом.
     *
     * У всех уже записанных строк `base` нулевой: с облаком они ещё не
     * сходились ни разу.
     *
     * Триггеры не трогаются: `base` они не пишут вовсе — её ставит сама
     * синхронизация, отправив своё или приняв чужое. Дописка колонки со
     * значением по умолчанию их не ломает.
     */
    fun migrate47to48(): List<String> = listOf(
        "ALTER TABLE `sync_state` ADD COLUMN `base` INTEGER NOT NULL DEFAULT 0",
    )
}
