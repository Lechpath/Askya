package app.askya.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.askya.data.account.AccountPreferences
import app.askya.data.backup.Snapshots
import app.askya.data.db.AppDatabase
import app.askya.data.db.build
import app.askya.data.audio.VoiceRecorder
import app.askya.data.audio.VoiceStore
import app.askya.data.images.ImageStore
import app.askya.data.library.AskyaLibrary
import app.askya.data.preferences.EchoPreferences
import app.askya.data.preferences.ReaderPreferences
import app.askya.data.preferences.SettingsPreferences
import app.askya.data.preferences.VideoPreferences
import app.askya.data.preferences.WeatherPreferences
import app.askya.data.repository.EchoRepository
import app.askya.data.repository.VideoRepository
import app.askya.data.entity.Reminder
import app.askya.reminders.ReminderAlarms
import app.askya.reminders.ReminderClock
import app.askya.reminders.ReminderSoundPreview
import app.askya.reminders.ReminderSoundSource
import app.askya.reminders.ReminderSounds
import app.askya.update.Updates
import app.askya.echo.EchoEffects
import app.askya.echo.EchoEqualizer
import app.askya.echo.EchoLibrary
import app.askya.echo.EchoAside
import app.askya.echo.EchoPlayer
import app.askya.echo.EchoPulse
import app.askya.video.VideoDownloads
import app.askya.video.VideoEngine
import app.askya.video.VideoStore
import app.askya.weather.WeatherRepository

/**
 * Ручной DI телефона. Общая половина — база, репозитории, настройки — в
 * [AppContainer] модуля `shared`; здесь то, где всё это лежит на Android, и то,
 * что есть только у телефона: плееры, закачки, погода по месту, Слепок,
 * обновления.
 */
class AndroidContainer(context: Context) : AppContainer() {

    private val appContext = context.applicationContext

    public override val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    /**
     * Библиотека Askya — одна папка в корне памяти под всё, что приложение
     * положило к себе. Стоит впереди остальных хранилищ: и картинки, и видео
     * спрашивают у неё, готова ли она принять файл, и пишут в общие разделы
     * только тогда, когда она отвечает «нет».
     */
    val library: AskyaLibrary by lazy { AskyaLibrary(appContext) }

    /**
     * Папка Askya под картинки — библиотека, а без разрешения на неё
     * «Внутренняя память → Pictures → Askya». Заводится раньше репозитория:
     * тот удаляет копию вместе с записью, иначе в папке копились бы файлы, на
     * которые в Scroll уже ничего не ссылается.
     */
    override val imageStore: ImageStore by lazy { ImageStore(appContext, library) }

    /**
     * Папка Askya под голос — «Внутренняя память → Music → Askya». Заводится
     * раньше репозитория по той же причине, что и папка картинок: тот убирает
     * файл вместе с записью.
     */
    override val voiceStore: VoiceStore by lazy { VoiceStore(appContext) }

    /**
     * Диктофон голосовых заметок. Один на приложение, как плеер: запись не
     * должна обрываться от пересборки экрана. В фоне, в отличие от плеера, он
     * не живёт — см. [VoiceRecorder].
     */
    val voiceRecorder: VoiceRecorder by lazy { VoiceRecorder(appContext) }

    override val settings: SettingsPreferences by lazy { SettingsPreferences(appContext) }

    override val account: AccountPreferences by lazy { AccountPreferences(appContext) }

    /** Точный будильник системы и уведомление в шторке — см. [ReminderAlarms]. */
    override val alarms: ReminderClock = object : ReminderClock {
        override fun schedule(reminder: Reminder) = ReminderAlarms.schedule(appContext, reminder)
        override fun cancel(id: Long) = ReminderAlarms.cancel(appContext, id)
        override val exact: Boolean get() = ReminderAlarms.canBeExact(appContext)

        // Экрана может не быть на переделанной прошивке: тогда лучше
        // остаться на месте, чем уронить приложение об чужой Android.
        override fun openExactSettings() {
            runCatching {
                appContext.startActivity(
                    ReminderAlarms.exactAlarmSettings(appContext)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** Мелодии телефона и своя музыка — см. [ReminderSounds]. */
    override val reminderSounds: ReminderSoundSource = object : ReminderSoundSource {
        override suspend fun system(context: Context) = ReminderSounds.system(context)
        override suspend fun music(context: Context) = ReminderSounds.music(context)
        override fun hasMusicAccess(context: Context) = ReminderSounds.hasMusicAccess(context)
        override val musicPermission: String get() = ReminderSounds.musicPermission()
        override fun preview(context: Context, uri: String?) = ReminderSoundPreview.play(context, uri)
        override fun stopPreview() = ReminderSoundPreview.stop()
    }

    /**
     * Обновление из облака. На весь век приложения, как и закачки AskyaV, и по
     * той же причине: сборка весит сотню мегабайт, а закрытое окно настроек не
     * повод бросать её на середине.
     */
    val updates: Updates by lazy { Updates(appContext, settings) }

    /**
     * «Слепок» — вся память приложения одним файлом. Знает и базу, и папку
     * картинок, потому что копия без картинок — это не копия.
     */
    val snapshots: Snapshots by lazy { Snapshots(appContext, database, imageStore) }

    override val readerPreferences: ReaderPreferences by lazy { ReaderPreferences(appContext) }

    /** Настройки звука Echo — своё хранилище, отдельно от настроек приложения. */
    val echoPreferences: EchoPreferences by lazy { EchoPreferences(appContext) }

    /**
     * Плеер Echo — один на приложение, а не на экран: музыка не должна
     * замолкать от того, что человек ушёл в расписание.
     */
    val echoPlayer: EchoPlayer by lazy { EchoPlayer(appContext, echoPreferences) }

    /**
     * Голосовая заметка, играющая поверх экрана. Рядом с плеером и на том же
     * веку: она не должна обрываться от перехода в другой раздел, и музыку,
     * которую приглушила, возвращает тоже она.
     */
    val echoAside: EchoAside by lazy { EchoAside(appContext, echoPlayer) }

    /**
     * Эквалайзер — рядом с плеером и на том же веку: он настраивает его
     * сессию, и переживать закрытие карточки настройка должна.
     */
    val echoEqualizer: EchoEqualizer by lazy { EchoEqualizer(echoPlayer, echoPreferences) }

    /**
     * Остальная обработка звука — бас, объём, догромкость, зал. Тоже на сессии
     * плеера и тоже на весь век приложения: настроенный звук не должен
     * сбрасываться от ухода с экрана.
     */
    val echoEffects: EchoEffects by lazy { EchoEffects(echoPlayer.audioSessionId, echoPreferences) }

    /**
     * Вспышки обложки в ритм: слушают ту же сессию, что и эффекты. Тоже на
     * весь век приложения — сессия одна, и заводить слушание заново на каждом
     * заходе в раздел значило бы каждый раз ловить `Visualizer` с нуля.
     */
    val echoPulse: EchoPulse by lazy { EchoPulse(echoPlayer.audioSessionId) }

    /** Настройки просмотра и места остановки в фильмах — своё хранилище. */
    val videoPreferences: VideoPreferences by lazy { VideoPreferences(appContext) }

    /**
     * Плеер AskyaV. Как и плеер Echo — один на приложение, но по другой
     * причине: музыка не должна замолкать от ухода в другой раздел, а фильм не
     * должен начинаться заново от поворота телефона.
     */
    val videoEngine: VideoEngine by lazy { VideoEngine(appContext) }

    /**
     * Куда ложатся вырезанные куски и скачанное: полка «Видео» библиотеки, а
     * без разрешения на неё — «Movies/Askya», как было прежде.
     */
    val videoStore: VideoStore by lazy { VideoStore(appContext, library) }

    /**
     * Закачки AskyaV. Рядом с плеером и на том же веку: фильм качается
     * минутами, и закачка не должна обрываться от закрытия лаборатории, из
     * которой её начали.
     */
    val videoDownloads: VideoDownloads by lazy { VideoDownloads(appContext, videoStore) }

    /** Плейлисты AskyaV — то, что человек сложил смотреть подряд. */
    val videoRepository: VideoRepository by lazy { VideoRepository(database.videoDao()) }

    override val weatherPreferences: WeatherPreferences by lazy { WeatherPreferences(appContext) }

    /**
     * Погода — одна на приложение: её спрашивают и строка в меню, и раздел, и
     * спрашивать её дважды подряд ради двух мест на одном экране незачем.
     */
    val weather: WeatherRepository by lazy { WeatherRepository(appContext, weatherPreferences) }

    /** Плейлисты Echo — то, что человек собрал сам. */
    val echoRepository: EchoRepository by lazy { EchoRepository(database.echoDao()) }

    /** Музыка читается заново при каждом открытии: телефон пополняют. */
    suspend fun echoLibrary(context: Context) = EchoLibrary.load(context)
}

/**
 * Контейнер телефона из Compose — со всем, что есть только у него. Общим
 * экранам хватает [appContainer]; этот нужен экранам Echo, AskyaV и прочему,
 * что живёт только в приложении телефона.
 */
@Composable
fun androidContainer(): AndroidContainer {
    val context = LocalContext.current.applicationContext
    return remember(context) { (context as AskyaApplication).container }
}
