package app.askya.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import app.askya.app.appContainer
import app.askya.data.preferences.ChosenPlace
import app.askya.data.preferences.WeatherSettings
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogChoice
import app.askya.ui.components.DialogField
import app.askya.ui.components.DialogTitle
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.theme.Muted
import app.askya.weather.FoundPlace
import app.askya.weather.WeatherService
import kotlinx.coroutines.launch

/**
 * Окно «Места»: чьей погоды мы ждём и какие города вообще заведены.
 *
 * До него место было только одно — последнее, известное системе, — и это
 * работало ровно до первого телефона с выключенной геолокацией. Там погоды не
 * было никогда и взяться ей было неоткуда: точки нет, включать приёмник
 * человек не хочет, а погода за окном есть. Выбранный город закрывает этот
 * случай, ничего не включая.
 *
 * Городов теперь несколько: где живёшь, где работаешь, где родители, куда
 * собрался. Заведённые лежат списком, нажатие переключает, крестик убирает.
 * Переключаться между ними можно и не открывая это окно — строкой городов в
 * шапке раздела; сюда приходят, чтобы завести новый или убрать лишний.
 *
 * Город ищется по названию у геокодера Open-Meteo — того же источника, что и
 * сама погода, и так же без ключа. Спрашивается он ровно в этом окне: дальше в
 * настройках остаются широта с долготой, и погода ходит уже по ним.
 *
 * Найденное показывается списком, а не подставляется первым попавшимся:
 * Москва на свете не одна, и выбирать, которая из них, — не дело приложения.
 * Под каждым названием стоит страна с областью — иначе восемь одинаковых строк
 * неразличимы.
 *
 * Последней строкой стоит возврат к телефону: раз уж выбранный город отменяет
 * системное место, отменить сам выбор должно быть так же просто, как сделать.
 */
@Composable
fun PlaceDialog(onDismiss: () -> Unit) {
    val container = appContainer()
    val preferences = container.weatherPreferences
    val settings by remember(container) { preferences.settings }
        .collectAsStateWithLifecycle(initialValue = WeatherSettings())
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    // `null` — ещё не искали. Пустой список — искали и не нашли. Это разные
    // вещи, и говорятся они разными словами.
    var found by remember { mutableStateOf<List<FoundPlace>?>(null) }
    var trouble by remember { mutableStateOf<String?>(null) }

    fun search() {
        if (query.isBlank() || searching) return
        searching = true
        trouble = null
        scope.launch {
            val places = WeatherService.findPlaces(query)
            searching = false
            found = places.orEmpty()
            trouble = if (places == null) "Не спросилось: нет сети или сервис молчит" else null
        }
    }

    /** Завести найденный город. Окно закрывается: за этим и приходили. */
    fun add(place: FoundPlace) {
        preferences.addPlace(ChosenPlace(place.name, place.latitude, place.longitude))
        // Сразу за выбором — запрос: у нового города погоды ещё нет, и без
        // этого раздел остался бы пустым до следующего входа.
        container.weather.refresh()
        onDismiss()
    }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Place) }) {
        DialogTitle("Места")
        DialogCaption(
            when {
                settings.place != null -> "Сейчас показывается ${settings.place?.name}."
                settings.places.isNotEmpty() -> "Сейчас берётся у телефона."
                else -> "Сейчас берётся у телефона. Он своего места не знает — выберите город."
            },
        )

        // Заведённые города — первыми: чаще сюда приходят переключиться, а не
        // искать новый. Поиск ниже, под ними.
        if (settings.places.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                settings.places.forEach { place ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DialogChoice(
                            icon = Icons.Outlined.Place,
                            title = place.name,
                            about = coordinates(place),
                            picked = settings.place?.key == place.key,
                            onClick = {
                                preferences.choose(place)
                                container.weather.refresh()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        // Крестик убирает город из списка, а не закрывает окно:
                        // убрав лишний, обычно убирают и второй, и окно должно
                        // остаться открытым.
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { preferences.removePlace(place) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Убрать ${place.name}",
                                tint = Muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        DialogField(
            value = query,
            onValueChange = { query = it },
            hint = "Найти город",
            autoFocus = settings.places.isEmpty(),
            onDone = { search() },
            modifier = Modifier.padding(top = 14.dp),
        )

        val places = found
        when {
            searching -> Note("Ищем…")
            trouble != null -> Note(trouble.orEmpty())
            places != null && places.isEmpty() -> Note("Такого места не нашлось")
        }

        if (!places.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .padding(top = 8.dp)
                    .fadingVerticalScroll(),
            ) {
                places.forEach { place ->
                    DialogChoice(
                        icon = Icons.Outlined.Place,
                        title = place.name,
                        about = place.region,
                        onClick = { add(place) },
                    )
                }
            }
        }

        DialogChoice(
            icon = Icons.Outlined.MyLocation,
            title = "По телефону",
            about = "Место берётся у системы — как было",
            picked = settings.place == null,
            onClick = {
                preferences.choose(null)
                container.weather.refresh()
                onDismiss()
            },
            modifier = Modifier.padding(top = 8.dp),
        )

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Search,
                label = "Найти",
                accent = true,
                enabled = query.isNotBlank() && !searching,
                onClick = { search() },
            )
            Spacer(modifier = Modifier.width(16.dp))
            ActionButton(
                icon = Icons.Outlined.Close,
                label = "Закрыть",
                onClick = onDismiss,
            )
        }
    }
}

/**
 * Координаты города под его названием.
 *
 * Область и страна, по которым его выбирали, не хранятся: погоде они не нужны,
 * а держать их ради одной строчки значило бы завести ещё два поля в каждом
 * месте. Широта с долготой отвечают на тот же вопрос — которая это из
 * одинаково названных.
 */
private fun coordinates(place: ChosenPlace): String =
    "%.2f, %.2f".format(place.latitude, place.longitude)

/** Строчка о том, что сейчас происходит с поиском: ищем, не нашли, не вышло. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp),
    )
}
