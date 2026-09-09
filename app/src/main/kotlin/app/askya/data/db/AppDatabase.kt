package app.askya.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.askya.data.db.converter.Converters
import app.askya.data.db.dao.AlbumDao
import app.askya.data.db.dao.BridgeDao
import app.askya.data.db.dao.DeedTaskDao
import app.askya.data.db.dao.EchoDao
import app.askya.data.db.dao.LedgerDao
import app.askya.data.db.dao.NoteDao
import app.askya.data.db.dao.ReminderDao
import app.askya.data.db.dao.RoutineDao
import app.askya.data.db.dao.ScheduleDao
import app.askya.data.db.dao.TopicDao
import app.askya.data.db.dao.VideoDao
import app.askya.data.db.dao.YetDao
import app.askya.data.entity.Bridge
import app.askya.data.entity.DeedTask
import app.askya.data.entity.EchoFavorite
import app.askya.data.entity.EchoPlaylist
import app.askya.data.entity.EchoPlaylistTrack
import app.askya.data.entity.GeneratedDay
import app.askya.data.entity.ImageAlbum
import app.askya.data.entity.LedgerAccount
import app.askya.data.entity.LedgerCategory
import app.askya.data.entity.LedgerEntry
import app.askya.data.entity.Note
import app.askya.data.entity.Reminder
import app.askya.data.entity.RoutineItem
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.ScrollTopic
import app.askya.data.entity.VideoPlaylist
import app.askya.data.entity.VideoPlaylistClip
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList

/**
 * Таблиц разговора с моделью — `profile_sections`, `interview_messages`,
 * `self_answers` — здесь нет и в базе тоже нет. Разговор и опросник убрали из
 * приложения давно, а записанное ими оставили лежать: в нём человек говорил о
 * себе, и стереть такое за него нельзя. Стереть совсем он попросил отдельно, и
 * это отдельная миграция 45 → 46.
 *
 * Таблиц раздела Active — `workouts`, `workout_sets`, `run_points`,
 * `body_checks`, `activity_days`, `exercises` — здесь нет и в базе тоже нет.
 * Сперва убрали раздел, а записанное им оставили лежать (миграция 31 → 32);
 * стереть его совсем человек попросил отдельно, и это отдельная миграция
 * 32 → 33. Так и должно быть: за него такое не решают, но когда он решил сам —
 * не откладывают.
 *
 * `check_ins` и `practice_logs` по тому же правилу не остались, и разница не в
 * настроении, а в содержимом: там не лежало ничего. Экраны чек-ина и практик
 * были заглушками, ни одна строка ни разу не записалась, и сносить было нечего
 * (миграция 23 → 24). Убрать функцию и стереть рассказанное человеком — разные
 * решения; второе за него не принимают, а первое не откладывают.
 *
 * Таблиц Threads — `threads`, `thread_nodes`, `thread_ties` — здесь нет и в
 * базе тоже нет, и в этот раз оба решения человек принял разом: убрать раздел и
 * стереть записанное им (миграция 44 → 45). Вместе с нитями по его же просьбе
 * ушло и то, что к ним тянулось: дела расписания с привязкой `thread:…`,
 * списки Yet целиком и траты книги. Записи Scroll остались — у них снялась
 * только привязка: написанное человеком стирают, лишь когда он просит стереть
 * именно его.
 */
@Database(
    entities = [
        Note::class,
        ScheduleItem::class,
        DeedTask::class,
        Bridge::class,
        Reminder::class,
        RoutineItem::class,
        GeneratedDay::class,
        ScrollTopic::class,
        ImageAlbum::class,
        YetList::class,
        YetItem::class,
        EchoPlaylist::class,
        EchoPlaylistTrack::class,
        EchoFavorite::class,
        VideoPlaylist::class,
        VideoPlaylistClip::class,
        LedgerAccount::class,
        LedgerCategory::class,
        LedgerEntry::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun deedTaskDao(): DeedTaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun routineDao(): RoutineDao
    abstract fun topicDao(): TopicDao
    abstract fun albumDao(): AlbumDao
    abstract fun yetDao(): YetDao
    abstract fun echoDao(): EchoDao
    abstract fun videoDao(): VideoDao
    abstract fun bridgeDao(): BridgeDao
    abstract fun ledgerDao(): LedgerDao

    companion object {
        private const val NAME = "askya.db"

        /**
         * Версия схемы. Названа константой, а не числом в аннотации, потому
         * что её спрашивает не только Room: «Слепок» кладёт её внутрь файла и
         * сверяет при чтении, а два числа об одном разошлись бы в первый же
         * раз, когда правят одно из них.
         */
        const val VERSION = 46

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43, MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46)
                .build()
    }
}
