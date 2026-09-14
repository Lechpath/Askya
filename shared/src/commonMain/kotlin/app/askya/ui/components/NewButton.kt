package app.askya.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.askya.ui.theme.Accent
import app.askya.ui.theme.Ink

/**
 * Единственная кнопка раздела: чёрная таблетка с коралловой подписью.
 *
 * Та же, что «new card» в AskyaDay: разделы отличаются тем, что заводят, а не
 * тем, как выглядит кнопка. Подпись меняется — «new file» на полке, «new list»
 * в списках, — а место, форма и цвет остаются, и рука находит её не глядя.
 *
 * Кнопка одна на весь экран. Две-три кнопки заставляли бы выбирать кнопку
 * раньше, чем дело; что именно заводим, спрашивается уже внутри.
 */
@Composable
fun NewButton(label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        containerColor = Ink,
        contentColor = Accent,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
