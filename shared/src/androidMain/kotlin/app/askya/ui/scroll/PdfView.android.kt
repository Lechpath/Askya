package app.askya.ui.scroll

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.askya.ui.components.FadingColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * PDF страницами. `PdfRenderer` входит в систему, поэтому библиотека не нужна.
 *
 * Страницы рисуются по мере прокрутки, а не все сразу: разворот на ширину
 * экрана — это мегабайты, и десяток таких на старте съел бы память впустую.
 * Рисование под замком: `PdfRenderer` не терпит двух открытых страниц разом, а
 * ленивый список легко просит соседние одновременно.
 */
@Composable
actual fun PdfView(uri: String) {
    val context = LocalContext.current
    var renderer by remember(uri) { mutableStateOf<PdfRenderer?>(null) }
    var descriptor by remember(uri) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    val lock = remember(uri) { Mutex() }

    DisposableEffect(uri) {
        runCatching {
            val fd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                ?: error("нет доступа к файлу")
            descriptor = fd
            renderer = PdfRenderer(fd)
        }.onFailure { failed = true }

        onDispose {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
            renderer = null
            descriptor = null
        }
    }

    val open = renderer
    when {
        failed -> Failed("Открыть не вышло — файл удалили или отозвали доступ.")
        open == null -> Waiting()
        else -> FadingColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(open.pageCount) { index ->
                PdfPage(renderer = open, lock = lock, index = index)
            }
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, lock: Mutex, index: Int) {
    var page by remember(renderer, index) { mutableStateOf<ImageBitmap?>(null) }
    var ratio by remember(renderer, index) { mutableFloatStateOf(0.7f) }

    LaunchedEffect(renderer, index) {
        val rendered = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    val open = renderer.openPage(index)
                    try {
                        val width = PAGE_WIDTH_PX
                        val height = (width.toFloat() * open.height / open.width).toInt()
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // Белая подложка: PDF рисует по прозрачному, и на
                        // кремовом фоне страница выглядела бы грязной.
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        open.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        (open.width.toFloat() / open.height) to bitmap.asImageBitmap()
                    } finally {
                        open.close()
                    }
                }.getOrNull()
            }
        }
        if (rendered != null) {
            ratio = rendered.first
            page = rendered.second
        }
    }

    val bitmap = page
    if (bitmap == null) {
        // Место под страницу держится заранее: без него список прыгал бы на
        // каждой дорисованной странице.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = "Страница ${index + 1}",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Ширина отрисовки страницы. Хватает для чтения и не съедает память. */
private const val PAGE_WIDTH_PX = 1240
