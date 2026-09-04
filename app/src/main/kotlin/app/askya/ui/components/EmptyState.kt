package app.askya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Пустое состояние списка: заголовок и подсказка, что делать дальше.
 *
 * [actionLabel] добавляет под подсказкой само это «дальше». Нужен он не
 * всякому пустому списку: где заводят кнопкой снизу, там второй вход посреди
 * экрана лишний. Но там, где выход из пустоты не под пальцем — как выбор
 * города в погоде, спрятанный значком в шапке, — подсказка без кнопки
 * оставляет человека искать то, о чём ему только что сказали.
 */
@Composable
fun EmptyState(
    title: String,
    hint: String,
    // По умолчанию занимает экран целиком, но внутри LazyColumn так нельзя —
    // там передают ширину и отступ сверху.
    modifier: Modifier = Modifier.fillMaxSize(),
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (actionLabel != null) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}
