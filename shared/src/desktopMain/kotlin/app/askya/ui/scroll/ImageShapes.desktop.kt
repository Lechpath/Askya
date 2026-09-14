package app.askya.ui.scroll

import app.askya.platform.PlatformContext
import app.askya.platform.fileOf
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedOrigin

internal actual fun aspectOf(context: PlatformContext, uri: String): Float? = runCatching {
    val file = fileOf(uri)?.takeIf { it.isFile } ?: return@runCatching null
    val codec = Codec.makeFromData(Data.makeFromBytes(file.readBytes()))
    if (codec.width <= 0 || codec.height <= 0) return@runCatching null
    val sideways = when (codec.encodedOrigin) {
        EncodedOrigin.LEFT_TOP, EncodedOrigin.RIGHT_TOP,
        EncodedOrigin.RIGHT_BOTTOM, EncodedOrigin.LEFT_BOTTOM -> true
        else -> false
    }
    val width = if (sideways) codec.height else codec.width
    val height = if (sideways) codec.width else codec.height
    width.toFloat() / height.toFloat()
}.getOrNull()
