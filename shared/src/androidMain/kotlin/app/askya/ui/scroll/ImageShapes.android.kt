package app.askya.ui.scroll

import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import app.askya.platform.PlatformContext

internal actual fun aspectOf(context: PlatformContext, uri: String): Float? = runCatching {
    val parsed = Uri.parse(uri)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    // Отдельной попыткой: ExifInterface спотыкается на некоторых файлах, и
    // из-за метки поворота терять уже прочитанные размеры нельзя.
    val orientation = runCatching {
        context.contentResolver.openInputStream(parsed)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    val sideways = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
        orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE

    val width = if (sideways) bounds.outHeight else bounds.outWidth
    val height = if (sideways) bounds.outWidth else bounds.outHeight
    width.toFloat() / height.toFloat()
}.getOrNull()
