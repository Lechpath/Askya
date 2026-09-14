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
import androidx.compose.foundation.layout.Spacer
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
import app.askya.ui.theme.cardEdge
import app.askya.ui.theme.cardShade

/**
 * Диалоговое окно внизу страницы: строка во всю ширину, кнопки под ней.
 *
 * Записывают в Askya везде одинаково — не формой и не анкетой, а разговором с
 * тем, что открыто: то, что набрано в строке, уходит в карточку абзацем, а в
 * список — строкой, как сообщение. Поэтому строка стоит внизу и остаётся на
 * месте, пока написанное растёт вверх.
 *
 * ## Почему кнопки под строкой, а не по её краям
 *
 * Кнопки стояли слева и справа от строки, и текст набирался в щель между
 * ними: три кружка по сорок точек съедали треть ширины окна, и абзац,
 * умещавшийся в две строки, ложился в четыре. Строка — то, ради чего окно и
 * открывают, и ширину надо отдавать ей, а не тому, чем её отправляют.
 *
 * Под строкой кнопкам не тесно: там пусто, и ряд из трёх кружков занимает
 * ровно одну свою высоту вместо трети каждой строки текста.
 *
 * Плюс остался слева, отправка справа — то же деление, что и было: плюс
 * приносит то, чего не наберёшь буквами, картинку или адрес страницы, и это
 * выбор, а не отправка. Класть их рядом значило бы просить целиться. Приносить
 * нечего — [onAttach] не задан, и плюса нет вовсе: пустая кнопка хуже её
 * отсутствия.
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

    // Окно приподнято тенью — той же, что у карточек.
    //
    // Без неё белое окно лежало на белой карточке записи вплотную, и граница
    // между ними была одной линией скругления: непонятно, где кончается то,
    // что уже записано, и начинается то, что набирают. Тень отвечает на это
    // раньше, чем человек прочитает хоть слово, — окно лежит поверх карточки,
    // а не продолжает её.
    //
    // [cardShade] потому, что ночью тень не работает: она чёрная, а лист под
    // ней и так почти чёрный. Ночью то же самое говорит [cardEdge] — тонкая
    // граница, которой ночью отделены все карточки.
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardShade(shape, elevation = 8.dp)
            .cardEdge(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
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

            // Пустота между плюсом и отправкой, а не отступ числом: ряд должен
            // разъезжаться по краям окна на любой ширине экрана.
            Spacer(modifier = Modifier.weight(1f))

            if (DICTATION_AVAILABLE) MicButton(listening = listening, onClick = onMic)

            RoundButton(
                icon = Icons.Outlined.ArrowUpward,
                description = "Отправить",
                background = if (ready) Ink else Cream,
                tint = if (ready) Accent else Muted,
                enabled = ready,
                modifier = Modifier.padding(start = 8.dp),
                onClick = onSend,
            )
        }
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

/** Круглая кнопка в ряду под строкой — плюс слева, микрофон и отправка справа. */
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
