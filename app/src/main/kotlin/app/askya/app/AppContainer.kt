package app.askya.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.askya.data.db.AppDatabase
import app.askya.data.images.ImageStore
import app.askya.data.preferences.EchoPreferences
import app.askya.data.preferences.ReaderPreferences
import app.askya.data.preferences.SettingsPreferences
import app.askya.data.repository.CheckInRepository
import app.askya.data.repository.EchoRepository
import app.askya.data.repository.NoteRepository
import app.askya.data.repository.PracticeRepository
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.RoutineRepository
import app.askya.data.repository.DayRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.data.repository.YetRepository
import app.askya.echo.EchoEffects
import app.askya.echo.EchoEqualizer
import app.askya.echo.EchoLibrary
import app.askya.echo.EchoPlayer
import app.askya.echo.EchoPulse
import app.askya.domain.plan.DayComposer
import app.askya.domain.plan.RoutineDayComposer

/**
 * Ручной DI. База и репозитории создаются лениво: до первого обращения
 * приложение не трогает диск.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    /**
     * Папка Askya под картинки — «Внутренняя память → Pictures → Askya».
     * Заводится раньше репозитория: тот удаляет копию вместе с записью, иначе
     * в папке копились бы файлы, на которые в Scroll уже ничего не ссылается.
     */
    val imageStore: ImageStore by lazy { ImageStore(appContext) }

    val noteRepository: NoteRepository by lazy {
        NoteRepository(
            database.noteDao(),
            database.topicDao(),
            database.albumDao(),
            imageStore,
        )
    }
    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepository(database.scheduleDao()) }
    val checkInRepository: CheckInRepository by lazy { CheckInRepository(database.checkInDao()) }
    val practiceRepository: PracticeRepository by lazy { PracticeRepository(database.practiceLogDao()) }
    val reminderRepository: ReminderRepository by lazy { ReminderRepository(database.reminderDao()) }
    val routineRepository: RoutineRepository by lazy { RoutineRepository(database) }
    val yetRepository: YetRepository by lazy { YetRepository(database.yetDao()) }
    val settings: SettingsPreferences by lazy { SettingsPreferences(appContext) }

    /**
     * Настройки чтения книг и место, на котором остановились в каждой: своё
     * хранилище — кегль двигают посреди чтения, а место пишется на каждой
     * остановке прокрутки.
     */
    val readerPreferences: ReaderPreferences by lazy { ReaderPreferences(appContext) }

    /** Настройки звука Echo — своё хранилище, отдельно от настроек приложения. */
    val echoPreferences: EchoPreferences by lazy { EchoPreferences(appContext) }

    /**
     * Плеер Echo — один на приложение, а не на экран: музыка не должна
     * замолкать от того, что человек ушёл в расписание.
     */
    val echoPlayer: EchoPlayer by lazy { EchoPlayer(appContext, echoPreferences) }

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

    /** Плейлисты Echo — то, что человек собрал сам. */
    val echoRepository: EchoRepository by lazy { EchoRepository(database.echoDao()) }

    /** Музыка читается заново при каждом открытии: телефон пополняют. */
    suspend fun echoLibrary(context: Context) = EchoLibrary.load(context)

    /**
     * Сборка дня — по списку дел на телефоне.
     *
     * Модель отсюда убрана: день собирается из того, что человек сам записал в
     * список дел, а не из разговора о себе. Ни ключа, ни сети для этого не
     * нужно, и собранный день перестал зависеть от чужого сервиса.
     */
    private val dayComposer: DayComposer by lazy { RoutineDayComposer() }

    val dayRepository: DayRepository by lazy {
        DayRepository(database, dayComposer)
    }
}

/** Доступ к контейнеру из Compose — без библиотек DI. */
@Composable
fun appContainer(): AppContainer {
    val context = LocalContext.current.applicationContext
    return remember(context) { (context as AskyaApplication).container }
}
