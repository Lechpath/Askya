package app.askya.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.askya.domain.markdown.Links
import app.askya.platform.PlatformContext

actual fun openLink(context: PlatformContext, url: String) {
    val address = Links.web(url)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(address)).apply {
        // Из не-активити (виджет, сервис) намерение без своей задачи не летит.
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val opened = runCatching { context.startActivity(intent) }.isSuccess
    if (!opened) {
        Toast.makeText(context, "Эту ссылку нечем открыть", Toast.LENGTH_SHORT).show()
    }
}
