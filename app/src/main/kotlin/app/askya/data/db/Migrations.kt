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

/**
 * 20 → 21: раздел «Тренировки».
 *
 * Три таблицы: сама тренировка, её подходы и наблюдение за состоянием.
 * Подходы лежат отдельно, потому что их у тренировки сколько угодно, а
 * наблюдения — потому что они бывают и в дни без тренировок (и чаще всего
 * именно тогда и важны).
 *
 * У наблюдений дата уникальна: по записи на день. Уникальность задаётся здесь
 * же индексом — иначе второе взвешивание за сутки завело бы вторую строку, и
 * на вопрос «сколько я вешу сегодня» нашлось бы два ответа.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workouts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`minutes` INTEGER NOT NULL, " +
                "`meters` INTEGER NOT NULL, " +
                "`pulseAvg` INTEGER NOT NULL, " +
                "`pulseMax` INTEGER NOT NULL, " +
                "`effort` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_date` ON `workouts` (`date`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_sets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`workoutId` INTEGER NOT NULL, " +
                "`exercise` TEXT NOT NULL, " +
                "`grams` INTEGER NOT NULL, " +
                "`reps` INTEGER NOT NULL, " +
                "`seconds` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_sets_workoutId` " +
                "ON `workout_sets` (`workoutId`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_checks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`grams` INTEGER NOT NULL, " +
                "`restingPulse` INTEGER NOT NULL, " +
                "`sleepMinutes` INTEGER NOT NULL, " +
                "`feeling` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_body_checks_date` " +
                "ON `body_checks` (`date`)",
        )
    }
}

/**
 * Двадцать вторая: день движения.
 *
 * Одна таблица на всё, что меряют приборы, — шаги, метры, минуты движения,
 * калории, пульс покоя и сон. Дата уникальна: «сколько я прошёл сегодня» —
 * вопрос с одним ответом, и вторая строка на тот же день означала бы два.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `activity_days` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`steps` INTEGER NOT NULL, " +
                "`meters` INTEGER NOT NULL, " +
                "`activeMinutes` INTEGER NOT NULL, " +
                "`kcal` INTEGER NOT NULL, " +
                "`restingPulse` INTEGER NOT NULL, " +
                "`sleepMinutes` INTEGER NOT NULL, " +
                "`fromWatch` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_activity_days_date` " +
                "ON `activity_days` (`date`)",
        )
    }
}

/**
 * Двадцать третья: плейлисты AskyaV.
 *
 * Две таблицы, как у Echo: сам плейлист и строки в нём. Подписи и размеры
 * лежат в строке рядом со ссылкой — плейлист должен читаться и после того, как
 * файл с телефона убрали.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `video_playlists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `video_playlist_clips` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`playlistId` INTEGER NOT NULL, " +
                "`uri` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, " +
                "`sizeBytes` INTEGER NOT NULL, " +
                "`width` INTEGER NOT NULL, " +
                "`height` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_video_playlist_clips_playlistId` " +
                "ON `video_playlist_clips` (`playlistId`)",
        )
    }
}

/**
 * Двадцать четвёртая: сносит `check_ins` и `practice_logs`.
 *
 * Единственная миграция в проекте, которая не добавляет, а убирает, и потому
 * ей нужно объяснение. Правило «убрать функцию из приложения и стереть чужой
 * текст — разные решения» здесь не нарушено: в обеих таблицах не лежало
 * ничего. Экраны чек-ина и практик были заглушками и ни разу не записали ни
 * одной строки — сносится пустая форма, а не чей-то рассказ. Таблицы разговора
 * с моделью (`profile_sections`, `interview_messages`, `self_answers`) поэтому
 * и остаются на месте: в них человек говорил.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `check_ins`")
        db.execSQL("DROP TABLE IF EXISTS `practice_logs`")
    }
}

/**
 * Двадцать пятая: привязка — чем делается дело.
 *
 * По колонке у дела дня и у строки списка дел. Пара «вид + адрес» одной
 * строкой (`book:12`), а не номер на каждый вид: видов будет прибавляться, и
 * следующий из них внешний — «Мост» в другое приложение телефона.
 *
 * ALTER'ом, а не пересозданием таблицы: колонка необязательная, и Room сверяет
 * у неё ровно тип, обязательность и ключ.
 */
val MIGRATION_24_25 = object : Migration(24, 25) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `schedule_items` ADD COLUMN `link` TEXT")
        db.execSQL("ALTER TABLE `routine_items` ADD COLUMN `link` TEXT")
    }
}

/**
 * Двадцать шестая: мосты — связи Askya с приложениями телефона.
 *
 * Одна таблица: название, куда мост ведёт, чем он оканчивается (приложение или
 * ссылка), к какому знаку дела подключён и когда им пользовались в последний
 * раз. Имён чужих приложений в схеме нет и быть не может: что подключено к
 * мосту, решает человек, а не Askya.
 *
 * `icon` без уникального ключа намеренно: одному знаку — один мост, но следит
 * за этим сам экран мостов, а не база. Уникальный ключ на необязательной
 * колонке в SQLite считает NULL разными значениями, и правило «один знак — один
 * мост» он всё равно бы не выразил без ухищрений, зато сломал бы вставку
 * второго моста без знака.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bridges` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`target` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`icon` TEXT, " +
                "`usedAt` INTEGER NOT NULL)",
        )
    }
}

/**
 * Двадцать седьмая: справочник упражнений — то, что человек написал сам.
 *
 * Готовые описания в базу не кладутся: они живут в коде
 * (`domain/model/Exercises.kt`), и обновление приложения должно приносить новые,
 * не затирая написанное человеком. Здесь — только его строки: переписанные шаги,
 * своё «на что смотреть», своё видео.
 *
 * Уникальный ключ по названию, потому что название и есть связь с дневником:
 * `workout_sets.exercise` — обычная строка, и два описания одного упражнения
 * означали бы, что историю подходов не к чему привязать.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercises` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`area` TEXT NOT NULL, " +
                "`sort` TEXT NOT NULL, " +
                "`steps` TEXT NOT NULL, " +
                "`watch` TEXT NOT NULL, " +
                "`videoUri` TEXT)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)",
        )
    }
}

/**
 * Двадцать восьмая: корзина на сутки вместо подтверждений.
 *
 * По колонке `removedAt` у дела дня, записи и строки Yet. Убранное не исчезает,
 * а помечается и живёт двадцать четыре часа; снизу на несколько секунд
 * появляется «Вернуть», и вопрос «вы уверены?» снимается вовсе.
 *
 * Так честнее: подтверждение не отменяет ошибку, оно перекладывает её на
 * человека, который торопится, — и через месяц жмётся не читая. Возврат
 * отменяет ошибку по-настоящему.
 *
 * ALTER'ом: колонка необязательная, и Room сверяет у неё тип, обязательность и
 * ключ. Прошлые записи остаются с NULL, то есть «на месте», — как и должно.
 */
val MIGRATION_27_28 = object : Migration(27, 28) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `schedule_items` ADD COLUMN `removedAt` TEXT")
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `removedAt` TEXT")
        db.execSQL("ALTER TABLE `yet_items` ADD COLUMN `removedAt` TEXT")
    }
}

/**
 * Двадцать девятая: Ledger — расходная книга.
 *
 * Три таблицы: счета, статьи и сами записи. Начальных строк миграция не
 * кладёт: статьи и первый счёт заводятся при первом входе в раздел
 * (`LedgerRepository.ensureStarted`), а не при обновлении приложения. Тот, кто
 * в раздел не зайдёт, не должен получить в базе «Еду» и «Кошелёк», которых не
 * заводил.
 *
 * Суммы — целыми копейками (`INTEGER`), а не дробью: см. `domain/model/Money`.
 * `REAL` в книге, где складывают сотни строк, к концу месяца расходится сам с
 * собой, и найти эту копейку потом невозможно.
 *
 * Внешних ключей нет — как и везде в Askya: связность держит репозиторий.
 * Закрытый счёт остаётся строкой, убранная статья отвязывается от записей
 * явным `UPDATE`, а каскад из базы снёс бы вместе со статьёй и прошлогодние
 * траты по ней.
 */
val MIGRATION_28_29 = object : Migration(28, 29) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_accounts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`opening` INTEGER NOT NULL, " +
                "`closed` INTEGER NOT NULL, " +
                "`createdAt` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`limit` INTEGER NOT NULL, " +
                "`createdAt` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`toAccountId` INTEGER, " +
                "`categoryId` INTEGER, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL, " +
                "`removedAt` TEXT)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_date` " +
                "ON `ledger_entries` (`date`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_accountId` " +
                "ON `ledger_entries` (`accountId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_categoryId` " +
                "ON `ledger_entries` (`categoryId`)",
        )
    }
}

/**
 * Тридцатая: путь пробежки.
 *
 * Одна таблица — точки маршрута. Тренировкам она ничего не меняет: пробежка
 * остаётся обычной записью в `workouts`, с километрами и минутами, — просто у
 * записанной телефоном есть ещё и путь, а у вписанной руками нет. Поэтому и
 * отдельной таблицей, а не колонкой: колонка «путь» стояла бы пустой у всего
 * дневника, а список тренировок читал бы её при каждом открытии раздела.
 *
 * Градусы целыми миллионными долями (`INTEGER`), как метры метрами и деньги
 * копейками, — см. `data/entity/RunPoint`.
 *
 * Внешнего ключа нет, как и у подходов: путь удалялся вместе с тренировкой
 * явно, кодом раздела Active — того самого, который убран миграцией 31 → 32.
 */
val MIGRATION_29_30 = object : Migration(29, 30) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `run_points` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`workoutId` INTEGER NOT NULL, " +
                "`latE6` INTEGER NOT NULL, " +
                "`lonE6` INTEGER NOT NULL, " +
                "`seconds` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_run_points_workoutId` " +
                "ON `run_points` (`workoutId`)",
        )
    }
}

/**
 * Тридцать первая: кредитный лимит у счёта и длина голосовой заметки.
 *
 * Две колонки в две разные таблицы, и одной миграцией они идут не потому, что
 * связаны, а потому, что вышли одним обновлением: миграция — это шаг версии, а
 * не смысловая единица.
 *
 * **Лимит** — потолок, до которого банк даёт занимать. Нужен он одному виду
 * счёта (`AccountKind.CREDIT`), и у всех прочих стоит нулём, но колонкой, а не
 * отдельной таблицей: это одно число, и живёт оно ровно столько же, сколько
 * сам счёт. Таблица «кредиты» из одной строки на счёт была бы join-ом ради
 * `Long`. Долга здесь нет намеренно: он не хранится, а складывается из
 * записей — как и всякий остаток в этой книге (см. `LedgerAccount`).
 * Записанным он был бы вторым источником правды, и первая же правка задним
 * числом развела бы их.
 *
 * **Длина** — сколько звучит голосовая заметка. Тоже колонкой и по тому же
 * правилу, что `isImage`: узнать её можно, только открыв файл, а стоит она в
 * каждой строке списка.
 *
 * `limit` — слово SQL, поэтому имя в кавычках; так же оно стоит и у статьи,
 * где эта колонка появилась раньше.
 */
val MIGRATION_30_31 = object : Migration(30, 31) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `ledger_accounts` ADD COLUMN `limit` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE `notes` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/**
 * Тридцать вторая: раздел Active убран, а записанное им — оставлено.
 *
 * Шаг версии без единого `DROP`, и это не забывчивость. Экранов Active больше
 * нет, и Room про таблицы `workouts`, `workout_sets`, `run_points`,
 * `body_checks`, `activity_days` и `exercises` теперь не знает — но в них
 * лежат тренировки, замеры и пути пробежек, которые человек записал сам.
 * Правило то же, по которому остались `profile_sections` (см. `AppDatabase`):
 * убрать функцию и стереть чужие записи — разные решения, и второе за человека
 * не принимают. Лишние таблицы Room не сверяет, места они занимают столько же,
 * сколько занимали, а «Слепок» их больше не открывает.
 *
 * Если однажды понадобится стереть их совсем — это будет отдельная миграция и
 * отдельное решение, а не побочный итог уборки экранов.
 */
val MIGRATION_31_32 = object : Migration(31, 32) {

    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

/**
 * Тридцать третья: таблицы Active стёрты совсем.
 *
 * Продолжение тридцать второй и её же вторая половина. Та убрала раздел и
 * оставила записанное им лежать в файле базы — по правилу «убрать функцию и
 * стереть чужие записи — разные решения». Правило не нарушено и здесь: второе
 * решение принял сам человек, отдельной просьбой и после того, как первое было
 * сделано. Две миграции, а не одна, потому что и решений было два, и между ними
 * стояла установленная сборка.
 *
 * Уходят все шесть: `workouts` и `workout_sets` — тренировки с подходами,
 * `run_points` — пути пробежек, `body_checks` — наблюдения за собой,
 * `activity_days` — дни шагомера, `exercises` — свои упражнения. Порядок
 * `DROP`-ов ничего не значит: внешних ключей между ними не было ни одного,
 * подходы и точки удалялись кодом раздела, а раздела больше нет.
 *
 * Своих видео к упражнениям это не касается — они лежат файлами в папке Askya,
 * а не в базе. Ссылки на них уходят вместе с `exercises`; сами файлы человек
 * убирает сам, как всякое своё в общей папке телефона.
 */
val MIGRATION_32_33 = object : Migration(32, 33) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `workout_sets`")
        db.execSQL("DROP TABLE IF EXISTS `run_points`")
        db.execSQL("DROP TABLE IF EXISTS `workouts`")
        db.execSQL("DROP TABLE IF EXISTS `body_checks`")
        db.execSQL("DROP TABLE IF EXISTS `activity_days`")
        db.execSQL("DROP TABLE IF EXISTS `exercises`")
    }
}

/**
 * Тридцать четвёртая: у счетов и статей появилась краска, у долга — ставка.
 *
 * Краска (`color`) — та же восьмицветная палитра, которой покрашены корешки
 * книг в Scroll, и колонка её хранит так же: именем константы, пусто значит
 * «не выбирали». Пустой она и остаётся у всех заведённых прежде счетов —
 * покрасить их за человека нельзя, но и серыми они не выглядят: цвет, которого
 * не выбирали, выводится из названия и потому есть у каждого счёта с первого
 * же взгляда.
 *
 * Ставка (`rate`) — сотые доли процента годовых, ноль значит «процентов нет».
 * Ноль по умолчанию и есть верный ответ для всех старых записей: книга до сих
 * пор про проценты не спрашивала, и придумывать их задним числом она не
 * станет. План погашения при нулевой ставке считает платёж и не считает
 * переплату — и честно об этом говорит.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ledger_accounts ADD COLUMN rate INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ledger_accounts ADD COLUMN color TEXT")
        db.execSQL("ALTER TABLE ledger_categories ADD COLUMN color TEXT")
    }
}

/**
 * Тридцать пятая: у дела появился свой список.
 *
 * Между заметкой и списком Yet не было ничего. Заметка у дела одна и сплошная:
 * в неё пишут «взять пропуск, позвонить в банк», и отметить в ней сделанное
 * нельзя — только переписать строку. Список Yet отмечается, но живёт своей
 * жизнью и никакому дню не принадлежит. Дело с четырьмя задачами на сегодня
 * не было ни тем, ни другим.
 *
 * Таблица новая, а не колонка в `schedule_items`: строк у дела сколько угодно,
 * и каждая отмечается порознь. Склеенные в одну колонку, они переписывались бы
 * целиком на каждую галочку — и вместе с ними переписывалось бы само дело, за
 * которым следит и виджет, и шторка.
 *
 * Внешнего ключа нет — как у строк Scroll и Yet: удаление дела убирает строки
 * явно, в репозитории. Каскад молча сработал бы и на мягком удалении, а
 * убранное дело живёт сутки и возвращается со списком.
 *
 * Ничего не переносится: до этой версии таких списков не существовало, и
 * догадываться, что в заметке было списком, а что предложением, — не дело
 * миграции.
 */
val MIGRATION_34_35 = object : Migration(34, 35) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deed_tasks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`deedId` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`done` INTEGER NOT NULL, " +
                "`createdAt` TEXT NOT NULL, " +
                "`removedAt` TEXT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_deed_tasks_deedId` ON `deed_tasks` (`deedId`)")
    }
}

/**
 * Тридцать шестая: у счёта появилось место в сетке.
 *
 * До сих пор счета шли по дате появления — тем порядком, в каком их однажды
 * завели. Порядок этот ничего не значит: смотрят на сетку счетов каждый день, и
 * первым человек хочет видеть тот счёт, которым платит, а не тот, что завёл
 * раньше всех. Сортировать по остатку нельзя тем более — карточки менялись бы
 * местами после каждой покупки хлеба.
 *
 * Колонкой у счёта, а не отдельной таблицей порядка: это одно число, и живёт
 * оно ровно столько же, сколько сам счёт.
 *
 * Номера расставляются по тому порядку, который человек видел вчера, — по дате
 * появления. Ноль у всех был бы честным «порядка ещё нет», но в первый же день
 * после обновления сетка перетасовалась бы сама собой, и виноватым оказалось бы
 * обновление.
 */
val MIGRATION_35_36 = object : Migration(35, 36) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ledger_accounts ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE ledger_accounts SET position = (" +
                "SELECT COUNT(*) FROM ledger_accounts AS earlier " +
                "WHERE earlier.createdAt < ledger_accounts.createdAt " +
                "OR (earlier.createdAt = ledger_accounts.createdAt " +
                "AND earlier.id < ledger_accounts.id))",
        )
    }
}

/**
 * Тридцать седьмая: у дела списка появились дни недели.
 *
 * Список дел знал одно повторение — «каждый день», — и всё, что в нём
 * записано, разворачивалось в любую дату. Обычный день при этом по дням
 * недели разный: мусор выносят по вторникам, в зал ходят через день, и
 * ежедневное дело приходилось каждое утро вычёркивать из дня руками. Список,
 * который каждый день врёт, перестают вести.
 *
 * Колонкой у дела, а не таблицей «повторений»: это одна строка вида «1,3,5»
 * (см. [app.askya.domain.model.DeedDays]), и живёт она ровно столько же,
 * сколько само дело.
 *
 * Пусто у всех, и это не заготовка, а значение: пустая колонка означает
 * «каждый день» — ровно то, чем дела были до сих пор. Ни одна строка не
 * переносится и ни один уже собранный день не меняется.
 */
val MIGRATION_36_37 = object : Migration(36, 37) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE routine_items ADD COLUMN days TEXT")
    }
}

/**
 * Тридцать восьмая: у счёта появилась валюта.
 *
 * Книга считала в одних рублях и знак валюты не спрашивала вовсе. Живут же и
 * с долларовым счётом рядом с рублёвым — не ради биржи, а потому что часть
 * денег просто лежит в другой валюте, и записывать их рублями «примерно по
 * курсу» значит вести книгу, которая не сходится ни с одной из двух.
 *
 * Колонкой у счёта, а не у записи: доллары лежат на долларовом счету, и всё,
 * что по нему прошло, — доллары. Курсов книга не знает и складывать разные
 * валюты не берётся — см. [app.askya.domain.model.Currency].
 *
 * Рубль у всех, и это не заготовка, а значение: колонка, дописанная старым
 * счетам, означает ровно то, чем они были. Ни одна запись не трогается, ни
 * один остаток не меняется.
 */
val MIGRATION_37_38 = object : Migration(37, 38) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE ledger_accounts ADD COLUMN currency TEXT NOT NULL DEFAULT 'RUB'",
        )
    }
}

/**
 * Тридцать девятая: у списка Yet появилось «когда трогали».
 *
 * Ради «Недавнего» в боковом меню: туда попадали одни записи Scroll, а список,
 * заведённый на ходу, приходилось искать через раздел и подраздел — притом
 * что открывают его обычно через минуту после того, как завели.
 *
 * Старым спискам ставится их же дата появления: другого следа о том, когда их
 * трогали, в базе нет, а придумывать «сегодня» всем разом значило бы поднять
 * в «Недавнее» списки пятилетней давности.
 */
val MIGRATION_38_39 = object : Migration(38, 39) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE yet_lists ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE yet_lists SET updatedAt = createdAt")
    }
}

/**
 * Сороковая: строка списка внутри дела бывает заголовком.
 *
 * Список дела оказался не одним списком: у поездки на объект в нём и что там
 * сделать, и что туда взять, и что купить по дороге. Сваленные подряд, они
 * читались кашей. Заголовок разделяет их, оставаясь такой же строкой на том же
 * месте, — своей таблицы «разделов» ради этого не завели.
 *
 * Ноль у всех: ни одна строка не становится заголовком задним числом.
 */
val MIGRATION_39_40 = object : Migration(39, 40) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE deed_tasks ADD COLUMN heading INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Сорок первая: нити — шестой раздел.
 *
 * Нить это личное начинание, которое тянется неделями: ремонт, язык, книга.
 * Своего содержимого у неё почти нет — она тянется через разделы, и поэтому
 * миграция заводит одну маленькую таблицу и дописывает по одной колонке тем
 * таблицам, через которые нить проходит.
 *
 * У дела своей колонки не появилось: привязка дела хранится строкой
 * «вид:адрес» ([app.askya.domain.model.DeedLink]), и новый вид `thread`
 * укладывается в неё без единого изменения схемы.
 *
 * Все колонки пустые, и это значение, а не заготовка: у списка, траты и
 * записи, заведённых до нитей, нити и не было.
 */
val MIGRATION_40_41 = object : Migration(40, 41) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `threads` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`ending` TEXT NOT NULL, " +
                "`color` TEXT, " +
                "`state` TEXT NOT NULL, " +
                "`due` TEXT, " +
                "`budget` INTEGER NOT NULL, " +
                "`createdAt` TEXT NOT NULL, " +
                "`closedAt` TEXT)",
        )

        db.execSQL("ALTER TABLE yet_lists ADD COLUMN threadId INTEGER")

        db.execSQL("ALTER TABLE ledger_entries ADD COLUMN threadId INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_threadId` " +
                "ON `ledger_entries` (`threadId`)",
        )

        db.execSQL("ALTER TABLE notes ADD COLUMN threadId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_threadId` ON `notes` (`threadId`)")
    }
}
/**
 * Сорок вторая: у нити появилась карта замысла.
 *
 * До неё нить была вопросом «как оно идёт», заданным поперёк разделов, и
 * своего содержимого не имела вовсе. Оказалось, что замысел живёт **до** дел:
 * сперва искра, вопросы, возможные пути и то, что мешает, — и только потом
 * часть этого превращается в дела дня. Класть такое было решительно некуда, и
 * миграция заводит две таблицы: узлы и связи между ними.
 *
 * ## Состояния переписываются, а не добавляются
 *
 * Прежних было четыре: «идёт», «отложена», «закончена», «брошена». Стало семь,
 * и три из них разбирают прежнее «идёт» на то, что с замыслом происходит:
 * горит, растёт, плетётся. Идущие переводятся в «растёт» — самое скромное из
 * трёх: сказать за человека, что у него горит, приложение не вправе.
 * Отложенные становятся «спит»: их отложили нарочно, и это ровно оно.
 * «Закончена» и «брошена» остались собой и переписывания не требуют.
 *
 * Читатель перечисления помнит старые имена и без этой миграции
 * ([app.askya.domain.model.ThreadState.of]): снимок, снятый прежней версией,
 * приходит со строкой `LIVE` и обязан открыться.
 */
val MIGRATION_41_42 = object : Migration(41, 42) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `thread_nodes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`threadId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`x` REAL NOT NULL, " +
                "`y` REAL NOT NULL, " +
                "`link` TEXT, " +
                "`deedId` INTEGER, " +
                "`doneAt` TEXT, " +
                "`createdAt` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_thread_nodes_threadId` " +
                "ON `thread_nodes` (`threadId`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `thread_edges` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`threadId` INTEGER NOT NULL, " +
                "`fromId` INTEGER NOT NULL, " +
                "`toId` INTEGER NOT NULL, " +
                "`createdAt` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_thread_edges_threadId` " +
                "ON `thread_edges` (`threadId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_thread_edges_fromId` " +
                "ON `thread_edges` (`fromId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_thread_edges_toId` " +
                "ON `thread_edges` (`toId`)",
        )

        db.execSQL("UPDATE threads SET state = 'GROWING' WHERE state = 'LIVE'")
        db.execSQL("UPDATE threads SET state = 'SLEEPING' WHERE state = 'PAUSED'")
    }
}

/**
 * 42 → 43. Threads пересобраны от искры.
 *
 * ## Почему таблицы не правятся, а сносятся
 *
 * У нити больше нет ни названия, ни цвета, ни срока, ни сметы: её зовут
 * словами искры, с которой она началась, и вся анкета, которую прежде
 * заполняли до первой мысли, ушла целиком. Колонка `sparkId` появилась вместо
 * них, и заполнить её у старых нитей нечем: у прежних искр не было ни
 * обязанности быть первыми, ни обязанности быть вовсе.
 *
 * Перекладывать такое переписыванием колонок значит писать миграцию, которая
 * угадывает, что у человека было в голове. Раздел прожил две версии и
 * пересобран целиком по прямой просьбе — вместе с ним уходит и то, что в нём
 * лежало.
 *
 * ## Чужое не трогается, а отвязывается
 *
 * Дела, траты, записи и списки, привязанные к прежним нитям, остаются на своих
 * местах: они принадлежат дню, книге и полке. Снимается только привязка —
 * иначе она указывала бы на номер, которого больше нет, и «дело нити» тянуло
 * бы в пустоту.
 */
val MIGRATION_42_43 = object : Migration(42, 43) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE schedule_items SET link = NULL WHERE link LIKE 'thread:%'")
        db.execSQL("UPDATE routine_items SET link = NULL WHERE link LIKE 'thread:%'")
        db.execSQL("UPDATE yet_lists SET threadId = NULL")
        db.execSQL("UPDATE ledger_entries SET threadId = NULL")
        db.execSQL("UPDATE notes SET threadId = NULL")

        db.execSQL("DROP TABLE IF EXISTS thread_edges")
        db.execSQL("DROP TABLE IF EXISTS thread_nodes")
        db.execSQL("DROP TABLE IF EXISTS threads")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `threads` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sparkId` INTEGER NOT NULL, " +
                "`state` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL, " +
                "`closedAt` TEXT)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `thread_nodes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`threadId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`x` REAL NOT NULL, " +
                "`y` REAL NOT NULL, " +
                "`link` TEXT, " +
                "`deedId` INTEGER, " +
                "`doneAt` TEXT, " +
                "`createdAt` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_thread_nodes_threadId` " +
                "ON `thread_nodes` (`threadId`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `thread_ties` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`threadId` INTEGER NOT NULL, " +
                "`fromId` INTEGER NOT NULL, " +
                "`toId` INTEGER NOT NULL, " +
                "`createdAt` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_thread_ties_threadId` " +
                "ON `thread_ties` (`threadId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_thread_ties_fromId` " +
                "ON `thread_ties` (`fromId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_thread_ties_toId` " +
                "ON `thread_ties` (`toId`)",
        )
    }
}

/**
 * 43 → 44. У узла нити появляются срок, отставка и память о строке в списке.
 *
 * Четыре колонки, и ни одна не трогает того, что уже записано: пустое значение
 * у старых узлов — правда, а не пробел. Узел, заведённый вчера, не отставлен,
 * срока у него нет, и в списке он не стоит — ровно это и говорят `NULL`.
 *
 * `asideAt` — не «удалён» и не «сделан». Путь, от которого человек отказался,
 * остаётся на полотне зачёркнутым: он часть того, как замысел стал нынешним.
 * Хранится временем, а не флажком, по той же причине, что и `doneAt`: «когда
 * отставили» — это сведения, а `1` в колонке — нет.
 *
 * `lineId` доводит до конца то, что раньше делалось наполовину: шаг уходил
 * строкой в список Yet и терялся из виду. Старым узлам его не восстановить —
 * строки уходили без обратного адреса, — и придумывать связь по совпадению
 * слов не станем: угадавшая неверно связь хуже отсутствующей.
 */
val MIGRATION_43_44 = object : Migration(43, 44) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE thread_nodes ADD COLUMN lineId INTEGER")
        db.execSQL("ALTER TABLE thread_nodes ADD COLUMN due TEXT")
        db.execSQL("ALTER TABLE thread_nodes ADD COLUMN asideAt TEXT")
    }
}

/**
 * 44 → 45. Раздела Threads больше нет — вместе с тем, что в нём лежало.
 *
 * ## Оба решения приняты разом
 *
 * С Active было в два шага: сперва убрали раздел, а записанное оставили лежать,
 * и стереть его человек попросил отдельной просьбой полгода спустя. Здесь он
 * попросил сразу и то и другое — «удали полностью раздел и все записи по нему»,
 * — и откладывать половину значило бы решать за него, что он передумает.
 *
 * Уходят `threads`, `thread_nodes`, `thread_ties` — искры, полотна замыслов,
 * связи между узлами. Вернуть будет нечем.
 *
 * ## Чужое здесь тоже уходит, и это не общее правило
 *
 * Прежде при удалении нити чужое **отвязывалось**: дело принадлежит дню, список
 * — полке, трата — книге, и нить лишь тянулась через них. В этот раз человек
 * попросил иначе, и просьба звучала именно так: «и все записи по нему». Поэтому
 * стираются
 *
 * - дела расписания с привязкой `thread:…` — вместе с их подсписками
 *   (`deed_tasks`) и напоминаниями (`reminders`): напоминание, пережившее своё
 *   дело, звонило бы в пустоту;
 * - повторы (`routine_items`) с той же привязкой;
 * - списки Yet, тянувшие нить, — целиком, со всеми строками;
 * - траты, записанные по нити.
 *
 * Уже поставленные будильники Android отменить отсюда нечем, но и беды в этом
 * нет: получатель ищет напоминание по номеру и молча уходит, не найдя строки.
 *
 * **Записи Scroll остаются.** Это единственное исключение, и оно не от
 * непоследовательности: заметка — то, что человек написал руками, а не то, чем
 * приложение обвесило нить. Стирают такое, только когда просят стереть именно
 * его. У записей снимается привязка — вместе с колонкой.
 *
 * ## Колонки убираются перестройкой
 *
 * `threadId` был у списков, трат и записей. `ALTER TABLE … DROP COLUMN` в
 * SQLite появился поздно, а приложение живёт с Android 8, — поэтому три таблицы
 * пересобираются заново и данные переливаются. Индексы восстанавливаются
 * следом: без них Room при первом же запуске скажет, что схема не та.
 */
val MIGRATION_44_45 = object : Migration(44, 45) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // ---- Дела нити: подсписки, напоминания, сами дела ----
        db.execSQL(
            "DELETE FROM reminders WHERE itemId IN " +
                "(SELECT id FROM schedule_items WHERE link LIKE 'thread:%')",
        )
        db.execSQL(
            "DELETE FROM deed_tasks WHERE deedId IN " +
                "(SELECT id FROM schedule_items WHERE link LIKE 'thread:%')",
        )
        db.execSQL("DELETE FROM schedule_items WHERE link LIKE 'thread:%'")
        db.execSQL("DELETE FROM routine_items WHERE link LIKE 'thread:%'")

        // ---- Списки Yet, тянувшие нить, — целиком ----
        db.execSQL(
            "DELETE FROM yet_items WHERE listId IN " +
                "(SELECT id FROM yet_lists WHERE threadId IS NOT NULL)",
        )
        db.execSQL("DELETE FROM yet_lists WHERE threadId IS NOT NULL")

        // ---- Траты по нити ----
        db.execSQL("DELETE FROM ledger_entries WHERE threadId IS NOT NULL")

        // ---- Колонка threadId у списков ----
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `yet_lists_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`mark` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL, " +
                "`updatedAt` TEXT NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO yet_lists_new (id, title, mark, createdAt, updatedAt) " +
                "SELECT id, title, mark, createdAt, updatedAt FROM yet_lists",
        )
        db.execSQL("DROP TABLE yet_lists")
        db.execSQL("ALTER TABLE yet_lists_new RENAME TO yet_lists")

        // ---- Колонка threadId у трат ----
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_entries_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`toAccountId` INTEGER, " +
                "`categoryId` INTEGER, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` TEXT NOT NULL, " +
                "`removedAt` TEXT)",
        )
        db.execSQL(
            "INSERT INTO ledger_entries_new (id, date, kind, amount, accountId, toAccountId, " +
                "categoryId, note, createdAt, removedAt) " +
                "SELECT id, date, kind, amount, accountId, toAccountId, categoryId, note, " +
                "createdAt, removedAt FROM ledger_entries",
        )
        db.execSQL("DROP TABLE ledger_entries")
        db.execSQL("ALTER TABLE ledger_entries_new RENAME TO ledger_entries")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_date` ON `ledger_entries` (`date`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_accountId` " +
                "ON `ledger_entries` (`accountId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ledger_entries_categoryId` " +
                "ON `ledger_entries` (`categoryId`)",
        )

        // ---- Колонка threadId у записей. Сами записи остаются ----
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`tags` TEXT NOT NULL, " +
                "`topicId` INTEGER, " +
                "`albumId` INTEGER, " +
                "`uri` TEXT, " +
                "`mime` TEXT NOT NULL, " +
                "`isImage` INTEGER NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, " +
                "`createdAt` TEXT NOT NULL, " +
                "`updatedAt` TEXT NOT NULL, " +
                "`removedAt` TEXT)",
        )
        db.execSQL(
            "INSERT INTO notes_new (id, title, body, tags, topicId, albumId, uri, mime, " +
                "isImage, durationMs, createdAt, updatedAt, removedAt) " +
                "SELECT id, title, body, tags, topicId, albumId, uri, mime, isImage, " +
                "durationMs, createdAt, updatedAt, removedAt FROM notes",
        )
        db.execSQL("DROP TABLE notes")
        db.execSQL("ALTER TABLE notes_new RENAME TO notes")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_topicId` ON `notes` (`topicId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_albumId` ON `notes` (`albumId`)")

        // ---- И сами нити ----
        db.execSQL("DROP TABLE IF EXISTS thread_ties")
        db.execSQL("DROP TABLE IF EXISTS thread_nodes")
        db.execSQL("DROP TABLE IF EXISTS threads")
    }
}
