package app.askya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.askya.domain.model.MarkColor
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Ink
import app.askya.ui.theme.markColor

/**
 * Выбор краски-метки: восемь кружков, у выбранного галочка и обводка.
 *
 * Одна на всех, кто красится: счёт, статья, нить. Жила она в карточках Ledger,
 * пока красились только счета и статьи; с третьим желающим копия начала бы
 * расходиться с оригиналом — восемь кружков в одном месте и семь в другом
 * человек заметит раньше, чем программист.
 *
 * По четыре в ряд, чтобы два ряда стояли квадратом: восемь в строку не
 * помещаются, а по три получается ряд из двух в хвосте.
 *
 * Галочка кремовая, а не белая: она лежит на краске, а не на бумаге, и белое
 * пятно на тёмно-синем светит.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkPalette(chosen: MarkColor?, onPick: (MarkColor) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 4,
        modifier = Modifier.fillMaxWidth(),
    ) {
        MarkColor.entries.forEach { option ->
            val picked = option == chosen
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(markColor(option))
                    .border(
                        width = if (picked) 2.dp else 0.dp,
                        color = if (picked) Ink else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onPick(option) },
            ) {
                if (picked) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Выбрано",
                        tint = Cream,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
