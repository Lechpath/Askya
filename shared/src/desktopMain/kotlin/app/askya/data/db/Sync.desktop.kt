package app.askya.data.db

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import app.askya.data.sync.SyncSchema

/**
 * То же, что у телефона (`Migrations.kt`), но на языке, которым Windows-версия
 * говорит с SQLite: у неё свой драйвер, и миграции пишутся на `SQLiteConnection`,
 * а не на `SupportSQLiteDatabase`.
 *
 * Сам список команд один и тот же — `SyncSchema`. Расходиться двум системам
 * здесь нельзя: база у них одна и та же, её переносит Слепок, а скоро понесёт
 * и облако.
 *
 * Миграций у компьютера всего одна, и это не небрежность: Windows-версия
 * появилась на 46-й версии схемы, базы старше у неё не бывает.
 */
val MIGRATION_46_47_DESKTOP = object : Migration(46, 47) {

    override fun migrate(connection: SQLiteConnection) {
        SyncSchema.migrate46to47().forEach(connection::execSQL)
    }
}

/** Триггеры журнала на новой базе — Room о них не знает. */
val SYNC_CALLBACK_DESKTOP = object : RoomDatabase.Callback() {

    override fun onCreate(connection: SQLiteConnection) {
        SyncSchema.onCreate().forEach(connection::execSQL)
    }
}
