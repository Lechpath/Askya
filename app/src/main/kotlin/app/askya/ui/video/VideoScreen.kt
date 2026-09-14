package app.askya.ui.video

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.androidContainer
import app.askya.data.repository.asClip
import app.askya.echo.formatDuration
import app.askya.ui.components.EmptyState
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.echo.EchoPill
import app.askya.ui.theme.EchoTheme
import app.askya.ui.theme.Night
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import app.askya.video.Clip
import app.askya.video.VideoFormats
import app.askya.video.VideoLibrary
import app.askya.video.VideoSource
import app.askya.video.formatSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * AskyaV — видео с телефона.
 *
 * Второй тёмный раздел приложения, и тьма у него та же, что у AskyaEcho: те же
 * цвета, те же карточки, те же кружки в шапке. Своей палитры ему не заводится
 * намеренно — вторая тьма в одном приложении была бы вторым ответом на тот же
 * вопрос. Кино и музыку смотрят и слушают в одной и той же темноте.
 *
 * Церемонии на входе, как у Echo, здесь нет. Там несколько секунд цветка
 * оправданы тем, что в плеер приходят слушать вечер; сюда приходят открыть
 * один файл, и занавес перед каждым роликом читался бы как задержка.
 *
 * Экран — библиотека, а не плеер: в отличие от музыки, у видео нет «сейчас
 * играет», к которому возвращаются. Плеер накрывает библиотеку целиком, когда
 * файл открыт, и уходит, когда закрыт; открытость определяется состоянием
 * самого плеера, а не отдельным маршрутом, — тогда файл, открытый из
 * проводника, попадает на тот же экран без пересылки ссылки через навигацию.
 *
 * Рядом со списком всегда стоит «Открыть файл»: `MediaStore` показывает не всё,
 * что плеер играет (см. [VideoLibrary]), и системный выбор — единственный
 * способ добраться до `.ts` или `.flv`, которые система за видео не считает.
 *
 * Рядом с ней — дверь в лабораторию ([VideoLabCard]): всё, что с роликами
 * делают, а не то, чем их смотрят. Там же и скачивание по ссылке, заменившее
 * собой прежний просмотр по ссылке: скачанное ложится в «Movies/Askya» и
 * дальше живёт обычным файлом раздела — играет, режется, помнит место
 * остановки и не зависит от того, отвечает ли сегодня чужой сервер.
 *
 * ## Полка вместо списка
 *
 * Ролики стоят коробками, как диски на полке ([ClipShelf]). Список строчками
 * отвечал на вопрос «что здесь лежит», а от библиотеки чаще спрашивают другое —
 * «что посмотреть», — и на это отвечают взглядом по обложкам, а не чтением
 * имён. Строчками остался один список: выбор роликов в плейлист, где важна не
 * обложка, а галочка, и где их набирают пачкой.
 *
 * ## Очередь
 *
 * У раздела нет «сейчас играет», но есть «что смотрим дальше»: файл, открытый
 * из плейлиста, ведёт за собой соседние — доиграв, раздел сам ставит
 * следующий. Очередь живёт здесь, а не в плеере: плеер знает про один открытый
 * файл и знать про списки не должен, а библиотека знает, откуда файл взяли.
 * Открытый из «Всего» или из папки ведёт за собой ровно тот список, в котором
 * его нашли, — так же, как песня в Echo ведёт за собой очередь.
 */
@Composable
fun VideoScreen(onOpenMenu: () -> Unit) {
    EchoTheme {
        val container = androidContainer()
        val engine = container.videoEngine
        val preferences = container.videoPreferences
        val state by engine.state.collectAsStateWithLifecycle()
        val settings by preferences.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        // Список, из которого взяли открытый файл. Пустой — значит, файл
        // пришёл сам по себе: из системного выбора или из проводника.
        var queue by remember { mutableStateOf<List<Clip>>(emptyList()) }

        // Место остановки спрашивается здесь, одним чтением, а не берётся из
        // готовой карты библиотеки: следующий из очереди начинается, когда
        // список на экране может быть уже другим.
        val start: (List<Clip>, Clip) -> Unit = { list, clip ->
            queue = list
            scope.launch {
                val spot = if (settings.resume) preferences.spot(clip.uri).first() else 0L
                engine.open(VideoSource(uri = clip.uri, title = clip.title), startMs = spot)
                engine.setRate(settings.rate)
                engine.setScale(settings.scale)
            }
        }

        // Доиграл — дальше идёт следующий из очереди. Последний в очереди
        // никуда не ведёт: плеер остаётся на титрах с кнопкой «играть», а не
        // закрывается сам, — экран, гаснущий сам собой, читается как поломка.
        LaunchedEffect(state.ended, state.source?.uri) {
            if (!state.ended) return@LaunchedEffect
            val playing = state.source?.uri ?: return@LaunchedEffect
            val index = queue.indexOfFirst { it.uri == playing }
            if (index < 0) return@LaunchedEffect
            val next = queue.getOrNull(index + 1) ?: return@LaunchedEffect
            start(queue, next)
        }

        Box(modifier = Modifier.fillMaxSize().background(Night)) {
            VideoLibraryScreen(onOpenMenu = onOpenMenu, onPlay = start)

            // Плеер живёт поверх библиотеки, а не вместо неё: закрыв фильм,
            // человек возвращается ровно туда, откуда его взял, — на ту же
            // строчку в том же списке.
            if (state.source != null) {
                VideoPlayerScreen(
                    onClose = {
                        engine.stop()
                        queue = emptyList()
                    },
                )
            }
        }
    }
}

/**
 * Что показывает библиотека.
 *
 * «Плейлисты» стоят между «Всем» и «Папками» намеренно: слева — все файлы, как
 * их видит система, справа — они же, разложенные по диску, а посередине то,
 * что разложил человек. Порядок читается от чужого к своему.
 */
private enum class LibraryTab(val label: String) {
    ALL("Всё"),
    PLAYLISTS("Плейлисты"),
    FOLDERS("Папки"),
}

@Composable
private fun VideoLibraryScreen(onOpenMenu: () -> Unit, onPlay: (List<Clip>, Clip) -> Unit) {
    val context = LocalContext.current
    val container = androidContainer()
    val preferences = container.videoPreferences
    val repository = container.videoRepository

    // Разрешение спрашивается при входе в раздел, а не на первом запуске
    // приложения: просить доступ к видео у человека, открывшего заметки,
    // значит просить ни за что. То же правило, что в Echo.
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(permission) }

    var files by remember { mutableStateOf<List<Clip>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // Счётчик перечитываний: лаборатория меняет файлы на телефоне, и после
    // каждой правки список должен собраться заново. Числом, а не флагом:
    // правок подряд бывает несколько, и второй сброс флага потерялся бы.
    var reread by remember { mutableStateOf(0) }
    // Скачанное должно появиться в списке само. Файл уже лежит в
    // «Movies/Askya» и виден системе, но список читается один раз на вход, и
    // без этого человек, забравший ролик по ссылке, не нашёл бы его на полке,
    // пока не вышел бы из раздела и не вошёл заново.
    val fetched by container.videoDownloads.state.collectAsStateWithLifecycle()
    val ready = fetched.count { it.ended && it.ok }
    LaunchedEffect(ready) { if (ready > 0) reread++ }

    // Читается заново при каждом входе с разрешением: телефон между заходами
    // пополняют — то же правило, что у музыки в Echo.
    LaunchedEffect(granted, reread) {
        if (!granted) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        files = VideoLibrary.load(context)
        loading = false
    }

    val spots by remember(files) { preferences.spotsOf(files.map { it.uri }) }
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    // Свои имена подставляются поверх прочитанного, а не вместо него: файл на
    // телефоне остаётся собой (см. `VideoPreferences.rename`), и настоящее имя
    // никуда не девается — оно живёт в `fileName` и видно в сведениях.
    val names by remember(files) { preferences.namesOf(files.map { it.uri }) }
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val clips = remember(files, names) {
        if (names.isEmpty()) files
        else files.map { clip -> names[clip.uri]?.let { clip.copy(title = it) } ?: clip }
    }

    var tab by remember { mutableStateOf(LibraryTab.ALL) }
    // Открытая папка. null — показывается то, что выбрано вкладкой.
    var folder by remember { mutableStateOf<String?>(null) }
    // Открытый плейлист — номером, а не самим списком: имя у него меняется, и
    // хранить его копию значило бы показывать старое имя после переименования.
    var openId by remember { mutableStateOf<Long?>(null) }
    // В открытый плейлист складывают ролики: тот же список всей библиотеки, но
    // с галочками.
    var adding by remember { mutableStateOf(false) }
    // Ролик, раскрытый карточкой.
    var menu by remember { mutableStateOf<Clip?>(null) }
    var naming by remember { mutableStateOf(false) }
    // Открыта лаборатория — то, где с роликами что-нибудь делают.
    var lab by remember { mutableStateOf(false) }
    // Открыта калибровка полки.
    var shelf by remember { mutableStateOf(false) }

    val settings by preferences.state.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    val playlists by remember(repository) { repository.playlists() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playlist = playlists.firstOrNull { it.id == openId }
    val rows by remember(repository, openId) {
        openId?.let { repository.clips(it) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    /**
     * Открыть что угодно системным выбором.
     *
     * Постоянное разрешение берётся с попыткой: без него открытый из выбора
     * файл перестал бы открываться завтра, а вместе с ним потерялось бы и
     * место, на котором его закрыли. Не дали — не беда: сегодня он всё равно
     * играет.
     */
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val name = context.displayNameOf(uri)
        val single = Clip(
            id = uri.hashCode().toLong(),
            uri = uri.toString(),
            title = name.substringBeforeLast('.', name),
            durationMs = 0,
            sizeBytes = 0,
            width = 0,
            height = 0,
            folder = "",
            addedAt = 0,
        )
        // Своей очереди у одиночного файла нет: за ним ничего не стоит, и
        // после него ничего не начинается.
        onPlay(listOf(single), single)
    }

    val deep = adding || openId != null || folder != null
    val back: () -> Unit = {
        when {
            adding -> adding = false
            openId != null -> openId = null
            folder != null -> folder = null
            else -> onOpenMenu()
        }
    }
    BackHandler(enabled = deep, onBack = back)

    ScreenScaffold(
        title = when {
            adding -> "В «${playlist?.title?.ifBlank { "плейлист" } ?: "плейлист"}»"
            playlist != null -> playlist.title.ifBlank { "Плейлист" }
            folder != null -> folder.orEmpty()
            else -> "AskyaV"
        },
        onNavigationClick = back,
        navigationIsBack = deep,
        actions = {
            // Плюс всегда значит «добавить сюда»: в списке плейлистов он
            // заводит новый, в открытом плейлисте кладёт в него ролики. Пока
            // ролики складывают, в шапке не нужно ничего: складывание и есть
            // то, что сейчас происходит.
            when {
                adding -> Unit

                openId != null -> HeaderIcon(
                    icon = Icons.Outlined.Add,
                    contentDescription = "Добавить ролики",
                    onClick = { adding = true },
                )

                tab == LibraryTab.PLAYLISTS -> HeaderIcon(
                    icon = Icons.Outlined.Add,
                    contentDescription = "Новый плейлист",
                    onClick = { naming = true },
                )

                else -> Unit
            }

            // «Открыть файл» стоит рядом со списком всегда, на любой вкладке:
            // системный выбор — единственный путь к тому, чего `MediaStore` за
            // видео не считает, и прятать его за вкладкой значило бы прятать
            // половину форматов. Нет его только внутри плейлиста: туда кладут
            // из библиотеки, а не из проводника.
            if (openId == null) {
                // Лаборатория стоит перед папкой: за ней всё, что с роликами
                // делают, — переименовать, обрезать, повернуть, скачать, — а
                // папка рядом открывает один файл и ничего с ним не делает.
                HeaderIcon(
                    icon = Icons.Outlined.Science,
                    contentDescription = "Лаборатория",
                    onClick = { lab = true },
                )
                // Калибровка полки стоит рядом с самой полкой, а не в
                // настройках приложения: её правят, глядя на полку, и уходить
                // ради этого в другой раздел значило бы подбирать размер по
                // памяти.
                HeaderIcon(
                    icon = Icons.Outlined.GridView,
                    contentDescription = "Полка",
                    onClick = { shelf = true },
                )
                HeaderIcon(
                    icon = Icons.Outlined.FolderOpen,
                    contentDescription = "Открыть файл",
                    onClick = { pick.launch(VideoFormats.pickTypes) },
                )
            }
        },
    ) {
        val shown = when {
            folder != null -> clips.filter { it.folder == folder }
            else -> clips
        }
        val inside = remember(rows) { rows.map { it.asClip() } }
        val insideUris = remember(rows) { rows.map { it.uri }.toSet() }

        Column(modifier = Modifier.fillMaxSize()) {
            if (!deep) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryTab.entries.forEach { entry ->
                        EchoPill(
                            label = entry.label,
                            chosen = tab == entry,
                            onClick = { tab = entry },
                        )
                    }
                }
            }

            when {
                // Складывание в плейлист — поверх всего остального: пока оно
                // идёт, на экране библиотека, а не плейлист.
                adding -> AddClipsList(
                    clips = clips,
                    added = insideUris,
                    onAdd = { clip ->
                        val id = openId ?: return@AddClipsList
                        scope.launch { repository.add(id, clip) }
                    },
                )

                openId != null -> if (inside.isEmpty()) {
                    EmptyState(
                        title = "Плейлист пуст",
                        hint = "Плюс в шапке положит в него ролики — они встанут в том " +
                            "порядке, в каком их выбрали.",
                    )
                } else {
                    ClipShelf(
                        clips = inside,
                        spots = spots,
                        onPlay = { clip -> onPlay(inside, clip) },
                        onMenu = { clip -> menu = clip },
                        columns = settings.shelfColumns,
                    )
                }

                // Плейлисты стоят выше проверок доступа и пустоты: они лежат в
                // самой Askya, и ни разрешения, ни файлов на телефоне для того,
                // чтобы их открыть, не нужно.
                tab == LibraryTab.PLAYLISTS -> VideoPlaylistsGrid(
                    onOpen = { id -> openId = id },
                    onPlay = { id ->
                        scope.launch {
                            val list = repository.clips(id).first().map { it.asClip() }
                            list.firstOrNull()?.let { onPlay(list, it) }
                        }
                    },
                )

                loading -> EmptyState(title = "Ищем видео", hint = "Смотрим, что лежит на телефоне.")

                !granted -> EmptyState(
                    title = "Нужен доступ к видео",
                    hint = "Без него список пуст. Отдельные файлы всё равно можно открыть " +
                        "папкой в шапке — на это разрешения не нужно.",
                )

                clips.isEmpty() -> EmptyState(
                    title = "Видео не нашлось",
                    hint = "Система показывает только то, что сама признала видео. " +
                        "Остальное открывается папкой в шапке — плеер играет и это.",
                )

                tab == LibraryTab.FOLDERS && folder == null -> FolderList(
                    folders = VideoLibrary.folders(clips),
                    onOpen = { folder = it },
                )

                else -> ClipShelf(
                    clips = shown,
                    spots = spots,
                    onPlay = { clip -> onPlay(shown, clip) },
                    onMenu = { clip -> menu = clip },
                    columns = settings.shelfColumns,
                )
            }
        }
    }

    if (shelf) {
        ShelfCalibration(
            columns = settings.shelfColumns,
            onColumns = preferences::setShelfColumns,
            onDismiss = { shelf = false },
        )
    }

    if (lab) {
        VideoLabCard(
            library = clips,
            onClose = { lab = false },
            onChanged = { reread++ },
            // Выбрал ролик — лаборатория закрывается: сюда приходят, чтобы
            // что-нибудь посмотрелось, и держать список поверх начавшегося
            // фильма значило бы прятать его от того, кто выбрал.
            onPlay = { list, clip ->
                lab = false
                onPlay(list, clip)
            },
        )
    }

    if (naming) {
        NewPlaylistDialog(
            onDismiss = { naming = false },
            onDone = { name ->
                naming = false
                scope.launch { openId = repository.addPlaylist(name) }
            },
        )
    }

    menu?.let { clip ->
        // Строка плейлиста, если ролик открыт из него: только у неё есть чем
        // «убрать из плейлиста» — у файла в общем списке этой строки нет.
        val row = rows.firstOrNull { it.uri == clip.uri }
        VideoClipCard(
            clip = clip,
            queue = if (openId != null) rows.map { it.asClip() } else clips,
            onDismiss = { menu = null },
            onPlay = onPlay,
            onOpenFolder = { name ->
                menu = null
                openId = null
                tab = LibraryTab.FOLDERS
                folder = name
            },
            onRemoveFromPlaylist = if (row == null) null else {
                { scope.launch { repository.remove(row.id) } }
            },
            // Стёртый файл уходит с полки сразу, а не при следующем заходе.
            onErased = { reread++ },
        )
    }
}

@Composable
private fun FolderList(
    folders: List<app.askya.video.VideoFolder>,
    onOpen: (String) -> Unit,
) {
    FadingColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(folders, key = { it.name }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpen(entry.name) }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    tint = Sunset,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NightInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${entry.clips.size} ${filesWord(entry.clips.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NightMuted,
                    )
                }
            }
        }
    }
}

/**
 * Выбор роликов в плейлист.
 *
 * Тап кладёт и оставляет список открытым: ролики в плейлист складывают пачкой,
 * и возвращаться в него после каждого значило бы открывать этот список заново.
 * Уже сложенные помечены — иначе один и тот же файл ложится дважды незаметно
 * для того, кто его кладёт. Ровно так же устроен выбор песен в Echo.
 */
@Composable
private fun AddClipsList(clips: List<Clip>, added: Set<String>, onAdd: (Clip) -> Unit) {
    if (clips.isEmpty()) {
        EmptyState(
            title = "Складывать нечего",
            hint = "Система не нашла на телефоне видео — значит, и в плейлист пока нечего класть.",
        )
        return
    }

    FadingColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(clips, key = { it.id }) { clip ->
            val chosen = clip.uri in added
            ClipRow(
                clip = clip,
                watchedMs = 0L,
                onPlay = { if (!chosen) onAdd(clip) },
                onMenu = { if (!chosen) onAdd(clip) },
                trailing = {
                    Icon(
                        imageVector = if (chosen) Icons.Outlined.Check else Icons.Outlined.Add,
                        contentDescription = if (chosen) "Уже в плейлисте" else "Положить",
                        tint = if (chosen) Sunset else NightMuted,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
    }
}

/**
 * Строчка списка: кадр, название и одна строка подробностей.
 *
 * Подробности идут одной строкой и в одном порядке — длительность, размер
 * кадра, вес файла: список читают глазами по вертикали, и три величины,
 * стоящие на своих местах, сравниваются между строками без чтения.
 *
 * Полоска под кадром — сколько досмотрено. Она есть только у начатых файлов:
 * пустая полоска у каждого непросмотренного ролика превратила бы список в
 * график.
 *
 * Долгое нажатие раскрывает ролик карточкой: переименовать, положить в
 * плейлист, посмотреть сведения, удалить. Коротко — смотреть; так же, как в Echo
 * короткое нажатие включает песню, а долгое раскрывает её.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipRow(
    clip: Clip,
    watchedMs: Long,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onPlay, onLongClick = onMenu)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            ClipFrame(clip = clip, modifier = Modifier.size(width = 116.dp, height = 66.dp))

            if (watchedMs > 0 && clip.durationMs > 0) {
                val done = (watchedMs.toFloat() / clip.durationMs).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .width(116.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(NightBorder),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(done)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Sunset),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = clip.title,
                fontFamily = FontFamily.Serif,
                fontSize = 17.sp,
                color = NightInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    formatDuration(clip.durationMs).takeIf { clip.durationMs > 0 },
                    clip.resolution.takeIf { it.isNotEmpty() },
                    formatSize(clip.sizeBytes).takeIf { it.isNotEmpty() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        trailing?.invoke()
    }
}

/**
 * Кадр ролика в рамке — то, чем он показан и в списке, и в карточке, и на
 * обложке плейлиста.
 *
 * Общий на все три места, потому что кадр — дорогая вещь: его вынимают из
 * файла, и три своих способа его показать означали бы три разных кэша.
 * `null` от [VideoLibrary.frame] означает «кадра нет»: тогда на его месте
 * стоит свой знак, а не пустой прямоугольник.
 */
@Composable
internal fun ClipFrame(clip: Clip, modifier: Modifier = Modifier) {
    val frame = rememberFrame(clip)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NightPanelSoft)
            .border(1.dp, NightBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = frame
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = NightMuted,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Кадр ролика — один раз прочитанный и общий на всех.
 *
 * Отдельно от того, кто его показывает, потому что показывают его двое:
 * строчка списка ([ClipFrame]) и коробка на полке ([DvdCase]). Кадр вынимается
 * из файла и стоит дорого; два своих способа его достать означали бы два кэша
 * на одну и ту же картинку.
 */
@Composable
internal fun rememberFrame(clip: Clip): Bitmap? {
    val context = LocalContext.current
    val frame by produceState<Bitmap?>(initialValue = frameCache[clip.uri], clip.uri) {
        if (value != null) return@produceState
        val loaded = VideoLibrary.frame(context, clip)
        if (loaded != null) frameCache.put(clip.uri, loaded)
        value = loaded
    }
    return frame
}

/**
 * Кадры карточек — в памяти и с потолком.
 *
 * Без него список перечитывал бы кадр с диска на каждой прокрутке мимо строки,
 * а с бесконечным кэшем сотня роликов сложила бы в память сотню картинок.
 * Восемь мегабайт — примерно полсотни кадров: на длину экрана хватает с
 * запасом, а до предела памяти далеко.
 */
private val frameCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/** «файл», «файла», «файлов» — по числу. */
private fun filesWord(count: Int): String {
    val tens = count % 100
    if (tens in 11..14) return "файлов"
    return when (count % 10) {
        1 -> "файл"
        2, 3, 4 -> "файла"
        else -> "файлов"
    }
}

/** Имя выбранного документа: в ссылке его нет, спрашивается у провайдера. */
private fun android.content.Context.displayNameOf(uri: Uri): String = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Видео"
