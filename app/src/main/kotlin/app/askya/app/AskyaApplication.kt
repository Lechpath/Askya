package app.askya.app

import android.app.Application
import app.askya.data.backup.SnapshotAlarms
import app.askya.shade.TaskShade
import app.askya.widget.DayLockScreen
import app.askya.widget.DayWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

class AskyaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        prepareLibrary()
        lookForUpdate()
        watchScheduleForWidget()
        watchScheduleForLockScreen()
        watchDeedTasksForShade()
        moveImagesToVisibleFolder()
        dropUnpackedBooks()
        dropRemovedModelSecrets()
        emptyTrash()
        finishSnapshotRestore()
        syncSnapshotReminder()
    }

    /**
     * Завести папку Askya, если на неё есть право.
     *
     * При каждом запуске, а не разово на установке: разрешение «Доступ ко всем
     * файлам» выдают позже, чем ставят приложение, — и, наоборот, папку удаляют
     * проводником, ничего не спрашивая. Первый запуск после того и другого —
     * единственное место, где это можно поправить молча.
     *
     * Разрешения нет — не делается ничего и ничего не спрашивается: просьба о
     * таком праве уместна там, где объяснено зачем, то есть в настройках, а не
     * на голом старте.
     */
    private fun prepareLibrary() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { container.library.prepare() }
    }

    /**
     * Посмотреть, нет ли новой сборки, — если об этом просили.
     *
     * Три условия, и все три обязательны: включён выключатель в настройках,
     * задан адрес и с прошлой проверки прошли сутки. Не совпало хоть одно — в
     * сеть приложение не выходит вовсе.
     *
     * Проверка тихая: она ничего не показывает и уж тем более ничего не
     * качает. Найденное обновление ждёт в строке настроек, а решение — за
     * человеком; приложение, которое обновляет себя само, — не то, о чём
     * просили.
     */
    private fun lookForUpdate() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val settings = container.settings.settings.first()
            if (!settings.updateOnStart || settings.updateSource.isBlank()) return@launch
            if (!container.settings.dueForUpdateCheck()) return@launch
            container.updates.check(quiet = true)
        }
    }

    /**
     * Список дела в шторке ходит за днём следом: отметили строку, дописали
     * новую, отметили само дело — шторка пересобралась.
     *
     * Три потока, а не один: строки живут в своей таблице, «дело сделано»
     * убирает его из шторки целиком — и об этом знает только расписание, — а
     * выключатель в настройках должен убирать её всю и сразу.
     *
     * Без `drop(1)`, в отличие от виджета: первое значение приходит сразу при
     * подписке, и по нему шторка и заводится на запуске. Виджету это было бы
     * лишней работой — он и так нарисован на рабочем столе, — а уведомления
     * после перезагрузки телефона нет, и восстановить его больше некому.
     *
     * Пересборка идёт в [app.askya.shade.TaskShade] и там же решает, показывать
     * ли вообще: настройку спрашивает она.
     */
    private fun watchDeedTasksForShade() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val today = LocalDate.now()
        merge(
            // Все три потока сводятся к «что-то изменилось»: шторка всё равно
            // перечитывает день целиком, и содержимое ей отсюда не нужно.
            container.deedTaskRepository.tasksOn(today).map { },
            container.scheduleRepository.itemsOn(today).map { },
            // Выключатель — третьим: выключенная настройка должна убрать
            // уведомления сразу, а не при следующей правке списка.
            container.settings.settings.map { it.deedShade }.distinctUntilChanged().map { },
        )
            .onEach { TaskShade.refresh(this) }
            .launchIn(scope)
    }

    /**
     * Выбросить то, что пролежало убранным сутки.
     *
     * При запуске и без фоновой службы: службу ради уборки четырёх таблиц
     * заводить не за что, а Askya запускают чаще, чем раз в сутки.
     */
    private fun emptyTrash() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { container.trash.purge() }
    }

    /**
     * Второй заход чтения слепка: картинки.
     *
     * Первый заход подменил базу и настройки и перезапустил приложение — вот
     * этот запуск и есть тот самый. Картинки ждут во временной папке, потому
     * что раскладывать их можно только по уже подменённой базе.
     *
     * Не выполнилось — папка остаётся, и заход повторится при следующем
     * запуске: незаконченное чтение лучше повторить, чем забыть.
     */
    private fun finishSnapshotRestore() {
        if (!container.snapshots.restorePending()) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { container.snapshots.finishRestore() }
    }

    /**
     * Напоминание про слепок — при каждом запуске.
     *
     * Будильник не переживает ни перезагрузку, ни переустановку приложения, а
     * записан он в двух значениях настроек; сверять их при запуске дешевле,
     * чем ловить каждое место, где они меняются.
     */
    private fun syncSnapshotReminder() {
        val settings = container.settings.state.value
        SnapshotAlarms.sync(this, settings.snapshotReminder, settings.snapshotAt)
    }

    /**
     * Разовая уборка того, что осталось от убранной модели: ключа и рассказа
     * о себе (`SettingsPreferences.dropRemovedModelSecrets`).
     *
     * Здесь, а не в настройках, потому что стереть надо и у того, кто в
     * настройки больше не заходит. И не разово по флагу: восстановление из
     * старой резервной копии вернёт ключ на место, и уборка должна встретить
     * его снова.
     */
    private fun dropRemovedModelSecrets() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { container.settings.dropRemovedModelSecrets() }
    }

    /**
     * Разовая уборка распакованного из книг.
     *
     * Папку `cache/books` наполняли картинки и ролики из epub; вместе с самой
     * читалкой epub её наполнять стало нечему, а лежать у людей, читавших
     * иллюстрированные книги, осталось до сотни мегабайт — и кнопки «очистить»
     * в настройках больше нет, она ушла тем же движением.
     *
     * Как и переезд картинок, без отдельного флага «уже ли»: второй раз убирать
     * будет нечего, а `deleteRecursively` по несуществующей папке — это один
     * вопрос к файловой системе.
     */
    private fun dropUnpackedBooks() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { File(cacheDir, "books").deleteRecursively() }
    }

    /**
     * Разовый переезд картинок в видимую папку Askya.
     *
     * Копии, сделанные до этого, лежат в `Android/data`, куда с Android 11 не
     * заходит проводник. Переносятся они при запуске и по одной: пока переезд
     * идёт, старые картинки открываются со старого места, а когда закончится —
     * все лежат в `Pictures/Askya`. Второй раз переносить будет нечего, так
     * что проверять «уже ли» отдельным флагом незачем.
     *
     * В фоне и без ожидания: запуск приложения не должен ждать копирования
     * файлов, а раздел «Изображения» показывает картинки одинаково с обоих
     * мест.
     */
    private fun moveImagesToVisibleFolder() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { container.noteRepository.moveImagesToFolder() }
    }

    /**
     * Пока приложение живо, виджет ходит за ним следом: поправил дело —
     * виджет перерисовался, не дожидаясь получаса или будильника.
     *
     * `drop(1)` — первое значение приходит сразу при подписке и ничего не
     * меняет; перерисовывать по нему значило бы будить виджет на каждом
     * запуске приложения впустую.
     *
     * Когда процесс мёртв, за свежесть отвечают будильники на границы дел
     * (`DayWidgetRefresh`) — этот поток их не заменяет, а дополняет.
     */
    private fun watchScheduleForWidget() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        container.scheduleRepository.itemsOn(LocalDate.now())
            .drop(1)
            .onEach { DayWidgetProvider.refresh(this) }
            .launchIn(scope)
    }

    /**
     * Дела дня на экране блокировки ходят за расписанием так же, как виджет, и
     * за своим выключателем — выключенное уведомление убирается сразу.
     *
     * Без `drop(1)`, как и шторка: уведомление после перезагрузки может
     * пропасть, и первое значение на запуске его возвращает. Между запусками
     * его обновляют будильники на границы дел (`DayWidgetRefresh`).
     */
    private fun watchScheduleForLockScreen() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        merge(
            container.scheduleRepository.itemsOn(LocalDate.now()).map { },
            container.scheduleRepository.itemsOn(LocalDate.now().plusDays(1)).map { },
            container.settings.settings.map { it.dayLockScreen }.distinctUntilChanged().map { },
        )
            .onEach { DayLockScreen.refresh(this) }
            .launchIn(scope)
    }
}
