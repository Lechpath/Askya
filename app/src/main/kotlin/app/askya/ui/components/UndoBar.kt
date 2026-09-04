package app.askya.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.askya.ui.theme.Accent
import kotlinx.coroutines.delay

/**
 * «Дело убрано · Вернуть» — полоска внизу экрана.
 *
 * Заменяет собой вопрос «вы уверены?». Подтверждение не отменяет ошибку, оно
 * перекладывает её на человека, который торопится: он читает вопрос первые
 * три раза, а через месяц жмёт «да» не глядя — и ошибается ровно так же, только
 * теперь виноват сам. Возврат отменяет ошибку по-настоящему, и стоит он одного
 * нажатия вместо одного нажатия.
 *
 * Живёт несколько секунд и уходит сама. Убранное при этом никуда не девается —
 * оно лежит сутки, — но говорить об этом полоске незачем: человек либо
 * спохватился сразу, либо не спохватился вовсе.
 *
 * [id] нужен, чтобы отсчёт начинался заново на каждое новое убирание: без него
 * второе подряд удаление гасло бы вместе с первым.
 *
 * Ставит себя внизу сама, не спрашивая у экрана: полоска всегда внизу, и
 * заставлять каждый экран помнить об этом значило бы завести четыре места, где
 * это можно сделать по-разному. Пока её не видно, она не занимает ни высоты, ни
 * касаний — пустой Box поверх страницы прозрачен и для пальца.
 */
@Composable
fun UndoBar(
    id: Long?,
    text: String,
    onUndo: () -> Unit,
    onGone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        delay(SHOWN_MS)
        onGone()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
    AnimatedVisibility(
        visible = id != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Вернуть",
                style = MaterialTheme.typography.labelLarge,
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    }
}

/**
 * Пять секунд. Меньше — не успевает тот, кто убрал не то и на секунду
 * растерялся; больше — полоска начинает висеть над экраном как сообщение,
 * которое надо закрыть.
 */
private const val SHOWN_MS = 5_000L
