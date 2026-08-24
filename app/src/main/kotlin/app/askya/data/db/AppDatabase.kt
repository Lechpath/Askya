package app.askya.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.askya.data.db.converter.Converters
import app.askya.data.db.dao.AlbumDao
import app.askya.data.db.dao.CheckInDao
import app.askya.data.db.dao.EchoDao
import app.askya.data.db.dao.NoteDao
import app.askya.data.db.dao.PracticeLogDao
import app.askya.data.db.dao.ReminderDao
import app.askya.data.db.dao.RoutineDao
import app.askya.data.db.dao.ScheduleDao
import app.askya.data.db.dao.TopicDao
import app.askya.data.db.dao.YetDao
import app.askya.data.entity.CheckIn
import app.askya.data.entity.EchoFavorite
import app.askya.data.entity.EchoPlaylist
import app.askya.data.entity.EchoPlaylistTrack
import app.askya.data.entity.GeneratedDay
import app.askya.data.entity.ImageAlbum
import app.askya.data.entity.Note
import app.askya.data.entity.InterviewMessage
import app.askya.data.entity.PracticeLog
import app.askya.data.entity.ProfileSection
import app.askya.data.entity.Reminder
import app.askya.data.entity.RoutineItem
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.ScrollTopic
import app.askya.data.entity.SelfAnswer
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList

/**
 * Таблицы `profile_sections`, `interview_messages` и `self_answers` остались,
 * хотя разговор с моделью из приложения убран: в них лежит то, что человек
 * когда-то рассказал, а удаление таблицы необратимо. Они просто не читаются.
 */
@Database(
    entities = [
        Note::class,
        ScheduleItem::class,
        CheckIn::class,
        PracticeLog::class,
        Reminder::class,
        RoutineItem::class,
        GeneratedDay::class,
        ProfileSection::class,
        InterviewMessage::class,
        SelfAnswer::class,
        ScrollTopic::class,
        ImageAlbum::class,
        YetList::class,
        YetItem::class,
        EchoPlaylist::class,
        EchoPlaylistTrack::class,
        EchoFavorite::class,
    ],
    version = 20,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun checkInDao(): CheckInDao
    abstract fun practiceLogDao(): PracticeLogDao
    abstract fun reminderDao(): ReminderDao
    abstract fun routineDao(): RoutineDao
    abstract fun topicDao(): TopicDao
    abstract fun albumDao(): AlbumDao
    abstract fun yetDao(): YetDao
    abstract fun echoDao(): EchoDao

    companion object {
        private const val NAME = "askya.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                .build()
    }
}
