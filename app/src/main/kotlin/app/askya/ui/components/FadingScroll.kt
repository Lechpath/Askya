package app.askya.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Прокрутка, у которой текст тает у верхнего и нижнего края.
 *
 * Тот же приём, что в разговоре (`Conversation.kt`): содержимое рисуется в
 * отдельный слой, и по нему проходит маска `BlendMode.DstIn` — вертикальный
 * градиент, прозрачный на краях и непрозрачный в середине. Маска режет альфу
 * самого текста, поэтому работает на любом фоне и гасит строку, а не блок
 * целиком: заливка цветом фона так не умеет.
 *
 * Вынесено сюда, потому что приём понадобился второй раз — в «AskyaKnewClaude»,
 * где профиль читают крупно и целиком. Держать его в двух местах значило бы
 * чинить маску дважды.
 */
@Composable
fun FadingScroll(
    modifier: Modifier = Modifier,
    // Состояние прокрутки можно передать снаружи: экрану профиля оно нужно,
    // чтобы считать по нему размер строк, а держать его в двух местах значило
    // бы считать по устаревшему.
    scroll: ScrollState = rememberScrollState(),
    edge: Dp = EDGE,
    horizontalPadding: Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Слой рисуется отдельно, иначе DstIn вырезал бы не текст,
            // а всё, что уже лежит на экране под ним.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fraction = (edge.toPx() / size.height).coerceIn(0f, 0.45f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        fraction to Color.Black,
                        1f - fraction to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .verticalScroll(scroll)
            .padding(horizontal = horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Высота растворения у краёв — около двух крупных строк. */
private val EDGE = 96.dp
