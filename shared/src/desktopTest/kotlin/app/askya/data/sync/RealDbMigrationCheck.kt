package app.askya.data.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.askya.data.db.AppDatabase
import app.askya.data.db.MIGRATION_46_47_DESKTOP
import app.askya.data.db.SYNC_CALLBACK_DESKTOP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Разовая проверка на настоящей базе — запускается только с путём к ней:
 *
 *   gradlew :shared:desktopTest --tests "*RealDbMigrationCheck*" -Daskya.realdb=<файл>
 *
 * Без этого свойства тест ничего не делает: в репозитории чужих баз нет.
 */
class RealDbMigrationCheck {

    @Test
    fun `настоящая база с телефона переносится`() = runBlocking {
        val path = System.getProperty("askya.realdb") ?: return@runBlocking
        val file = File(path)
        assertTrue(file.exists(), path)

        val db = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_46_47_DESKTOP)
            .addCallback(SYNC_CALLBACK_DESKTOP)
            .build()

        val waiting = db.syncDao().dirtyCount()
        println("СТРОК В ЖУРНАЛЕ: " + waiting)
        assertTrue(waiting > 0)
        db.close()
    }
}
