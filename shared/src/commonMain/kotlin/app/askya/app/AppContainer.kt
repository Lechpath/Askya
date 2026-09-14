package app.askya.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.askya.data.audio.VoiceFiles
import app.askya.data.db.AppDatabase
import app.askya.data.images.ImageFiles
import app.askya.data.preferences.ReaderPreferences
import app.askya.data.preferences.SettingsPreferences
import app.askya.data.preferences.WeatherPreferences
import app.askya.data.repository.BridgeRepository
import app.askya.data.repository.DayRepository
import app.askya.data.repository.DeedTaskRepository
import app.askya.data.repository.LedgerRepository
import app.askya.data.repository.NoteRepository
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.RoutineRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.data.repository.Trash
import app.askya.data.repository.YetRepository
import app.askya.domain.plan.DayComposer
import app.askya.domain.plan.RoutineDayComposer
import app.askya.reminders.ReminderClock
import app.askya.reminders.ReminderSoundSource

/**
 * Ручной DI — общая половина. База и репозитории создаются лениво: до первого
 * обращения приложение не трогает диск.
 *
 * Здесь то, что у телефона и компьютера одно и то же: база и всё, что стоит
 * на ней. Где лежат файл базы, картинки и настройки, решает система — это
 * абстрактные члены, и заполняют их `AndroidContainer` в приложении телефона
 * и `DesktopContainer` в Windows-версии. Там же живёт то, что есть только у
 * своей системы: плееры, виджеты и будильники у телефона.
 */
abstract class AppContainer {

    protected abstract val database: AppDatabase

    /** Папка Askya под картинки — см. [ImageFiles]. */
    abstract val imageStore: ImageFiles

    /** Папка голосовых заметок — см. [VoiceFiles]. */
    abstract val voiceStore: VoiceFiles

    /** Настройки приложения — своё хранилище у каждой системы. */
    abstract val settings: SettingsPreferences

    /**
     * Настройки чтения книг и место, на котором остановились в каждой: своё
     * хранилище — кегль двигают посреди чтения, а место пишется на каждой
     * остановке прокрутки.
     */
    abstract val readerPreferences: ReaderPreferences

    /** Настройки погоды и последний ответ сервиса. */
    abstract val weatherPreferences: WeatherPreferences

    /** Будильник напоминаний — см. [ReminderClock]. */
    abstract val alarms: ReminderClock

    /** Мелодии напоминаний — см. [ReminderSoundSource]. */
    abstract val reminderSounds: ReminderSoundSource

    val noteRepository: NoteRepository by lazy {
        NoteRepository(
            database.noteDao(),
            database.topicDao(),
            database.albumDao(),
            imageStore,
            voiceStore,
        )
    }

    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepository(database.scheduleDao()) }
    val reminderRepository: ReminderRepository by lazy { ReminderRepository(database.reminderDao()) }

    /**
     * Списки внутри дел дня. Отдельно от расписания: строки переписываются от
     * каждой галочки, а само дело — раз в день.
     */
    val deedTaskRepository: DeedTaskRepository by lazy { DeedTaskRepository(database.deedTaskDao()) }
    val routineRepository: RoutineRepository by lazy { RoutineRepository(database) }
    val yetRepository: YetRepository by lazy { YetRepository(database.yetDao()) }

    /** Расходная книга Ledger: счета, статьи и записи. */
    val ledgerRepository: LedgerRepository by lazy { LedgerRepository(database.ledgerDao()) }

    /**
     * Корзина на сутки — то, что заменило собой вопрос «вы уверены?».
     * Одна на всё приложение: убранное дело, строка его списка, запись, строка
     * Yet и трата — одно и то же событие для человека, и полоска «Вернуть» у
     * них должна быть одна.
     */
    val trash: Trash by lazy {
        Trash(
            scheduleRepository,
            deedTaskRepository,
            noteRepository,
            yetRepository,
            ledgerRepository,
        )
    }

    /**
     * Мосты — чем дело делается за пределами Askya. Хранятся в общей базе,
     * но открываются только на телефоне: мост ведёт в его приложение.
     */
    val bridgeRepository: BridgeRepository by lazy { BridgeRepository(database.bridgeDao()) }

    /**
     * Сборка дня — по списку дел.
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

/**
 * Контейнер для экранов. Кладёт его в дерево корень приложения — `MainActivity`
 * у телефона и окно у Windows-версии; без него экраны не собираются, и это
 * громкая ошибка, а не пустой экран.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer не положен в дерево: см. LocalAppContainer")
}

/** Доступ к контейнеру из Compose — без библиотек DI. */
@Composable
fun appContainer(): AppContainer = LocalAppContainer.current
