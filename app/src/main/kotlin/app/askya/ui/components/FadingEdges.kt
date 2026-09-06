package app.askya.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Растворение верхнего и нижнего края прокрутки.
 *
 * Список, обрезанный посередине строки, читается как оборванный: половина
 * буквы у самого края экрана — это не «дальше есть ещё», а брак печати.
 * Поэтому уезжающее не обрывается, а тает: последние два сантиметра ленты
 * плавно уходят в бумагу, и край перестаёт быть линией разреза.
 *
 * Растворение появляется только с той стороны, куда есть куда ехать: у самого
 * верха верхний край чистый, в конце списка — нижний. Иначе первая строка
 * всегда была бы приглушённой, а недоехавшего до неё человек прочитал бы как
 * неисправность. Появление и уход — плавные: скачок тумана заметнее самого
 * тумана.
 *
 * ## Как это сделано
 *
 * Не полосками краски поверх содержимого, а вырезанием прозрачности из него:
 * `BlendMode.DstIn` оставляет содержимое там, где маска непрозрачна. Так край
 * тает над **любой** бумагой — кремовой, ночной, поверх картинки, — а полоска
 * краски работала бы только на том фоне, в который её покрасили.
 *
 * Для этого слой рисуется отдельно (`CompositingStrategy.Offscreen`): без
 * этого маска съела бы не ленту, а всё, что нарисовано под ней.
 */
fun Modifier.fadingEdges(top: Float, bottom: Float, height: Dp = FADE): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = height.toPx().coerceAtMost(size.height / 2f)
            if (fade <= 0f) return@drawWithContent

            if (top > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 1f - top), Color.Black),
                        startY = 0f,
                        endY = fade,
                    ),
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn,
                )
            }

            if (bottom > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - bottom)),
                        startY = size.height - fade,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height - fade),
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

/** То же, но силу краёв читает у самого списка. */
@Composable
fun Modifier.fadingEdges(state: LazyListState, height: Dp = FADE): Modifier =
    fadingEdges(
        top = edge(state.canScrollBackward, "top"),
        bottom = edge(state.canScrollForward, "bottom"),
        height = height,
    )

/** То же для сетки: «Галерея» разложена ею. */
@Composable
fun Modifier.fadingEdges(state: LazyGridState, height: Dp = FADE): Modifier =
    fadingEdges(
        top = edge(state.canScrollBackward, "top"),
        bottom = edge(state.canScrollForward, "bottom"),
        height = height,
    )

/** То же для страницы, прокручиваемой целиком. */
@Composable
fun Modifier.fadingEdges(state: ScrollState, height: Dp = FADE): Modifier =
    fadingEdges(
        top = edge(state.canScrollBackward, "top"),
        bottom = edge(state.canScrollForward, "bottom"),
        height = height,
    )

@Composable
private fun edge(on: Boolean, label: String): Float {
    val value by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = tween(200),
        label = label,
    )
    return value
}

/** Сколько тает: два пальца от края — меньше не читается как туман. */
private val FADE = 28.dp

/**
 * Прокрутка с тающими краями — одним словом вместо двух.
 *
 * `Modifier.fadingEdges(state).verticalScroll(state)` — это две строки, у
 * которых обязано совпадать состояние, и вторая половина легко теряется при
 * правке: список едет, а края обрываются. Одно слово потерять нельзя.
 */
@Composable
fun Modifier.fadingVerticalScroll(state: ScrollState = rememberScrollState()): Modifier =
    fadingEdges(state).verticalScroll(state)

/**
 * `LazyColumn`, у которого края тают.
 *
 * Своя обёртка, а не строчка в каждом списке, по той же причине: состояние
 * нужно и списку, и маске, и завести его порознь значит однажды завести два
 * разных. Здесь оно одно и заводится само.
 *
 * Список принимает то же, что и обычный, — отступы, интервал, выравнивание, —
 * и своё состояние, когда список смотрит на него сам (место в книге, прыжок к
 * сегодняшнему дню).
 */
@Composable
fun FadingColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fadingEdges(state),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/** То же для сетки: «Галерея», полка AskyaV, обложки AskyaEcho. */
@Composable
fun FadingGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier.fadingEdges(state),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}
