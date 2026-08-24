package app.askya.echo

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * Цвета обложки — те, которыми она вспыхивает вокруг себя.
 *
 * Своя разборка, а не `androidx.palette`: библиотека тянется ради одного
 * подсчёта, который здесь занимает полсотни строк, — а всё остальное в Echo
 * сделано системными средствами, и обложка не повод отступать от этого.
 *
 * Картинка сначала сжимается до наперстка: цвет обложки — это её общее
 * впечатление, и считать его по двум миллионам точек значит потратить кадр
 * ради того же ответа.
 *
 * Точки складываются в кучки по огрублённому цвету, а вес кучки — не число
 * точек, а их живость: серый фон занимает половину любой обложки и победил бы
 * в честном подсчёте, а вспышке нужен цвет, который в обложке узнаётся.
 * Совсем тёмное и совсем белое выбрасывается вовсе — вспышка чёрным не
 * вспышка.
 *
 * Возвращаются непохожие друг на друга цвета: три оттенка одного красного
 * дали бы вокруг обложки одно пятно вместо переливающегося венца.
 */
fun coverColors(bitmap: Bitmap, count: Int = 3): List<Int> {
    val small = runCatching {
        Bitmap.createScaledBitmap(bitmap, SAMPLE_SIDE, SAMPLE_SIDE, true)
    }.getOrNull() ?: return emptyList()

    val pixels = IntArray(SAMPLE_SIDE * SAMPLE_SIDE)
    small.getPixels(pixels, 0, SAMPLE_SIDE, 0, 0, SAMPLE_SIDE, SAMPLE_SIDE)
    if (small !== bitmap) small.recycle()

    val weights = HashMap<Int, Float>()
    val sums = HashMap<Int, IntArray>()
    val hsv = FloatArray(3)

    pixels.forEach { pixel ->
        Color.colorToHSV(pixel, hsv)
        val saturation = hsv[1]
        val value = hsv[2]
        // Тени и засветы выбрасываются: у них нет цвета, которым можно
        // вспыхнуть, — есть только «темно» и «светло».
        if (value < 0.18f || value > 0.97f) return@forEach

        // Огрубление: соседние оттенки — это один и тот же цвет обложки,
        // разбитый сжатием и шумом камеры.
        val key = (Color.red(pixel) / STEP shl 16) or
            (Color.green(pixel) / STEP shl 8) or
            (Color.blue(pixel) / STEP)

        // Живость: насыщенное весит больше серого, но и совсем тусклое не
        // выбрасывается — у чёрно-белой обложки других цветов нет.
        val weight = 0.15f + saturation * saturation * value
        weights[key] = (weights[key] ?: 0f) + weight
        val sum = sums.getOrPut(key) { IntArray(4) }
        sum[0] += Color.red(pixel)
        sum[1] += Color.green(pixel)
        sum[2] += Color.blue(pixel)
        sum[3]++
    }

    val ordered = weights.entries.sortedByDescending { it.value }
    val chosen = ArrayList<Int>(count)

    ordered.forEach { (key, _) ->
        if (chosen.size >= count) return@forEach
        val sum = sums[key] ?: return@forEach
        val color = Color.rgb(sum[0] / sum[3], sum[1] / sum[3], sum[2] / sum[3])
        // Похожее на уже взятое пропускается: венец из трёх одинаковых пятен
        // читается одним пятном.
        if (chosen.none { near(it, color) }) chosen += vivid(color)
    }

    return chosen
}

/**
 * Цвет, доведённый до вспышки.
 *
 * Обложка бывает выцветшей, а вспышка на чёрном должна быть видна: оттенок
 * берётся у обложки, а насыщенность и яркость подтягиваются до тех, при
 * которых цвет светит. Оттенок при этом не трогается — вспышка должна
 * оставаться цветом этой обложки, а не общим оранжевым для всех.
 */
private fun vivid(color: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.35f)
    hsv[2] = hsv[2].coerceIn(0.55f, 1f)
    return Color.HSVToColor(hsv)
}

/** Два цвета неразличимы, если расходятся меньше чем на глаз. */
private fun near(first: Int, second: Int): Boolean =
    abs(Color.red(first) - Color.red(second)) +
        abs(Color.green(first) - Color.green(second)) +
        abs(Color.blue(first) - Color.blue(second)) < DISTANCE

/** Сторона наперстка, до которого сжимается обложка перед подсчётом. */
private const val SAMPLE_SIDE = 40

/** Огрубление цвета: 256 оттенков канала складываются в восемь. */
private const val STEP = 32

/** Ближе этого цвета считаются одним. */
private const val DISTANCE = 90
