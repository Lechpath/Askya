package app.askya.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import app.askya.ui.scroll.imageedit.CollageScreen
import app.askya.ui.scroll.imageedit.ImageEditorScreen
import app.askya.ui.echo.EchoScreen
import app.askya.ui.reminders.RemindersScreen
import app.askya.ui.routine.RoutineScreen
import app.askya.ui.settings.SettingsScreen
import app.askya.ui.yet.YetListScreen
import app.askya.ui.yet.YetScreen
import kotlinx.coroutines.launch

@Composable
fun AskyaApp(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val container = appContainer()
    val recents by remember(container) { container.noteRepository.notes() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val player = container.echoPlayer
    val echo by player.state.collectAsStateWithLifecycle()

    // Быстрая заметка живёт поверх всего приложения, а не внутри экрана: её
    // открывают из меню, и к какому экрану меню было открыто — неважно.
    var quickNote by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    fun closeDrawer() = scope.launch { drawerState.close() }

    val inSection = Destination.entries.any { it.route == currentRoute }
    // На AskyaDay горизонтальный свайп листает дни, и закрытое меню его
    // забирало бы себе. Но пока меню открыто, жесты нужны всегда: в Material 3
    // этот же флаг гасит закрытие по тапу на затемнение, и без него открытое
    // меню невозможно закрыть ничем, кроме свайпа.
    val drawerGestures = drawerState.isOpen ||
        (inSection && currentRoute != Destination.TODAY.route)
    val openReminders: () -> Unit = { navController.navigate(Routes.REMINDERS) }

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
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo(Destination.TODAY.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
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
                onPlay = {
                    if (!player.resume()) {
                        closeDrawer()
                        if (currentRoute != Destination.ECHO.route) {
                            navController.navigate(Destination.ECHO.route) {
                                popUpTo(Destination.TODAY.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
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
            NavHost(
                navController = navController,
                startDestination = Destination.TODAY.route,
            ) {
                composable(Destination.TODAY.route) {
                    AskyaDayScreen(
                        onOpenMenu = openDrawer,
                        onOpenReminders = openReminders,
                        onOpenTasks = { navController.navigate(Routes.TASKS) },
                    )
                }
                composable(Destination.NOTES.route) {
                    ScrollScreen(
                        onOpenMenu = openDrawer,
                        onOpenImages = { navController.navigate(Routes.IMAGES) },
                        onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                        onOpenLists = { navController.navigate(Routes.LISTS) },
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
                        onLeave = {
                            navController.navigate(Destination.TODAY.route) {
                                popUpTo(Destination.TODAY.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onOpenMenu = openDrawer)
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
                    RoutineScreen(onBack = { navController.popBackStack() })
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
