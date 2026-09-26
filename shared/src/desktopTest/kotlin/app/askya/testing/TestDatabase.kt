package app.askya.testing

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.askya.data.audio.VoiceFiles
import app.askya.data.db.AppDatabase
import app.askya.data.images.ImageFiles
import app.askya.data.db.SYNC_CALLBACK_DESKTOP
import app.askya.data.entity.Reminder
import app.askya.reminders.ReminderClock
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.Files

/**
 * Настоящая база Askya во временной папке — та же, что у Windows-версии, с
 * журналом правок. Закрыть и стереть — [close].
 */
class TestDatabase : AutoCloseable {

    private val folder: File = Files.createTempDirectory("askya-test").toFile()

    val db: AppDatabase = Room.databaseBuilder<AppDatabase>(
        name = File(folder, AppDatabase.NAME).absolutePath,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(SYNC_CALLBACK_DESKTOP)
        .build()

    override fun close() {
        db.close()
        folder.deleteRecursively()
    }
}

/** Картинок в этих тестах нет — папка, которой не бывает. */
object NoImages : ImageFiles {
    override val folderName = "нет"
    override suspend fun importFrom(source: String, name: String, mime: String): String? = null
    override suspend fun adopt(uri: String?): String? = null
    override suspend fun rename(uri: String?, name: String): String? = null
    override fun mimeOf(uri: String) = ""
    override fun shareLink(uri: String?): String? = null
    override fun isOurs(uri: String?) = false
    override suspend fun delete(uri: String?) = Unit
}

/** Голоса в этих тестах тоже нет. */
object NoVoices : VoiceFiles {
    override suspend fun remove(uri: String?) = Unit
}

/** Будильник, который запоминает, что ему дали, и умеет отказать. */
class RecordingClock(
    var exactAllowed: Boolean = true,
    var failing: Boolean = false,
) : ReminderClock {

    val scheduled = mutableListOf<Reminder>()

    override fun schedule(reminder: Reminder) {
        if (failing) throw SecurityException("нет права: напоминание «личное»")
        scheduled += reminder
    }

    override fun cancel(id: Long) {
        scheduled.removeAll { it.id == id }
    }

    override val exact: Boolean get() = exactAllowed
}
