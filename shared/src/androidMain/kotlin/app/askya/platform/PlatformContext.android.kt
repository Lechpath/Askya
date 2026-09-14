package app.askya.platform

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.platform.LocalContext
import java.io.InputStream

actual typealias PlatformContext = Context

actual val LocalPlatformContext: ProvidableCompositionLocal<PlatformContext>
    get() = LocalContext

actual fun PlatformContext.openStream(uri: String): InputStream? =
    runCatching { contentResolver.openInputStream(Uri.parse(uri)) }.getOrNull()

actual fun PlatformContext.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
