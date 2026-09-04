package app.askya.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.askya.app.OPEN_TODAY
import app.askya.app.OPEN_VOICE
import app.askya.app.OPEN_WEATHER
import app.askya.app.appContainer
import app.askya.ui.askyaday.AskyaDayScreen
import app.askya.ui.components.QuickNoteCard
import app.askya.ui.noteedit.NoteEditScreen
import app.askya.ui.scroll.ImageCardScreen
import app.askya.ui.scroll.BookScreen
import app.askya.ui.scroll.ImagesScreen
import app.askya.ui.scroll.LibraryScreen
import app.askya.ui.scroll.ScrollScreen
import app.askya.ui.scroll.ScrollViewerScreen
import app.askya.ui.scroll.VoiceScreen
import app.askya.ui.scroll.imageedit.CollageScreen
import app.askya.ui.scroll.imageedit.ImageEditorScreen
import app.askya.ui.echo.EchoMini
import app.askya.ui.echo.EchoScreen
import app.askya.ui.ledger.LedgerScreen
import app.askya.ui.video.VideoScreen
import app.askya.ui.components.UndoBar
import app.askya.domain.model.DeedLink
import app.askya.ui.weather.WeatherScreen
import app.askya.data.preferences.WeatherSettings
import app.askya.weather.formatDegrees
import app.askya.weather.weatherMark
import app.askya.ui.askyaday.LivedScreen
import app.askya.ui.bridges.BridgesScreen
import app.askya.ui.reminders.RemindersScreen
import app.askya.ui.routine.RoutineScreen
import app.askya.ui.settings.SettingsScreen
import app.askya.ui.yet.YetListScreen
import app.askya.ui.yet.YetScreen
import kotlinx.coroutines.launch

/**
 * Всё приложение: меню, разделы и то, что раскрывается поверх них.
 *
 * [openRoute] — раздел, в который просят открыться снаружи (виджеты погоды и
 * голоса на рабочем столе). Не стартовый раздел, а переход: приложение
 * открывается тем же, чем всегда, и тут же уходит туда, куда позвали, — тогда
 * «назад» возвращает в день, а не выбрасывает из приложения. [onOpened]
 * говорит, что просьба исполнена: второй раз по ней ходить не нужно.
 *
 * [saying] — просят не только открыть «Голос», но и сразу начать запись:
 * кружок виджета. Хранится оно дальше своего маршрута, потому что экран, где
 * запись начнётся, соберётся уже после перехода.
 */
@Composable
fun AskyaApp(
    navController: NavHostController = rememberNavController(),
    openRoute: String? = null,
    saying: Boolean = false,
    /** Дело, которое просят раскрыть, — из шторки со списком (`OPEN_DEED`). */
    openDeed: Long? = null,
    onOpened: () -> Unit = {},
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val container = appContainer()
    val recents by remember(container) { container.noteRepository.notes() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val player = container.echoPlayer
    val echo by player.state.collectAsStateWithLifecycle()

    // Погода для строки в шапке меню. Спрашивается не на запуске, а при первом
    // открытии меню: человеку, который весь день не открывал меню, погода не
    // понадобилась ни разу — и в сеть за ней ходить незачем.
    val weather = container.weather
    val weatherState by weather.state.collectAsStateWithLifecycle()
    val weatherSettings by remember(container) { container.weatherPreferences.settings }
        .collectAsStateWithLifecycle(initialValue = WeatherSettings())
    val weatherLine = when {
        !weatherSettings.inMenu || !weatherSettings.enabled -> null
        else -> weatherState.forecast?.let {
            WeatherLine(weatherMark(it.now.code, it.now.day), formatDegrees(it.now.temperature))
        }
        // Погоды ещё нет — но строка нужна: она единственный вход в раздел, а
        // доступ к месту спрашивается уже внутри него.
            ?: WeatherLine(mark = "", degrees = "погода", known = false)
    }

    // Быстрая заметка живёт поверх всего приложения, а не внутри экрана: её
    // открывают из меню, и к какому экрану меню было открыто — неважно.
    var quickNote by remember { mutableStateOf(false) }

    // Позвали снаружи — уходим туда, откуда позвали. Раздел здесь пока один,
    // и разбор его в одну строку: список маршрутов «для внешнего мира» из
    // одного значения был бы списком ради списка.
    // Просьба «начни писать» переживает [onOpened]: маршрут исполняется сразу,
    // а экран, которому эта просьба адресована, соберётся следующим кадром.
    var sayNow by remember { mutableStateOf(false) }

    // Какое дело просят раскрыть. Переживает [onOpened] по той же причине, что
    // и просьба «начни писать»: маршрут исполняется сразу, а экран, которому
    // просьба адресована, соберётся следующим кадром.
    var openingDeed by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(openRoute, openDeed) {
        when (openRoute) {
            OPEN_WEATHER -> navController.navigate(Routes.WEATHER)
            OPEN_VOICE -> {
                sayNow = saying
                navController.navigate(Routes.VOICE)
            }

            // День — начало навигации и всегда лежит в её низу, поэтому
            // возврат к нему это шаг назад, а не новый переход: `navigate`
            // положил бы второй день поверх раздела, из которого пришли.
            OPEN_TODAY -> {
                openingDeed = openDeed
                navController.popBackStack(Destination.TODAY.route, false)
            }

            else -> return@LaunchedEffect
        }
        onOpened()
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = {
        // Погода обновляется вместе с открытием меню — не чаще, чем ей
        // положено: сам репозиторий не пойдёт в сеть, пока запомненное свежее.
        if (weatherSettings.enabled && weatherSettings.inMenu) weather.refresh()
        scope.launch { drawerState.open() }
    }
    fun closeDrawer() = scope.launch { drawerState.close() }

    val inSection = Destination.entries.any { it.route == currentRoute }
    // На AskyaDay горизонтальный свайп листает дни, и закрытое меню его
    // забирало бы себе. Но пока меню открыто, жесты нужны всегда: в Material 3
    // этот же флаг гасит закрытие по тапу на затемнение, и без него открытое
    // меню невозможно закрыть ничем, кроме свайпа.
    val drawerGestures = drawerState.isOpen ||
        (inSection && currentRoute != Destination.TODAY.route)
    val openReminders: () -> Unit = { navController.navigate(Routes.REMINDERS) }

    /**
     * Уйти в раздел — тем же переходом, каким его открывает меню.
     *
     * Раздел не кладётся поверх раздела: он встаёт на своё место в списке, а
     * над днём его остаётся ровно один. Иначе экраны копятся стопкой, и
     * «назад» из раздела возвращает в него же — см. [openLink].
     */
    val openSection: (String) -> Unit = { route ->
        if (route != currentRoute) {
            navController.navigate(route) {
                popUpTo(Destination.TODAY.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    /**
     * Перейти по привязке дела — «чем оно делается».
     *
     * Разбор здесь, а не в экране дня: экран знает про дело, а куда ведёт
     * `book:12`, знает навигация. Неизвестный вид и стёртая запись молча
     * никуда не ведут — падать или открывать пустой экран из-за строки,
     * записанной другой версией, незачем.
     *
     * ## Раздел открывается как раздел, а не как страница поверх дня
     *
     * Привязка ведёт двумя разными способами, и это не придирка. Книга,
     * запись и список — это **страницы**: они кладутся поверх того, откуда
     * позвали, и «назад» с них возвращает туда же. AskyaEcho и AskyaV — это
     * **разделы**: у них своё место в меню, и открываться они должны ровно
     * так же, как из меню.
     *
     * Раньше и то и другое шло простым `navigate`, и раздел ложился поверх
     * дня. Из этого выходила петля: дело вело в Echo, Echo выходил в день
     * новым переходом — а закрытый раздел так и оставался под ним, и первое
     * же «назад» открывало его снова. И так без конца.
     */
    val openLink: (String) -> Unit = { raw ->
        val link = DeedLink.of(raw)
        if (link != null) {
            scope.launch {
                val note = if (needsNote(link)) container.noteRepository.get(link.id) else null
                routeOf(link, note)?.let { route ->
                    if (Destination.entries.any { it.route == route }) {
                        openSection(route)
                    } else {
                        navController.navigate(route)
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // На детальных экранах свайп от края отдан их содержимому, а не меню.
        gesturesEnabled = drawerGestures,
        drawerContent = {
            AppDrawer(
                currentRoute = currentRoute,
                recents = recents.take(8),
                playing = echo.playing,
                onSelect = { route ->
                    closeDrawer()
                    openSection(route)
                },
                onOpenNote = { id ->
                    closeDrawer()
                    navController.navigate(Routes.noteEdit(id))
                },
                onQuickNote = {
                    closeDrawer()
                    quickNote = true
                },
                // Нечего продолжать — значит, музыку ещё не выбирали: тогда
                // кнопка честно отправляет туда, где её выбирают.
                weather = weatherLine,
                onWeather = {
                    closeDrawer()
                    navController.navigate(Routes.WEATHER)
                },
                onPlay = {
                    if (!player.resume()) {
                        closeDrawer()
                        openSection(Destination.ECHO.route)
                    }
                },
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Стартовый раздел — из настроек, и решается один раз за жизнь
            // экрана: менять его на ходу значило бы перестраивать навигацию
            // под человеком. Неизвестный маршрут (настройка из другой версии)
            // сводится к дню, а не роняет приложение.
            val start = remember(container) {
                val saved = container.settings.state.value.startRoute
                Destination.entries.firstOrNull { it.route == saved }?.route
                    ?: Destination.TODAY.route
            }

            NavHost(
                navController = navController,
                startDestination = start,
            ) {
                composable(Destination.TODAY.route) {
                    AskyaDayScreen(
                        onOpenMenu = openDrawer,
                        onOpenReminders = openReminders,
                        onOpenTasks = { navController.navigate(Routes.TASKS) },
                        onOpenLived = { navController.navigate(Routes.LIVED) },
                        onOpenLink = openLink,
                        openDeed = openingDeed,
                        onDeedOpened = { openingDeed = null },
                    )
                }
                composable(Destination.NOTES.route) {
                    ScrollScreen(
                        onOpenMenu = openDrawer,
                        onOpenImages = { navController.navigate(Routes.IMAGES) },
                        onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                        onOpenLists = { navController.navigate(Routes.LISTS) },
                        onOpenVoice = { navController.navigate(Routes.VOICE) },
                        // Из ленты открывают не только раздел, но и то, что в
                        // нём лежит: карточки в ней не подпись «6 записей», а
                        // сами записи, и тап по записи должен вести к ней.
                        onOpenBook = { id -> navController.navigate(Routes.topic(id)) },
                        onOpenNote = { id -> navController.navigate(Routes.noteEdit(id)) },
                        onViewFile = { id -> navController.navigate(Routes.view(id)) },
                        // Картинка листается вместе с соседними — тем же
                        // срезом, что был под пальцем в ленте: всей галереей.
                        onViewImage = { id -> navController.navigate(Routes.gallery(id, null)) },
                        onOpenList = { id -> navController.navigate(Routes.yetList(id)) },
                    )
                }

                composable(Routes.VOICE) {
                    VoiceScreen(
                        onBack = { navController.popBackStack() },
                        sayNow = sayNow,
                        onSaid = { sayNow = false },
                    )
                }

                composable(Routes.IMAGES) {
                    ImagesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCard = { id -> navController.navigate(Routes.imageCard(id)) },
                        // Из раздела картинку открывают с листанием соседних.
                        onViewImage = { id -> navController.navigate(Routes.gallery(id, null)) },
                        onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
                        onCollage = { navController.navigate(Routes.COLLAGE) },
                    )
                }

                composable(
                    route = Routes.ALBUM,
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
                ) { entry ->
                    val albumId = entry.arguments?.getLong("albumId")
                    ImagesScreen(
                        albumId = albumId,
                        onBack = { navController.popBackStack() },
                        onOpenCard = { id -> navController.navigate(Routes.imageCard(id)) },
                        // Листается тот же альбом, а не все картинки подряд.
                        onViewImage = { id -> navController.navigate(Routes.gallery(id, albumId)) },
                    )
                }

                composable(
                    route = Routes.IMAGE_CARD,
                    arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
                ) { entry ->
                    ImageCardScreen(
                        noteId = entry.arguments?.getLong("noteId") ?: Routes.NEW,
                        onDone = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Routes.IMAGE_EDIT,
                    arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
                ) { entry ->
                    ImageEditorScreen(
                        noteId = entry.arguments?.getLong("noteId") ?: Routes.NEW,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.COLLAGE) {
                    CollageScreen(
                        onBack = { navController.popBackStack() },
                        // Готовый коллаж сразу открывается правкой: обычно его
                        // тут же и подписывают.
                        onCreated = { id ->
                            navController.popBackStack()
                            navController.navigate(Routes.imageEdit(id))
                        },
                    )
                }

                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBook = { id -> navController.navigate(Routes.topic(id)) },
                        onOpenNote = { id -> navController.navigate(Routes.noteEdit(id)) },
                        onViewFile = { id -> navController.navigate(Routes.view(id)) },
                    )
                }

                composable(
                    route = Routes.TOPIC,
                    arguments = listOf(navArgument("topicId") { type = NavType.LongType }),
                ) { entry ->
                    BookScreen(
                        topicId = entry.arguments?.getLong("topicId") ?: Routes.NEW,
                        onBack = { navController.popBackStack() },
                        onOpenNote = { id -> navController.navigate(Routes.noteEdit(id)) },
                        onViewFile = { id -> navController.navigate(Routes.view(id)) },
                    )
                }
                composable(Routes.LISTS) {
                    YetScreen(
                        onBack = { navController.popBackStack() },
                        onOpenList = { id -> navController.navigate(Routes.yetList(id)) },
                    )
                }

                composable(
                    route = Routes.YET_LIST,
                    arguments = listOf(navArgument("listId") { type = NavType.LongType }),
                ) { entry ->
                    YetListScreen(
                        listId = entry.arguments?.getLong("listId") ?: Routes.NEW,
                        onBack = { navController.popBackStack() },
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
                            val backToDay = navController.popBackStack(
                                route = Destination.TODAY.route,
                                inclusive = false,
                            )
                            if (!backToDay) {
                                // Дня под плеером нет — Echo стоит стартовым
                                // разделом. Тогда стопка сносится целиком и
                                // день встаёт на её место: оставить закрытый
                                // раздел под днём значит вернуть ту же петлю,
                                // только через «назад».
                                navController.navigate(Destination.TODAY.route) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                }
                composable(Destination.VIDEO.route) {
                    VideoScreen(onOpenMenu = openDrawer)
                }
                composable(Destination.LEDGER.route) {
                    LedgerScreen(onOpenMenu = openDrawer)
                }
                composable(Routes.WEATHER) {
                    WeatherScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onOpenMenu = openDrawer,
                        onOpenBridges = { navController.navigate(Routes.BRIDGES) },
                    )
                }

                composable(Routes.LIVED) {
                    LivedScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.BRIDGES) {
                    BridgesScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.REMINDERS) {
                    RemindersScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Routes.NOTE_EDIT,
                    arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
                ) { entry ->
                    NoteEditScreen(
                        noteId = entry.arguments?.getLong("noteId") ?: Routes.NEW,
                        onBack = { navController.popBackStack() },
                        onView = { id -> navController.navigate(Routes.view(id)) },
                    )
                }

                composable(Routes.TASKS) {
                    RoutineScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLink = openLink,
                    )
                }

                composable(
                    route = Routes.GALLERY,
                    arguments = listOf(
                        navArgument("noteId") { type = NavType.LongType },
                        navArgument("albumId") { type = NavType.LongType },
                    ),
                ) { entry ->
                    ScrollViewerScreen(
                        noteId = entry.arguments?.getLong("noteId") ?: Routes.NEW,
                        album = entry.arguments?.getLong("albumId")?.takeIf { it != Routes.ALL },
                        paged = true,
                        onBack = { navController.popBackStack() },
                        onEditImage = { id -> navController.navigate(Routes.imageEdit(id)) },
                        onRename = { id -> navController.navigate(Routes.imageCard(id)) },
                    )
                }

                composable(
                    route = Routes.VIEW,
                    arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
                ) { entry ->
                    ScrollViewerScreen(
                        noteId = entry.arguments?.getLong("noteId") ?: Routes.NEW,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(Routes.noteEdit(id)) },
                        onEditImage = { id -> navController.navigate(Routes.imageEdit(id)) },
                        // Переименование — та же карточка, что после загрузки:
                        // подпись и альбом там уже есть.
                        onRename = { id -> navController.navigate(Routes.imageCard(id)) },
                    )
                }

            }

            // Голосовая заметка, играющая поверх экрана, — тоже одна на всё
            // приложение и по той же причине, что полоска ниже: её включают на
            // одном экране, а слушают, уже уйдя на другой. Кончилась —
            // карточка ушла сама (см. EchoAside).
            val aside by container.echoAside.state.collectAsStateWithLifecycle()
            EchoMini(
                aside = aside,
                position = container.echoAside::position,
                onToggle = container.echoAside::toggle,
                onClose = container.echoAside::close,
            )

            // Полоска «Убрано · Вернуть» — одна на всё приложение и поверх
            // всех экранов. Запись убирают с её собственного экрана и уходят с
            // него сразу: полоске, живущей внутри экрана, было бы негде
            // появиться.
            //
            // Место внизу теперь её и ничьё больше: карточка заметки ушла на
            // середину экрана, и обходить её полоске не приходится.
            val removed by container.trash.last.collectAsStateWithLifecycle()
            UndoBar(
                id = removed?.id,
                text = removed?.kind?.what.orEmpty(),
                onUndo = { scope.launch { container.trash.restore() } },
                onGone = { container.trash.forget() },
            )

            if (quickNote) {
                QuickNoteCard(
                    onDismiss = { quickNote = false },
                    onOpenFull = { id ->
                        quickNote = false
                        navController.navigate(Routes.noteEdit(id))
                    },
                )
            }
        }
    }
}


