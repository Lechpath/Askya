package app.askya.ui.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.echo.formatDuration
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.echo.EchoCard
import app.askya.ui.echo.EchoPill
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import app.askya.video.VideoEdits

/**
 * Ножницы AskyaV — отдельная карточка, а не строчка в окне.
 *
 * ## Что было не так
 *
 * Прежде обрезка жила в тесном окне двумя ползунками «Откуда» и «Докуда».
 * По ним нельзя было понять главного — **что именно вырежется**: два числа не
 * показывают отрезок, ползунки стояли впритык к краям, а кадра, на котором
 * стоит граница, не было видно вовсе. Человек ставил границы вслепую и узнавал
 * результат, только открыв готовый файл.
 *
 * ## Что здесь вместо этого
 *
 * Одна полоса на весь ролик, и на ней **виден сам кусок**: выбранное светится,
 * отрезанное гаснет, границы — два столбика, за которые тянут пальцем. Под
 * полосой — кадры фильма, разложенные по времени: по ним место в фильме
 * узнают глазами, а не по числу минут.
 *
 * Сверху — кадр той границы, которую сейчас двигают. Это и есть ответ на
 * вопрос «с какого момента начнётся»: не «с 4:32», а вот с этого кадра.
 *
 * Точную подводку делают кнопками «−1с» и «+1с» рядом с каждой границей:
 * пальцем по полосе часового фильма секунду не поймать, и требовать этого от
 * пальца — значит требовать невозможного.
 *
 * ## Про то, что кусок всё равно сдвинется
 *
 * Начало уезжает назад до ближайшего опорного кадра — так устроена пересборка
 * без пережатия (см. `VideoEdits`). Об этом сказано прямо под полосой, а не
 * мелким шрифтом в конце: человек, поставивший границу по кадру, должен знать,
 * что кадр этот приблизительный, до того как нажмёт «Обрезать».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoTrimCard(
    title: String,
    source: String,
    durationMs: Long,
    onDismiss: () -> Unit,
    onDone: (Long, Long, String) -> Unit,
) {
    val context = LocalContext.current

    // Длина ролика: сперва та, что знает раздел, а если он не знает — та, что
    // знает сам файл. Ноль в списке — обычное дело для скачанного и для
    // потоковых контейнеров, и прежде ножницы на нём молча переставали
    // работать: кнопка не загоралась никогда.
    var whole by remember(source) { mutableLongStateOf(durationMs.coerceAtLeast(0)) }
    var asked by remember(source) { mutableStateOf(durationMs > 0) }
    LaunchedEffect(source) {
        if (whole <= 0) {
            whole = VideoEdits.duration(context, source)
            asked = true
        }
    }

    var from by remember(source) { mutableLongStateOf(0L) }
    var to by remember(source) { mutableLongStateOf(0L) }
    var edge by remember(source) { mutableStateOf(TrimEdge.START) }
    var name by remember(source) { mutableStateOf("$title-кусок") }

    // Конец встаёт на конец ролика, как только длина стала известна.
    LaunchedEffect(whole) { if (whole > 0 && to <= 0) to = whole }

    val at = if (edge == TrimEdge.START) from else to
    val piece = (to - from).coerceAtLeast(0)
    val ready = whole > 0 && piece >= LEAST_MS && name.isNotBlank()

    // Кадр границы. Перечитывается на каждой остановке пальца, а не на каждом
    // его движении: вынуть кадр стоит десятков миллисекунд, и делать это по
    // сорок раз в секунду значило бы дёргать всю карточку.
    var shot by remember(source) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source, at, edge) {
        kotlinx.coroutines.delay(SETTLE_MS)
        shot = VideoEdits.frame(context, source, at)
    }

    // Лента кадров под полосой — по одному на каждую восьмую ролика. Считается
    // один раз на файл: она не зависит от границ и не должна пересчитываться,
    // пока их двигают.
    var strip by remember(source) { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(source, whole) {
        if (whole <= 0) return@LaunchedEffect
        strip = (0 until STRIP).mapNotNull { step ->
            VideoEdits.frame(context, source, whole * step / STRIP)
        }
    }

    EchoCard(
        title = "Обрезать",
        subtitle = title,
        onDismiss = onDismiss,
        height = null,
    ) {
        Column(
            modifier = Modifier
                .fadingVerticalScroll()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            if (whole <= 0) {
                Text(
                    text = if (asked) {
                        "Длину этого файла не знает ни раздел, ни сам файл — резать не по " +
                            "чему. Так бывает у потоков и у недокачанного."
                    } else {
                        "Спрашиваю длину ролика…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NightMuted,
                )
                return@Column
            }

            FramePreview(shot)

            Text(
                text = if (edge == TrimEdge.START) "Кадр начала" else "Кадр конца",
                style = MaterialTheme.typography.labelMedium,
                color = NightMuted,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )

            TrimBar(
                from = from,
                to = to,
                whole = whole,
                edge = edge,
                strip = strip,
                onGrab = { grabbed, ms ->
                    edge = grabbed
                    if (grabbed == TrimEdge.START) {
                        from = ms.coerceIn(0, to - LEAST_MS)
                    } else {
                        to = ms.coerceIn(from + LEAST_MS, whole)
                    }
                },
                modifier = Modifier.padding(top = 16.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("0:00", style = MaterialTheme.typography.labelSmall, color = NightMuted)
                Text(
                    text = formatDuration(whole),
                    style = MaterialTheme.typography.labelSmall,
                    color = NightMuted,
                )
            }

            EdgeRow(
                label = "Начало",
                valueMs = from,
                active = edge == TrimEdge.START,
                onPick = { edge = TrimEdge.START },
                onNudge = { step ->
                    edge = TrimEdge.START
                    from = (from + step).coerceIn(0, to - LEAST_MS)
                },
                modifier = Modifier.padding(top = 14.dp),
            )
            EdgeRow(
                label = "Конец",
                valueMs = to,
                active = edge == TrimEdge.END,
                onPick = { edge = TrimEdge.END },
                onNudge = { step ->
                    edge = TrimEdge.END
                    to = (to + step).coerceIn(from + LEAST_MS, whole)
                },
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NightPanelSoft)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Вырежется",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NightMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDuration(piece),
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    color = Sunset,
                )
            }

            Text(
                text = "Начало сдвинется назад до ближайшего опорного кадра — обычно меньше " +
                    "чем на две секунды. Исходник остаётся на месте, кусок ложится " +
                    "отдельным файлом.",
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                modifier = Modifier.padding(top = 12.dp),
            )

            // Имя и кнопки под ним — одним куском, который встаёт над
            // клавиатурой, пока в поле пишут. Поле стоит в самом низу
            // карточки, и без этого клавиатура накрывала его целиком: имя
            // набиралось вслепую, а «Обрезать» приходилось искать, закрыв её.
            // Ловится каждый шаг выезда клавиатуры, а не только первый:
            // карточка сжимается вместе с ней, и кусок едет следом.
            val naming = remember { BringIntoViewRequester() }
            var typing by remember { mutableStateOf(false) }
            val keyboard = WindowInsets.ime.getBottom(LocalDensity.current)
            LaunchedEffect(typing, keyboard) { if (typing) naming.bringIntoView() }

            Column(modifier = Modifier.bringIntoViewRequester(naming)) {
                Text(
                    text = "Как назвать кусок",
                    style = MaterialTheme.typography.labelMedium,
                    color = NightMuted,
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                )
                TrimName(value = name, onChange = { name = it }, onFocus = { typing = it })

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EchoPill(
                        label = "Отмена",
                        chosen = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    EchoPill(
                        label = "Обрезать",
                        chosen = ready,
                        onClick = { if (ready) onDone(from, to, name) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!ready && piece < LEAST_MS) {
                Text(
                    text = "Кусок короче секунды вырезать нечем: между опорными кадрами " +
                        "обычно больше.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NightMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Какую границу сейчас двигают. */
private enum class TrimEdge { START, END }

/**
 * Кадр границы во всю ширину карточки.
 *
 * Пустой прямоугольник вместо кадра — не сбой: кадр снимается системными
 * средствами, и не со всякого файла, который играет плеер, его удаётся снять.
 * Тогда остаётся полоса и числа, и ножницы работают по-прежнему.
 */
@Composable
private fun FramePreview(shot: Bitmap?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(NightPanelSoft)
            .border(1.dp, NightBorder, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val frame = shot
        if (frame == null) {
            Text(
                text = "Кадр не снимается",
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
            )
        } else {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Полоса ролика: лента кадров, светящийся кусок и два столбика по краям.
 *
 * Тянут за столбик, но хватать его точно не обязательно: касание берёт ту
 * границу, что ближе, — попасть пальцем в линию шириной в три точки нельзя, и
 * требовать этого нечестно.
 */
@Composable
private fun TrimBar(
    from: Long,
    to: Long,
    whole: Long,
    edge: TrimEdge,
    strip: List<Bitmap>,
    onGrab: (TrimEdge, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NightPanelSoft),
    ) {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            maxWidth.toPx()
        }

        // Лента кадров — фоном под полосой. Кадры растянуты поровну: они
        // говорят «что было примерно здесь», а не «ровно на этой секунде».
        if (strip.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                strip.forEach { frame ->
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(whole, widthPx) {
                    // Какую границу держит палец — решается один раз, в момент
                    // касания, и дальше не меняется: иначе перетянутая через
                    // середину граница перескакивала бы на соседку.
                    var held = TrimEdge.START
                    detectDragGestures(
                        onDragStart = { touch ->
                            held = nearer(touch.x, from, to, whole, widthPx)
                            onGrab(held, msAt(touch.x, whole, widthPx))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onGrab(held, msAt(change.position.x, whole, widthPx))
                        },
                    )
                }
                .pointerInput(whole, widthPx) {
                    detectTapGestures { touch ->
                        onGrab(
                            nearer(touch.x, from, to, whole, widthPx),
                            msAt(touch.x, whole, widthPx),
                        )
                    }
                },
        ) {
            val left = size.width * partOf(from, whole)
            val right = size.width * partOf(to, whole)

            // Отрезанное гасится, выбранное остаётся как есть: так кусок виден
            // и на ленте кадров, а не только по цвету полосы.
            drawRect(
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f),
                topLeft = Offset.Zero,
                size = Size(left, size.height),
            )
            drawRect(
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f),
                topLeft = Offset(right, 0f),
                size = Size(size.width - right, size.height),
            )

            drawRect(
                color = Sunset.copy(alpha = 0.18f),
                topLeft = Offset(left, 0f),
                size = Size((right - left).coerceAtLeast(0f), size.height),
            )

            handle(left, edge == TrimEdge.START)
            handle(right, edge == TrimEdge.END)
        }
    }
}

/** Столбик границы. Та, что сейчас в руках, — толще и ярче. */
private fun DrawScope.handle(x: Float, active: Boolean) {
    val thick = if (active) 6f else 3f
    drawRect(
        color = if (active) Sunset else Sunset.copy(alpha = 0.55f),
        topLeft = Offset((x - thick / 2).coerceIn(0f, size.width - thick), 0f),
        size = Size(thick, size.height),
    )
}

private fun partOf(ms: Long, whole: Long): Float =
    if (whole <= 0) 0f else (ms.toFloat() / whole).coerceIn(0f, 1f)

private fun msAt(x: Float, whole: Long, width: Float): Long =
    if (width <= 0f) 0L else ((x / width).coerceIn(0f, 1f) * whole).toLong()

/** Ближняя к касанию граница — по расстоянию в точках, а не во времени. */
private fun nearer(x: Float, from: Long, to: Long, whole: Long, width: Float): TrimEdge {
    val left = width * partOf(from, whole)
    val right = width * partOf(to, whole)
    return if (kotlin.math.abs(x - left) <= kotlin.math.abs(x - right)) {
        TrimEdge.START
    } else {
        TrimEdge.END
    }
}

/**
 * Строка границы: название, время и точная подводка.
 *
 * Нажатие на строку берёт границу в руки — тогда кадр наверху показывает её, а
 * столбик на полосе становится толще. Так видно, что двигают, до того как
 * что-нибудь сдвинулось.
 */
@Composable
private fun EdgeRow(
    label: String,
    valueMs: Long,
    active: Boolean,
    onPick: () -> Unit,
    onNudge: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) NightPanelSoft else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = 1.dp,
                color = if (active) Sunset else NightBorder,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) Sunset else NightMuted,
            )
            Text(
                text = formatDuration(valueMs),
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                color = NightInk,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NudgeButton("−5с") { onNudge(-5_000) }
            NudgeButton("−1с") { onNudge(-1_000) }
            NudgeButton("+1с") { onNudge(1_000) }
            NudgeButton("+5с") { onNudge(5_000) }
        }
    }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = NightInk,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, NightBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/**
 * Имя куска.
 *
 * Своё поле, а не общее `EchoField`: то забирает себе набор текста сразу,
 * как открылось окно, и клавиатура накрыла бы и полосу, и кадр — всё, ради
 * чего эта карточка и заведена.
 */
@Composable
private fun TrimName(value: String, onChange: (String) -> Unit, onFocus: (Boolean) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, color = NightInk),
        cursorBrush = SolidColor(Sunset),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocus(it.isFocused) }
            .clip(RoundedCornerShape(14.dp))
            .background(NightPanelSoft)
            .border(1.dp, NightBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/** Кусок короче секунды — не кусок: между опорными кадрами обычно больше. */
private const val LEAST_MS = 1_000L

/** Сколько кадров в ленте под полосой. */
private const val STRIP = 8

/** Сколько ждать остановки пальца, прежде чем вынимать кадр. */
private const val SETTLE_MS = 220L
