package app.askya.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import app.askya.domain.markdown.Links

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
fun openLink(context: Context, url: String) {
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

/**
 * Что делать с нажатой ссылкой в разметке.
 *
 * Своё, а не то, что Compose делает сам: `LocalUriHandler` на неоткрываемом
 * адресе роняет приложение, а здесь промах по ссылке стоит одной строчки внизу
 * экрана.
 */
@Composable
internal fun rememberLinkOpener(): LinkInteractionListener {
    val context = LocalContext.current
    return remember(context) {
        LinkInteractionListener { annotation ->
            val url = (annotation as? LinkAnnotation.Url)?.url
            if (!url.isNullOrBlank()) openLink(context, url)
        }
    }
}
