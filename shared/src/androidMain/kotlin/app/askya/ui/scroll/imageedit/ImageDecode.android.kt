package app.askya.ui.scroll.imageedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/*
 * Чтение картинки телефоном — общее у правки (она в приложении телефона) и у
 * сеток и превью Scroll (они общие с Windows-версией). Поэтому здесь, в
 * `shared`, а не рядом с правкой.
 */

/** Картинка, повёрнутая или отражённая матрицей, — новым битмапом. */
fun Bitmap.transformed(build: Matrix.() -> Unit): Bitmap {
    val matrix = Matrix().apply(build)
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * Читает картинку под правку.
 *
 * Сторона ограничена: снимок с телефона — это под сотню мегабайт в памяти, а
 * правят его на экране в пару тысяч точек. Ограничение общее и для сохранения:
 * что видели, то и легло в файл.
 *
 * Разворот из EXIF применяется здесь же. Снимки с камеры лежат боком, а
 * поворот записан меткой рядом с пикселями; без неё правка начиналась бы с
 * того, что картинка лежит не той стороной.
 */
suspend fun decodeImage(context: Context, uri: String, maxSide: Int = 2400): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val parsed = Uri.parse(uri)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val longest = max(bounds.outWidth, bounds.outHeight)

            val options = BitmapFactory.Options().apply {
                // Степень двойки: другие коэффициенты decodeStream округляет
                // сам, и результат выходил крупнее просимого.
                var step = 1
                while (longest / step > maxSide) step *= 2
                inSampleSize = step
            }
            val decoded = context.contentResolver.openInputStream(parsed)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@runCatching null

            // Отдельной попыткой, а не в общем runCatching: ExifInterface
            // спотыкается на некоторых файлах (у png метки ориентации обычно
            // нет вовсе), и прочитанную картинку нельзя из-за этого терять —
            // именно так копии в разделе выходили пустыми квадратами.
            val orientation = runCatching {
                context.contentResolver.openInputStream(parsed)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

            decoded.upright(orientation)
        }.getOrNull()
    }

/** Разворачивает снимок по метке EXIF. Метка «как есть» — самая частая. */
fun Bitmap.upright(orientation: Int): Bitmap = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> transformed { postRotate(90f) }
    ExifInterface.ORIENTATION_ROTATE_180 -> transformed { postRotate(180f) }
    ExifInterface.ORIENTATION_ROTATE_270 -> transformed { postRotate(270f) }
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transformed { postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> transformed { postScale(1f, -1f) }
    ExifInterface.ORIENTATION_TRANSPOSE -> transformed { postRotate(90f); postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_TRANSVERSE -> transformed { postRotate(270f); postScale(-1f, 1f) }
    else -> this
}
