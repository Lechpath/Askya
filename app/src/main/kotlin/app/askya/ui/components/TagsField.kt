package app.askya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tag
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
import androidx.compose.ui.unit.dp
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Muted

/**
 * Теги записи: чем она помечена, чтобы потом найтись.
 *
 * Тег — не вторая книга и не второй альбом. Книга — это место, где запись
 * лежит, и место у неё одно; тег — слово, по которому её ищут, и таких слов
 * бывает сколько угодно. «Дача» может быть и в книге «Ремонт», и в книге
 * «Покупки», и найти её нужно одним словом сразу в обеих.
 *
 * Лежат теги в самой записи списком строк ([app.askya.data.entity.Note.tags]),
 * а не отдельной таблицей: для поиска по слову этого достаточно, а join-таблица
 * добавила бы сущность, DAO и миграцию без всякого выигрыша.
 *
 * Тег — одно слово. Не из скупости: набирают их на бегу, в одну строку через
 * запятую, и «купить молоко» тегом означало бы, что по слову «молоко» запись
 * уже не найдётся. Пробел поэтому разделяет теги, а не входит в них.
 */

/**
 * Разбирает набранное в теги: «дом, #дача покупки» → «дом», «дача»,
 * «покупки».
 *
 * Решётка снимается: её пишут по привычке из других приложений, и тег «#дача»,
 * который не находится по слову «дача», был бы ловушкой. Повторы отбрасываются
 * без оглядки на регистр — «Дача» и «дача» это один тег, и первым остаётся то
 * написание, которым его набрали.
 */
fun parseTags(source: String): List<String> {
    val kept = LinkedHashMap<String, String>()
    source.split(',', ';', ' ', '\n', '\t')
        .map { it.trim().trimStart('#').trim() }
        .filter { it.isNotEmpty() }
        .map { it.take(TAG_LIMIT) }
        .forEach { tag -> kept.putIfAbsent(tag.lowercase(), tag) }
    return kept.values.toList()
}

/** Потолок на длину тега: искать по слову, а не по абзацу. */
private const val TAG_LIMIT = 24

/**
 * Строка тегов под названием записи. Тап открывает [TagsDialog].
 *
 * Пока тегов нет, вместо них стоит приглашение — тем же приглушённым голосом,
 * которым сказано «отдельным файлом» в строке книги: тег дело необязательное, и
 * требовать его у каждой записи не за что.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsLine(tags: List<String>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Tag,
            contentDescription = null,
            tint = if (tags.isEmpty()) Muted else Accent,
            modifier = Modifier.size(16.dp),
        )
        if (tags.isEmpty()) {
            Text(
                text = "добавить тег",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
        } else {
            tags.forEach { tag -> TagChip(tag) }
        }
    }
}

/** Тег плашкой: то же, чем в Askya отмечено выбранное слово. */
@Composable
fun TagChip(tag: String, modifier: Modifier = Modifier) {
    Text(
        text = "#$tag",
        style = MaterialTheme.typography.labelMedium,
        color = AccentInk,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AccentSoft)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * Окно тегов: что уже стоит и что добавить.
 *
 * Правится списком, а не одной строкой со всеми тегами: строку «дом дача
 * покупки» приходится вычитывать глазами, чтобы убрать из середины одно слово,
 * а плашка с крестиком убирается пальцем.
 *
 * «Готово» забирает и то, что осталось недобранным в строке: человек, набравший
 * тег и нажавший «Готово» вместо клавиши ввода, имел в виду именно этот тег.
 */
@Composable
fun TagsDialog(
    tags: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var current by remember { mutableStateOf(tags) }
    var draft by remember { mutableStateOf("") }

    /** Дописать набранное к тем, что уже стоят, и очистить строку. */
    fun take(): List<String> {
        val next = parseTags(current.joinToString(" ") + " " + draft)
        current = next
        draft = ""
        return next
    }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Tag) }) {
        DialogTitle("Теги")
        DialogText(
            "Слово, по которому запись найдётся в Scroll. Тег — одно слово; " +
                "несколько набираются через запятую.",
        )

        if (current.isNotEmpty()) {
            DialogCaption("Уже стоят")
            TagsEditable(tags = current, onRemove = { tag -> current = current - tag })
        }

        DialogCaption("Добавить")
        DialogField(
            value = draft,
            onValueChange = { draft = it },
            hint = "дача, покупки",
            onDone = { take() },
        )

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Add,
                label = "Ещё",
                onClick = { take() },
                enabled = draft.isNotBlank(),
            )
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Готово",
                accent = true,
                onClick = { onSave(take()) },
            )
        }
    }
}

/** Уже стоящие теги — каждый с крестиком: убирают их пальцем, а не правкой. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditable(tags: List<String>, onRemove: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentSoft)
                    .clickable { onRemove(tag) }
                    .padding(start = 9.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentInk,
                )
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Убрать тег",
                    tint = AccentInk,
                    modifier = Modifier.padding(start = 4.dp).size(14.dp),
                )
            }
        }
    }
}
