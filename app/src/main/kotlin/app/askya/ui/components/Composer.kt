package app.askya.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.askya.ui.theme.Accent
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted

/**
 * Диалоговое окно внизу страницы: слева плюс, посередине строка, справа отправка.
 *
 * Записывают в Askya везде одинаково — не формой и не анкетой, а разговором с
 * тем, что открыто: то, что набрано в строке, уходит в карточку абзацем, а в
 * список — строкой, как сообщение. Поэтому строка стоит внизу и остаётся на
 * месте, пока написанное растёт вверх.
 *
 * Плюс слева приносит то, чего не наберёшь буквами, — картинку или адрес
 * страницы. Он стоит отдельно от строки: это не отправка, а выбор, и попасть в
 * него пальцем нужно не глядя. Приносить нечего — [onAttach] не задан, и плюса
 * нет вовсе: пустая кнопка хуже её отсутствия.
 *
 * Кнопка отправки гаснет, пока строка пуста: отправлять нечего, и нажатие в
 * пустоту не должно выглядеть поломкой.
 */
@Composable
fun Composer(
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onAttach: (() -> Unit)? = null,
    placeholder: String = "Написать в карточку…",
    canSendEmpty: Boolean = false,
    listening: Boolean = false,
    onMic: () -> Unit = {},
) {
    val ready = draft.text.isNotBlank() || canSendEmpty

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(6.dp),
    ) {
        if (onAttach != null) {
            RoundButton(
                icon = Icons.Outlined.Add,
                description = "Добавить к заметке",
                background = Cream,
                tint = Ink,
                onClick = onAttach,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            if (draft.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Muted,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                // Растёт до десяти строк и дальше прокручивается само. Пяти не
                // хватало: в окне пишут и правят целые абзацы, и длинный кусок
                // приходилось читать в щель на две строки. Десять — примерно
                // треть экрана: карточку над окном ещё видно.
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp)
                    .focusRequester(focusRequester),
            )
        }

        MicButton(listening = listening, onClick = onMic)

        RoundButton(
            icon = Icons.Outlined.ArrowUpward,
            description = "Отправить",
            background = if (ready) Ink else Cream,
            tint = if (ready) Accent else Muted,
            enabled = ready,
            onClick = onSend,
        )
    }
}

/**
 * Микрофон: сказанное вслух ложится буквами в это же окно.
 *
 * Стоит рядом с отправкой, а не вместо неё: надиктованное почти всегда
 * дописывают или правят руками, и подменять одну кнопку другой значило бы
 * прятать то, чем пользуются следом.
 *
 * Пока слушает — дышит: значок то ярче, то бледнее. Замерший красный кружок
 * не отличить от застрявшего, а по дыханию видно, что телефон жив и слушает.
 * Анимация заводится только на время слушания: вечная, она перерисовывала бы
 * экран каждый кадр всё время, пока карточка открыта.
 */
@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    if (!listening) {
        RoundButton(
            icon = Icons.Outlined.Mic,
            description = "Надиктовать",
            background = Cream,
            tint = Ink,
            onClick = onClick,
        )
        return
    }

    val breath = rememberInfiniteTransition(label = "mic")
    val alpha by breath.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "mic-alpha",
    )

    RoundButton(
        icon = Icons.Outlined.Mic,
        description = "Слушаю — нажмите, чтобы остановить",
        background = Accent,
        tint = Cream,
        onClick = onClick,
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
    )
}

/** Круглая кнопка диалогового окна — плюс слева и отправка справа. */
@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    background: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
