package app.askya.ui.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.echo.formatDuration
import app.askya.ui.echo.EchoControl
import app.askya.ui.echo.EchoLine
import app.askya.ui.echo.EchoMode
import app.askya.ui.theme.Night
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.Sunset
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs

/** Какая карточка открыта поверх плеера. */
private enum class PlayerPanel {
    TRACKS,
    SPEED,
}

/**
 * Как стоит экран во время просмотра.
 *
 * [BY_FILE] — по кадру: широкий разворачивает телефон поперёк, снятый стоя
 * оставляет стоя. Прежде разворот был односторонним — поперёк уводило всё
 * подряд, включая вертикальное видео, потому что размер кадра брался таким,
 * каким он записан в файле, без метки поворота (см. `VideoState.videoRotated`).
 *
 * Остальные два — рука человека: файл бывает снят боком, кровать бывает
 * повёрнута, и последнее слово всегда за тем, кто смотрит.
 */
private enum class PlayerTurn(val caption: String) {
    BY_FILE("Экран по кадру"),
    PORTRAIT("Экран стоя"),
    LANDSCAPE("Экран поперёк"),
    ;

    /** Следующий: кнопка одна, а состояний три. */
    fun next(): PlayerTurn = entries[(ordinal + 1) % entries.size]
}

/** Что человек крутит пальцем прямо сейчас. */
private enum class Sliding {
    NONE,
    SEEK,
    BRIGHTNESS,
    VOLUME,
}

/**
 * Плеер AskyaV во весь экран.
 *
 * Устроен как плеер, а не как страница с видео: экран занят кадром целиком,
 * системные полосы убраны, всё управление приходит поверх по касанию и уходит
 * через несколько секунд. Кино смотрят, а не пользуются им.
 *
 * ## Жесты — те же, что в VLC
 *
 * Их не выдумывали заново: человек, пришедший из VLC, должен попасть пальцем
 * туда же, куда привык. Слева вверх-вниз — яркость, справа вверх-вниз —
 * громкость, поперёк экрана — перемотка, двойное касание слева и справа —
 * шаг назад и вперёд. Одно касание показывает и прячет управление.
 *
 * Замок в шапке выключает всё это разом: телефон в руках посреди фильма живёт
 * своей жизнью, и случайная перемотка на середину — худшее, что он может
 * сделать.
 *
 * ## Громкость — своя, а не системная
 *
 * Жест громкости крутит громкость самого VLC (до 200 %), а не системную. Так
 * тихую дорожку можно вытянуть выше системного потолка — ровно за этим её и
 * крутят в VLC, — и так плеер не меняет громкость всего телефона, выйдя из
 * которого человек получил бы оглушительное уведомление.
 *
 * ## Место остановки
 *
 * Пишется не по кнопке, а само: каждые десять секунд, на каждой паузе и при
 * уходе с экрана. Фильм закрывают как придётся — кнопкой «домой», звонком,
 * разрядившейся батареей, — и «сохранить просмотр» человек не нажмёт никогда.
 */
@Composable
fun VideoPlayerScreen(onClose: () -> Unit) {
    val container = appContainer()
    val engine = container.videoEngine
    val preferences = container.videoPreferences
    val state by engine.state.collectAsStateWithLifecycle()
    val settings by preferences.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }

    var controls by remember { mutableStateOf(true) }
    // Открытое по ссылке нигде не оставалось. Решение «оставить» приходит не в
    // окне ввода, а во время просмотра: посмотрел и понял, что будет открывать
    // это ещё. У файла с телефона такое действие уже есть в его карточке.
    var locked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    // Поворот держится за файлом, а не за экраном: следующий фильм — своя
    // история, и повёрнутое руками на прошлом ему не наследуется.
    var turn by remember(state.source?.uri) { mutableStateOf(PlayerTurn.BY_FILE) }

    // Что показывает подсказка посреди экрана, пока палец на экране.
    var sliding by remember { mutableStateOf(Sliding.NONE) }
    var hint by remember { mutableStateOf("") }
    // Подсказка двойного касания: пальца на экране уже нет, и убрать её
    // отрывом некому — она гаснет по времени. Счётчик нужен, чтобы второе
    // касание подряд начинало отсчёт заново.
    var flash by remember { mutableStateOf<String?>(null) }
    var flashes by remember { mutableIntStateOf(0) }
    // Перемотка пальцем накапливается и применяется на отрыве: дёргать плеер
    // на каждом движении значит показывать кашу вместо перемотки.
    var seekTo by remember { mutableStateOf(0L) }
    var brightness by remember { mutableFloatStateOf(currentBrightness(activity)) }

    val source = state.source

    // ---- Уход с экрана и запись места ----

    // Последнее состояние — чтобы уходящий эффект писал место, а не ноль.
    val latest by rememberUpdatedState(state)
    DisposableEffect(Unit) {
        onDispose {
            val leaving = latest
            leaving.source?.let {
                preferences.remember(it.uri, leaving.positionMs, leaving.durationMs)
            }
        }
    }
    LaunchedEffect(source?.uri) {
        while (true) {
            delay(10_000)
            val now = latest
            now.source?.let { preferences.remember(it.uri, now.positionMs, now.durationMs) }
        }
    }
    LaunchedEffect(state.playing) {
        if (!state.playing) {
            val now = latest
            now.source?.let { preferences.remember(it.uri, now.positionMs, now.durationMs) }
        }
    }

    BackHandler {
        if (panel != null) panel = null
        else if (locked) locked = false
        else onClose()
    }

    // ---- Экран: не гаснет, без полос, поперёк для широкого кадра ----

    DisposableEffect(settings.keepAwake) {
        if (settings.keepAwake) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Экран встаёт по кадру: широкий разворачивает телефон поперёк, снятый
    // стоя оставляет стоя. И то и другое — «по датчику», а не намертво: внутри
    // выбранной стороны телефон слушается руки, и перевернуть его через голову
    // можно всегда.
    //
    // Сам разворот — только если человек его разрешил (настройка раздела) и
    // только пока он не сказал иначе кнопкой в шапке: сказанное рукой сильнее
    // всего, что плеер вычитал из файла.
    DisposableEffect(turn, settings.autoRotate, state.landscape, state.portrait) {
        val wanted = when {
            turn == PlayerTurn.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            turn == PlayerTurn.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            !settings.autoRotate -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            state.landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            state.portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            // Размера кадра ещё не знаем — пусть телефон стоит как стоял.
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        activity?.requestedOrientation = wanted
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // ---- Сторож: плеер иногда встаёт совсем ----
    //
    // Разбор изредка замирает насмерть: кадр стоит, время не идёт, кнопки
    // нажимаются впустую. До сих пор помогал только выход из раздела — там
    // файл закрывается и открывается заново, — и это ровно то, что делается
    // здесь, только само и с той секунды, на которой встало.
    //
    // Считается по времени плеера, а не по картинке: время — единственное, что
    // говорит, идёт ли разбор. Буферизация и пауза сторожа не будят: там
    // стоящее время законно.
    //
    // **Перезапуск на файл один.** Прежде сторож будил плеер сколько угодно
    // раз, и на файле, который не даётся вовсе, это становилось петлёй:
    // открылся, не пошёл, встал, открылся заново — и так до тех пор, пока
    // приложение не переставало отвечать. Один заход честен: он лечит
    // случайную заминку разбора и не притворяется, что вылечит неигоспособный
    // файл. Второе зависание подряд — это уже не заминка, и сказать об этом
    // словами полезнее, чем открывать в третий раз.
    var restarted by remember(source?.uri) { mutableStateOf(false) }
    LaunchedEffect(state.playing, source?.uri) {
        if (!state.playing) return@LaunchedEffect
        var was = -1L
        var still = 0
        while (true) {
            delay(1_000)
            val now = engine.state.value
            if (!now.playing || now.buffering) {
                was = now.positionMs
                still = 0
                continue
            }
            still = if (now.positionMs == was) still + 1 else 0
            was = now.positionMs
            if (still >= STALL_SECONDS) {
                if (restarted) {
                    flash = "Плеер снова встал — этот файл ему не даётся"
                    flashes++
                    engine.pause()
                    return@LaunchedEffect
                }
                restarted = true
                flash = "Плеер встал — открываю заново, без железного разбора"
                flashes++
                engine.restart()
                return@LaunchedEffect
            }
        }
    }

    // Управление уходит само — но только пока фильм идёт: на паузе оно нужно.
    LaunchedEffect(controls, state.playing, panel, sliding) {
        if (controls && state.playing && panel == null && sliding == Sliding.NONE) {
            delay(3_500)
            controls = false
        }
    }

    val stepMs = settings.seekStepSeconds * 1000L

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ---- Сам кадр ----
        //
        // Место под кадр заводится заново на каждый пересозданный плеер
        // ([VideoState.generation]): замерший VLC поверхность по-хорошему не
        // отдаёт, и отбирать её у него — значит ждать того, кто уже не
        // отвечает. Пока плеер один и тот же, число не меняется и лист живёт
        // своей обычной жизнью — поворот экрана его не пересоздаёт.
        key(state.generation) {
            AndroidView(
                factory = { ctx ->
                    VLCVideoLayout(ctx).also { layout -> engine.attach(layout) }
                },
                onRelease = { layout -> engine.detach(layout) },
                modifier = Modifier
                    .fillMaxSize()
                    // Поворот экрана меняет место под кадром, и без этого кадр
                    // остался бы нарисованным по старому размеру.
                    .onSizeChanged { engine.refreshSurfaces() },
            )
        }

        // ---- Жесты ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { if (!locked) controls = !controls else controls = true },
                        onDoubleTap = { offset ->
                            if (locked) return@detectTapGestures
                            val forward = offset.x > size.width / 2
                            engine.seekBy(if (forward) stepMs else -stepMs)
                            flash = (if (forward) "+" else "−") + "${settings.seekStepSeconds} с"
                            flashes++
                        },
                    )
                }
                .pointerInput(locked, settings.gestures, stepMs) {
                    if (locked || !settings.gestures) return@pointerInput
                    var axis = Sliding.NONE
                    var startX = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            axis = Sliding.NONE
                            startX = offset.x
                            seekTo = engine.state.value.positionMs
                        },
                        onDragEnd = {
                            if (axis == Sliding.SEEK) engine.seekTo(seekTo)
                            axis = Sliding.NONE
                            sliding = Sliding.NONE
                        },
                        onDragCancel = {
                            axis = Sliding.NONE
                            sliding = Sliding.NONE
                        },
                    ) { change, amount ->
                        change.consume()
                        // Ось выбирается на первом заметном движении и дальше
                        // не меняется: иначе дрогнувшая рука посреди подстройки
                        // яркости уводила бы фильм на середину.
                        if (axis == Sliding.NONE) {
                            if (abs(amount.x) < abs(amount.y)) {
                                axis = if (startX < size.width / 2) Sliding.BRIGHTNESS
                                else Sliding.VOLUME
                            } else {
                                axis = Sliding.SEEK
                            }
                            sliding = axis
                        }

                        when (axis) {
                            Sliding.SEEK -> {
                                // Экран во всю ширину — это две минуты перемотки:
                                // достаточно, чтобы попасть в нужную сцену, и
                                // мало, чтобы промахнуться на полфильма.
                                val perPixel = 120_000f / size.width
                                val duration = engine.state.value.durationMs
                                seekTo = (seekTo + (amount.x * perPixel).toLong())
                                    .coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
                                val delta = seekTo - engine.state.value.positionMs
                                hint = formatDuration(seekTo) + "  " +
                                    (if (delta >= 0) "+" else "−") + formatDuration(abs(delta))
                            }

                            Sliding.BRIGHTNESS -> {
                                brightness = (brightness - amount.y / size.height)
                                    .coerceIn(0.01f, 1f)
                                activity?.window?.let { window ->
                                    window.attributes = window.attributes.apply {
                                        screenBrightness = brightness
                                    }
                                }
                                hint = "Яркость ${(brightness * 100).toInt()} %"
                            }

                            Sliding.VOLUME -> {
                                val next = engine.state.value.volume -
                                    (amount.y / size.height * 200).toInt()
                                engine.setVolume(next)
                                hint = "Громкость ${engine.state.value.volume} %"
                            }

                            Sliding.NONE -> Unit
                        }
                    }
                },
        )

        // ---- Подсказка жеста ----
        LaunchedEffect(flashes) {
            if (flash != null) {
                delay(700)
                flash = null
            }
        }
        val overlay = if (sliding != Sliding.NONE) hint.takeIf { it.isNotEmpty() } else flash
        if (overlay != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Night.copy(alpha = 0.82f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(text = overlay, color = NightInk, fontSize = 18.sp)
            }
        }

        if (state.buffering && state.error == null) {
            CircularProgressIndicator(
                color = Sunset,
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
        }

        state.error?.let { message ->
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = message,
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    color = NightInk,
                )
                Text(
                    // У файла и у ссылки разные причины, и подсказка про
                    // форматы на оборванной раздаче только сбивает с толку:
                    // формат тут ни при чём, отвечал чужой сервер.
                    text = if (source?.network == true) {
                        "Сервер не отдал видео. Так бывает, когда ссылка устарела, " +
                            "закрыта паролем или ведёт на страницу с видео, а не на сам файл."
                    } else {
                        "Ни один плеер не открывает вообще всё. Этот открывает почти всё."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NightMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // ---- Управление ----
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    title = source?.title.orEmpty(),
                    locked = locked,
                    turned = turn != PlayerTurn.BY_FILE,
                    onBack = onClose,
                    onLock = { locked = !locked },
                    onTurn = {
                        turn = turn.next()
                        flash = turn.caption
                        flashes++
                    },
                    modifier = Modifier.align(Alignment.TopStart),
                )

                if (!locked) {
                    BottomBar(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        playing = state.playing,
                        rate = state.rate,
                        scale = state.scale.label,
                        stepSeconds = settings.seekStepSeconds,
                        subtitlesOn = state.subtitleTrackId >= 0,
                        onSeek = { engine.seekTo(it) },
                        onPlay = { engine.togglePlay() },
                        onBack10 = { engine.seekBy(-stepMs) },
                        onForward10 = { engine.seekBy(stepMs) },
                        onScale = { engine.setScale(state.scale.next()) },
                        onSpeed = { panel = PlayerPanel.SPEED },
                        onTracks = { panel = PlayerPanel.TRACKS },
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
        }

        when (panel) {
            PlayerPanel.TRACKS -> VideoTracksCard(onDismiss = { panel = null })
            PlayerPanel.SPEED -> VideoSpeedCard(onDismiss = { panel = null })
            null -> Unit
        }

    }
}

@Composable
private fun TopBar(
    title: String,
    locked: Boolean,
    turned: Boolean,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onTurn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Night.copy(alpha = 0.55f))
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!locked) {
            PlayerIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Закрыть", onBack)
        }
        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            color = NightInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        // Поворот стоит слева от замка: замок выключает всё, а этот трогают
        // руками — и после него замок как раз и запирают.
        if (!locked) {
            PlayerIcon(
                icon = Icons.Outlined.ScreenRotation,
                label = "Повернуть экран",
                onClick = onTurn,
                tint = if (turned) Sunset else NightInk,
            )
        }
        PlayerIcon(
            icon = if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
            label = if (locked) "Разблокировать" else "Заблокировать касания",
            onClick = onLock,
            tint = if (locked) Sunset else NightInk,
        )
    }
}

/**
 * Панель управления AskyaV — та же, что у AskyaEcho, и теми же словами.
 *
 * Раньше здесь стоял ряд значков Material и ползунок Material: катушка со
 * стрелкой, две палки, спидометр, рамка кадра. Работало, но выглядело панелью
 * из чужого приложения, положенной поверх Askya, — и главное, спорило с
 * соседним плеером: в Echo то же самое сказано словами, набранными пером.
 * Человек ходит из одного плеера в другой, и «Play» в обоих должно и значить,
 * и выглядеть одинаково ([EchoControl], [EchoMode], [EchoLine]).
 *
 * Порядок сверху вниз: время, три кнопки, режимы. Кнопок три и они крупные —
 * их нажимают, не глядя, посреди фильма; режимы мельче и в один ряд — их
 * трогают раз за вечер.
 *
 * «Back» и «Next» отматывают на шаг, а не переключают файл: у фильма нет
 * следующего, а у отмотки на десять секунд — есть кому её нажимать. Насколько
 * отматывает, сказано тут же, под словом: шаг настраивается, и молчаливая
 * стрелка о нём не говорит.
 */
@Composable
private fun BottomBar(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    rate: Float,
    scale: String,
    stepSeconds: Int,
    subtitlesOn: Boolean,
    onSeek: (Long) -> Unit,
    onPlay: () -> Unit,
    onBack10: () -> Unit,
    onForward10: () -> Unit,
    onScale: () -> Unit,
    onSpeed: () -> Unit,
    onTracks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Пока палец на полосе, время берётся из-под пальца, а не от плеера:
    // иначе граница цвета дёргалась бы назад между событиями плеера.
    var dragging by remember { mutableStateOf(false) }
    var draft by remember { mutableFloatStateOf(0f) }

    val progress = when {
        dragging -> draft
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Night.copy(alpha = 0.66f))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        EchoLine(
            progress = progress,
            durationMs = durationMs,
            enabled = durationMs > 0,
            onScrub = {
                dragging = true
                draft = it
            },
            onSeek = {
                draft = it
                dragging = false
                if (durationMs > 0) onSeek((it * durationMs).toLong())
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepControl(
                text = "Back",
                seconds = stepSeconds,
                label = "Назад на $stepSeconds секунд",
                onClick = onBack10,
                modifier = Modifier.weight(1f),
            )
            EchoControl(
                text = if (playing) "Pause" else "Play",
                label = if (playing) "Пауза" else "Играть",
                onClick = onPlay,
                modifier = Modifier.weight(1f),
                accent = true,
            )
            StepControl(
                text = "Next",
                seconds = stepSeconds,
                label = "Вперёд на $stepSeconds секунд",
                onClick = onForward10,
                modifier = Modifier.weight(1f),
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            EchoMode(
                text = speedWord(rate),
                label = "Скорость",
                // Обычная скорость — не режим: гореть закатом ей незачем.
                on = rate != 1f,
                onClick = onSpeed,
                modifier = Modifier.weight(1f),
            )
            EchoMode(
                text = scale,
                label = "Кадр в экране",
                on = false,
                onClick = onScale,
                modifier = Modifier.weight(1f),
            )
            EchoMode(
                text = "Subs",
                label = "Дорожки и субтитры",
                on = subtitlesOn,
                onClick = onTracks,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Кнопка отмотки: слово и под ним шаг.
 *
 * Шаг подписан цифрой, а не вшит в слово («Back 10»): он настраивается, и
 * строка «Back 10» при шаге в тридцать секунд врала бы. Цифра мелкая и стоит
 * под словом — так же, как у Echo под кнопками стоят режимы.
 */
@Composable
private fun StepControl(
    text: String,
    seconds: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EchoControl(
            text = text,
            label = label,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "$seconds с",
            style = MaterialTheme.typography.labelSmall,
            color = NightMuted,
        )
    }
}

/** «1,5x» — и «обычная» вместо «1x»: единица о скорости ничего не говорит. */
private fun speedWord(rate: Float): String =
    if (rate == 1f) "Speed" else "%.2f".format(rate).trimEnd('0').trimEnd('.', ',') + "x"

/** Круглая кнопка плеера — тот же круг, что в шапке экрана, только в темноте. */
@Composable
private fun PlayerIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = NightInk,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Сколько секунд стоящего времени считать зависанием.
 *
 * Пять, а не одна: на перемотке и на смене дорожки время замирает на мгновение
 * законно, и плеер, перезапускающийся от каждой такой заминки, был бы хуже
 * самой заминки.
 */
private const val STALL_SECONDS = 5

/** Activity из контекста Compose: он приходит завёрнутым в несколько слоёв. */
internal fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Яркость окна сейчас; -1 означает «как в системе» — считаем это половиной. */
private fun currentBrightness(activity: Activity?): Float {
    val value = activity?.window?.attributes?.screenBrightness ?: -1f
    return if (value < 0f) 0.5f else value
}
