package app.askya.app

import android.app.Application
import app.askya.widget.DayWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate

class AskyaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        watchScheduleForWidget()
        moveImagesToVisibleFolder()
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
}
