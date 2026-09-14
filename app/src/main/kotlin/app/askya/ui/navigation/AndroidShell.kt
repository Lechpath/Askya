package app.askya.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.askya.app.AndroidContainer
import app.askya.app.androidContainer
import app.askya.data.preferences.WeatherSettings
import app.askya.ui.bridges.BridgesScreen
import app.askya.ui.echo.EchoMini
import app.askya.ui.echo.EchoScreen
import app.askya.ui.scroll.VoiceScreen
import app.askya.ui.scroll.imageedit.CollageScreen
import app.askya.ui.scroll.imageedit.ImageEditorScreen
import app.askya.ui.video.VideoScreen
import app.askya.ui.settings.AndroidDaySettings
import app.askya.ui.settings.AndroidSettingsGroups
import app.askya.ui.weather.PlaceDialog
import app.askya.ui.weather.WeatherScreen
import app.askya.weather.formatDegrees
import app.askya.weather.weatherMark

/**
 * Телефон — всё приложение: общие экраны из `shared` и то, что есть только
 * здесь, — Echo, AskyaV, «Голос», правка картинок, погода, мосты и играющая
 * поверх экранов заметка.
 */
@Composable
fun rememberAndroidShell(): PlatformShell {
    val container = androidContainer()

    // Погода обновляется вместе с открытием меню — не чаще, чем ей
    // положено: сам репозиторий не пойдёт в сеть, пока запомненное свежее.
    val weatherSettings by remember(container) { container.weatherPreferences.settings }
        .collectAsStateWithLifecycle(initialValue = WeatherSettings())
    val weatherNow by rememberUpdatedState(weatherSettings)

    // Окно городов из настроек — то же самое, что открывается значком места
    // в разделе погоды. Своего у настроек нет: два окна об одном разошлись бы.
    val choosingPlace = remember { mutableStateOf(false) }

    return remember(container) {
        PlatformShell(
            sections = Destination.entries,
            playing = { container.echoPlayer.state.collectAsStateWithLifecycle().value.playing },
            onPlay = { container.echoPlayer.resume() },
            weatherLine = { weatherLineOf(container) },
            refreshWeather = {
                if (weatherNow.enabled && weatherNow.inMenu) container.weather.refresh()
            },
            voice = true,
            voicePlayer = VoicePlayer(
                sounding = {
                    val aside by container.echoAside.state.collectAsStateWithLifecycle()
                    aside?.noteId to (aside?.playing == true)
                },
                play = { note -> container.echoAside.play(note) },
            ),
            imageEditing = true,
            bridges = true,
            splash = true,
            settingsDay = { general -> AndroidDaySettings(general) },
            settingsGroups = { general, nav ->
                AndroidSettingsGroups(
                    general = general,
                    onOpenBridges = { nav.controller.navigate(Routes.BRIDGES) },
                    onChoosePlace = { choosingPlace.value = true },
                )
            },
            settingsOverlay = {
                if (choosingPlace.value) PlaceDialog(onDismiss = { choosingPlace.value = false })
            },
            routes = { nav -> androidRoutes(nav) },
            overlay = { EchoOverlay(container) },
        )
    }
}

/**
 * Погода для строки в шапке меню. Спрашивается не на запуске, а при первом
 * открытии меню: человеку, который весь день не открывал меню, погода не
 * понадобилась ни разу — и в сеть за ней ходить незачем.
 */
@Composable
private fun weatherLineOf(container: AndroidContainer): WeatherLine? {
    val weatherState by container.weather.state.collectAsStateWithLifecycle()
    val weatherSettings by remember(container) { container.weatherPreferences.settings }
        .collectAsStateWithLifecycle(initialValue = WeatherSettings())
    return when {
        !weatherSettings.inMenu || !weatherSettings.enabled -> null
        else -> weatherState.forecast?.let {
            WeatherLine(weatherMark(it.now.code, it.now.day), formatDegrees(it.now.temperature))
        }
        // Погоды ещё нет — но строка нужна: она единственный вход в раздел, а
        // доступ к месту спрашивается уже внутри него.
            ?: WeatherLine(mark = "", degrees = "погода", known = false)
    }
}

/**
 * Голосовая заметка, играющая поверх экрана, — одна на всё приложение: её
 * включают на одном экране, а слушают, уже уйдя на другой. Кончилась —
 * карточка ушла сама (см. EchoAside).
 */
@Composable
private fun EchoOverlay(container: AndroidContainer) {
    val aside by container.echoAside.state.collectAsStateWithLifecycle()
    EchoMini(
        aside = aside,
        position = container.echoAside::position,
        onToggle = container.echoAside::toggle,
        onClose = container.echoAside::close,
    )
}

/** Маршруты, которых нет у Windows-версии. */
private fun NavGraphBuilder.androidRoutes(nav: ShellNav) {
    composable(Routes.VOICE) {
        VoiceScreen(
            onBack = { nav.controller.popBackStack() },
            sayNow = nav.sayNow(),
            onSaid = nav.onSaid,
        )
    }

    composable(
        route = Routes.IMAGE_EDIT,
        arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
    ) { entry ->
        ImageEditorScreen(
            noteId = entry.arguments?.getLong("noteId") ?: Routes.NEW,
            onBack = { nav.controller.popBackStack() },
        )
    }

    composable(Routes.COLLAGE) {
        CollageScreen(
            onBack = { nav.controller.popBackStack() },
            // Готовый коллаж сразу открывается правкой: обычно его
            // тут же и подписывают.
            onCreated = { id ->
                nav.controller.popBackStack()
                nav.controller.navigate(Routes.imageEdit(id))
            },
        )
    }

    composable(Destination.ECHO.route) {
        EchoScreen(
            // Из Echo выходят в день, а не в список разделов:
            // музыку включают, занимаясь чем-то ещё, и после
            // плеера нужен день, а не вопрос «куда теперь».
            //
            // Уходя, раздел снимается со стопки — а не заслоняется
            // днём: см. рассуждение ниже, там же и про петлю.
            onLeave = {
                // «Назад до дня», а не переход поверх него.
                //
                // Прежде здесь стоял обычный переход в день с
                // `popUpTo(день) { saveState }` и `restoreState`, и
                // он был холостым: одно и то же место сперва
                // снимало Echo со стопки «на память», а потом
                // тут же возвращало его оттуда обратно. Стопка до
                // перехода и после совпадала до строчки —
                // `today | practices` в обе стороны, — и человек
                // видел ровно то, о чём говорил: закрываешь
                // раздел, он закрывается и открывается снова.
                //
                // Сохранять и восстанавливать под одним и тем же
                // днём нельзя вообще: это две половины одного
                // действия, и вместе они дают ноль. Поэтому здесь
                // не переход, а возврат — стопка просто снимается
                // до дня, и раздела над ним не остаётся.
                //
                // Плата — состояние страницы Echo не запоминается.
                // Терять там нечего: плеер живёт в контейнере и
                // играет дальше, а церемония входа и должна
                // играться заново на каждый заход (см. `opening` в
                // EchoScreen).
                val backToDay = nav.controller.popBackStack(
                    route = Destination.TODAY.route,
                    inclusive = false,
                )
                if (!backToDay) {
                    // Дня под плеером нет — Echo стоит стартовым
                    // разделом. Тогда стопка сносится целиком и
                    // день встаёт на её место: оставить закрытый
                    // раздел под днём значит вернуть ту же петлю,
                    // только через «назад».
                    nav.controller.navigate(Destination.TODAY.route) {
                        popUpTo(nav.controller.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            },
        )
    }

    composable(Destination.VIDEO.route) {
        VideoScreen(onOpenMenu = nav.openDrawer)
    }

    composable(Routes.WEATHER) {
        WeatherScreen(onBack = { nav.controller.popBackStack() })
    }

    composable(Routes.BRIDGES) {
        BridgesScreen(onBack = { nav.controller.popBackStack() })
    }
}
