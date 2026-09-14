package app.askya.ui.scroll.imageedit

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Правки картинки и их применение.
 *
 * Порядок здесь неслучаен и один и тот же для предпросмотра и для сохранения:
 * повороты уже применены к самой картинке, дальше цвет, поверх него надписи и
 * штрихи, и в самом конце обрезка. Поэтому нарисованное и написанное живёт в
 * долях от повёрнутой картинки, а не от того, что видно на экране: обрежешь
 * позже — стрелка останется на том же месте снимка и обрежется вместе с ним.
 *
 * Повороты применяются сразу к битмапу, а не хранятся углом. Иначе каждое
 * место, где картинка рисуется, считало бы поворот само, и обрезка с надписями
 * жили бы в системе координат, которая меняется под ними.
 */

/** Прямоугольник в долях от размера картинки: 0 — край, 1 — противоположный. */
data class NormRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun toPixels(imageWidth: Int, imageHeight: Int): Rect = Rect(
        (left * imageWidth).roundToInt().coerceIn(0, imageWidth - 1),
        (top * imageHeight).roundToInt().coerceIn(0, imageHeight - 1),
        (right * imageWidth).roundToInt().coerceIn(1, imageWidth),
        (bottom * imageHeight).roundToInt().coerceIn(1, imageHeight),
    )

    companion object {
        val Full = NormRect()
    }
}

/** Штрих маркера: точки в долях, цвет и толщина в долях меньшей стороны. */
data class Stroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
)

/** Надпись: её середина в долях, высота букв в долях высоты картинки. */
data class TextItem(
    val text: String,
    val center: Offset,
    val color: Color,
    val size: Float,
)

/**
 * Всё состояние правки. Хранится целиком, а не по кусочкам: «отменить» — это
 * шаг назад по списку таких состояний, и собирать его из отдельных полей
 * значило бы помнить, какое поле менялось последним.
 */
data class ImageEdits(
    val image: Bitmap,
    val crop: NormRect = NormRect.Full,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val strokes: List<Stroke> = emptyList(),
    val texts: List<TextItem> = emptyList(),
) {

    /** Поворот на четверть. Вместе с картинкой едет всё, что на ней нарисовано. */
    fun rotated(clockwise: Boolean): ImageEdits {
        val turned = image.transformed { postRotate(if (clockwise) 90f else -90f) }
        // Точка (x, y) при повороте по часовой уходит в (1 - y, x), против —
        // в (y, 1 - x). Обрезка переносится по двум своим углам.
        val move: (Offset) -> Offset =
            if (clockwise) { p -> Offset(1f - p.y, p.x) } else { p -> Offset(p.y, 1f - p.x) }
        return moved(turned, move)
    }

    /** Отражение. Надписи не зеркалятся — читаться задом наперёд они не должны. */
    fun flipped(horizontal: Boolean): ImageEdits {
        val turned = image.transformed {
            if (horizontal) postScale(-1f, 1f) else postScale(1f, -1f)
        }
        val move: (Offset) -> Offset =
            if (horizontal) { p -> Offset(1f - p.x, p.y) } else { p -> Offset(p.x, 1f - p.y) }
        return moved(turned, move)
    }

    private fun moved(turned: Bitmap, move: (Offset) -> Offset): ImageEdits {
        val a = move(Offset(crop.left, crop.top))
        val b = move(Offset(crop.right, crop.bottom))
        return copy(
            image = turned,
            crop = NormRect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y)),
            strokes = strokes.map { stroke -> stroke.copy(points = stroke.points.map(move)) },
            texts = texts.map { item -> item.copy(center = move(item.center)) },
        )
    }

    /**
     * Готовая картинка: обрезанная, с цветом и со всем, что нарисовано поверх.
     *
     * Тем же кодом, что и предпросмотр, отрисовать не выйдет — там Compose, а
     * здесь битмап, — но числа берутся одни и те же (см. [paintFor],
     * [textPaint]), поэтому сохранённое совпадает с тем, что было на экране.
     */
    suspend fun render(): Bitmap = withContext(Dispatchers.IO) {
        val src = crop.toPixels(image.width, image.height)
        val out = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)

        canvas.drawBitmap(
            image,
            src,
            Rect(0, 0, out.width, out.height),
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(colorValues()))
            },
        )

        // Доли считаны от целой картинки, а не от вырезанного куска: рисовали
        // по снимку, и сдвиг обрезки не должен таскать штрихи по нему.
        fun place(point: Offset) = Offset(
            point.x * image.width - src.left,
            point.y * image.height - src.top,
        )

        strokes.forEach { stroke ->
            val paint = paintFor(stroke, min(image.width, image.height))
            if (stroke.points.size == 1) {
                val p = place(stroke.points.first())
                canvas.drawPoint(p.x, p.y, paint)
            } else {
                val path = android.graphics.Path()
                stroke.points.forEachIndexed { index, point ->
                    val p = place(point)
                    if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                canvas.drawPath(path, paint)
            }
        }

        texts.forEach { item ->
            val paint = textPaint(item, image.height)
            val p = place(item.center)
            // Середина надписи — это её середина по обеим осям: так подпись
            // остаётся там, куда её поставили пальцем.
            val dy = (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(item.text, p.x, p.y - dy, paint)
        }

        out
    }

    /**
     * Цветовая матрица одним массивом: из него собирается и системная
     * `ColorMatrix` для сохранения, и `ColorMatrix` из Compose для
     * предпросмотра — раскладка у них одна и та же.
     *
     * Насыщенность считается по тем же весам, что и в системной
     * `setSaturation`, затем поверх ложится контраст со сдвигом яркости.
     * Сдвиг `(1 - c) * 128` держит середину серого на месте: без него
     * контраст заодно затемнял бы всю картинку.
     */
    fun colorValues(): FloatArray {
        val s = saturation
        val r = 0.213f * (1 - s)
        val g = 0.715f * (1 - s)
        val b = 0.072f * (1 - s)
        val c = contrast
        val t = (1f - c) * 128f + brightness * 255f
        return floatArrayOf(
            c * (r + s), c * g, c * b, 0f, t,
            c * r, c * (g + s), c * b, 0f, t,
            c * r, c * g, c * (b + s), 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}

/** Кисть штриха. Круглая, чтобы линия не рассыпалась на углах. */
fun paintFor(stroke: Stroke, shortSide: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = stroke.color.toArgb()
    style = Paint.Style.STROKE
    strokeWidth = (stroke.width * shortSide).coerceAtLeast(1f)
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

/**
 * Кисть надписи. Под буквами тонкая тёмная обводка: белый текст на светлом
 * снимке иначе пропадает, а гадать, каким цветом писать, человеку не нужно.
 */
fun textPaint(item: TextItem, imageHeight: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = item.color.toArgb()
    textSize = (item.size * imageHeight).coerceAtLeast(8f)
    textAlign = Paint.Align.CENTER
    isFakeBoldText = true
    setShadowLayer(textSize / 12f, 0f, 0f, android.graphics.Color.argb(140, 0, 0, 0))
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
)
