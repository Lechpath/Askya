package app.askya.ui.noteedit

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogChoice
import app.askya.ui.components.DialogField

/**
 * Что принести в заметку кроме букв: картинку или адрес страницы.
 *
 * Два пункта, а не список всего на свете: файл кладут на полку целиком, а не
 * внутрь заметки, — там у него своя карточка и свой просмотр.
 */
@Composable
internal fun AttachDialog(onDismiss: () -> Unit, onImage: () -> Unit, onLink: () -> Unit) {
    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Add) }) {
        DialogCaption("Что добавим?")

        // Ответа внизу нет: строка сама и есть выбор.
        DialogChoice(
            icon = Icons.Outlined.Image,
            title = "Изображение",
            about = "Ляжет в заметку картинкой",
            onClick = onImage,
        )
        DialogChoice(
            icon = Icons.Outlined.Link,
            title = "Ссылку",
            about = "Адрес страницы, можно с подписью",
            onClick = onLink,
        )
    }
}

/**
 * Ссылка: адрес и подпись к нему.
 *
 * Подпись необязательна — без неё в заметку ляжет сам адрес. Голый адрес
 * читается плохо, но врать про содержимое страницы приложение не может, а
 * подписать её человек успеет и позже.
 */
@Composable
internal fun LinkDialog(onDismiss: () -> Unit, onAdd: (url: String, label: String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    val ready = url.isNotBlank()
    val add = { if (ready) onAdd(url.trim(), label.trim()) }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Link) }) {
        DialogCaption("Ссылка")

        DialogField(
            value = url,
            onValueChange = { url = it },
            hint = "Адрес страницы",
            autoFocus = true,
            onDone = add,
        )
        DialogField(
            value = label,
            onValueChange = { label = it },
            hint = "Подпись — если нужна",
            modifier = Modifier.padding(top = 10.dp),
            onDone = add,
        )

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Добавить",
                accent = ready,
                enabled = ready,
                onClick = add,
            )
        }
    }
}
