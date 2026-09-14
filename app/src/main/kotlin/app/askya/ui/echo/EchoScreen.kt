package app.askya.ui.echo

import app.askya.resources.Res
import app.askya.resources.ic_wordmark_echo
import org.jetbrains.compose.resources.painterResource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Science
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
import app.askya.app.androidContainer
import app.askya.echo.EchoBeat
import app.askya.echo.EchoRepeat
import app.askya.echo.Track
import app.askya.echo.formatDuration
import app.askya.ui.components.EmptyState
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.fadingVerticalScroll
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
    val container = androidContainer()
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
    // Церемония играется на каждый вход в раздел — обычным `remember`, а не
    // `rememberSaveable`.
    //
    // Saveable здесь был ошибкой, и она стоила разделу входа: уходя из Echo,
    // навигация сохраняет состояние его страницы (`saveState`) и возвращает
    // его при следующем заходе (`restoreState`). Вместе со всем прочим
    // возвращался и снятый флаг — то есть цветок показывался ровно один раз за
    // установку приложения, а дальше раздел открывался пустым плеером.
    //
    // Поворот экрана этому флагу не страшен: Activity объявлена
    // `configChanges="orientation|screenSize|…"` и при повороте не
    // пересоздаётся — композиция, а с ней и `remember`, остаются на месте.
    // Пересобирается всё только при смерти процесса, а там церемония уместна:
    // это и есть новый вход в раздел.
    var opening by remember { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }

    // Вопрос «что поставить» — то, чем раздел встречает вошедшего.
    //
    // Тем же обычным `remember`, что и церемония, и по той же причине: он
    // задаётся на каждый вход в Echo, а не один раз за установку. [asked]
    // держит его от повтора внутри одного захода — библиотека дочитывается
    // уже после занавеса, и без засечки вопрос всплыл бы снова.
    var start by remember { mutableStateOf(false) }
    var asked by remember { mutableStateOf(false) }

    // Лаборатория — работа с самими файлами. Открывается долгим нажатием на
    // вкладку и строкой в настройках; см. [EchoLabCard].
    var lab by remember { mutableStateOf(false) }

    // Дорожка, на которой остановились, — ею подписана карточка «Продолжить».
    val lastTrack by preferences.lastTrack
        .collectAsStateWithLifecycle(initialValue = preferences.last.value)

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

    /*
     * Когда спрашивать. Всё сразу: занавес снят, доступ есть, музыка прочитана
     * и она не пуста.
     *
     * И только если ничего не играет: человек, вернувшийся в раздел посреди
     * песни, уже ответил на этот вопрос — переспрашивать значило бы предлагать
     * ему прервать самого себя.
     */
    LaunchedEffect(opening, granted, tracks, echoSettings.askOnStart, state.track) {
        if (asked || opening || !granted || !echoSettings.askOnStart) return@LaunchedEffect
        if (state.track != null) {
            asked = true
            return@LaunchedEffect
        }
        val found = tracks ?: return@LaunchedEffect
        asked = true
        start = found.isNotEmpty()
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
                onNavigationClick = { closing = true },
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
                    LabLink(
                        // Раздел без разрешения показал бы пустые списки —
                        // проще сразу попросить доступ, чем открывать пустоту.
                        onOpen = { if (granted) lab = true else ask.launch(audioPermission()) },
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
                    // Лаборатория, наоборот, настройки закрывает: она сама во
                    // весь экран, и оставлять карточку под ней незачем.
                    onLab = {
                        settings = false
                        lab = true
                    },
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

            if (lab) {
                EchoLabCard(
                    library = tracks,
                    onClose = { lab = false },
                    onChanged = { reread++ },
                    // Выбрал песню — лаборатория закрывается: сюда приходят за
                    // тем, чтобы что-нибудь заиграло, и держать список поверх
                    // заигравшего значило бы прятать его от того, кто выбрал.
                    onPlay = { queue, track ->
                        player.play(queue, track)
                        lab = false
                    },
                )
            }

            // Вопрос стоит над плеером, но под занавесом: церемония входа
            // идёт первой, а спрашивают уже у того, кто вошёл.
            if (start) {
                EchoStartCard(
                    library = tracks,
                    last = lastTrack,
                    onResume = {
                        // Плееру нечего продолжать — карточка «Продолжить»
                        // тогда и не показывается, но состояние могло
                        // устареть, пока карточка была открыта.
                        player.resume()
                    },
                    onPlay = { queue, track ->
                        player.play(queue, track)
                        start = false
                    },
                    onSection = { chosen ->
                        section = chosen
                        start = false
                    },
                    onDismiss = { start = false },
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

/**
 * Списки музыки, открывающиеся поверх плеера.
 *
 * Кнопок под шапкой у них больше нет — там теперь одна дверь, Lab. Сами
 * разделы остались: ими открывается [EchoSectionLayer], и зовёт его карточка
 * входа («Плейлист», «Папка»), где выбор — это выбор того, что сейчас
 * заиграет, а не работа с файлами.
 */
enum class EchoSection(val label: String, val icon: ImageVector) {
    ALL_MUSIC("Вся музыка", Icons.Outlined.LibraryMusic),
    PLAYLISTS("Плейлисты", Icons.Outlined.QueueMusic),
    FOLDERS("Папки", Icons.Outlined.FolderOpen),
}

/**
 * Единственная кнопка под шапкой — дверь в лабораторию.
 *
 * Прежде их было три: «Вся музыка», «Плейлисты», «Папки». Три двери в один и
 * тот же дом — за всеми лежит музыка телефона, разложенная по-разному, — и,
 * что хуже, за каждой можно было только включить песню: всё остальное, что с
 * ней делают, жило в отдельной комнате, о которой надо было знать. Теперь
 * дверь одна, а те три стали её страницами ([EchoLabCard]).
 *
 * Слово английское и короткое, как имена самих разделов: Scroll, Echo, Ledger,
 * Lab. «Лаборатория» кириллицей и во всю ширину читалась бы вывеской на
 * заводе, а это дверь.
 *
 * Подпись рядом — не украшение: за словом Lab не угадать, что там музыка,
 * списки и папки, а строка под ним говорит это прямо и один раз.
 */
@Composable
private fun LabLink(onOpen: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, NightBorder, RoundedCornerShape(14.dp))
                .background(NightPanel)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Science,
                contentDescription = null,
                tint = Sunset,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Lab",
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                color = NightInk,
            )
            Text(
                text = "вся музыка, плейлисты, папки",
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                        .fadingVerticalScroll(),
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
                        wordHeight = 28.dp,
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
            text = track?.artist ?: "Выбери песню в Lab",
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
        EchoControl("Back", "Прошлая", onPrevious, Modifier.weight(1f))
        EchoControl(
            text = if (playing) "Pause" else "Play",
            label = if (playing) "Пауза" else "Играть",
            onClick = onToggle,
            modifier = Modifier.weight(1f),
            accent = true,
        )
        EchoControl("Next", "Следующая", onNext, Modifier.weight(1f))
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
        EchoMode("Shuffle", "Вперемешку", shuffle, onShuffle, Modifier.weight(1f))
        EchoMode(
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
 *
 * Подпись — «EQ», а не «Эквалайзер»: слово в одиннадцать букв стоит под самой
 * нижней кнопкой экрана, где место меряется на глаз, и в узком телефоне оно
 * растягивало ряд под собой. «EQ» на панели плеера читается всеми, кто вообще
 * знает, что такое эквалайзер, — теми же двумя буквами он подписан и в
 * магнитоле, и в самом VLC. Полное слово осталось там, где его слышат, а не
 * видят: в подписи для чтения с экрана.
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
            text = "EQ",
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
 * ## Мельче и шире, чем написано пером
 *
 * Прежде высота была вдвое больше, а ширина считалась по ней один в один:
 * слово стояло под обложкой плотным чёрным бруском и спорило с ней за
 * внимание — а мерить время должно то, на что смотрят вторым взглядом, а не
 * первым. Теперь оно ниже и растянуто вдоль экрана [stretch]: перо в мелком
 * кегле, вытянутое по горизонтали, читается росчерком — тем самым, каким
 * подписывают, а не вывеской.
 *
 * Растяжение задано числом, а не «во всю ширину»: перо, растянутое насколько
 * попало, перестаёт быть пером — на широком экране слово доходит до края и
 * дальше не тянется. Подписи времени держатся той же ширины, что и слово: они
 * подписывают его, а не экран.
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
    wordHeight: Dp = 34.dp,
    stretch: Float = 1.6f,
) {
    val word = painterResource(Res.drawable.ic_wordmark_echo)
    val height = wordHeight
    val width: Dp = height * (word.intrinsicSize.width / word.intrinsicSize.height) * stretch

    Column(
        modifier = modifier.widthIn(max = width).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
