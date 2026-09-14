package app.askya.ui.weather

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.androidContainer
import app.askya.data.preferences.ChosenPlace
import app.askya.data.preferences.WeatherSettings
import app.askya.ui.components.EmptyState
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.MONTHS
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.cardEdge
import app.askya.weather.WeatherDay
import app.askya.weather.WeatherHour
import app.askya.weather.formatDegrees
import app.askya.weather.weatherMark
import app.askya.weather.weatherWords
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Погода там, где человек находится.
 *
 * Единственный раздел, ради которого приложение выходит в сеть, — и устроен он
 * так, чтобы этого выхода было как можно меньше: спрашивает не чаще раза в
 * полчаса, показывает запомненное сразу и живёт без сети, честно подписывая,
 * когда спрашивал в последний раз.
 *
 * Порядок сверху вниз — от «что сейчас» к «что дальше»: сейчас крупно,
 * ближайшие сутки строкой поперёк, неделя столбиком. Открывший погоду хочет
 * знать, брать ли зонт, а не изучать метеосводку.
 *
 * Разрешение спрашивается здесь, а не на первом запуске, — как и всякое
 * другое в Askya: доступ к месту нужен погоде и только ей.
 *
 * И оно же не единственный путь: значком места в шапке города заводятся
 * руками ([PlaceDialog]). Телефон с выключенной геолокацией своего места не
 * знает и с разрешением — там выбранный город не запасной ход, а
 * единственный.
 *
 * Заведённых городов бывает несколько, и между ними переключаются строкой под
 * шапкой — не открывая окна: смотреть погоду в двух местах подряд человек
 * приходит чаще, чем заводить третье.
 */
@Composable
fun WeatherScreen(onBack: () -> Unit) {
    val container = androidContainer()
    val repository = container.weather
    val state by repository.state.collectAsStateWithLifecycle()
    val settings by remember(container) { container.weatherPreferences.settings }
        .collectAsStateWithLifecycle(initialValue = WeatherSettings())

    var choosingPlace by remember { mutableStateOf(false) }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) repository.refresh(force = true)
        // Отказ не тупик: город выбирается руками, и окно выбора — то же
        // самое, что и под значком места в шапке.
        else choosingPlace = true
    }

    // Ключ — выбранный город: он меняет, у кого спрашивать место, и после
    // переключения раздел должен показать погоду нового, а не ждать
    // следующего входа.
    LaunchedEffect(settings.place) {
        when {
            settings.place != null -> repository.refresh()
            repository.located() -> repository.refresh()
            else -> ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val forecast = state.forecast

    // Окно выбора стоит после раздела и внутри общего Box: оно рисуется
    // поверх, а не под. Написанное раньше по порядку уходит вниз стопки —
    // раскрытая карточка оказывалась под погодой, и нажать её было нечем.
    Box(modifier = Modifier.fillMaxSize()) {

    ScreenScaffold(
        title = "Погода",
        onNavigationClick = onBack,
        navigationIsBack = true,
        actions = {
            // Место стоит перед «обновить»: сперва решают, чья погода, и
            // только потом — насколько она свежая.
            HeaderIcon(
                icon = Icons.Outlined.Place,
                contentDescription = "Места",
                onClick = { choosingPlace = true },
            )
            HeaderIcon(
                icon = Icons.Outlined.Refresh,
                contentDescription = "Обновить",
                onClick = { repository.refresh(force = true) },
            )
        },
    ) {
        if (forecast == null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Places(
                    places = settings.places,
                    chosen = settings.place,
                    onChoose = { place ->
                        container.weatherPreferences.choose(place)
                        repository.refresh()
                    },
                )
                EmptyState(
                    title = if (state.loading) "Спрашиваем погоду" else "Погоды пока нет",
                    hint = state.trouble ?: "Сейчас узнаем, что на улице.",
                    actionLabel = if (state.loading) null else "Выбрать город",
                    onAction = { choosingPlace = true },
                )
            }
            return@ScreenScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fadingVerticalScroll(),
        ) {
            Places(
                places = settings.places,
                chosen = settings.place,
                onChoose = { place ->
                    container.weatherPreferences.choose(place)
                    repository.refresh()
                },
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Now(forecast)

                if (forecast.hours.isNotEmpty()) {
                    Section("Ближайшие сутки")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        forecast.hours.forEach { hour -> Hour(hour) }
                    }
                }

                if (forecast.days.isNotEmpty()) {
                    Section("Неделя")
                    forecast.days.forEach { day -> Day(day) }
                }

                // Возраст сведений — не мелочь и не отговорка: без сети раздел
                // показывает вчерашнее, и человек должен знать об этом сам, а не
                // догадываться по несовпадению с окном.
                Text(
                    text = ageWords(forecast.fetchedAt) +
                        if (state.trouble != null) " · ${state.trouble}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (choosingPlace) PlaceDialog(onDismiss = { choosingPlace = false })
    }
}

/**
 * Строка городов: по кому смотрим погоду.
 *
 * Показывается, только когда города заведены: одному человеку хватает
 * телефона, и пустая строка над погодой была бы полкой без книг. Первым
 * стоит сам телефон — он и есть то, что происходит без всякого выбора.
 *
 * Словами в рамке, теми же, что и всюду в приложении, а не вкладками: города
 * заводят и убирают, вкладки же обещают неизменный набор.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Places(
    places: List<ChosenPlace>,
    chosen: ChosenPlace?,
    onChoose: (ChosenPlace?) -> Unit,
) {
    if (places.isEmpty()) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlaceWord(name = "По телефону", picked = chosen == null, onClick = { onChoose(null) })
        places.forEach { place ->
            PlaceWord(
                name = place.name,
                picked = chosen?.key == place.key,
                onClick = { onChoose(place) },
            )
        }
    }
}

@Composable
private fun PlaceWord(name: String, picked: Boolean, onClick: () -> Unit) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = if (picked) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (picked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Now(forecast: app.askya.weather.Forecast) {
    val now = forecast.now
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (forecast.place.isNotBlank()) {
            Text(
                text = forecast.place,
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                color = AccentInk,
            )
        }

        Text(
            text = weatherMark(now.code, now.day),
            fontSize = 52.sp,
            modifier = Modifier.padding(top = 6.dp),
        )

        Text(
            text = formatDegrees(now.temperature),
            fontFamily = FontFamily.Serif,
            fontSize = 56.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = weatherWords(now.code),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Fact("ощущается", formatDegrees(now.feelsLike))
            Fact("ветер", "${now.windKmh} км/ч")
            Fact("влажность", "${now.humidity} %")
        }
    }
}

@Composable
private fun Fact(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Hour(hour: WeatherHour) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "%02d".format(hour.at.hour),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = weatherMark(hour.code, hour.at.hour in 6..20), fontSize = 20.sp)
        Text(
            text = formatDegrees(hour.temperature),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        // Проценты осадков показываются только когда о них есть что сказать:
        // ноль под каждым часом превращает строку в частокол нулей.
        if (hour.rainChance >= RAIN_WORTH_SAYING) {
            Text(
                text = "${hour.rainChance}%",
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
            )
        }
    }
}

@Composable
private fun Day(day: WeatherDay) {
    val today = LocalDate.now()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .cardEdge(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dayWords(day.date, today),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(96.dp),
        )
        Text(text = weatherMark(day.code), fontSize = 20.sp)
        Text(
            text = if (day.rainChance >= RAIN_WORTH_SAYING) "${day.rainChance}%" else "",
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
            modifier = Modifier.width(48.dp).padding(start = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${formatDegrees(day.min)} … ${formatDegrees(day.max)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        fontFamily = FontFamily.Serif,
        fontSize = 20.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
    )
}

/** «Сегодня», «Завтра», дальше — «14 августа». */
private fun dayWords(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Сегодня"
    today.plusDays(1) -> "Завтра"
    else -> "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
}

/** «Только что», «12 минут назад», «вчера в 21:40». */
private fun ageWords(at: Long): String {
    if (at <= 0) return ""
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 2 -> "Спрошено только что"
        minutes < 60 -> "Спрошено $minutes мин назад"
        minutes < 60 * 24 -> "Спрошено ${minutes / 60} ч назад"
        else -> {
            val moment = LocalDateTime.ofEpochSecond(at / 1000, 0, java.time.OffsetDateTime.now().offset)
            "Спрошено ${moment.dayOfMonth} ${MONTHS[moment.monthValue - 1]}"
        }
    }
}

/**
 * Ниже этого порога вероятность осадков не пишется.
 *
 * Двадцать процентов — это «скорее нет»: строка «10%» под каждым часом не
 * добавляет знания, а отнимает место у того, что действительно стоит увидеть.
 */
private const val RAIN_WORTH_SAYING = 20
