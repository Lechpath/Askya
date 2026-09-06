package app.askya.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.R
import app.askya.data.entity.Note
import app.askya.ui.components.fadingVerticalScroll

/**
 * Боковое меню: вордмарк, разделы, список недавних заметок и три кнопки внизу.
 *
 * Кнопки внизу — не четвёртый, пятый и шестой разделы, а то, что делают, не
 * уходя из меню: записать пришедшее в голову, включить музыку, заглянуть в
 * настройки. Их место — под большим пальцем, у нижнего края, а разделы стоят
 * там, где их читают, — сверху.
 *
 * «Настройки» переехали из списка разделов сюда: настройки не место, куда
 * ходят, а ящик с инструментами, и в одном ряду с AskyaDay и Scroll они
 * выглядели четвёртым разделом приложения, которым никогда не были.
 */
@Composable
fun AppDrawer(
    currentRoute: String?,
    recents: List<Note>,
    playing: Boolean,
    onSelect: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onQuickNote: () -> Unit,
    onPlay: () -> Unit,
    weather: WeatherLine? = null,
    onWeather: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            // Правые углы скруглены, левые — нет: меню выезжает из-за левого
            // края экрана, и там у него края нет вовсе. Скругление той же
            // величины, что у карточек и окон (28), — меню такой же лист
            // поверх страницы, только во всю высоту.
            .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
            .width(356.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 28.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Название — не текст, а обведённая в кривые каллиграфия: тот же
            // рисунок, что на иконке. Тонируется цветом темы.
            Icon(
                painter = painterResource(R.drawable.ic_wordmark),
                contentDescription = "Askya",
                tint = MaterialTheme.colorScheme.onBackground,
                // Размер намеренно не задан: берётся из самого ресурса, поэтому
                // при перегенерации вордмарка пропорции не разъезжаются с кодом.
            )

            Spacer(Modifier.weight(1f))

            // Погода стоит рядом с именем приложения, а не строчкой в списке
            // разделов: на неё смотрят мельком, открывая меню за чем-то другим,
            // — и ради этого взгляда её и вынесли наверх. Нажатие уводит в
            // раздел, где та же погода расписана по часам и по дням.
            if (weather != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onWeather)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (weather.mark.isNotEmpty()) {
                        Text(text = weather.mark, fontSize = 18.sp)
                    }
                    Text(
                        text = weather.degrees,
                        style = MaterialTheme.typography.titleMedium,
                        // Неизвестная погода стоит приглушённой: это ещё не
                        // сведения, а приглашение за ними сходить.
                        color = if (weather.known) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fadingVerticalScroll(),
        ) {
            Destination.entries.forEach { destination ->
                DrawerRow(
                    destination = destination,
                    selected = destination.route == currentRoute,
                    onClick = { onSelect(destination.route) },
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )

            Text(
                text = "Недавнее",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
            )

            if (recents.isEmpty()) {
                Text(
                    text = "Пока ничего",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp),
                )
            } else {
                recents.forEach { note ->
                    Text(
                        text = note.title.ifBlank { "Без названия" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenNote(note.id) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        BottomBar(
            playing = playing,
            settingsOpen = currentRoute == Routes.SETTINGS,
            onQuickNote = onQuickNote,
            onPlay = onPlay,
            onSettings = { onSelect(Routes.SETTINGS) },
        )
    }
}

/**
 * Погода строкой: знак и градусы.
 *
 * Именно два коротких слова, а не карточка с прогнозом: в шапке меню место
 * ровно на взгляд, а всё остальное живёт в разделе.
 *
 * [known] = false означает «погоды ещё нет» — не спрашивали, не дали доступ к
 * месту, не было сети. Тогда на месте градусов стоит слово «погода», и оно
 * ведёт туда же.
 *
 * Убирать строку в этом случае нельзя, хотя поначалу так и было сделано: вход в
 * раздел погоды один — сама эта строка, — и без неё раздел оказывался
 * недостижим ровно тогда, когда в него и надо попасть (доступ к месту
 * спрашивается уже внутри). Приглашение вместо прочерка — не украшение, а
 * единственная дверь.
 */
data class WeatherLine(val mark: String, val degrees: String, val known: Boolean = true)

/**
 * Три кнопки внизу меню: заметка, музыка, настройки.
 *
 * Знаком и подписью, а не одним знаком: у карандаша и шестерёнки значения
 * угадываются, у «play Echo» — нет, а подписывать одну из трёх стыдно.
 *
 * Средняя кнопка меняет лицо по тому, что делает плеер: играет — значит,
 * нажатие остановит, и на ней «Pause». Меню при этом не закрывается: это
 * управление, а не переход, и ответ на нажатие человек должен увидеть.
 */
@Composable
private fun BottomBar(
    playing: Boolean,
    settingsOpen: Boolean,
    onQuickNote: () -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomButton(
                icon = Icons.Outlined.EditNote,
                label = "Заметка",
                onClick = onQuickNote,
                modifier = Modifier.weight(1f),
            )
            BottomButton(
                icon = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                label = if (playing) "Pause" else "play Echo",
                accent = playing,
                onClick = onPlay,
                modifier = Modifier.weight(1f),
            )
            BottomButton(
                painter = painterResource(R.drawable.ic_menu_settings),
                label = "Настройки",
                accent = settingsOpen,
                onClick = onSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Одна кнопка нижнего ряда: знак и подпись под ним. */
@Composable
private fun BottomButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    accent: Boolean = false,
) {
    val color = if (accent) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onBackground

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )

            painter != null -> Icon(
                painter = painter,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DrawerRow(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(destination.icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = color,
        )
        Text(
            text = destination.label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}
