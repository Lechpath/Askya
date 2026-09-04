package app.askya.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import app.askya.data.preferences.PHONE_PLACE
import app.askya.data.preferences.WeatherPreferences
import app.askya.widget.WeatherWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Что сейчас с погодой — для экрана и для строки в меню. */
data class WeatherState(
    val forecast: Forecast? = null,
    val loading: Boolean = false,
    /** Почему нечего показать. `null` — всё в порядке. */
    val trouble: String? = null,
)

/**
 * Погода: где мы и что там.
 *
 * Собирает вместе три разных дела, каждое из которых по отдельности не имеет
 * смысла: спросить у системы место, спросить у Open-Meteo погоду на этом месте
 * и запомнить ответ, чтобы не спрашивать снова через минуту.
 *
 * ## Место
 *
 * Берётся **последнее известное системе**, а не запрашивается заново. Разница
 * тут не в лишней строчке кода: свежая точка означает включить приёмник и
 * подождать — то есть потратить заряд ради того, чтобы уточнить погоду на
 * километр. Последнее известное место система хранит сама, отдаёт мгновенно и
 * для погоды точнее, чем нужно.
 *
 * Не известно ничего — так и говорится: без места погоды нет, а выдумывать
 * Москву за человека нельзя. Но **выбрать город можно самому**
 * ([app.askya.data.preferences.ChosenPlace]), и тогда система не спрашивается
 * вовсе: ни разрешения, ни последней точки, ни имени у геокодера. Это не
 * запасной ход на случай отказа, а обычное положение дел для тех, у кого
 * геолокация выключена совсем: телефон своего места не знает и не узнает, а
 * погода за окном есть.
 *
 * Городов у человека несколько, и переключаются они мгновенно: у каждого своя
 * запомненная погода, поэтому возвращение к вчерашнему городу показывает его
 * погоду сразу — и обновляет её, только если она успела состариться.
 *
 * ## Когда спрашивается
 *
 * По входу в раздел, по открытию меню и не чаще, чем раз в полчаса (настройка).
 * Запомненное показывается сразу и без сети — с честной подписью, когда оно
 * спрошено. Погода получасовой давности — это погода; крутящийся кружок вместо
 * неё — нет.
 */
class WeatherRepository(
    private val context: Context,
    private val preferences: WeatherPreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    init {
        // Запомненное поднимается сразу: строка в меню должна быть на месте
        // ещё до того, как приложение решит, идти ли в сеть. Того места, на
        // которое человек смотрел в прошлый раз, — не первого попавшегося.
        scope.launch {
            val cached = preferences.cache(chosenKey()).first()
            if (cached != null) _state.value = WeatherState(forecast = cached.toForecast())
        }
    }

    /** Ключ места, на которое смотрят сейчас. */
    private suspend fun chosenKey(): String =
        preferences.settings.first().place?.key ?: PHONE_PLACE

    /** Есть ли у приложения право знать, где телефон. */
    fun located(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Идёт ли запрос прямо сейчас.
     *
     * Просьб о погоде приходит больше, чем запросов должно уйти: раздел просит
     * при входе, меню — при открытии, выбранный город — сразу после выбора, и
     * всё это случается в один миг. Второй запрос поверх идущего не добавляет
     * ничего, кроме ещё одного выхода в сеть.
     *
     * Обыкновенными полями, без замка: [scope] однопоточный, и все проверки
     * приходятся на главный поток.
     */
    private var running = false

    /**
     * Просили ли обновиться, пока шёл запрос.
     *
     * Просьба, пришедшая посреди запроса, не отбрасывается совсем, а
     * откладывается до его конца. Появилось это вместе с переключением между
     * городами: человек нажимает второй город, не дождавшись первого, — и
     * молча брошенная просьба оставила бы его перед пустым разделом до
     * следующего входа.
     */
    private var again = false

    /**
     * Обновить, если пора.
     *
     * [force] — по нажатию «обновить»: тогда возраст запомненного не считается.
     *
     * Возвращает начатую работу — она нужна одному месту, виджету погоды: тот
     * зовёт обновление из приёмника намерения, и ему надо знать, когда разговор
     * с сетью кончился (`WeatherWidgetProvider`). Экранам она не нужна, и они её
     * не берут. `null` означает, что своей работы не начиналось: запрос уже шёл,
     * и просьба отложена до его конца — ждать в этом случае нечего, за живость
     * процесса отвечает тот, кто запрос начал.
     */
    fun refresh(force: Boolean = false): Job? {
        if (running) {
            again = true
            return null
        }
        running = true
        return scope.launch {
            try {
                refreshOnce(force)
                // Отложенная просьба — уже про другое место, и «обновить во
                // что бы то ни стало» к ней не относится: свежая погода
                // нового города берётся из памяти, как обычно.
                while (again) {
                    again = false
                    refreshOnce(false)
                }
            } finally {
                running = false
                again = false
            }
        }
    }

    /** Само обновление. Вынесено из [refresh], чтобы `finally` был один. */
    private suspend fun refreshOnce(force: Boolean) {
        val settings = preferences.settings.first()
        if (!settings.enabled) {
            _state.value = WeatherState(trouble = "Погода выключена в настройках")
            return
        }

        // Выбранный город отменяет весь разговор с системой: ни
        // разрешения, ни последней точки, ни геокодера — координаты уже
        // лежат в настройках, и спрашивать по ним можно с выключенной
        // геолокацией.
        val chosen = settings.place
        val key = chosen?.key ?: PHONE_PLACE

        val cached = preferences.cache(key).first()
        val fresh = cached != null &&
            System.currentTimeMillis() - cached.fetchedAt < settings.refreshMinutes * 60_000L
        // Не copy, а замена целиком: на экране может стоять погода города, с
        // которого только что ушли, и оставить её под новой подписью значило
        // бы соврать. Пусто на полсекунды — честнее.
        _state.value = WeatherState(forecast = cached?.toForecast())
        if (fresh && !force) return

        if (chosen == null && !located()) {
            _state.value = _state.value.copy(
                trouble = if (cached == null) {
                    "Нужен доступ к месту — или выберите город сами, значком места в шапке"
                } else {
                    null
                },
            )
            return
        }

        _state.value = _state.value.copy(loading = true)

        val latitude: Double
        val longitude: Double
        // Имя выбранного города известно сразу, а имя системной точки —
        // только после того, как её назовёт геокодер; поэтому здесь оно
        // либо готово, либо спрашивается ниже, после ответа о погоде.
        var name = chosen?.name.orEmpty()

        if (chosen != null) {
            latitude = chosen.latitude
            longitude = chosen.longitude
        } else {
            val place = lastKnownPlace()
            if (place == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    trouble = if (cached == null) {
                        "Телефон не знает, где он: геолокация выключена или ещё не " +
                            "включалась. Включите её — или выберите город сами."
                    } else {
                        null
                    },
                )
                return
            }
            latitude = place.latitude
            longitude = place.longitude
            name = placeName(place)
        }

        val body = WeatherService.fetchBody(latitude, longitude)
        if (body == null) {
            _state.value = _state.value.copy(
                loading = false,
                trouble = if (cached == null) "Погода не пришла: нет сети или сервис молчит" else null,
            )
            return
        }

        preferences.remember(key, body, name, System.currentTimeMillis())
        // Виджет на рабочем столе показывает то же запомненное — значит, новая
        // погода есть и у него. Своего запроса он не делает вовсе (см.
        // `WeatherWidgetProvider`), и без этой строчки менялся бы только по
        // получасовому будильнику системы.
        WeatherWidgetProvider.refresh(context)
        val parsed = runCatching { WeatherService.parse(body) }.getOrNull()
        _state.value = WeatherState(
            forecast = parsed?.copy(place = name.ifBlank { cached?.place.orEmpty() }),
            loading = false,
            trouble = if (parsed == null) "Ответ не разобрался" else null,
        )
    }

    /**
     * Последнее место, известное системе.
     *
     * Спрашиваются все поставщики подряд и берётся самый свежий ответ: сеть
     * знает место почти всегда, но грубо; спутники — точно, но только если
     * телефон недавно был на улице.
     */
    private suspend fun lastKnownPlace(): Location? = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    /**
     * Название места словами.
     *
     * Спрашивается у системного `Geocoder`, а не у сервиса погоды: тот отдаёт
     * координаты обратно, а не имя, и просить у него имя значило бы отправить
     * ещё один запрос. Не назвал — раздел обойдётся без подписи.
     */
    @Suppress("DEPRECATION")
    private suspend fun placeName(location: Location): String = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching ""
            val found = Geocoder(context, Locale("ru"))
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
            listOfNotNull(
                found?.locality ?: found?.subAdminArea ?: found?.adminArea,
            ).joinToString(", ")
        }.getOrDefault("")
    }

    private fun app.askya.data.preferences.WeatherCache.toForecast(): Forecast? =
        runCatching { WeatherService.parse(body).copy(place = place, fetchedAt = fetchedAt) }
            .getOrNull()
}
