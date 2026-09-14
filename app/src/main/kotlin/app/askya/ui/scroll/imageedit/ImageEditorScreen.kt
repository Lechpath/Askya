package app.askya.ui.scroll.imageedit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.androidContainer
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogField
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.scroll.NoShareDialog
import app.askya.ui.scroll.shareImage
import app.askya.ui.scroll.ScrollViewModel
import app.askya.ui.theme.Accent
import app.askya.ui.theme.CoralAccent
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Ink
import app.askya.ui.theme.PaperInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Правка картинки: повороты, обрезка, цвет, кисть и надписи.
 *
 * Своё, а не «открыть в чужом редакторе»: картинка теперь лежит в папке Askya
 * (см. ImageStore), и отдавать её наружу ради поворота на четверть значило бы
 * снова зависеть от того, что стоит на телефоне и вернёт ли оно файл обратно.
 *
 * Правки живут в памяти до «Сохранить», а «Отменить» ходит по ним назад.
 * Сохранение кладёт **новый** файл и переключает на него запись, старый
 * удаляет: перезапись на месте оставила бы в сетке старое превью (оно помнится
 * по ссылке, а ссылка не изменилась бы) и потеряла бы исходник, оборвись запись
 * на середине.
 */
@Composable
fun ImageEditorScreen(noteId: Long, onBack: () -> Unit) {
    val viewModel: ScrollViewModel = viewModel(factory = ScrollViewModel.factory(androidContainer()))
    val note by remember(noteId) { viewModel.note(noteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val store = androidContainer().imageStore
    val scope = rememberCoroutineScope()

    var edits by remember { mutableStateOf<ImageEdits?>(null) }
    var failed by remember { mutableStateOf(false) }

    // Что не получилось — словами. Раньше неудача сохранения только гасила
    // «Сохраняю…», и экран молча возвращался к правке: выглядело так, будто
    // ничего и не нажимали.
    var trouble by remember { mutableStateOf<String?>(null) }
    var noShare by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(Tool.Turn) }
    var asking by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<ImageEdits>() }

    // Чем рисуют сейчас. Не часть правок: это выбор инструмента, а не то, что
    // уже лежит на картинке.
    var color by remember { mutableStateOf(PALETTE.first()) }
    var brush by remember { mutableFloatStateOf(0.008f) }
    var letters by remember { mutableFloatStateOf(0.09f) }
    var chosen by remember { mutableStateOf(-1) }

    val uri = note?.uri
    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        val bitmap = decodeImage(context, uri)
        if (bitmap == null) failed = true else edits = ImageEdits(image = bitmap)
    }

    /** Запомнить нынешнее состояние как шаг назад. */
    fun remember0() {
        edits?.let { history.add(it) }
        if (history.size > HISTORY) history.removeAt(0)
    }

    fun apply(next: ImageEdits) {
        remember0()
        edits = next
    }

    fun undo() {
        if (history.isEmpty()) return
        edits = history.removeAt(history.lastIndex)
        chosen = -1
    }

    BackHandler(onBack = onBack)

    ScreenScaffold(
        title = "Правка",
        onNavigationClick = onBack,
        navigationIsBack = true,
        actions = {
            // «Поделиться» отправляет то, что на экране, вместе с
            // несохранёнными правками: показать другу повёрнутый снимок с
            // надписью — не то же самое, что решить оставить его себе.
            // Отправляемое пишется в кэш, а не в папку Askya (см.
            // ImageStore.shareableCopy).
            val ready = edits
            if (ready != null && !saving) {
                TextButton(onClick = {
                    scope.launch {
                        val rendered = withContext(Dispatchers.Default) { ready.render() }
                        val link = store.shareableCopy(rendered)
                        when {
                            link == null -> trouble = "Картинку не удалось подготовить к отправке — на телефоне нет места."
                            !shareImage(context, link) -> noShare = true
                        }
                    }
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Поделиться")
                }
            }
            if (history.isNotEmpty() && !saving) {
                TextButton(onClick = { undo() }) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Отменить")
                }
            }
            val current = edits
            if (current != null && !saving) {
                TextButton(onClick = {
                    saving = true
                    scope.launch {
                        val saved = store.save(current.render())
                        val target = note
                        if (saved != null && target != null) {
                            viewModel.replaceImage(target, saved) { onBack() }
                        } else {
                            saving = false
                            trouble = "Правку не удалось сохранить — на телефоне нет места."
                        }
                    }
                }) { Text("Сохранить") }
            }
        },
    ) {
        val current = edits
        when {
            current == null && failed -> Message("Картинка не открылась — файл удалили или он испорчен.")
            current == null -> Message("Читаю…")
            saving -> Message("Сохраняю…")
            else -> Column(modifier = Modifier.fillMaxSize()) {
                Preview(
                    edits = current,
                    tool = tool,
                    color = color,
                    brush = brush,
                    chosen = chosen,
                    onChoose = { chosen = it },
                    onBegin = { remember0() },
                    onLive = { edits = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )

                ToolPanel(
                    tool = tool,
                    edits = current,
                    color = color,
                    brush = brush,
                    letters = letters,
                    chosen = chosen,
                    onColor = { picked ->
                        color = picked
                        if (chosen in current.texts.indices) {
                            apply(current.copy(texts = current.texts.replaced(chosen) { it.copy(color = picked) }))
                        }
                    },
                    onBrush = { brush = it },
                    onLetters = { size ->
                        letters = size
                        if (chosen in current.texts.indices) {
                            edits = current.copy(texts = current.texts.replaced(chosen) { it.copy(size = size) })
                        }
                    },
                    onAddText = { asking = true },
                    onDropText = {
                        if (chosen in current.texts.indices) {
                            apply(current.copy(texts = current.texts.filterIndexed { i, _ -> i != chosen }))
                            chosen = -1
                        }
                    },
                    onApply = { apply(it) },
                    onBegin = { remember0() },
                    onLive = { edits = it },
                )

                ToolRow(tool = tool, onPick = { tool = it; chosen = -1 })
            }
        }
    }

    trouble?.let { message ->
        AskyaNotice(
            title = "Не вышло",
            text = message,
            onDismiss = { trouble = null },
        )
    }

    if (noShare) NoShareDialog(onDismiss = { noShare = false })

    if (asking) {
        TextDialog(
            onDismiss = { asking = false },
            onAdd = { text ->
                asking = false
                val current = edits ?: return@TextDialog
                // Появляется посередине видимого куска: ставить её пальцем всё
                // равно придётся, а середина видна на любой картинке.
                val added = current.texts + TextItem(text, current.crop.middle(), color, letters)
                apply(current.copy(texts = added))
                chosen = added.lastIndex
            },
        )
    }
}

/** Какой инструмент раскрыт. Один за раз: панели разные и в ряд не влезут. */
private enum class Tool(val label: String, val icon: ImageVector) {
    Turn("Поворот", Icons.AutoMirrored.Outlined.RotateLeft),
    Crop("Обрезка", Icons.Outlined.Crop),
    Color("Цвет", Icons.Outlined.Tune),
    Draw("Кисть", Icons.Outlined.Brush),
    Text("Текст", Icons.Outlined.TextFields),
}

/**
 * Картинка со всем, что на ней, и жесты по ней.
 *
 * В обрезке показывается картинка целиком, а рамка лежит поверх: чтобы вернуть
 * отрезанное, надо видеть, что отрезано. В остальных режимах видно только то,
 * что останется, — правят по тому, что получится.
 */
@Composable
private fun Preview(
    edits: ImageEdits,
    tool: Tool,
    color: Color,
    brush: Float,
    chosen: Int,
    onChoose: (Int) -> Unit,
    onBegin: () -> Unit,
    onLive: (ImageEdits) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val visible = if (tool == Tool.Crop) NormRect.Full else edits.crop
        val box = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val view = remember(edits.image, visible, box) {
            Placement(edits.image.width, edits.image.height, visible, box)
        }
        val image = remember(edits.image) { edits.image.asImageBitmap() }
        val matrix = remember(edits.brightness, edits.contrast, edits.saturation) {
            ColorMatrix(edits.colorValues())
        }

        // Правки читаются через rememberUpdatedState, а в ключи pointerInput
        // не идут. Иначе каждая точка штриха меняла бы ключ, блок жестов
        // перезапускался бы и перетаскивание обрывалось на первом же кадре —
        // от штриха оставалась одна точка.
        val latest = rememberUpdatedState(edits)

        // Слоями: у надписей жеста два (выбрать касанием, передвинуть
        // перетаскиванием), и в одном detect-блоке они мешали бы друг другу.
        val gestures = when (tool) {
            Tool.Draw -> Modifier.pointerInput(view, color, brush) {
                drawGestures(view, color, brush, latest, onBegin, onLive)
            }
            Tool.Crop -> Modifier.pointerInput(view) { cropGestures(view, latest, onBegin, onLive) }
            Tool.Text -> Modifier
                .pointerInput(view) {
                    detectTapGestures { point ->
                        onChoose(latest.value.texts.nearestTo(view.toImage(point)))
                    }
                }
                .pointerInput(view, chosen) { textDrag(view, latest, chosen, onBegin, onLive) }
            else -> Modifier
        }

        Canvas(modifier = Modifier.fillMaxSize().then(gestures)) {
            val src = visible.toPixels(edits.image.width, edits.image.height)
            drawImage(
                image = image,
                srcOffset = IntOffset(src.left, src.top),
                srcSize = IntSize(src.width(), src.height()),
                dstOffset = IntOffset(view.left.roundToInt(), view.top.roundToInt()),
                dstSize = IntSize(view.width.roundToInt(), view.height.roundToInt()),
                colorFilter = ColorFilter.colorMatrix(matrix),
                filterQuality = FilterQuality.Medium,
            )

            drawOverlay(edits, view, if (tool == Tool.Text) chosen else -1)
            if (tool == Tool.Crop) drawCropFrame(edits.crop, view)
        }
    }
}

/** Штрихи и надписи поверх картинки — теми же числами, что и при сохранении. */
private fun DrawScope.drawOverlay(edits: ImageEdits, view: Placement, chosen: Int) {
    val short = min(edits.image.width, edits.image.height)

    edits.strokes.forEach { stroke ->
        val width = (stroke.width * short * view.perImagePx).coerceAtLeast(1f)
        if (stroke.points.size == 1) {
            drawCircle(stroke.color, radius = width / 2f, center = view.toScreen(stroke.points.first()))
        } else {
            val path = Path()
            stroke.points.forEachIndexed { index, point ->
                val p = view.toScreen(point)
                if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(
                path = path,
                color = stroke.color,
                style = StrokeStyle(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }

    edits.texts.forEachIndexed { index, item ->
        // Кисть та же, что при сохранении, только размер букв переведён из
        // точек картинки в точки экрана — иначе предпросмотр и файл разошлись
        // бы ровно на масштаб показа.
        val paint = textPaint(item, edits.image.height)
        paint.textSize = (item.size * edits.image.height * view.perImagePx).coerceAtLeast(8f)
        paint.setShadowLayer(paint.textSize / 12f, 0f, 0f, android.graphics.Color.argb(140, 0, 0, 0))
        val p = view.toScreen(item.center)
        val dy = (paint.descent() + paint.ascent()) / 2f
        drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(item.text, p.x, p.y - dy, paint) }

        if (index == chosen) {
            val half = paint.measureText(item.text) / 2f + 10f
            val height = paint.textSize * 0.7f
            drawRect(
                color = CoralAccent,
                topLeft = Offset(p.x - half, p.y - height),
                size = Size(half * 2f, height * 2f),
                style = StrokeStyle(width = 2f),
            )
        }
    }
}

/** Рамка обрезки: затемнение вокруг, светлый контур и углы. */
private fun DrawScope.drawCropFrame(crop: NormRect, view: Placement) {
    val a = view.toScreen(Offset(crop.left, crop.top))
    val b = view.toScreen(Offset(crop.right, crop.bottom))
    val dim = Color.Black.copy(alpha = 0.45f)

    drawRect(dim, topLeft = Offset(view.left, view.top), size = Size(view.width, a.y - view.top))
    drawRect(dim, topLeft = Offset(view.left, b.y), size = Size(view.width, view.top + view.height - b.y))
    drawRect(dim, topLeft = Offset(view.left, a.y), size = Size(a.x - view.left, b.y - a.y))
    drawRect(dim, topLeft = Offset(b.x, a.y), size = Size(view.left + view.width - b.x, b.y - a.y))

    drawRect(Color.White, topLeft = a, size = Size(b.x - a.x, b.y - a.y), style = StrokeStyle(width = 2f))
    listOf(a, Offset(b.x, a.y), Offset(a.x, b.y), b).forEach { corner ->
        drawCircle(Color.White, radius = 10f, center = corner)
    }
}

/**
 * Где на экране лежит картинка и как перевести доли в точки экрана и обратно.
 *
 * Считается один раз на раскладку: перевод нужен и кисти, и обрезке, и
 * надписям, и три отдельных счёта разошлись бы между собой.
 */
private class Placement(
    imageWidth: Int,
    imageHeight: Int,
    val visible: NormRect,
    box: Size,
) {
    private val srcWidth = imageWidth * visible.width
    private val srcHeight = imageHeight * visible.height
    private val scale = min(box.width / srcWidth, box.height / srcHeight)

    val width = srcWidth * scale
    val height = srcHeight * scale
    val left = (box.width - width) / 2f
    val top = (box.height - height) / 2f

    /** Сколько точек экрана приходится на точку картинки. */
    val perImagePx = scale

    fun toScreen(point: Offset) = Offset(
        left + (point.x - visible.left) / visible.width * width,
        top + (point.y - visible.top) / visible.height * height,
    )

    fun toImage(point: Offset) = Offset(
        (visible.left + (point.x - left) / width * visible.width).coerceIn(0f, 1f),
        (visible.top + (point.y - top) / height * visible.height).coerceIn(0f, 1f),
    )

    /** Сдвиг пальца в долях картинки. */
    fun shift(delta: Offset) = Offset(delta.x / width * visible.width, delta.y / height * visible.height)
}

private suspend fun PointerInputScope.drawGestures(
    view: Placement,
    color: Color,
    brush: Float,
    latest: State<ImageEdits>,
    onBegin: () -> Unit,
    onLive: (ImageEdits) -> Unit,
) {
    // Основа берётся в начале штриха и дальше не меняется: каждая новая точка
    // кладётся на неё заново, поэтому в правках всегда ровно один растущий
    // штрих, а не по штриху на кадр.
    var base = latest.value
    var points = emptyList<Offset>()
    detectDragGestures(
        onDragStart = { start ->
            base = latest.value
            // Шаг назад запоминается один на весь штрих: отменять его по точке
            // никто не станет.
            onBegin()
            points = listOf(view.toImage(start))
            onLive(base.copy(strokes = base.strokes + Stroke(points, color, brush)))
        },
        onDrag = { change, _ ->
            points = points + view.toImage(change.position)
            onLive(base.copy(strokes = base.strokes + Stroke(points, color, brush)))
            change.consume()
        },
    )
}

private suspend fun PointerInputScope.cropGestures(
    view: Placement,
    latest: State<ImageEdits>,
    onBegin: () -> Unit,
    onLive: (ImageEdits) -> Unit,
) {
    var base = latest.value
    var grabbed = Grab.Inside
    var rect = base.crop

    detectDragGestures(
        onDragStart = { point ->
            base = latest.value
            onBegin()
            rect = base.crop
            grabbed = grabOf(point, base.crop, view)
        },
        onDrag = { change, delta ->
            val step = view.shift(delta)
            rect = if (grabbed == Grab.Inside) {
                rect.movedBy(step)
            } else {
                rect.pulled(grabbed, view.toImage(change.position))
            }
            onLive(base.copy(crop = rect))
            change.consume()
        },
    )
}

/** Перетаскивание выбранной надписи. Не выбрана — жест ничего не делает. */
private suspend fun PointerInputScope.textDrag(
    view: Placement,
    latest: State<ImageEdits>,
    chosen: Int,
    onBegin: () -> Unit,
    onLive: (ImageEdits) -> Unit,
) {
    if (chosen !in latest.value.texts.indices) return
    var base = latest.value
    var center = base.texts[chosen].center

    detectDragGestures(
        onDragStart = {
            base = latest.value
            if (chosen !in base.texts.indices) return@detectDragGestures
            onBegin()
            center = base.texts[chosen].center
        },
        onDrag = { change, delta ->
            val step = view.shift(delta)
            center = Offset(
                (center.x + step.x).coerceIn(0f, 1f),
                (center.y + step.y).coerceIn(0f, 1f),
            )
            onLive(base.copy(texts = base.texts.replaced(chosen) { it.copy(center = center) }))
            change.consume()
        },
    )
}

private enum class Grab { Inside, TopLeft, TopRight, BottomLeft, BottomRight }

/** За что взялись пальцем: за угол рамки или за её середину. */
private fun grabOf(point: Offset, crop: NormRect, view: Placement): Grab {
    val corners = listOf(
        Grab.TopLeft to Offset(crop.left, crop.top),
        Grab.TopRight to Offset(crop.right, crop.top),
        Grab.BottomLeft to Offset(crop.left, crop.bottom),
        Grab.BottomRight to Offset(crop.right, crop.bottom),
    )
    corners.forEach { (grab, corner) ->
        val screen = view.toScreen(corner)
        if (abs(screen.x - point.x) < HANDLE && abs(screen.y - point.y) < HANDLE) return grab
    }
    return Grab.Inside
}

private fun NormRect.pulled(grab: Grab, to: Offset): NormRect = when (grab) {
    Grab.TopLeft -> copy(left = to.x.coerceIn(0f, right - GAP), top = to.y.coerceIn(0f, bottom - GAP))
    Grab.TopRight -> copy(right = to.x.coerceIn(left + GAP, 1f), top = to.y.coerceIn(0f, bottom - GAP))
    Grab.BottomLeft -> copy(left = to.x.coerceIn(0f, right - GAP), bottom = to.y.coerceIn(top + GAP, 1f))
    Grab.BottomRight -> copy(right = to.x.coerceIn(left + GAP, 1f), bottom = to.y.coerceIn(top + GAP, 1f))
    Grab.Inside -> this
}

private fun NormRect.movedBy(step: Offset): NormRect {
    val x = (left + step.x).coerceIn(0f, 1f - width)
    val y = (top + step.y).coerceIn(0f, 1f - height)
    return NormRect(x, y, x + width, y + height)
}

private fun NormRect.middle() = Offset((left + right) / 2f, (top + bottom) / 2f)

/**
 * Рамка под заданное соотношение сторон — самая большая, какая влезает,
 * посередине. Соотношение задаётся для того, что получится, поэтому в счёт
 * идут и точки картинки: у неё свои пропорции.
 */
private fun aspectCrop(aspect: Float, imageWidth: Int, imageHeight: Int): NormRect {
    val k = aspect * imageHeight / imageWidth
    val height = min(1f, 1f / k)
    val width = height * k
    return NormRect((1f - width) / 2f, (1f - height) / 2f, (1f + width) / 2f, (1f + height) / 2f)
}

private fun List<TextItem>.nearestTo(point: Offset): Int {
    var best = -1
    var distance = Float.MAX_VALUE
    forEachIndexed { index, item ->
        val d = abs(item.center.x - point.x) + abs(item.center.y - point.y)
        if (d < distance) {
            distance = d
            best = index
        }
    }
    // Далёкое касание снимает выбор: иначе надпись нельзя было бы отпустить.
    return if (distance < 0.25f) best else -1
}

private fun <T> List<T>.replaced(index: Int, change: (T) -> T): List<T> =
    mapIndexed { i, item -> if (i == index) change(item) else item }

@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TextDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    val add = { typed.trim().takeIf { it.isNotEmpty() }?.let(onAdd) ?: onDismiss() }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.TextFields) }) {
        DialogCaption("Надпись")

        DialogField(
            value = typed,
            onValueChange = { typed = it },
            hint = "Что написать на картинке",
            autoFocus = true,
            onDone = { add() },
        )

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Добавить",
                accent = typed.isNotBlank(),
                enabled = typed.isNotBlank(),
                onClick = { add() },
            )
        }
    }
}

/** Ряд инструментов внизу. Выбранный подсвечен — как отмеченный раздел. */
@Composable
private fun ToolRow(tool: Tool, onPick: (Tool) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tool.entries.forEach { item ->
            val active = item == tool
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) AccentSoft else Color.Transparent)
                    .clickable { onPick(item) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (active) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) CoralAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Панель выбранного инструмента. */
@Composable
private fun ToolPanel(
    tool: Tool,
    edits: ImageEdits,
    color: Color,
    brush: Float,
    letters: Float,
    chosen: Int,
    onColor: (Color) -> Unit,
    onBrush: (Float) -> Unit,
    onLetters: (Float) -> Unit,
    onAddText: () -> Unit,
    onDropText: () -> Unit,
    onApply: (ImageEdits) -> Unit,
    onBegin: () -> Unit,
    onLive: (ImageEdits) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (tool) {
            Tool.Turn -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Action(Icons.AutoMirrored.Outlined.RotateLeft, "Влево") { onApply(edits.rotated(clockwise = false)) }
                Action(Icons.AutoMirrored.Outlined.RotateRight, "Вправо") { onApply(edits.rotated(clockwise = true)) }
                Action(Icons.Outlined.Flip, "Слева направо") { onApply(edits.flipped(horizontal = true)) }
                Action(Icons.Outlined.Flip, "Сверху вниз", turned = true) {
                    onApply(edits.flipped(horizontal = false))
                }
            }

            Tool.Crop -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Свободно") { onApply(edits.copy(crop = NormRect.Full)) }
                RATIOS.forEach { (label, ratio) ->
                    Chip(label) {
                        onApply(edits.copy(crop = aspectCrop(ratio, edits.image.width, edits.image.height)))
                    }
                }
            }

            Tool.Color -> Column {
                Tuner("Яркость", edits.brightness, -0.5f..0.5f, onBegin) {
                    onLive(edits.copy(brightness = it))
                }
                Tuner("Контраст", edits.contrast, 0.4f..1.8f, onBegin) {
                    onLive(edits.copy(contrast = it))
                }
                Tuner("Насыщенность", edits.saturation, 0f..2f, onBegin) {
                    onLive(edits.copy(saturation = it))
                }
            }

            Tool.Draw -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Palette(color, onColor)
                Tuner("Толщина", brush, 0.002f..0.03f, {}) { onBrush(it) }
            }

            Tool.Text -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip("Добавить надпись", onClick = onAddText)
                    if (chosen in edits.texts.indices) {
                        Action(Icons.Outlined.DeleteOutline, "Убрать надпись", onClick = onDropText)
                    }
                }
                Palette(color, onColor)
                Tuner("Размер букв", letters, 0.03f..0.3f, onBegin) { onLetters(it) }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, hint: String, turned: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(AccentSoft.copy(alpha = 0.6f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = hint,
            tint = Ink,
            modifier = Modifier
                .size(22.dp)
                // «Сверху вниз» — тот же знак, положенный на бок: своего знака
                // для отражения по вертикали в наборе нет.
                .rotate(if (turned) 90f else 0f),
        )
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(AccentSoft.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Полоска цветов. Выбранный обведён — на светлых цветах иначе не видно. */
@Composable
private fun Palette(color: Color, onPick: (Color) -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PALETTE.forEach { option ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(option)
                    .border(
                        width = if (option == color) 3.dp else 1.dp,
                        color = if (option == color) Accent else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onPick(option) },
            )
        }
    }
}

/**
 * Ползунок с подписью. Шаг назад запоминается один раз, в начале движения:
 * иначе «отменить» отматывало бы ползунок по одному делению.
 */
@Composable
private fun Tuner(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onBegin: () -> Unit,
    onChange: (Float) -> Unit,
) {
    var moving by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Slider(
            value = value,
            onValueChange = {
                if (!moving) {
                    moving = true
                    onBegin()
                }
                onChange(it)
            },
            onValueChangeFinished = { moving = false },
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val HANDLE = 60f
private const val GAP = 0.05f
private const val HISTORY = 12

private val RATIOS = listOf("1:1" to 1f, "4:3" to 4f / 3f, "3:4" to 3f / 4f, "16:9" to 16f / 9f)

// Краски карандаша — сами по себе, а не роли темы: ими пишут поверх картинки,
// и написанное остаётся в файле. Гамма приложения к чернилам на фотографии
// отношения не имеет, а надпись, поменявшая цвет от настройки, была бы уже
// другой надписью.
private val PALETTE = listOf(
    Color.White,
    PaperInk,
    CoralAccent,
    Color(0xFFC0392B),
    Color(0xFFE0A526),
    Color(0xFF3E7A4F),
    Color(0xFF2F6FB0),
)
