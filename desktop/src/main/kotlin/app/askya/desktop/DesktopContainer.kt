package app.askya.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.askya.app.AppContainer
import app.askya.data.account.AccountPreferences
import app.askya.data.audio.VoiceFiles
import app.askya.data.db.AppDatabase
import app.askya.data.db.MIGRATION_46_47_DESKTOP
import app.askya.data.db.MIGRATION_47_48_DESKTOP
import app.askya.data.db.SYNC_CALLBACK_DESKTOP
import app.askya.data.preferences.AgentPreferences
import app.askya.data.preferences.ReaderPreferences
import app.askya.data.preferences.SettingsPreferences
import app.askya.data.preferences.WeatherPreferences
import app.askya.platform.fileOf
import app.askya.reminders.ReminderSound
import app.askya.reminders.ReminderSoundSource
import app.askya.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath
import java.io.File

/**
 * Ручной DI Windows-версии. Общая половина — база, репозитории, настройки — в
 * [AppContainer]; здесь то, где всё это лежит на компьютере.
 *
 * Всё лежит в одной папке [home] — `%LOCALAPPDATA%\Askya`: база, настройки и
 * копии картинок. Локальной, а не перемещаемой (`%APPDATA%`), и не в
 * «Изображениях»: у многих «Изображения» и перемещаемый профиль уезжают в
 * OneDrive, а данные Askya в чужое облако сами не уходят — это правило
 * телефона, и у компьютера оно то же (см. «Про сеть и приватность»).
 */
class DesktopContainer(val home: File) : AppContainer() {

    init {
        home.mkdirs()
    }

    public override val database: AppDatabase by lazy {
        Room.databaseBuilder<AppDatabase>(name = File(home, AppDatabase.NAME).absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_46_47_DESKTOP, MIGRATION_47_48_DESKTOP)
            .addCallback(SYNC_CALLBACK_DESKTOP)
            .build()
    }

    override val imageStore: DesktopImages by lazy { DesktopImages(File(home, IMAGES)) }

    /**
     * Голоса у Windows-версии не пишут; заметки, пришедшие Слепком с
     * телефона, ссылаются на файлы телефона, и убирать здесь нечего. Свой
     * файл — если он всё же наш — убирается вместе с записью.
     */
    override val voiceStore: VoiceFiles = object : VoiceFiles {
        override suspend fun remove(uri: String?) {
            val file = fileOf(uri) ?: return
            if (file.canonicalPath.startsWith(home.canonicalPath)) file.delete()
        }
    }

    override val settings: SettingsPreferences by lazy { SettingsPreferences(store("settings")) }
    override val readerPreferences: ReaderPreferences by lazy { ReaderPreferences(store("reader")) }
    override val weatherPreferences: WeatherPreferences by lazy { WeatherPreferences(store("weather")) }

    /** Аккаунт — своим файлом и мимо Слепка, как у телефона: см. [AccountPreferences]. */
    override val account: AccountPreferences by lazy { AccountPreferences(store("account")) }

    /** Ключ Claude и согласие на облако — своим файлом и мимо Слепка: см. [AgentPreferences]. */
    override val agent: AgentPreferences by lazy { AgentPreferences(store(AgentPreferences.STORE)) }

    /** Напоминания — часами внутри запущенной Askya, см. [DesktopAlarms]. */
    override val alarms: DesktopAlarms by lazy { DesktopAlarms(reminderRepository) }

    /**
     * Своих мелодий у Windows-версии нет: напоминание звучит звуком
     * уведомлений Windows. Выбор мелодии в карточке сводится к «молча» и
     * «со звуком».
     */
    override val reminderSounds: ReminderSoundSource = object : ReminderSoundSource {
        override suspend fun system(context: PlatformContext): List<ReminderSound> = emptyList()
        override suspend fun music(context: PlatformContext): List<ReminderSound> = emptyList()
        override fun hasMusicAccess(context: PlatformContext): Boolean = false
        override val musicPermission: String? = null
        override fun preview(context: PlatformContext, uri: String?) {
            java.awt.Toolkit.getDefaultToolkit().beep()
        }
        override fun stopPreview() {}
    }

    /** «Слепок» — тот же файл, что делает телефон, в обе стороны. */
    val snapshots: DesktopSnapshots by lazy { DesktopSnapshots(this) }

    /**
     * Хранилище DataStore по имени — тем же именем, что у телефона, и тем же
     * форматом файла. Поэтому Слепок переносит настройки как есть.
     */
    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { storeFile(name).absolutePath.toPath() },
        )

    fun storeFile(name: String): File = File(home, "datastore/$name.preferences_pb")

    companion object {
        /** Папка картинок внутри данных Askya. */
        const val IMAGES = "images"

        /** Хранилища, которые есть у Windows-версии. Echo и AskyaV — телефону. */
        val STORES = listOf("settings", "reader", "weather")

        /**
         * Папка данных: `%LOCALAPPDATA%\Askya`. Переменной нет (чужая система,
         * запуск из-под странной оболочки) — папка Askya в домашней.
         *
         * Свойство `askya.home` ставит данные в другое место — для пробных
         * запусков из-под Gradle, чтобы не трогать настоящие записи.
         */
        fun defaultHome(): File {
            System.getProperty("askya.home")?.takeIf { it.isNotBlank() }?.let { return File(it) }
            val local = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            return if (local != null) File(local, "Askya") else File(System.getProperty("user.home"), ".askya")
        }
    }
}
