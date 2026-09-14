package app.askya.ui.scroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.resources.Res
import app.askya.resources.ic_album
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.EditableLine
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Ink

/**
 * Альбом, раскрытый поверх экрана: знак, название и что с ним можно сделать.
 *
 * Та же карточка, что у дела в AskyaDay (`CardDialog`) — те же скруглённые 28,
 * тот же рост пружиной из ничего, та же строка правки на подложке и те же
 * значки с подписями внизу. Раньше здесь стояло системное окно Material с
 * полем и двумя словами по правому краю: оно выглядело чужим — так спрашивает
 * телефон, а не Askya.
 *
 * Поле сразу под правкой: имя — единственное, что здесь заполняют, и ждать
 * тапа по строке незачем. Enter сохраняет, но пустое имя не принимается — как
 * и галочка: альбом без названия не отличить от соседнего.
 *
 * [onDelete] `null` означает новый альбом: удалять ещё нечего. [hint] —
 * строка под полем, где нужно сказать, что случится с картинками.
 */
@Composable
internal fun AlbumDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    hint: String? = null,
    onDelete: (() -> Unit)? = null,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val ready = draft.isNotBlank()

    AlbumCard(title = title, onDismiss = onDismiss) {
        EditableLine(
            value = draft,
            onValueChange = { draft = it },
            // Всегда открытая строка: правку здесь не выбирают между местами,
            // место одно.
            active = true,
            dimmed = false,
            hint = "Как назовём?",
            fontSize = 26.sp,
            weight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            onDone = { if (ready) onConfirm(draft) },
        )

        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // «Удалить» уходит к левому краю, подальше от галочки: рядом их
            // легко перепутать, а промах здесь необратим.
            if (onDelete != null) {
                ActionButton(
                    icon = Icons.Outlined.DeleteOutline,
                    label = "Удалить",
                    color = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Сохранить",
                accent = ready,
                enabled = ready,
                onClick = { onConfirm(draft) },
            )
        }
    }
}

/**
 * Куда переложить выбранные картинки — та же карточка альбома, только вместо
 * имени в ней метки альбомов.
 *
 * Выбор здесь и есть действие: тап по метке перекладывает и закрывает. Галочки
 * «сохранить» нет намеренно — подтверждать нечего, а лишний шаг превратил бы
 * перекладывание пачки в два касания на каждую.
 */
@Composable
internal fun AlbumPickDialog(
    count: Int,
    albums: List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
    onNew: () -> Unit,
    title: String = "Куда переложить?",
) {
    AlbumCard(title = title, onDismiss = onDismiss) {
        Text(
            text = "$count ${plural(count, "картинка", "картинки", "картинок")}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        AlbumChoice(
            albums = albums,
            selected = null,
            // Здесь метка — не выбранное, а действие: подсвечивать «без
            // альбома» значило бы соврать, что картинки уже там.
            marked = false,
            onSelect = onPick,
            onNew = onNew,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            ActionButton(
                icon = Icons.Outlined.Close,
                label = "Отмена",
                onClick = onDismiss,
            )
        }
    }
}

/**
 * Общая оболочка обеих карточек — общее окно приложения ([AskyaDialog]) со
 * знаком альбома.
 *
 * Знак крупный и в мягком круге: карточка говорит про папку, у которой пока
 * нет ни обложки, ни содержимого, и кроме знака показать в ней нечего. Он же
 * стоит на пустых альбомах в ленте — по нему их и узнают.
 */
@Composable
private fun AlbumCard(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AskyaDialog(
        onDismiss = onDismiss,
        badge = { DialogBadge(painterResource(Res.drawable.ic_album)) },
    ) {
        DialogCaption(title)
        content()
    }
}

/**
 * Выбор альбома: «без альбома», уже заведённые и «новый».
 *
 * Метками в строку, а не списком: альбомов у человека единицы, и списку на три
 * строки пришлось бы отдать пол-экрана — в карточке картинки его отобрали бы у
 * самой картинки.
 *
 * «Без альбома» — не пустое место, а такой же выбор: иначе непонятно, выбрано
 * ли что-то вообще или вопрос ещё ждёт ответа.
 *
 * Общий на карточку картинки и на перекладывание пачки: вопрос «в какой
 * альбом» один и тот же, и отвечать на него в двух местах по-разному незачем.
 * Разница одна — [marked]: в карточке метка показывает, где картинка лежит
 * сейчас, а при перекладывании выбранного ещё нет, там метка это действие.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlbumChoice(
    albums: List<Pair<Long, String>>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onNew: () -> Unit,
    marked: Boolean = true,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Choice(
            text = "Без альбома",
            selected = marked && selected == null,
            onClick = { onSelect(null) },
        )

        albums.forEach { (id, title) ->
            Choice(
                text = title.ifBlank { "Без названия" },
                selected = marked && selected == id,
                onClick = { onSelect(id) },
            )
        }

        Choice(text = "Новый альбом", selected = false, onClick = onNew)
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentSoft,
            selectedLabelColor = Ink,
        ),
    )
}
