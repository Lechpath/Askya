package app.askya.widget

import android.content.Context
import app.askya.app.AskyaApplication
import app.askya.data.preferences.PHONE_PLACE
import app.askya.weather.WeatherService
import app.askya.weather.formatDegrees
import app.askya.weather.weatherMark
import app.askya.weather.weatherWords
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Час вперёд — один столбик в нижнем ряду виджета. */
data class WidgetHour(val time: String, val mark: String, val degrees: String)

/**
 * Что показывает виджет погоды.
 *
 * [known] = false означает, что показывать нечего, и тогда весь виджет — одна
 * строка [empty]: почему нечего и что с этим делать.
 *
 * [aged] — подпись «спрошено в 14:20». Появляется только у постаревшего:
 * у свежей погоды время запроса — лишнее число на маленьком экране, а у
 * трёхчасовой это единственное, что отличает её от нынешней.
 */
data class WeatherWidgetState(
    val known: Boolean,
    val mark: String = "",
    val degrees: String = "",
    val words: String = "",
    val place: String = "",
    val hours: List<WidgetHour> = emptyList(),
    val aged: String = "",
    val empty: String = "",
)

/**
 * Погода для виджета — из того, что приложение запомнило.
 *
 * Своего запроса виджет не делает и своих правил о погоде не заводит: за сетью
 * ходит [app.askya.weather.WeatherRepository] — он один знает про выбранный
 * город, про разрешение на место, про то, как часто спрашивать и что делать,
 * когда сети нет. Виджет читает запомненный ответ, а обновиться просит его же.
 *
 * Ответ хранится строкой json, как пришёл, и разбирается здесь заново —
 * дешёвое дело, ради которого не стоит держать разобранную копию.
 */
object WeatherWidgetData {

    /** Старше этого — уже не «сейчас», и виджет подписывает, когда это спрошено. */
    private const val AGED_MS = 2 * 60 * 60 * 1000L

    /** Часов вперёд — ровно столько, сколько столбиков в макете. */
    private const val HOURS = 4

    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH")

    suspend fun read(context: Context): WeatherWidgetState {
        val container = (context.applicationContext as AskyaApplication).container
        val preferences = container.weatherPreferences

        val settings = preferences.settings.first()
        if (!settings.enabled) {
            return WeatherWidgetState(
                known = false,
                empty = "Погода выключена в настройках",
            )
        }

        val key = settings.place?.key ?: PHONE_PLACE
        val cached = preferences.cache(key).first()
            ?: return WeatherWidgetState(
                known = false,
                // Не «нет сети» и не «нет доступа»: виджет не знает, что именно
                // не вышло, и врать не должен. Нажатие открывает раздел, где об
                // этом сказано прямо.
                empty = "Погоды ещё нет. Нажмите, чтобы спросить.",
            )

        val now = LocalDateTime.now()
        val forecast = runCatching { WeatherService.parse(cached.body, now) }.getOrNull()
            ?: return WeatherWidgetState(known = false, empty = "Погода не разобралась")

        val ahead = forecast.hours
            .filter { it.at.isAfter(now) }
            .take(HOURS)
            .map { hour ->
                WidgetHour(
                    time = hour.at.format(HOUR),
                    mark = weatherMark(hour.code, day = hour.at.hour in DAYLIGHT),
                    degrees = formatDegrees(hour.temperature),
                )
            }

        val old = System.currentTimeMillis() - cached.fetchedAt > AGED_MS

        return WeatherWidgetState(
            known = true,
            mark = weatherMark(forecast.now.code, forecast.now.day),
            degrees = formatDegrees(forecast.now.temperature),
            words = listOfNotNull(
                weatherWords(forecast.now.code),
                "ощущается ${formatDegrees(forecast.now.feelsLike)}"
                    .takeIf { forecast.now.feelsLike != forecast.now.temperature },
            ).joinToString(" · "),
            place = cached.place,
            hours = ahead,
            aged = if (old) "в " + at(cached.fetchedAt).format(CLOCK) else "",
        )
    }

    /**
     * День на улице или ночь у часа прогноза.
     *
     * У часовых значений `is_day` в ответе нет — оно приходит только у
     * нынешней погоды, — а знак ясного неба без него был бы солнцем и в час
     * ночи. Считается грубо, по часам суток: восход и закат в ответе есть, но
     * ради выбора между солнцем и луной в четырёх столбиках разбирать их для
     * каждого дня отдельно — работа не по месту.
     */
    private val DAYLIGHT = 7..20

    private fun at(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
}
