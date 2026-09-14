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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.askya.app.OPEN_ECHO
import app.askya.app.OPEN_TODAY
import app.askya.app.OPEN_VOICE
import app.askya.app.OPEN_WEATHER
import kotlinx.coroutines.flow.combine
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
import app.askya.ui.ledger.LedgerScreen
import app.askya.ui.components.UndoBar
import app.askya.domain.model.DeedLink
import app.askya.ui.askyaday.LivedScreen
import app.askya.ui.reminders.RemindersScreen
import app.askya.ui.routine.RoutineScreen
import app.askya.ui.settings.SettingsScreen
import app.askya.ui.yet.YetListScreen
import app.askya.ui.yet.YetScreen
import kotlinx.coroutines.launch
import androidx.savedstate.read

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
 *
 * [shell] — то, что у системы своё: разделы, которых на компьютере нет,
 * музыка, погода и то, что рисуется поверх экранов (см. [PlatformShell]).
 */
@Composable
fun AskyaApp(
    shell: PlatformShell,
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

    /*
     * «Недавнее» — записи Scroll и списки Yet одной лентой, по времени.
     *
     * Двумя потоками, сведёнными в один: таблицы разные, и запросом их не
     * склеить, а восьми верхних строк из каждой хватает с запасом — в ленту
     * попадают всё равно восемь. Список Yet отмечает время сам, когда его
     * трогают (`YetRepository.touch`), — иначе заведённый неделю назад, но
     * ведомый каждый день, тонул бы под вчерашними заметками.
     */
    val recents by remember(container) {
        combine(
            container.noteRepository.notes(),
            container.yetRepository.recentLists(RECENTS),
        ) { notes, lists ->
            val fromNotes = notes.take(RECENTS).map { note ->
                RecentEntry(
                    id = note.id,
                    title = note.title,
                    list = false,
                    at = note.updatedAt,
                )
            }
            val fromLists = lists.map { list ->
                RecentEntry(
                    id = list.id,
                    title = list.title,
                    list = true,
                    at = list.updatedAt,
                )
            }
            (fromNotes + fromLists).sortedByDescending { it.at }.take(RECENTS)
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val playing = shell.playing()

    // Погода для строки в шапке меню — у системы, где она есть. Спрашивается
    // не на запуске, а при первом открытии меню (см. [PlatformShell.refreshWeather]).
    val weatherLine = shell.weatherLine()

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
            OPEN_ECHO -> if (Destination.ECHO in shell.sections) navController.navigate(Destination.ECHO.route)
            OPEN_VOICE -> if (shell.voice) {
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
        shell.refreshWeather()
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
                    // Раздел, которого у этой системы нет (Echo на компьютере),
                    // молча никуда не ведёт — как и стёртая запись.
                    val section = Destination.entries.firstOrNull { it.route == route }
                    if (section != null && section !in shell.sections) return@let
                    if (section != null) {
                        openSection(route)
                    } else {
                        navController.navigate(route)
                    }
                }
            }
        }
    }

    val shellNav = ShellNav(
        controller = navController,
        openDrawer = openDrawer,
        openSection = openSection,
        sayNow = { sayNow },
        onSaid = { sayNow = false },
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        // На детальных экранах свайп от края отдан их содержимому, а не меню.
        gesturesEnabled = drawerGestures,
        drawerContent = {
            AppDrawer(
                currentRoute = currentRoute,
                recents = recents,
                playing = playing,
                sections = shell.sections,
                onSelect = { route ->
                    closeDrawer()
                    openSection(route)
                },
                onOpenRecent = { entry ->
                    closeDrawer()
                    navController.navigate(
                        if (entry.list) Routes.yetList(entry.id) else Routes.noteEdit(entry.id),
                    )
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
                onPlay = shell.onPlay?.let { play ->
                    {
                        if (!play()) {
                            closeDrawer()
                            openSection(Destination.ECHO.route)
                        }
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
                shell.sections.firstOrNull { it.route == saved }?.route
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
                        onOpenVoice = if (shell.voice) {
                            { navController.navigate(Routes.VOICE) }
                        } else null,
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
                        voicePlayer = shell.voicePlayer,
                    )
                }

                composable(Routes.IMAGES) {
                    ImagesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCard = { id -> navController.navigate(Routes.imageCard(id)) },
                        // Из раздела картинку открывают с листанием соседних.
                        onViewImage = { id -> navController.navigate(Routes.gallery(id, null)) },
                        onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
                        onCollage = if (shell.imageEditing) {
                            { navController.navigate(Routes.COLLAGE) }
                        } else null,
                    )
                }

                composable(
                    route = Routes.ALBUM,
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
                ) { entry ->
                    val albumId = entry.long("albumId")
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
                        noteId = entry.long("noteId") ?: Routes.NEW,
                        onDone = { navController.popBackStack() },
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
                        topicId = entry.long("topicId") ?: Routes.NEW,
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
                        listId = entry.long("listId") ?: Routes.NEW,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Destination.LEDGER.route) {
                    LedgerScreen(onOpenMenu = openDrawer)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onOpenMenu = openDrawer,
                        sections = shell.sections,
                        splash = shell.splash,
                        dayExtras = shell.settingsDay,
                        groups = { general -> shell.settingsGroups(general, shellNav) },
                        overlay = shell.settingsOverlay,
                    )
                }

                composable(Routes.LIVED) {
                    LivedScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.REMINDERS) {
                    RemindersScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Routes.NOTE_EDIT,
                    arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
                ) { entry ->
                    NoteEditScreen(
                        noteId = entry.long("noteId") ?: Routes.NEW,
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
                        noteId = entry.long("noteId") ?: Routes.NEW,
                        album = entry.long("albumId")?.takeIf { it != Routes.ALL },
                        paged = true,
                        onBack = { navController.popBackStack() },
                        onEditImage = if (shell.imageEditing) {
                            { id -> navController.navigate(Routes.imageEdit(id)) }
                        } else null,
                        onRename = { id -> navController.navigate(Routes.imageCard(id)) },
                    )
                }

                composable(
                    route = Routes.VIEW,
                    arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
                ) { entry ->
                    ScrollViewerScreen(
                        noteId = entry.long("noteId") ?: Routes.NEW,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(Routes.noteEdit(id)) },
                        onEditImage = if (shell.imageEditing) {
                            { id -> navController.navigate(Routes.imageEdit(id)) }
                        } else null,
                        // Переименование — та же карточка, что после загрузки:
                        // подпись и альбом там уже есть.
                        onRename = { id -> navController.navigate(Routes.imageCard(id)) },
                    )
                }

                // Маршруты, которые есть только у этой системы.
                shell.routes(this, shellNav)

            }

            // То, что у системы лежит поверх всех экранов, — у телефона
            // играющая голосовая заметка (см. [PlatformShell.overlay]).
            shell.overlay()

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
                    onOpenNote = { id ->
                        quickNote = false
                        navController.navigate(Routes.noteEdit(id))
                    },
                    onOpenList = { id ->
                        quickNote = false
                        navController.navigate(Routes.yetList(id))
                    },
                )
            }
        }
    }
}

/**
 * Сколько строк держит «Недавнее» в меню.
 *
 * Восемь: список длиннее меню не помещает, а «недавнее» из двадцати строк —
 * это уже не недавнее, а второй раздел, который надо читать.
 */
private const val RECENTS = 8

/**
 * Число из маршрута — `noteId`, `listId` и прочие. Через `SavedState`, а не
 * `Bundle`: навигация общая, и `Bundle` есть только у Android.
 */
private fun NavBackStackEntry.long(key: String): Long? =
    arguments?.read { if (contains(key)) getLong(key) else null }
