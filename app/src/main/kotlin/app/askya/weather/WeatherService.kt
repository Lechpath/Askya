package app.askya.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Погода из Open-Meteo.
 *
 * ## Почему именно этот источник
 *
 * Askya не выходила в сеть вовсе, и появление здесь запроса наружу — событие,
 * которое стоит объяснить. Погода без сети невозможна: её неоткуда взять,
 * кроме как спросить.
 *
 * Из всех, кого можно спросить, выбран Open-Meteo, потому что он единственный
 * не требует ничего в обмен: ни ключа, ни аккаунта, ни согласия на что-либо.
 * Запрос за погодой содержит ровно две вещи — широту и долготу; ни имени, ни
 * телефона, ни чего бы то ни было, по чему запрос можно связать с человеком, в
 * нём нет. Обратно приходит json с числами.
 *
 * Адресов у сервиса два, и второй — геокодер ([findPlaces]): им человек
 * выбирает город руками, когда телефон своего места не знает. Спрашивается он
 * по названию, которое набрали сами, и ровно в тот раз, когда город выбирают.
 *
 * Координаты округляются до двух знаков — это примерно километр. Погода на
 * километре одна и та же, а вот адрес по таким координатам уже не восстановить.
 *
 * ## Что спрашивается
 *
 * Всё за один запрос: сейчас, двое суток по часам и неделя по дням. Спрашивать
 * по частям значило бы ходить в сеть трижды ради одного экрана.
 *
 * Разбирается системным `org.json`: он входит в android.jar, и тащить ради
 * трёх десятков чисел разборщик со стороны незачем. Свой, как для fb2, тут не
 * нужен — json устроен строго, и терпимость, ради которой писан разбор
 * разметки, здесь ни к чему.
 */
object WeatherService {

    /**
     * Спросить погоду и отдать ответ как есть.
     *
     * Строкой, а не разобранным: разбирать её будет тот, кто спрашивал, а
     * запомнить надо именно ответ — см. [app.askya.data.preferences.WeatherPreferences].
     *
     * `null` означает «не вышло»: нет сети, нет ответа, ответ не тот. Что
     * именно случилось, разделу неважно — показывать он будет то, что запомнил
     * в прошлый раз.
     */
    suspend fun fetchBody(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            runCatching { request(url(latitude, longitude)) }.getOrNull()
        }

    /**
     * Найти место по названию.
     *
     * Второй и последний адрес, куда Askya ходит, — геокодер того же
     * Open-Meteo: без ключа, без аккаунта, без согласия. Уходит в нём одно
     * слово — то, что человек набрал сам; ни координат телефона, ни чего-либо
     * о нём в запросе нет.
     *
     * Нужен он ровно на один раз. Выбранное место остаётся в настройках
     * широтой и долготой, и дальше погода спрашивается по ним — геокодер
     * больше не тревожится, даже когда раздел открывают каждый день.
     *
     * `null` означает «не вышло»: нет сети, нет ответа, ответ не тот. Пустой
     * список — «такого места не нашлось»: это разные ответы, и окно говорит
     * их разными словами.
     */
    suspend fun findPlaces(query: String): List<FoundPlace>? = withContext(Dispatchers.IO) {
        val name = query.trim()
        if (name.isEmpty()) return@withContext emptyList()
        runCatching { parsePlaces(request(placesUrl(name))) }.getOrNull()
    }

    private fun placesUrl(name: String): String {
        val asked = URLEncoder.encode(name, "UTF-8")
        // `language=ru` — чтобы города возвращались теми же именами, какими их
        // набирают: «Казань», а не «Kazan». Восемь строк — потолок не от
        // жадности сервиса, а от окна: длиннее список не читают, а листают.
        return "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=$asked&count=8&language=ru&format=json"
    }

    /**
     * Разбор ответа геокодера.
     *
     * Место без координат отбрасывается молча: строка, по которой нельзя
     * спросить погоду, в списке только мешает.
     */
    internal fun parsePlaces(body: String): List<FoundPlace> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val places = ArrayList<FoundPlace>(results.length())
        for (index in 0 until results.length()) {
            val found = results.optJSONObject(index) ?: continue
            val name = found.optString("name").orEmpty()
            if (name.isBlank()) continue
            if (!found.has("latitude") || !found.has("longitude")) continue
            places += FoundPlace(
                name = name,
                // Страна и область — то, чем одна Москва отличается от другой.
                // Пустые части выкидываются, чтобы не оставалось «, » в никуда.
                region = listOf(
                    found.optString("country").orEmpty(),
                    found.optString("admin1").orEmpty(),
                ).filter { it.isNotBlank() }.distinct().joinToString(", "),
                latitude = found.getDouble("latitude"),
                longitude = found.getDouble("longitude"),
            )
        }
        return places
    }

    private fun url(latitude: Double, longitude: Double): String {
        // Округление до сотой доли градуса — около километра. Погода на нём
        // одна, а точное место по такой ссылке уже не найти.
        val lat = "%.2f".format(java.util.Locale.US, latitude)
        val lon = "%.2f".format(java.util.Locale.US, longitude)
        return "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
            "wind_speed_10m,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min," +
            "precipitation_probability_max,sunrise,sunset" +
            "&timezone=auto&forecast_days=7&forecast_hours=48"
    }

    private fun request(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // Своего имени в запрос не ставится: чем меньше о телефоне сказано,
            // тем меньше о нём известно.
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) error("ответ ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /**
     * Разбор ответа.
     *
     * Прошедшие часы отбрасываются: прогноз приходит с начала суток, и без
     * этого раздел показывал бы «на ближайшие часы» то, что уже случилось.
     */
    internal fun parse(body: String, at: LocalDateTime = LocalDateTime.now()): Forecast {
        val json = JSONObject(body)

        val current = json.getJSONObject("current")
        val now = WeatherNow(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            feelsLike = current.optDouble("apparent_temperature", current.getDouble("temperature_2m")).roundToInt(),
            code = current.optInt("weather_code", 0),
            day = current.optInt("is_day", 1) == 1,
            windKmh = current.optDouble("wind_speed_10m", 0.0).roundToInt(),
            humidity = current.optInt("relative_humidity_2m", 0),
        )

        val hourly = json.optJSONObject("hourly")
        val hours = ArrayList<WeatherHour>()
        if (hourly != null) {
            val times = hourly.getJSONArray("time")
            val temperatures = hourly.getJSONArray("temperature_2m")
            val codes = hourly.optJSONArray("weather_code")
            val rain = hourly.optJSONArray("precipitation_probability")
            for (index in 0 until times.length()) {
                val moment = runCatching { LocalDateTime.parse(times.getString(index)) }.getOrNull()
                    ?: continue
                if (moment.isBefore(at.withMinute(0).withSecond(0).withNano(0))) continue
                hours += WeatherHour(
                    at = moment,
                    temperature = temperatures.optDouble(index, 0.0).roundToInt(),
                    code = codes?.optInt(index, 0) ?: 0,
                    rainChance = rain?.optInt(index, 0) ?: 0,
                )
            }
        }

        val daily = json.optJSONObject("daily")
        val days = ArrayList<WeatherDay>()
        if (daily != null) {
            val dates = daily.getJSONArray("time")
            val highs = daily.getJSONArray("temperature_2m_max")
            val lows = daily.getJSONArray("temperature_2m_min")
            val codes = daily.optJSONArray("weather_code")
            val rain = daily.optJSONArray("precipitation_probability_max")
            val sunrise = daily.optJSONArray("sunrise")
            val sunset = daily.optJSONArray("sunset")
            for (index in 0 until dates.length()) {
                val date = runCatching { LocalDate.parse(dates.getString(index)) }.getOrNull() ?: continue
                days += WeatherDay(
                    date = date,
                    min = lows.optDouble(index, 0.0).roundToInt(),
                    max = highs.optDouble(index, 0.0).roundToInt(),
                    code = codes?.optInt(index, 0) ?: 0,
                    rainChance = rain?.optInt(index, 0) ?: 0,
                    // Восход приходит целой отметкой времени; в разделе нужен
                    // один час с минутами.
                    sunrise = sunrise?.optString(index).orEmpty().substringAfter('T', ""),
                    sunset = sunset?.optString(index).orEmpty().substringAfter('T', ""),
                )
            }
        }

        return Forecast(
            place = "",
            now = now,
            hours = hours.take(24),
            days = days,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** Погода — не то, ради чего стоит держать экран в ожидании. */
    private const val TIMEOUT_MS = 10_000
}
