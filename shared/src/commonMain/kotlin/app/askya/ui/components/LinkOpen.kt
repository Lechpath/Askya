package app.askya.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import app.askya.platform.LocalPlatformContext
import app.askya.platform.PlatformContext

/**
 * Открыть ссылку из записи.
 *
 * Приложение само в сеть не выходит и разрешения на это не просит — страницу
 * показывает браузер, которому адрес передан намерением. Это то же самое, что
 * происходит при нажатии на ссылку в сообщении: Askya не читает страницу, она
 * говорит системе, куда человек хочет попасть.
 *
 * Если открыть некому — на телефоне нет браузера или адрес не адрес вовсе, —
 * говорится об этом вслух: молчащая ссылка выглядит поломкой приложения, а не
 * опечаткой в строке.
 */
expect fun openLink(context: PlatformContext, url: String)

/**
 * Что делать с нажатой ссылкой в разметке.
 *
 * Своё, а не то, что Compose делает сам: `LocalUriHandler` на неоткрываемом
 * адресе роняет приложение, а здесь промах по ссылке стоит одной строчки внизу
 * экрана.
 */
@Composable
internal fun rememberLinkOpener(): LinkInteractionListener {
    val context = LocalPlatformContext.current
    return remember(context) {
        LinkInteractionListener { annotation ->
            val url = (annotation as? LinkAnnotation.Url)?.url
            if (!url.isNullOrBlank()) openLink(context, url)
        }
    }
}
