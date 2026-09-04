package app.askya.ui.echo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.askya.app.appContainer
import app.askya.echo.EchoBeat
import app.askya.echo.Track
import app.askya.echo.loadArtwork
import app.askya.ui.components.AskyaFlower
import app.askya.ui.theme.FlowerInk
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightPanel
import app.askya.ui.theme.NightPanelSoft
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Обложка дорожки.
 *
 * Сначала спрашивается у MediaStore (`albumart`) — там она уже разобрана и
 * лежит готовой картинкой. Не нашлось — читается из самого файла
 * `MediaMetadataRetriever`: у скачанных отдельными песнями обложка часто вшита
 * в тег, а альбома у них нет вовсе.
 *
 * `null` значит и «ещё ищу», и «обложки нет»: показывается в обоих случаях
 * одно и то же — цветок Askya, и различать эти состояния экрану незачем.
 */
@Composable
fun rememberCover(track: Track?): ImageBitmap? =
    rememberCover(albumId = track?.albumId ?: 0, uri = track?.uri)

/**
 * Та же обложка, но по альбому и файлу, а не по дорожке.
 *
 * Нужна карточке плейлиста: у плейлиста своей обложки нет, и он показывает
 * ту, что у первой сложенной в него песни, — целой дорожки для этого не надо.
 */
@Composable
fun rememberCover(albumId: Long, uri: String?): ImageBitmap? {
    val context = LocalContext.current
    var cover by remember(uri, albumId) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uri, albumId) {
        cover = loadArtwork(context, albumId, uri)?.asImageBitmap()
    }

    return cover
}

/**
 * Что стоит на месте обложки, пока её нет.
 *
 * Один цветок — знак приложения, и больше ничего. Прежде здесь стояла целая
 * вывеска: вордмарк, под ним слово «Echo», под ним цветок — три знака об одном
 * и том же на месте, где ждут картинку. Обложка есть не у всякой песни, и то,
 * что стоит вместо неё, человек видит чаще, чем сами обложки; вывеске,
 * повторяющей имя раздела, написанное тут же в шапке, столько показов не
 * нужно.
 *
 * Цветок берёт меньше половины стороны и стоит посередине: заглушка не должна
 * притворяться картинкой во весь квадрат.
 */
@Composable
fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Краска — выбранная человеком, а не закатная краска раздела: на месте
        // обложки стоит знак приложения, и он один и тот же во всей Askya.
        AskyaFlower(modifier = Modifier.fillMaxSize(0.42f))
    }
}

/** Обложка внутри квадрата: картинка, а если её нет — цветок Askya. */
@Composable
fun CoverImage(track: Track?, modifier: Modifier = Modifier) {
    val cover = rememberCover(track)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (cover == null) {
            CoverPlaceholder(modifier = Modifier.fillMaxSize())
        } else {
            Image(
                bitmap = cover,
                contentDescription = "Обложка",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Обложка размером с ноготь — для строк списка и карточек плейлистов.
 *
 * Пока обложки нет (или её нет вовсе), на её месте стоит цветок Askya: тот же
 * знак, что на большой обложке плеера, только мелко. Серый квадрат с нотой
 * выглядел бы заглушкой из чужого приложения.
 */
@Composable
fun CoverThumb(albumId: Long, uri: String?, modifier: Modifier = Modifier) {
    val cover = rememberCover(albumId, uri)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NightPanelSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (cover == null) {
            AskyaFlower(
                tint = FlowerInk.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxSize(0.5f),
            )
        } else {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Обложка плеера: то, на что смотрят, пока играет песня.
 *
 * Живёт отдельно от экрана, потому что она у Echo одна на два места — на
 * плеере и в полноэкранной карточке ([EchoFullCard]). Разойдись эти две
 * обложки хоть жестом, хоть скруглением — и вход в полный экран перестал бы
 * читаться как увеличение той же вещи.
 *
 * ## Смена песни — это смена пластинки
 *
 * Обложка смахивается вбок: влево — следующая песня, вправо — прошлая. Идёт
 * она за пальцем один к одному, а не вполсилы, как раньше: рука двигает не
 * «немножко картинку», а саму пластинку, и всё, что происходит на экране,
 * происходит ровно на столько, на сколько её увели. Отпущенный палец — это и
 * есть конец действия: до него ничего не решено и всё обратимо.
 *
 * Уходящая обложка не уезжает плоско: она отрывается от листа — растёт,
 * забирает тень и заваливается набок, как поднятая с диска пластинка. А
 * из-под неё, с глубины, поднимается следующая: мелкая и тёмная в начале
 * хода, она проворачивается, разворачивается лицом и укладывается ровно на
 * то место, где лежала прежняя, — как пластинка ложится на диск граммофона.
 *
 * Порог — треть стороны обложки, а не «любой сдвиг»: палец, лежащий на
 * картинке, дрожит, и менять от этого песню нельзя. Не дотянули — пружина
 * возвращает обе на места, и та, что поднималась, уходит обратно в глубину.
 * Дотянули — обложка долетает до края и уступает место уже уложенной.
 *
 * Смахивать некуда (очередь пуста или в ней одна дорожка) — обложка идёт за
 * пальцем вполсилы и возвращается: движение отвечает, но обещания подмены не
 * даёт.
 *
 * ## Что осталось на месте
 *
 * Кнопки `Back` и `Next` — смахивание не заменяет их, а добавляет то же
 * движение к самой большой вещи на экране: до середины экрана палец достаёт
 * из любого положения руки, а до слова внизу — не всегда.
 *
 * Венец вспышек ([CoverPulse]) больше не едет вместе с обложкой, а держит
 * место: он и есть тот диск, с которого пластинку снимают и на который
 * кладут следующую. Прежде свет ездил за картинкой, потому что двигалась она
 * одна и отставший венец читался бы дыркой в экране; теперь в этом месте
 * всегда что-то лежит.
 *
 * Нажатие ([onTap]) уводит в полноэкранную карточку и возвращает обратно:
 * увеличить картинку нажатием на неё же — единственное, чего от неё ждут.
 */
@Composable
fun PlayerCover(
    track: Track?,
    side: Dp,
    beat: EchoBeat,
    playing: Boolean,
    hearing: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Обложка читается здесь одна на всех: и картинкой, и цветами вспышек.
    // Дважды её не разбирают — то же изображение отдаёт кэш `loadArtwork`.
    val cover = rememberCover(track)
    val colors = rememberPulseColors(cover)

    // Соседние дорожки спрашиваются у плеера, а не считаются из очереди на
    // экране: вперемешку «следующая в списке» и «следующая на самом деле» —
    // разные песни, и порядок обхода знает только он.
    val player = appContainer().echoPlayer
    val state by player.state.collectAsStateWithLifecycle()
    val ahead = remember(state.track?.uri, state.queue, state.shuffle) { player.peek(+1) }
    val behind = remember(state.track?.uri, state.queue, state.shuffle) { player.peek(-1) }

    // Обе соседние обложки читаются заранее, а не в начале движения: разбор
    // картинки занимает десятки миллисекунд, и поднимающаяся из глубины
    // пластинка успела бы показать пустое место вместо себя.
    val aheadCover = rememberCover(ahead)
    val behindCover = rememberCover(behind)

    val shift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val sidePx = with(density) { side.toPx() }

    // Порог — доля самой обложки, а не постоянная в точках: на боку она втрое
    // меньше, и 56 точек значили бы там совсем другое движение.
    val enough = sidePx * COMMIT_SHARE
    val travel = sidePx * FLY_SHARE

    // Дорожка, которую смахнули, — ждём, пока плеер поставит новую.
    //
    // Без этого ожидания обложка на кадр возвращалась бы в середину экрана
    // старой картинкой: возврат на место случается сразу, а новое состояние
    // плеера доезжает до разметки следующим кадром.
    var leaving by remember { mutableStateOf<String?>(null) }

    // Та пластинка, что уже уложилась на место. Пока идёт подмена, соседи
    // пересчитываются вслед за плеером, и без этой засечки под уехавшей
    // обложкой мигнула бы дорожка через одну.
    var settled by remember { mutableStateOf<Track?>(null) }

    val moved = shift.value
    val incoming = settled ?: when {
        moved < 0f -> ahead
        moved > 0f -> behind
        else -> null
    }
    val incomingCover = when (incoming?.uri) {
        null -> null
        ahead?.uri -> aheadCover
        behind?.uri -> behindCover
        else -> null
    }

    // Насколько новая пластинка уже улеглась: ноль — она в глубине, единица —
    // лежит ровно на месте. Тем же числом уходящая отрывается от листа.
    val settling = (abs(moved) / enough).coerceIn(0f, 1f)
    // Насколько уходящая долетела до края — по нему она и гаснет.
    val flying = (abs(moved) / travel).coerceIn(0f, 1f)
    val away = if (moved < 0f) -1f else 1f

    LaunchedEffect(track?.uri, leaving) {
        val gone = leaving ?: return@LaunchedEffect
        if (track?.uri != gone) {
            leaving = null
            settled = null
            shift.snapTo(0f)
            return@LaunchedEffect
        }
        // Плеер промолчал: файла не оказалось, очередь опустела. Ждать подмену
        // бесконечно нельзя — экран остался бы вовсе без обложки.
        delay(700)
        if (leaving == gone) {
            leaving = null
            settled = null
            shift.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 380f))
        }
    }

    Box(modifier = modifier.size(side), contentAlignment = Alignment.Center) {
        CoverPulse(
            beat = beat,
            colors = colors,
            playing = playing,
            hearing = hearing,
            modifier = Modifier.fillMaxSize(),
        )

        // Пластинка, поднимающаяся с глубины. Рисуется под уходящей и только
        // тогда, когда есть куда смахивать.
        if (incoming != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val rise = 1f - settling
                        scaleX = DEPTH_SCALE + (1f - DEPTH_SCALE) * settling
                        scaleY = scaleX
                        // Проворот вокруг своей оси и завал в глубину: вместе
                        // они читаются как пластинка, которую опускают на диск,
                        // а не как картинка, которая просто выросла.
                        rotationZ = -away * SPIN * rise
                        rotationX = TILT * rise
                        translationY = size.height * DEPTH_LIFT * rise
                        cameraDistance = 16f * density.density
                        alpha = 0.2f + 0.8f * settling
                        shape = CoverShape
                        clip = true
                    }
                    .background(NightPanel)
                    .border(1.dp, NightBorder, CoverShape),
                contentAlignment = Alignment.Center,
            ) {
                CoverFace(cover = incomingCover)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = moved
                    // Отрыв от листа: чем дальше увели, тем крупнее картинка и
                    // тем гуще тень под ней.
                    val lift = 1f + LIFT * settling
                    scaleX = lift
                    scaleY = lift
                    rotationZ = away * SPIN * settling * 0.6f
                    shadowElevation = SHADOW * settling
                    alpha = 1f - flying
                    shape = CoverShape
                    clip = true
                }
                .background(NightPanel)
                .border(1.dp, NightBorder, CoverShape)
                .pointerInput(ahead?.uri, behind?.uri) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            // Смахивать некуда — обложка отвечает вполсилы и
                            // ничего не обещает.
                            val target = if (shift.value + delta < 0f) ahead else behind
                            val step = if (target == null) delta * 0.35f else delta
                            scope.launch { shift.snapTo(shift.value + step) }
                        },
                        onDragEnd = {
                            val gone = shift.value
                            val dir = if (gone < 0f) -1f else 1f
                            val target = if (gone < 0f) ahead else behind
                            scope.launch {
                                if (abs(gone) >= enough && target != null) {
                                    settled = target
                                    shift.animateTo(
                                        targetValue = dir * travel,
                                        animationSpec = tween(220, easing = LinearOutSlowInEasing),
                                    )
                                    leaving = track?.uri
                                    if (dir < 0f) onNext() else onPrevious()
                                } else {
                                    shift.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                shift.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 420f))
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { onTap() }
                },
            contentAlignment = Alignment.Center,
        ) {
            CoverFace(cover = cover)
        }
    }
}

/** Лицо пластинки: картинка, а если её нет — цветок Askya. */
@Composable
private fun CoverFace(cover: ImageBitmap?) {
    if (cover == null) {
        CoverPlaceholder(modifier = Modifier.fillMaxSize())
    } else {
        Image(
            bitmap = cover,
            contentDescription = "Обложка",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Скругление обложки. Одно на обе пластинки — иначе подмена видна стыком. */
private val CoverShape = RoundedCornerShape(28.dp)

/** Доля стороны, после которой отпущенный палец меняет песню. */
private const val COMMIT_SHARE = 0.34f

/** Куда обложка улетает, когда песню всё-таки сменили. */
private const val FLY_SHARE = 1.25f

/** Насколько мелкой поднимается пластинка из глубины. */
private const val DEPTH_SCALE = 0.62f

/** И насколько низко она при этом лежит — долей своей высоты. */
private const val DEPTH_LIFT = 0.12f

/** Проворот пластинки, градусы: заметный, но не карусель. */
private const val SPIN = 16f

/** Завал в глубину у той, что поднимается. */
private const val TILT = 24f

/** Насколько уходящая отрывается от листа. */
private const val LIFT = 0.06f

/** Тень под оторванной, в точках. */
private const val SHADOW = 26f

/**
 * Обложка, размытая до пятна цвета, — фон полноэкранной карточки.
 *
 * Размывается уменьшением, а не `Modifier.blur`: тот появился в Android 12, а
 * Askya живёт с восьмого, и на телефонах постарше фон остался бы резкой
 * картинкой во весь экран. Обложка, сжатая до трёх десятков точек и
 * растянутая обратно сглаживанием, даёт ровно то, что нужно, — свет от
 * картинки вместо самой картинки, и стоит это доли миллисекунды.
 *
 * Считается в стороне от главного потока и заново только при смене обложки:
 * фон не должен пересчитываться от того, что кто-то нажал «дальше».
 */
@Composable
fun rememberBlurredCover(cover: ImageBitmap?): ImageBitmap? {
    var blurred by remember(cover) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(cover) {
        if (cover == null) {
            blurred = null
            return@LaunchedEffect
        }
        blurred = withContext(Dispatchers.IO) {
            runCatching {
                android.graphics.Bitmap
                    .createScaledBitmap(cover.asAndroidBitmap(), BLUR_SIDE, BLUR_SIDE, true)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    return blurred
}

/** До скольких точек сжимается обложка, чтобы стать размытием. */
private const val BLUR_SIDE = 28
