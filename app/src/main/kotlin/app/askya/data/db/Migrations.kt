package app.askya.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.askya.domain.markdown.ListInput

/**
 * 1 → 2: у блока дня появились конец, тип и статус вместо флага «сделано»,
 * у чек-ина — боль в теле, а шкала оценок выросла с 1..5 до 1..10.
 *
 * Обе таблицы пересоздаются, а не правятся через ALTER: колонок меняется больше,
 * чем SQLite умеет добавить, и Room сверяет схему до последнего DEFAULT — проще
 * собрать таблицу ровно такой, какой её ожидает сгенерированный код.
 *
 * SQL сверен с выгруженными схемами в `app/schemas`.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedule_items_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`startTime` TEXT NOT NULL, " +
                "`endTime` TEXT, " +
                "`title` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `schedule_items_new` " +
                "(`id`, `date`, `startTime`, `endTime`, `title`, `type`, `status`) " +
                "SELECT `id`, `date`, `time`, NULL, `title`, 'OTHER', " +
                "CASE `done` WHEN 0 THEN 'PLANNED' ELSE 'DONE' END FROM `schedule_items`"
        )
        db.execSQL("DROP TABLE `schedule_items`")
        db.execSQL("ALTER TABLE `schedule_items_new` RENAME TO `schedule_items`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_schedule_items_date` ON `schedule_items` (`date`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `check_ins_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`mood` INTEGER NOT NULL, " +
                "`energy` INTEGER NOT NULL, " +
                "`bodyPain` INTEGER NOT NULL, " +
                "`comment` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL)"
        )
        // Старые оценки 1..5 растягиваются в новую шкалу удвоением: 1 -> 2, 5 -> 10.
        // Боли в старых записях нет, и придумать её нельзя — ставится минимум шкалы,
        // из-за чего режим по таким чек-инам считается фактически только по энергии.
        db.execSQL(
            "INSERT INTO `check_ins_new` " +
                "(`id`, `date`, `mood`, `energy`, `bodyPain`, `comment`, `createdAt`) " +
                "SELECT `id`, `date`, `mood` * 2, `energy` * 2, 1, `comment`, `createdAt` " +
                "FROM `check_ins`"
        )
        db.execSQL("DROP TABLE `check_ins`")
        db.execSQL("ALTER TABLE `check_ins_new` RENAME TO `check_ins`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_check_ins_date` ON `check_ins` (`date`)"
        )
    }
}

/**
 * 2 → 3: распорядок обычного дня и отметки о заполненных днях. Обе таблицы
 * новые, поэтому старые данные не трогаются вовсе.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`startTime` TEXT NOT NULL, " +
                "`endTime` TEXT, " +
                "`type` TEXT NOT NULL, " +
                "`enabled` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `generated_days` (" +
                "`date` TEXT NOT NULL, PRIMARY KEY(`date`))"
        )
    }
}

/**
 * 3 → 4: у дела распорядка появилась важность — по ней Askya решает, куда
 * поставить дело, время которого в рассказе не названо.
 *
 * Таблица пересоздаётся, а не правится через ALTER: у колонки нет DEFAULT
 * в сущности, и добавленный в миграции DEFAULT разошёлся бы со схемой Room.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine_items_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`startTime` TEXT NOT NULL, " +
                "`endTime` TEXT, " +
                "`type` TEXT NOT NULL, " +
                "`priority` TEXT NOT NULL, " +
                "`enabled` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `routine_items_new` " +
                "(`id`, `title`, `startTime`, `endTime`, `type`, `priority`, `enabled`) " +
                "SELECT `id`, `title`, `startTime`, `endTime`, `type`, 'NORMAL', `enabled` " +
                "FROM `routine_items`"
        )
        db.execSQL("DROP TABLE `routine_items`")
        db.execSQL("ALTER TABLE `routine_items_new` RENAME TO `routine_items`")
    }
}

/**
 * 4 → 5: у дела дня вместо типа и четырёхзначного статуса остались заметка и
 * флаг «сделано», у дела распорядка исчез тип.
 *
 * Тип и статусы не переносятся никуда: в расписании они не помогали читать
 * список, а заполнять их приходилось при каждом деле. Из статуса сохраняется
 * только то, было ли дело выполнено; «пропущено» и «перенесено» становятся
 * невыполненным — обратного отображения у них нет.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedule_items_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`startTime` TEXT NOT NULL, " +
                "`endTime` TEXT, " +
                "`title` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`done` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `schedule_items_new` " +
                "(`id`, `date`, `startTime`, `endTime`, `title`, `note`, `done`) " +
                "SELECT `id`, `date`, `startTime`, `endTime`, `title`, '', " +
                "CASE `status` WHEN 'DONE' THEN 1 ELSE 0 END FROM `schedule_items`"
        )
        db.execSQL("DROP TABLE `schedule_items`")
        db.execSQL("ALTER TABLE `schedule_items_new` RENAME TO `schedule_items`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_schedule_items_date` ON `schedule_items` (`date`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine_items_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`startTime` TEXT NOT NULL, " +
                "`endTime` TEXT, " +
                "`priority` TEXT NOT NULL, " +
                "`enabled` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `routine_items_new` " +
                "(`id`, `title`, `startTime`, `endTime`, `priority`, `enabled`) " +
                "SELECT `id`, `title`, `startTime`, `endTime`, `priority`, `enabled` " +
                "FROM `routine_items`"
        )
        db.execSQL("DROP TABLE `routine_items`")
        db.execSQL("ALTER TABLE `routine_items_new` RENAME TO `routine_items`")
    }
}

/**
 * 5 → 6: профиль человека и история интервью. Обе таблицы новые, старые
 * данные не трогаются.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile_sections` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `interview_messages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`fromUser` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL)"
        )
    }
}

/**
 * 6 → 7: ответы опросника «AskyaKnew». Таблица новая, старое не трогается.
 *
 * Ключ — номер вопроса, поэтому PRIMARY KEY объявлен отдельной строкой, как
 * его выгружает Room для неавтогенерируемого ключа: колонка с `PRIMARY KEY`
 * внутри объявления разошлась бы с выгруженной схемой.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `self_answers` (" +
                "`position` INTEGER NOT NULL, " +
                "`question` TEXT NOT NULL, " +
                "`answer` TEXT NOT NULL, " +
                "PRIMARY KEY(`position`))"
        )
    }
}

/**
 * 7 → 8: Scroll. Записи получают тему и ссылку на приложенный файл, темы
 * заводятся отдельной таблицей.
 *
 * `notes` пересоздаётся, а не правится через ALTER: колонок прибавляется
 * четыре, и Room сверяет схему до последнего DEFAULT — добавленный в миграции
 * DEFAULT разошёлся бы с сущностью, где его нет. Существующие заметки
 * переносятся как есть и оказываются в разделе «Файлы»: темы у них не было,
 * файла тоже.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scroll_topics` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`tags` TEXT NOT NULL, " +
                "`topicId` INTEGER, " +
                "`uri` TEXT, " +
                "`mime` TEXT NOT NULL, " +
                "`isImage` INTEGER NOT NULL, " +
                "`createdAt` TEXT NOT NULL, " +
                "`updatedAt` TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `notes_new` " +
                "(`id`, `title`, `body`, `tags`, `topicId`, `uri`, `mime`, `isImage`, " +
                "`createdAt`, `updatedAt`) " +
                "SELECT `id`, `title`, `body`, `tags`, NULL, NULL, '', 0, " +
                "`createdAt`, `updatedAt` FROM `notes`"
        )
        db.execSQL("DROP TABLE `notes`")
        db.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_topicId` ON `notes` (`topicId`)")
    }
}

/**
 * 8 → 9: Yet. Быстрые списки и их строки.
 *
 * Две новые таблицы и ничего больше — старые данные не трогаются. Внешнего
 * ключа на список нет намеренно: строки удаляются вместе со списком явно, в
 * репозитории, и каскад из базы только прятал бы это.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `yet_lists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `yet_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`listId` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`done` INTEGER NOT NULL, " +
                "`createdAt` TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_yet_items_listId` ON `yet_items` (`listId`)")
    }
}

/**
 * 9 → 10: у напоминания появились способ и связь с делом дня.
 *
 * ALTER, а не пересоздание таблицы: колонок две, у обеих есть DEFAULT в самой
 * сущности, и Room сверяет схему без придирок. Существующие напоминания
 * становятся звучащими и ничьими — это ровно то, чем они были.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `silent` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `itemId` INTEGER DEFAULT NULL")
    }
}

/**
 * 10 → 11: у дела появился выбранный руками знак.
 *
 * По колонке в обе таблицы, обе пустые: пусто значит «знак угадывается по
 * названию» — то есть ровно то, как всё работало до сих пор. Поэтому ALTER без
 * пересоздания и без переноса данных.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `schedule_items` ADD COLUMN `icon` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `routine_items` ADD COLUMN `icon` TEXT DEFAULT NULL")
    }
}

/**
 * 11 → 12: «Изображения» отвязаны от «Книг» и получили свои альбомы.
 *
 * Новая таблица под альбомы, колонка альбома у записи — и картинки выписаны из
 * книг: до сих пор единственной связью между разделами было то, что картинку
 * при добавлении спрашивали «в какую книгу». Книга от этого ничего не теряет —
 * в её списке картинки и так не показывались, они лишь давали ей обложку.
 *
 * Записи не удаляются и не переносятся: у картинок просто обнуляется книга.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `image_albums` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL)"
        )
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `albumId` INTEGER DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_albumId` ON `notes` (`albumId`)")
        db.execSQL("UPDATE `notes` SET `topicId` = NULL WHERE `isImage` = 1")
    }
}

/**
 * 12 → 13: у книги появился выбранный руками цвет корешка.
 *
 * Одна колонка, пустая у всех существующих книг: пусто значит «цвет выводится
 * из названия» — то есть ровно то, как полка выглядела до сих пор. Поэтому
 * ALTER без пересоздания таблицы и без переноса данных.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `scroll_topics` ADD COLUMN `color` TEXT DEFAULT NULL")
    }
}

/**
 * Строка списка узнаёт про уровень: пункт или подпункт. У прежних строк его не
 * было — все становятся пунктами, и список выглядит ровно так, как выглядел.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `yet_items` ADD COLUMN `nested` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Списки, записанные до строк, раскладываются на строки.
 *
 * Пока строка добавлялась по одной и не отмечалась, целый список набирали
 * одной записью с переносами, а сделанное помечали знаком «✓» в конце строки.
 * Такая запись — список только на вид: отметить в ней нельзя ничего, и
 * сделанное не уходит вниз.
 *
 * Разбирается тем же [ListInput], которым разбирается написанное в окне
 * сегодня: правила у записанного вчера и сегодня одни. Порядок сохраняется —
 * у новых строк время создания исходной записи, а внутри одного времени они
 * идут по номеру.
 *
 * Однострочные записи не трогаются: разбирать там нечего.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {

    override fun migrate(db: SupportSQLiteDatabase) {
        val stacked = mutableListOf<Array<String>>()

        db.query("SELECT id, listId, text, createdAt FROM yet_items").use { cursor ->
            while (cursor.moveToNext()) {
                val text = cursor.getString(2) ?: continue
                if (!text.contains('\n') && !text.contains('\r')) continue
                stacked += arrayOf(
                    cursor.getLong(0).toString(),
                    cursor.getLong(1).toString(),
                    text,
                    cursor.getString(3) ?: continue,
                )
            }
        }

        stacked.forEach { (id, listId, text, createdAt) ->
            val lines = ListInput.parse(text)
            // Разобралось в одну строку — запись и была одной строкой с
            // переносом на конце: трогать её незачем.
            if (lines.size < 2) return@forEach

            lines.forEach { line ->
                db.execSQL(
                    "INSERT INTO yet_items (listId, text, done, nested, createdAt) " +
                        "VALUES (?, ?, ?, ?, ?)",
                    arrayOf(
                        listId,
                        line.text,
                        if (line.done) 1 else 0,
                        if (line.nested) 1 else 0,
                        createdAt,
                    ),
                )
            }
            db.execSQL("DELETE FROM yet_items WHERE id = ?", arrayOf(id))
        }
    }
}

/**
 * У списка появляется знак: чем отмечать в нём строки. У заведённых раньше —
 * квадрат, тот же, которым они отмечались до сих пор.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `yet_lists` ADD COLUMN `mark` TEXT NOT NULL DEFAULT 'SQUARE'")
    }
}

/**
 * 16 → 17: напоминание перестаёт быть одной строчкой «во столько-то».
 *
 * У него появляются событие со своим временем (`eventDate`, `eventStart`,
 * `eventEnd`), промежуток «за сколько напомнить» (`lead`) и знак — всё, из чего
 * складывается карточка. Прежние `date` и `time` остаются моментом будильника:
 * по нему напоминание и ставится, и переучивать `ReminderAlarms` не за чем.
 *
 * У заведённых раньше событие приравнивается к самому напоминанию: другого
 * времени про них не записано, и придумывать его задним числом нельзя. Часа
 * напоминания это не меняет — он назван прямо, промежутка нет.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `eventDate` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `eventStart` TEXT")
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `eventEnd` TEXT")
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `lead` INTEGER")
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `icon` TEXT")
        db.execSQL("UPDATE `reminders` SET `eventDate` = `date`, `eventStart` = `time`")
    }
}

/**
 * 17 → 18: у AskyaEcho появляются свои плейлисты.
 *
 * Своя таблица, а не системные плейлисты MediaStore: писать в те с Android 11
 * приложению уже нельзя. Подписи лежат рядом со ссылкой на файл — плейлист
 * должен читаться и после того, как песню удалили с телефона.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `echo_playlists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `echo_playlist_tracks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`playlistId` INTEGER NOT NULL, " +
                "`uri` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`artist` TEXT NOT NULL, " +
                "`albumId` INTEGER NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_echo_playlist_tracks_playlistId` " +
                "ON `echo_playlist_tracks` (`playlistId`)",
        )
    }
}

/**
 * 18 → 19: у AskyaEcho появляется избранное.
 *
 * Ключ — ссылка на файл: отметку ставят на песню, и вторая отметка той же
 * песни должна попадать в ту же строку, а не заводить вторую.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `echo_favorites` (" +
                "`uri` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`artist` TEXT NOT NULL, " +
                "`albumId` INTEGER NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, " +
                "`addedAt` TEXT NOT NULL, " +
                "PRIMARY KEY(`uri`))",
        )
    }
}

/**
 * 19 → 20: у напоминания появилась своя мелодия.
 *
 * Две колонки, обе пустые у существующих напоминаний: пусто значит «обычный
 * звук напоминания» — то есть ровно то, чем они звучали до сих пор. Поэтому
 * ALTER без пересоздания таблицы.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `sound` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `reminders` ADD COLUMN `soundTitle` TEXT DEFAULT NULL")
    }
}
