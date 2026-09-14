package app.askya.ui.scroll.imageedit

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.androidContainer
import app.askya.data.entity.Note
import app.askya.ui.components.EmptyState
import app.askya.ui.components.FadingGrid
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.scroll.ScrollViewModel
import app.askya.ui.scroll.rememberThumbnail
import app.askya.ui.theme.Accent
import app.askya.ui.theme.CoralAccent
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Коллаж: несколько картинок раздела складываются в одну.
 *
 * Собирается из того, что уже лежит в Scroll, а не из галереи: картинку сюда
 * сперва приносят, и повторный выбор из телефона был бы вторым способом делать
 * одно и то же. Готовый коллаж — обычная запись раздела: его можно потом
 * править тем же редактором, что и любую картинку.
 *
 * Порядок отмечается номерами: раскладка расставляет картинки по местам, и без
 * номеров нельзя было бы сказать, какая где окажется.
 */
@Composable
fun CollageScreen(onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val viewModel: ScrollViewModel = viewModel(factory = ScrollViewModel.factory(androidContainer()))
    val images by viewModel.images.collectAsStateWithLifecycle()
    val store = androidContainer().imageStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val picked = remember { mutableStateListOf<Long>() }
    var layout by remember { mutableStateOf(0) }
    var building by remember { mutableStateOf(false) }

    val chosen = picked.mapNotNull { id -> images.firstOrNull { it.id == id } }
    val layouts = layoutsFor(chosen.size)
    val current = layouts.getOrNull(layout.coerceIn(0, (layouts.size - 1).coerceAtLeast(0)))

    BackHandler(onBack = onBack)

    ScreenScaffold(
        title = "Коллаж",
        onNavigationClick = onBack,
        navigationIsBack = true,
        floatingActionButton = {
            if (current != null && !building) {
                ExtendedFloatingActionButton(
                    onClick = {
                        building = true
                        scope.launch {
                            val bitmap = buildCollage(context, chosen, current)
                            val saved = bitmap?.let { store.save(it, "collage") }
                            if (saved == null) {
                                building = false
                            } else {
                                viewModel.addImage(saved, "Коллаж", onCreated)
                            }
                        }
                    },
                    shape = RoundedCornerShape(percent = 50),
                    containerColor = Ink,
                    contentColor = Accent,
                ) {
                    Text("Собрать", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) {
        if (images.size < 2) {
            EmptyState(
                title = "Для коллажа нужны хотя бы две картинки",
                hint = "Добавь их в раздел «Изображения».",
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            )
            return@ScreenScaffold
        }

        FadingGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = when (chosen.size) {
                            0 -> "Отметь от двух до четырёх картинок"
                            1 -> "Отметь ещё хотя бы одну"
                            else -> "Как разложить"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (layouts.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            layouts.forEachIndexed { index, option ->
                                LayoutChip(
                                    option = option,
                                    active = option == current,
                                    onClick = { layout = index },
                                )
                            }
                        }
                    }
                }
            }

            items(images, key = { it.id }) { image ->
                val place = picked.indexOf(image.id)
                Pick(
                    note = image,
                    place = place,
                    onClick = {
                        when {
                            place >= 0 -> picked.removeAt(place)
                            picked.size < MAX -> picked.add(image.id)
                            else -> Unit
                        }
                        layout = 0
                    },
                )
            }
        }
    }
}

/** Картинка в выборе. Отмеченная — с номером: он говорит, какой она по счёту. */
@Composable
private fun Pick(note: Note, place: Int, onClick: () -> Unit) {
    val bitmap = note.uri?.let { rememberThumbnail(it) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (place >= 0) 3.dp else 0.dp,
                color = if (place >= 0) Accent else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = note.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = note.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                modifier = Modifier.padding(6.dp),
            )
        }

        if (place >= 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${place + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Cream,
                )
            }
        }
    }
}

/**
 * Раскладка показывается схемой, а не названием: «один и два» приходится
 * представлять, а четыре прямоугольника видно сразу.
 */
@Composable
private fun LayoutChip(option: Layout, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 46.dp, height = 34.dp)) {
            val fit = min(size.width / option.aspect, size.height)
            val width = fit * option.aspect
            val left = (size.width - width) / 2f
            val top = (size.height - fit) / 2f
            option.cells.forEach { cell ->
                drawRect(
                    color = if (active) CoralAccent else Color(0xFF9A968C),
                    topLeft = Offset(left + cell.left * width + 1f, top + cell.top * fit + 1f),
                    size = Size(cell.width * width - 2f, cell.height * fit - 2f),
                )
            }
        }
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) CoralAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Раскладка: пропорции листа и места под картинки в долях от него. */
private data class Layout(val label: String, val aspect: Float, val cells: List<NormRect>)

private fun layoutsFor(count: Int): List<Layout> = when (count) {
    2 -> listOf(
        Layout("рядом", 3f / 2f, listOf(NormRect(0f, 0f, .5f, 1f), NormRect(.5f, 0f, 1f, 1f))),
        Layout("столбиком", 2f / 3f, listOf(NormRect(0f, 0f, 1f, .5f), NormRect(0f, .5f, 1f, 1f))),
    )
    3 -> listOf(
        Layout(
            "один и два", 3f / 2f,
            listOf(NormRect(0f, 0f, .5f, 1f), NormRect(.5f, 0f, 1f, .5f), NormRect(.5f, .5f, 1f, 1f)),
        ),
        Layout(
            "в ряд", 3f / 2f,
            listOf(
                NormRect(0f, 0f, 1f / 3f, 1f),
                NormRect(1f / 3f, 0f, 2f / 3f, 1f),
                NormRect(2f / 3f, 0f, 1f, 1f),
            ),
        ),
    )
    4 -> listOf(
        Layout(
            "квадратом", 1f,
            listOf(
                NormRect(0f, 0f, .5f, .5f), NormRect(.5f, 0f, 1f, .5f),
                NormRect(0f, .5f, .5f, 1f), NormRect(.5f, .5f, 1f, 1f),
            ),
        ),
        Layout(
            "полосой", 2f,
            listOf(
                NormRect(0f, 0f, .25f, 1f), NormRect(.25f, 0f, .5f, 1f),
                NormRect(.5f, 0f, .75f, 1f), NormRect(.75f, 0f, 1f, 1f),
            ),
        ),
    )
    else -> emptyList()
}

/**
 * Складывает картинки в одну по раскладке.
 *
 * Каждая вписывается в своё место с обрезкой по краям, а не сжимается: сжатая
 * картинка среди несжатых читается как ошибка вёрстки. Между местами кремовый
 * зазор — тот же цвет, что фон приложения, так лист выглядит страницей, а не
 * склейкой встык.
 */
private suspend fun buildCollage(
    context: android.content.Context,
    notes: List<Note>,
    layout: Layout,
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val height = (SIDE / layout.aspect).roundToInt().coerceAtLeast(1)
        val sheet = Bitmap.createBitmap(SIDE, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(sheet)
        canvas.drawColor(android.graphics.Color.rgb(250, 249, 245))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        layout.cells.forEachIndexed { index, cell ->
            val note = notes.getOrNull(index) ?: return@forEachIndexed
            val uri = note.uri ?: return@forEachIndexed
            val part = decodeImage(context, uri, maxSide = 1600) ?: return@forEachIndexed

            val target = Rect(
                (cell.left * SIDE).roundToInt() + GAP,
                (cell.top * height).roundToInt() + GAP,
                (cell.right * SIDE).roundToInt() - GAP,
                (cell.bottom * height).roundToInt() - GAP,
            )
            canvas.drawBitmap(part, part.centerCrop(target.width(), target.height()), target, paint)
        }
        sheet
    }.getOrNull()
}

/** Кусок картинки под нужные пропорции — по центру, без сжатия. */
private fun Bitmap.centerCrop(targetWidth: Int, targetHeight: Int): Rect {
    val wanted = targetWidth.toFloat() / targetHeight.toFloat()
    val have = width.toFloat() / height.toFloat()
    return if (have > wanted) {
        val cut = (height * wanted).roundToInt()
        val left = (width - cut) / 2
        Rect(left, 0, left + cut, height)
    } else {
        val cut = (width / wanted).roundToInt()
        val top = (height - cut) / 2
        Rect(0, top, width, top + cut)
    }
}

/** Сколько картинок влезает в раскладку. Больше четырёх — уже мозаика. */
private const val MAX = 4

/** Длинная сторона листа и зазор между местами, в точках. */
private const val SIDE = 1800
private const val GAP = 10
