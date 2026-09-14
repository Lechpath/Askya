package app.askya.ui.components

import app.askya.domain.markdown.Links
import app.askya.platform.PlatformContext
import app.askya.platform.toast
import java.awt.Desktop
import java.net.URI

/** Адрес — браузеру Windows, тем же ходом, каким его открыл бы проводник. */
actual fun openLink(context: PlatformContext, url: String) {
    val opened = runCatching {
        Desktop.getDesktop().browse(URI(Links.web(url)))
    }.isSuccess
    if (!opened) context.toast("Эту ссылку нечем открыть")
}
