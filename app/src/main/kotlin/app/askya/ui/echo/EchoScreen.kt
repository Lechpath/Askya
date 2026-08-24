package app.askya.ui.echo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.R
import app.askya.app.appContainer
import app.askya.echo.EchoBeat
import app.askya.echo.EchoRepeat
import app.askya.echo.Track
import app.askya.echo.formatDuration
import app.askya.ui.components.EmptyState
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.theme.EchoTheme
import app.askya.ui.theme.Night
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanel
import app.askya.ui.theme.Sunset
import app.askya.ui.theme.SunsetDeep
import kotlinx.coroutines.delay

/**
 * AskyaEcho — плеер музыки с телефона.
 *
 * Единственный тёмный раздел приложения. Музыку слушают вечером, в дороге и
 * перед сном, и кремовый лист в этот момент светит в лицо; заодно обложка на
 * тёмном читается как обложка, а не как картинка на бумаге. Тьма здесь —
 * свойство раздела, а не настройка: остальная Askya остаётся светлой, чем бы
 * ни был переключён телефон.
 *
 * Экран — сам плеер: обложка, название и три кнопки. Списки песен вынесены в
 * разделы и открываются поверх: то, что играет сейчас, важнее того, что можно
 * включить, и место в середине экрана принадлежит ему.
 *
 * Своей библиотеки нет: файлы уже разложены и подписаны системой, читаются
 * через `MediaStore`. Плеер живёт в контейнере приложения, а не на этом
 * экране, — музыка не должна замолкать от того, что человек ушёл в расписание.
 *
 * Разрешение спрашивается в момент открытия раздела, а не на первом запуске:
 * просить доступ к музыке у человека, который открыл заметки, значит просить
 * ни за что.
 *
 * Вход и уход обставлены цветком — [EchoOpening] и [EchoClosing]. Раздел
 * открывается не мгновенно потому, что сюда приходят слушать, а не заглянуть:
 * несколько секунд на то, чтобы имя приложения дописалось до имени раздела,
 * тут не потеря времени, а смена света.
 *
 * Уходят отсюда тем же цветком в шапке — и попадают в AskyaDay, а не в список
 * разделов: музыку включают, занимаясь чем-то ещё, и после плеера человеку
 * нужен день, а не вопрос «куда теперь». Меню из Echo никуда не делось —
 * оно, как и в других разделах, выезжает свайпом от края.
 */
@Composable
fun EchoScreen(onLeave: () -> Unit) {
    val container = appContainer()
    val player = container.echoPlayer
    val context = LocalContext.current

    val preferences = container.echoPreferences
    val pulse = container.echoPulse

    val state by player.state.collectAsStateWithLifecycle()
    val echoSettings by preferences.settings
        .collectAsStateWithLifecycle(initialValue = preferences.state.value)
    val beat by pulse.beat.collectAsStateWithLifecycle()
    val hearing by pulse.hearing.collectAsStateWithLifecycle()
    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    var granted by remember { mutableStateOf(hasAudioAccess(context)) }
    var section by remember { mutableStateOf<EchoSection?>(null) }
    var equalizer by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(false) }
    // Плеер во весь экран — то, что открывается нажатием на обложку.
    var full by remember { mutableStateOf(false) }
    // Карточка играющей дорожки: то же, что три точки в списке, но для того,
    // что уже звучит, — к нему приходят чаще всего.
    var opened by remember { mutableStateOf<Track?>(null) }
    // Церемония переживает поворот экрана: она открывает раздел, а не
    // сопровождает каждую пересборку композиции.
    var opening by rememberSaveable { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }

    // Уходя, раздел складывается внутрь себя: под занавесом плеер отступает
    // вглубь, а не стоит столбом, пока его закрывают. Обратно он разворачивается
    // сам, когда занавес снят, — и тем же движением возвращается, если меню
    // закрыли, ничего не выбрав.
    val fold by animateFloatAsState(
        targetValue = if (closing) 0.92f else 1f,
        animationSpec = tween(520),
        label = "fold",
    )

    // «Назад» уходит тем же занавесом, что и цветок в шапке: из Echo ведёт
    // одна дверь, и она открывается в AskyaDay. Карточки поверх плеера
    // перехватывают «назад» раньше — у каждой свой обработчик.
    BackHandler(enabled = !closing) { closing = true }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed -> granted = allowed }

    /**
     * Разрешение на уведомления — ради плеера в шторке и в центре управления:
     * карточку там система рисует из уведомления, и без него музыка играет,
     * а управлять ею из кармана нечем.
     *
     * Спрашивается не на входе в раздел, а с первой заигравшей песней: до неё
     * показывать в шторке нечего, а два системных окна подряд на входе — это
     * допрос вместо музыки.
     */
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Отказали — музыка играет, просто без карточки в шторке. */ }

    val playing = state.track != null
    LaunchedEffect(playing) {
        if (playing && needsNotificationPermission(context)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        if (!granted) ask.launch(audioPermission())
    }

    /*
     * Вспышки вокруг обложки слушают то, что звучит, а слушание держит
     * системный ресурс — поэтому оно заводится вместе с музыкой и отпускается
     * вместе с ней же.
     *
     * Среди условий стоит открытая карточка настроек: разрешение на микрофон,
     * без которого спектра не будет, просят именно там, и закрытая карточка —
     * тот самый момент, когда попытку стоит повторить.
     */
    LaunchedEffect(echoSettings.pulse, state.playing, settings) {
        val wanted = echoSettings.pulse && state.playing && hasMicAccess(context)
        if (wanted) pulse.attach() else pulse.release()
    }

    // Уходя из раздела, слушание отпускается: держать его ради экрана, на
    // который никто не смотрит, незачем.
    DisposableEffect(pulse) {
        onDispose { pulse.release() }
    }

    // Музыка перечитывается не только на входе: удалили песню — список
    // собирается заново, иначе в нём осталась бы строка без файла.
    var reread by remember { mutableIntStateOf(0) }

    LaunchedEffect(granted, reread) {
        if (granted) tracks = container.echoLibrary(context)
    }

    EchoTheme {
        NightSystemBars()

        Box(modifier = Modifier.fillMaxSize().sunsetBackground()) {
            ScreenScaffold(
                title = "AskyaEcho",
                modifier = Modifier.graphicsLayer {
                    scaleX = fold
                    scaleY = fold
                },
                // Полоски меню заменены цветком: знак приложения на входе в
                // раздел и на выходе из него — одно и то же лицо.
                onNavigationClick = { closing = true },
                navigationIcon = R.drawable.ic_flower,
                navigationLabel = "Закрыть раздел",
                actions = {
                    // Очередь — только когда она есть: пустая кнопка «что
                    // дальше» на пустом плеере обещает то, чего нет.
                    if (state.queue.isNotEmpty()) {
                        HeaderIcon(
                            icon = Icons.Outlined.QueueMusic,
                            contentDescription = "Очередь",
                            onClick = { queue = true },
                        )
                    }
                    HeaderIcon(
                        icon = Icons.Outlined.Tune,
                        contentDescription = "Настройки Echo",
                        onClick = { settings = true },
                    )
                },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SectionButtons(
                        // Раздел без разрешения показал бы пустые списки —
                        // проще сразу попросить доступ, чем открывать пустоту.
                        onOpen = { chosen ->
                            if (granted) section = chosen else ask.launch(audioPermission())
                        },
                    )

                    if (!granted) {
                        NoAccess(onAsk = { ask.launch(audioPermission()) })
                    } else {
                        Player(
                            track = state.track,
                            playing = state.playing,
                            beat = beat,
                            hearing = hearing,
                            durationMs = state.durationMs,
                            shuffle = state.shuffle,
                            repeat = state.repeat,
                            position = player::position,
                            onSeek = player::seekTo,
                            onToggle = {
                                // Кнопка «играть» до выбора песни включает
                                // первую попавшуюся: пустой плеер, который на
                                // нажатие не отвечает, выглядит сломанным.
                                if (state.track != null) {
                                    player.toggle()
                                } else {
                                    tracks?.firstOrNull()?.let { player.play(tracks.orEmpty(), it) }
                                }
                            },
                            onNext = player::next,
                            onPrevious = player::previous,
                            onShuffle = { player.shuffle(!state.shuffle) },
                            onRepeat = { player.repeat(state.repeat.next()) },
                            onEqualizer = { equalizer = true },
                            onTrack = { state.track?.let { opened = it } },
                            // Пустой плеер разворачивать не во что: в полном
                            // экране показывать было бы нечего, кроме заставки.
                            onFull = { if (state.track != null) full = true },
                        )
                    }
                }
            }

            section?.let { open ->
                EchoSectionLayer(
                    section = open,
                    library = tracks,
                    onClose = { section = null },
                    onPlay = { queue, track ->
                        player.play(queue, track)
                        section = null
                    },
                    onLibraryChanged = { reread++ },
                )
            }

            if (queue) {
                EchoQueueCard(
                    onDismiss = { queue = false },
                    onTrack = { track -> opened = track },
                )
            }

            if (settings) {
                EchoSettingsCard(
                    onDismiss = { settings = false },
                    // Эквалайзер открывается поверх настроек, а не вместо них:
                    // «назад» из него возвращает туда, откуда его позвали.
                    onEqualizer = { equalizer = true },
                )
            }

            if (equalizer) {
                EchoEqualizerCard(onDismiss = { equalizer = false })
            }

            opened?.let { track ->
                EchoTrackCard(
                    track = track,
                    // Очередь — та, что уже собрана плеером: карточка играющей
                    // дорожки не должна выдумывать себе список.
                    queue = state.queue.ifEmpty { listOf(track) },
                    onDismiss = { opened = null },
                    onPlay = { queue, chosen -> player.play(queue, chosen) },
                    onRemoved = { reread++ },
                )
            }

            if (full) {
                EchoFullCard(
                    track = state.track,
                    playing = state.playing,
                    durationMs = state.durationMs,
                    shuffle = state.shuffle,
                    repeat = state.repeat,
                    beat = beat,
                    hearing = hearing,
                    position = player::position,
                    onSeek = player::seekTo,
                    onToggle = player::toggle,
                    onNext = player::next,
                    onPrevious = player::previous,
                    onShuffle = { player.shuffle(!state.shuffle) },
                    onRepeat = { player.repeat(state.repeat.next()) },
                    onDismiss = { full = false },
                )
            }

            // Занавес последний в стопке: он закрывает собой и списки, и
            // эквалайзер, если раздел закрывают из них.
            if (opening) {
                EchoOpening(onDone = { opening = false })
            }
            if (closing) {
                EchoClosing(onLeave = onLeave, onDone = { closing = false })
            }
        }
    }
}

/** Разделы, которые открываются кнопками под шапкой. */
enum class EchoSection(val label: String, val icon: ImageVector) {
    ALL_MUSIC("Вся музыка", Icons.Outlined.LibraryMusic),
    PLAYLISTS("Плейлисты", Icons.Outlined.QueueMusic),
    FOLDERS("Папки", Icons.Outlined.FolderOpen),
}

/**
 * Три кнопки под шапкой. Прокручиваются вбок, а не сжимаются: названия
 * разделов — слова, и переносить их по слогам ради узкого телефона хуже, чем
 * дать сдвинуть ряд пальцем.
 */
@Composable
private fun SectionButtons(onOpen: (EchoSection) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EchoSection.entries.forEach { section ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, NightBorder, RoundedCornerShape(14.dp))
                    .background(NightPanel)
                    .clickable { onOpen(section) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = Sunset,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = NightInk,
                )
            }
        }
    }
}

@Composable
private fun NoAccess(onAsk: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            title = "Нужен доступ к музыке",
            hint = "Echo играет то, что лежит на телефоне, — без доступа он не видит " +
                "ни одной песни.",
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = onAsk, modifier = Modifier.padding(top = 16.dp)) {
            Text("Разрешить")
        }
    }
}

/**
 * Сам плеер: обложка, название, перемотка и три кнопки.
 *
 * Позиция спрашивается у плеера раз в секунду, пока экран открыт. Тик живёт
 * здесь, а не в плеере: за списком заметок отсчитывать секунды незачем.
 *
 * Экран, положенный на бок, — это не тот же экран, ставший шире: высоты в нём
 * остаётся треть. Обложка, взятая долей ширины, вырастала там выше экрана и
 * накрывала собой и название, и кнопки. Поэтому в горизонтальном положении
 * плеер раскладывается в строку: обложка слева ровно по высоте, справа — имя,
 * полоса и кнопки. Столбик и строка — одни и те же части, переставленные, а не
 * два разных плеера.
 */
@Composable
private fun Player(
    track: Track?,
    playing: Boolean,
    beat: EchoBeat,
    hearing: Boolean,
    durationMs: Long,
    shuffle: Boolean,
    repeat: EchoRepeat,
    position: () -> Long,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onEqualizer: () -> Unit,
    onTrack: () -> Unit,
    onFull: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(playing, durationMs, track?.uri) {
        while (true) {
            if (!dragging && durationMs > 0) {
                progress = (position().toFloat() / durationMs).coerceIn(0f, 1f)
            }
            delay(1_000)
        }
    }

    val scrub: (Float) -> Unit = {
        dragging = true
        progress = it
    }
    val seek: (Float) -> Unit = {
        progress = it
        onSeek((it * durationMs).toLong())
        dragging = false
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sideways = maxWidth > maxHeight
        // Обложка меряется по меньшей стороне отведённого ей места: доля
        // ширины на боку даёт квадрат выше экрана, доля высоты на узком
        // телефоне — марку вместо обложки.
        val side = if (sideways) {
            minOf(maxHeight - 16.dp, maxWidth * 0.4f)
        } else {
            minOf(maxWidth * 0.78f, maxHeight * 0.52f)
        }

        if (sideways) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerCover(
                    track = track,
                    side = side,
                    beat = beat,
                    playing = playing,
                    hearing = hearing,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onTap = onFull,
                )

                // Справа всё остальное, и оно прокручивается: на низком экране
                // кнопки должны доставаться пальцем, а не обрезаться краем.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    TrackTitles(track = track, onTrack = onTrack)
                    EchoProgress(
                        progress = progress,
                        durationMs = durationMs,
                        enabled = track != null,
                        onScrub = scrub,
                        onSeek = seek,
                        // Слово-полоса написано пером и тянуться под ширину не
                        // может: на боку оно берётся мельче, а не шире.
                        wordHeight = 44.dp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Controls(
                        playing = playing,
                        onToggle = onToggle,
                        onNext = onNext,
                        onPrevious = onPrevious,
                    )
                    Modes(
                        shuffle = shuffle,
                        repeat = repeat,
                        onShuffle = onShuffle,
                        onRepeat = onRepeat,
                    )
                    EqualizerButton(onEqualizer = onEqualizer)
                }
            }
            return@BoxWithConstraints
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.6f))

            // Чуть меньше, чем во всю ширину: место под обложкой занимает
            // слово, отсчитывающее время, и обложке незачем его выдавливать.
            PlayerCover(
                track = track,
                side = side,
                beat = beat,
                playing = playing,
                hearing = hearing,
                onNext = onNext,
                onPrevious = onPrevious,
                onTap = onFull,
            )

            TrackTitles(track = track, onTrack = onTrack, top = 24.dp)

            EchoProgress(
                progress = progress,
                durationMs = durationMs,
                // До первой песни двигать нечего: полоса стоит на месте и молчит.
                enabled = track != null,
                onScrub = scrub,
                onSeek = seek,
                modifier = Modifier.padding(top = 16.dp),
            )

            Controls(
                playing = playing,
                onToggle = onToggle,
                onNext = onNext,
                onPrevious = onPrevious,
            )
            Modes(
                shuffle = shuffle,
                repeat = repeat,
                onShuffle = onShuffle,
                onRepeat = onRepeat,
            )

            Spacer(modifier = Modifier.weight(1f))

            EqualizerButton(onEqualizer = onEqualizer)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Имя дорожки и имя того, кто её поёт.
 *
 * Название нажимается: за ним та же карточка дорожки, что за тремя точками в
 * списке, — очередь, плейлист, отметка, отправка.
 */
@Composable
private fun TrackTitles(
    track: Track?,
    onTrack: () -> Unit,
    modifier: Modifier = Modifier,
    top: Dp = 0.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = top),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = track?.title ?: "Название трека",
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            color = if (track == null) NightMuted else NightInk,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = track != null, onClick = onTrack)
                .padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Text(
            text = track?.artist ?: "Выбери песню в разделе выше",
            style = MaterialTheme.typography.bodyMedium,
            color = NightMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Три слова плеера: назад, играть, дальше. */
@Composable
private fun Controls(
    playing: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Control("Back", "Прошлая", onPrevious, Modifier.weight(1f))
        Control(
            text = if (playing) "Pause" else "Play",
            label = if (playing) "Пауза" else "Играть",
            onClick = onToggle,
            modifier = Modifier.weight(1f),
            accent = true,
        )
        Control("Next", "Следующая", onNext, Modifier.weight(1f))
    }
}

/** Два режима под кнопками: вперемешку и повтор. */
@Composable
private fun Modes(
    shuffle: Boolean,
    repeat: EchoRepeat,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 2.dp)) {
        Mode("Shuffle", "Вперемешку", shuffle, onShuffle, Modifier.weight(1f))
        Mode(
            text = repeat.caption(),
            label = repeat.next().spoken(),
            on = repeat != EchoRepeat.OFF,
            onClick = onRepeat,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Стрелка вверх — то, что тянут снизу: эквалайзер выезжает оттуда же, куда она
 * показывает.
 */
@Composable
private fun EqualizerButton(onEqualizer: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onEqualizer)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowUp,
            contentDescription = "Эквалайзер",
            tint = Sunset,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "Эквалайзер",
            style = MaterialTheme.typography.labelSmall,
            color = NightMuted,
        )
    }
}

/**
 * Полоса прокрутки — само название раздела, написанное каллиграфией.
 *
 * Прошедшее время наливает слово закатом слева направо, оставшееся стоит
 * тёмным: сколько песни позади, видно по тому, докуда дописано «AskyaEcho».
 * Обычный ползунок здесь был бы деталью Material поверх раздела, у которого
 * своё лицо, — а слово и так лежит поперёк экрана и меряет его собой.
 *
 * Слово одно и то же и на лепестке цветка при входе, и здесь: раздел
 * представился именем, и это же имя теперь отсчитывает время.
 *
 * Высота задана, ширина считается по ней: каллиграфию нельзя тянуть под
 * ширину экрана — растянутое перо перестаёт быть пером. Подписи времени
 * держатся той же ширины, что и слово: они подписывают его, а не экран.
 *
 * Перемотка — касанием в нужное место и протяжкой; ползунка нет, потому что
 * место в песне показывает граница цвета, и хватать пальцем нужно её.
 */
@Composable
internal fun EchoProgress(
    progress: Float,
    durationMs: Long,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    wordHeight: Dp = 56.dp,
) {
    val word = painterResource(R.drawable.ic_wordmark_echo)
    val height = wordHeight
    val width: Dp = height * (word.intrinsicSize.width / word.intrinsicSize.height)

    Column(modifier = modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var at = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            at = (start.x / size.width).coerceIn(0f, 1f)
                            onScrub(at)
                        },
                        onDragEnd = { onSeek(at) },
                        onDragCancel = { onSeek(at) },
                        onHorizontalDrag = { change, _ ->
                            at = (change.position.x / size.width).coerceIn(0f, 1f)
                            onScrub(at)
                        },
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { tap ->
                        onSeek((tap.x / size.width).coerceIn(0f, 1f))
                    }
                },
        ) {
            Image(
                painter = word,
                contentDescription = null,
                colorFilter = ColorFilter.tint(NightMuted.copy(alpha = 0.32f)),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = word,
                contentDescription = "Прокрутка",
                colorFilter = ColorFilter.tint(Sunset),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(right = size.width * progress) {
                            this@drawWithContent.drawContent()
                        }
                    },
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration((progress * durationMs).toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Кнопка плеера — слово, а не значок.
 *
 * Треугольник, две палки и стрелки с чёрточками — язык магнитофона, и на
 * экране, где название раздела написано пером, они выглядят наклейками с
 * чужой панели. Слово читается сразу и набрано тем же шрифтом, что заголовок
 * и имя дорожки.
 *
 * «Play» и «Pause» — одна кнопка: она называет не то, что происходит сейчас,
 * а то, что случится по нажатию.
 *
 * Ряд делится на три равные доли, и слово стоит посреди своей: иначе «Pause»,
 * которое шире «Play», раздвигало бы «Back» и «Next» на каждом нажатии.
 * Нажимается доля целиком — по слову в 22 кегля пальцем не попасть.
 */
@Composable
private fun Control(
    text: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontSize = if (accent) 34.sp else 22.sp,
        color = if (accent) Sunset else NightInk,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick, onClickLabel = label)
            .padding(vertical = 10.dp),
    )
}

/**
 * Надпись на кнопке повтора: она называет режим, который сейчас стоит.
 *
 * Выключенный повтор подписан просто «Repeat» — словом, а не «Repeat Off»:
 * рядом с ним нет ни цвета, ни черты, и по ним видно, что он выключен, а
 * лишнее слово только удлиняет строку.
 */
private fun EchoRepeat.caption(): String = when (this) {
    EchoRepeat.OFF -> "Repeat"
    EchoRepeat.QUEUE -> "Repeat All"
    EchoRepeat.TRACK -> "Repeat One"
}

/**
 * То же для голоса: TalkBack называет не режим, а то, что случится по
 * нажатию, — поэтому подпись берётся у следующего режима, а не у текущего.
 */
private fun EchoRepeat.spoken(): String = when (this) {
    EchoRepeat.OFF -> "Без повтора"
    EchoRepeat.QUEUE -> "Повторять список"
    EchoRepeat.TRACK -> "Повторять дорожку"
}

/**
 * Режим воспроизведения: перемешать очередь или крутить дорожку по кругу.
 *
 * Включённый режим горит закатом и подчёркнут: одного цвета мало — черта под
 * словом видна и краем глаза, и по ней режим читается, не вглядываясь. Какой
 * именно повтор включён, сказано самой надписью ([caption]): значок с двумя
 * стрелками и единицей внутри требует, чтобы его один раз кому-то объяснили.
 *
 * Мельче кнопок плеера намеренно: режим ставят раз за вечер, а «дальше»
 * нажимают каждые три минуты, и одинаковый вес путал бы редкое с частым.
 */
@Composable
private fun Mode(
    text: String,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontSize = 17.sp,
        color = if (on) Sunset else NightMuted,
        textDecoration = if (on) TextDecoration.Underline else null,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick, onClickLabel = label)
            .padding(vertical = 8.dp),
    )
}

/**
 * Фон раздела: чёрный лист и закат, встающий из правого верхнего угла.
 *
 * Рисуется, а не собирается из картинки: градиент должен тянуться на любой
 * экран, а `drawBehind` знает его размер и кладёт круг ровно в угол.
 */
fun Modifier.sunsetBackground(): Modifier = drawBehind {
    drawRect(Night)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Sunset.copy(alpha = 0.55f),
                SunsetDeep.copy(alpha = 0.18f),
                Color.Transparent,
            ),
            center = Offset(size.width, 0f),
            radius = size.minDimension * 0.95f,
        ),
    )
}

/**
 * Иконки системных панелей на время раздела становятся светлыми: под ними
 * теперь чёрный фон, а не кремовый, и тёмные значки на нём исчезают. При
 * уходе всё возвращается — остальное приложение по-прежнему светлое.
 */
@Composable
private fun NightSystemBars() {
    val view = LocalView.current

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLightStatus = controller?.isAppearanceLightStatusBars
        val wasLightNavigation = controller?.isAppearanceLightNavigationBars

        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false

        onDispose {
            wasLightStatus?.let { controller?.isAppearanceLightStatusBars = it }
            wasLightNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
}

/**
 * Разрешение на музыку: с Android 13 у аудио своё, до него — общее чтение
 * хранилища. Спрашивать «чтение хранилища» на новых версиях бессмысленно —
 * система его просто не даёт.
 */
private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/**
 * Разрешён ли микрофон. Спектр своей же сессии Android считает записью звука и
 * без этого права `Visualizer` не заводит — отсюда микрофон у плеера, который
 * ничего не записывает.
 */
private fun hasMicAccess(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

private fun hasAudioAccess(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

/**
 * Нужно ли просить право показывать уведомления. До Android 13 его выдавали
 * вместе с установкой, и спрашивать там нечего.
 */
private fun needsNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
