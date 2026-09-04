package app.askya.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Место, выбранное руками.
 *
 * Появилось потому, что «последнее известное системе» бывает неизвестно: с
 * выключенной геолокацией телефон не знает, где он, и не узнает — а погода за
 * окном при этом есть. Выбранный город закрывает этот случай, не заставляя
 * включать приёмник ради строчки в меню.
 *
 * Хранятся имя и координаты, а не одно имя: имя нужно, чтобы подписать раздел,
 * координаты — чтобы спросить погоду. Спрашивать геокодер заново при каждом
 * обновлении значило бы ходить в сеть дважды там, где хватает одного раза.
 *
 * [key] — короткое имя места для хранилища: по нему у каждого города лежит своя
 * запомненная погода. Считается из координат, округлённых до сотни метров:
 * тысячная доля градуса — это примерно столько, а два города с одинаковыми
 * координатами до сотни метров — это один город, как бы он ни назывался.
 * Названием ключ быть не может: «Москва» бывает не одна, а переименованное
 * человеком место потеряло бы свою погоду.
 */
data class ChosenPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    val key: String
        get() = "${(latitude * 1000).roundToInt()}_${(longitude * 1000).roundToInt()}"
}

/** Как человек хочет видеть погоду. */
data class WeatherSettings(
    /** Показывать ли строку погоды в меню, рядом с именем приложения. */
    val inMenu: Boolean = true,
    /** Через сколько минут спрашивать заново. */
    val refreshMinutes: Int = 30,
    /** Спрашивать ли погоду вовсе. Выключено — приложение в сеть не выходит. */
    val enabled: Boolean = true,
    /**
     * Города, заведённые руками, — в том порядке, в каком их заводили.
     *
     * Список, а не одно место: у человека их обычно больше одного — где он
     * живёт, где работает, где родители, куда собрался на выходные. Раньше
     * место было одно, и посмотреть погоду в другом значило потерять первое:
     * выбрать заново, дождаться ответа, а потом проделать то же обратно.
     */
    val places: List<ChosenPlace> = emptyList(),
    /**
     * Чья погода показывается сейчас. `null` — место берётся у телефона, как и
     * прежде: это по-прежнему то, что происходит само, если ничего не выбирать.
     */
    val place: ChosenPlace? = null,
)

/** Погода, запомненная с прошлого раза. */
data class WeatherCache(
    val body: String,
    val place: String,
    val fetchedAt: Long,
)

private val Context.weatherStore: DataStore<Preferences> by preferencesDataStore(name = "weather")

/**
 * Хранилище погоды: настройки, список мест и последний ответ по каждому.
 *
 * Ответ хранится **как пришёл** — строкой json, а не разобранным. Так проще и
 * честнее: разобранное пришлось бы раскладывать по два десятка ключей и
 * собирать обратно, а разбор — дело дешёвое и повторяемое. Заодно запомненное
 * переживает изменение того, что именно Askya из ответа достаёт.
 *
 * Запомненное лежит **по месту**, а не одно на всех: переключение между
 * городами иначе стирало бы погоду того, откуда ушли, и возвращение к нему
 * стоило бы нового запроса. Города переключают именно затем, чтобы сравнить, —
 * то есть туда-сюда и подряд.
 *
 * Своё хранилище, как у читалки и у видео: погода переписывается каждые
 * полчаса, и складывать её в один файл с настройками приложения значило бы
 * переписывать и их.
 */
class WeatherPreferences(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: Flow<WeatherSettings> = context.weatherStore.data.map { it.toSettings() }

    /**
     * Запомненная погода этого места. [PHONE_PLACE] — того, что знает телефон.
     *
     * Ключ в подписи, а не внутри: спрашивают о ней тогда, когда уже решено,
     * чья погода нужна, и решает это не хранилище.
     */
    fun cache(key: String): Flow<WeatherCache?> = context.weatherStore.data.map { it.cacheOf(key) }

    fun setInMenu(value: Boolean) = put { it[KEY_IN_MENU] = value }

    fun setRefreshMinutes(value: Int) = put { it[KEY_REFRESH] = value.coerceIn(10, 180) }

    /**
     * Выключить погоду совсем.
     *
     * Не украшение настроек: выключенная погода — единственное состояние, в
     * котором Askya снова не выходит в сеть ни разу. Поэтому вместе с ней
     * стирается и запомненное — по всем местам сразу: «выключил» значит «и
     * забудь». Сами места остаются: это не погода, а список городов, и человек
     * его собирал.
     */
    fun setEnabled(value: Boolean) = put { preferences ->
        preferences[KEY_ENABLED] = value
        if (!value) preferences.forgetAll()
    }

    /**
     * Завести город и сразу на него переключиться.
     *
     * Заведённый второй раз не задваивается, а просто выбирается: человек ищет
     * «Казань», не помня, что она уже в списке, — и получить две Казани за это
     * не должен.
     */
    fun addPlace(place: ChosenPlace) = put { preferences ->
        val places = preferences.places()
        if (places.none { it.key == place.key }) {
            preferences[KEY_PLACES] = (places + place).writePlaces()
        }
        preferences[KEY_CHOSEN] = place.key
    }

    /**
     * Убрать город из списка — вместе с его запомненной погодой.
     *
     * Если убрали тот, на который смотрели, показывается следующий оставшийся,
     * а когда не осталось ни одного — телефон. Спрашивать «а что теперь
     * показывать» после удаления было бы вторым вопросом на одно действие.
     */
    fun removePlace(place: ChosenPlace) = put { preferences ->
        val left = preferences.places().filterNot { it.key == place.key }
        preferences[KEY_PLACES] = left.writePlaces()
        preferences.forget(place.key)
        if (preferences[KEY_CHOSEN] == place.key) {
            preferences[KEY_CHOSEN] = left.firstOrNull()?.key ?: PHONE_PLACE
        }
    }

    /**
     * Переключиться на другой город или обратно на телефон (`null`).
     *
     * Запомненное при этом не стирается — ни у того, откуда ушли, ни у того,
     * куда пришли: у каждого места своя память, и переключение ничего не
     * теряет. Раньше здесь стиралось всё, потому что память была одна на всех и
     * показать вчерашнюю казанскую погоду под подписью «Москва» было бы хуже,
     * чем не показать ничего.
     */
    fun choose(place: ChosenPlace?) = put { preferences ->
        preferences[KEY_CHOSEN] = place?.key ?: PHONE_PLACE
    }

    fun remember(key: String, body: String, place: String, at: Long) = put { preferences ->
        preferences[stringPreferencesKey("$BODY$key")] = body
        // Пустое имя места не затирает прежнее: система называет место не
        // всегда, и потерять название из-за одного молчаливого ответа обидно.
        if (place.isNotBlank()) preferences[stringPreferencesKey("$NAME$key")] = place
        preferences[longPreferencesKey("$AT$key")] = at
    }

    private fun put(edit: (MutablePreferences) -> Unit) {
        scope.launch { context.weatherStore.edit(edit) }
    }

    private fun Preferences.toSettings(): WeatherSettings {
        val places = places()
        val chosen = this[KEY_CHOSEN]
        return WeatherSettings(
            inMenu = this[KEY_IN_MENU] ?: true,
            refreshMinutes = this[KEY_REFRESH] ?: 30,
            enabled = this[KEY_ENABLED] ?: true,
            places = places,
            // Ключ мог остаться от удалённого города — тогда место считается
            // невыбранным. Пустой ключ и [PHONE_PLACE] значат одно и то же.
            place = places.firstOrNull { it.key == chosen },
        )
    }

    /**
     * Список мест из хранилища.
     *
     * Строкой, а не двумя десятками ключей: список правится целиком, читается
     * целиком и живёт целиком, а имён вида `place_3_name` в DataStore не
     * перечислить, не зная заранее, сколько их. Разделители выбраны из тех, что
     * в названиях городов не встречаются.
     *
     * Здесь же поднимается единственное место, выбранное до появления списка:
     * оно становится первым в нём. Иначе человек, у которого была выбрана
     * Казань, после обновления увидел бы погоду по телефону — то есть никакой.
     */
    private fun Preferences.places(): List<ChosenPlace> {
        val written = this[KEY_PLACES]
        if (written != null) return written.readPlaces()

        val name = this[KEY_OLD_NAME]
        val latitude = this[KEY_OLD_LAT]
        val longitude = this[KEY_OLD_LON]
        return if (!name.isNullOrBlank() && latitude != null && longitude != null) {
            listOf(ChosenPlace(name, latitude, longitude))
        } else {
            emptyList()
        }
    }

    private fun Preferences.cacheOf(key: String): WeatherCache? {
        val body = this[stringPreferencesKey("$BODY$key")] ?: return null
        return WeatherCache(
            body = body,
            place = this[stringPreferencesKey("$NAME$key")].orEmpty(),
            fetchedAt = this[longPreferencesKey("$AT$key")] ?: 0L,
        )
    }

    private fun MutablePreferences.forget(key: String) {
        remove(stringPreferencesKey("$BODY$key"))
        remove(stringPreferencesKey("$NAME$key"))
        remove(longPreferencesKey("$AT$key"))
    }

    /** Забыть погоду всех мест — по списку и телефона заодно. */
    private fun MutablePreferences.forgetAll() {
        (places().map { it.key } + PHONE_PLACE).forEach { forget(it) }
    }

    private companion object {
        const val BODY = "body_"
        const val NAME = "place_"
        const val AT = "at_"

        val KEY_IN_MENU = booleanPreferencesKey("in_menu")
        val KEY_REFRESH = intPreferencesKey("refresh_minutes")
        val KEY_ENABLED = booleanPreferencesKey("enabled")

        val KEY_PLACES = stringPreferencesKey("places")
        val KEY_CHOSEN = stringPreferencesKey("chosen_key")

        // Единственное место, каким оно хранилось до появления списка. Только
        // читается — и только один раз, пока список не записан поверх.
        val KEY_OLD_NAME = stringPreferencesKey("chosen_place")
        val KEY_OLD_LAT = doublePreferencesKey("chosen_latitude")
        val KEY_OLD_LON = doublePreferencesKey("chosen_longitude")
    }
}

/**
 * Место, известное телефону.
 *
 * Ключ у него тоже есть: погода лежит по местам, и той, что спрошена по
 * телефону, нужна своя полка — иначе она затирала бы погоду города.
 */
const val PHONE_PLACE = "phone"

/** Список мест строкой: по месту на строку, поля через вертикальную черту. */
internal fun List<ChosenPlace>.writePlaces(): String =
    joinToString("\n") { "${it.name}|${it.latitude}|${it.longitude}" }

/** Разбор обратно. Строка, которая не разобралась, пропускается молча. */
internal fun String.readPlaces(): List<ChosenPlace> = lineSequence()
    .mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 3) return@mapNotNull null
        val latitude = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        val longitude = parts[2].toDoubleOrNull() ?: return@mapNotNull null
        ChosenPlace(parts[0], latitude, longitude)
    }
    .toList()
