package app.askya.ui.askyaday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.askya.data.entity.RoutineItem
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.blockIconOf
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.components.formatRange
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentSoft

/**
 * Взять дела из списка дел в этот день — третий способ собрать расписание.
 *
 * Два прежних отвечали на вопрос целиком: «заполнить по распорядку» разворачивает
 * весь список разом, «собрать день» раскладывает его сам. Между ними не было
 * ничего для обычного случая: сегодня нужны подъём, работа и бег, а остальное
 * из списка — нет. Оставалось переписывать дела руками в новую карточку, хотя
 * они уже записаны рядом.
 *
 * Выбирается сразу несколько: день собирают одним заходом, а не открывают окно
 * заново под каждое дело. Пока ничего не отмечено, «Добавить» гаснет — окно,
 * которое можно закрыть, ничего не сделав, честнее пустого действия.
 *
 * Дела ложатся в день на своё время — то, что названо в списке. Правка дня
 * список не трогает и наоборот: это по-прежнему две разные вещи — правило, по
 * которому день собирается, и сам прожитый день.
 *
 * Что уже стоит в дне, отмечено и не выбирается: одно и то же дело дважды в
 * одном дне — не выбор, а промах. Признак тот же, что и везде ([app.askya.domain.plan.sameDeed]):
 * совпали название и время начала — значит, дело то же самое.
 */
@Composable
internal fun RoutinePickCard(
    items: List<RoutineItem>,
    inDay: (RoutineItem) -> Boolean,
    onDismiss: () -> Unit,
    onAdd: (List<RoutineItem>) -> Unit,
) {
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    val chosen = items.filter { it.id in picked }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Checklist) }) {
        DialogCaption("Взять из списка дел")

        if (items.isEmpty()) {
            Text(
                text = "Список дел пуст. Заведи в нём дело — и его можно будет взять в день.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@AskyaDialog
        }

        // Список прокручивается внутри окна: дел обычного дня бывает два
        // десятка, и окно во весь экран из-за них разрослось бы до экрана.
        Column(
            modifier = Modifier
                .heightIn(max = 340.dp)
                .fadingVerticalScroll(),
        ) {
            items.forEach { item ->
                val already = inDay(item)
                PickRow(
                    item = item,
                    already = already,
                    picked = item.id in picked,
                    onClick = {
                        picked = if (item.id in picked) picked - item.id else picked + item.id
                    },
                )
            }
        }

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Check,
                label = if (chosen.isEmpty()) "Добавить" else "Добавить ${chosen.size}",
                accent = chosen.isNotEmpty(),
                enabled = chosen.isNotEmpty(),
                onClick = { onAdd(chosen) },
            )
        }
    }
}

/**
 * Строка выбора: знак дела, название и час, справа — кружок отметки.
 *
 * Знак тот же, что на карточке дня, и угадывается по названию тем же кодом:
 * дело должно узнаваться в окне тем же лицом, каким оно встанет в расписание.
 *
 * Отмеченное залито мягким кораллом — той же плашкой, которой в карточке дела
 * показано «правится вот это». Стоящее в дне отмечено кружком с галочкой, но
 * приглушено и не нажимается: это не выбор, а сообщение.
 */
@Composable
private fun PickRow(
    item: RoutineItem,
    already: Boolean,
    picked: Boolean,
    onClick: () -> Unit,
) {
    val ink = if (already) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (picked) AccentSoft else Color.Transparent)
            .clickable(enabled = !already, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = blockIconOf(item.title, item.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = ink,
                fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (already) {
                    "уже в дне"
                } else {
                    formatRange(item.startTime, item.endTime)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Отмеченное залито кораллом, стоящее в дне — только серая галочка без
        // кружка: залей его тем же цветом, и «я это выбрал» стало бы неотличимо
        // от «оно тут и так есть».
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (picked) Accent else Color.Transparent)
                .border(
                    width = if (picked || already) 0.dp else 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (picked || already) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = if (picked) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
