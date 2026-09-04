package app.askya.ui.echo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.echo.formatDuration
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.Sunset

/**
 * Управление плеером словами — общее для AskyaEcho и AskyaV.
 *
 * Треугольник, две палки и стрелки с чёрточками — язык магнитофона, и на
 * экране, где название раздела написано пером, они выглядят наклейками с
 * чужой панели. Слово читается сразу и набрано тем же шрифтом, что заголовок
 * и имя дорожки.
 *
 * Жило это в плеере Echo и было его частностью ровно до тех пор, пока у
 * AskyaV не спросили того же вида. Общее оно не из бережливости: у двух
 * плееров одного приложения не должно быть двух разных языков управления —
 * человек ходит из одного в другой, и «Play» в обоих должно значить и
 * выглядеть одинаково.
 */

/**
 * Кнопка плеера — слово, а не значок.
 *
 * «Play» и «Pause» — одна кнопка: она называет не то, что происходит сейчас,
 * а то, что случится по нажатию.
 *
 * Ряд делится на равные доли, и слово стоит посреди своей: иначе «Pause»,
 * которое шире «Play», раздвигало бы соседей на каждом нажатии. Нажимается
 * доля целиком — по слову в 22 кегля пальцем не попасть.
 */
@Composable
internal fun EchoControl(
    text: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontSize = if (accent) 34.sp else 22.sp,
        color = if (accent) Sunset else NightInk,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick, onClickLabel = label)
            .padding(vertical = 10.dp),
    )
}

/**
 * Режим — слово помельче, под кнопками.
 *
 * Включённый режим горит закатом и подчёркнут: одного цвета мало — черта под
 * словом видна и краем глаза, и по ней режим читается, не вглядываясь.
 *
 * Мельче кнопок плеера намеренно: режим ставят раз за вечер, а «дальше»
 * нажимают каждые три минуты, и одинаковый вес путал бы редкое с частым.
 */
@Composable
internal fun EchoMode(
    text: String,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontSize = 17.sp,
        color = if (on) Sunset else NightMuted,
        textDecoration = if (on) TextDecoration.Underline else null,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick, onClickLabel = label)
            .padding(vertical = 8.dp),
    )
}

/**
 * Полоса времени без ползунка — та же, что у Echo, но чертой.
 *
 * У Echo прошедшее время наливает закатом само название раздела, написанное
 * пером ([EchoProgress]). В AskyaV этого не сделать и не нужно: каллиграфии
 * «AskyaV» не существует, а слово поперёк кадра спорило бы с самим кадром —
 * там и так есть на что смотреть. Осталось то, ради чего слово и рисовалось:
 * место в записи показывает **граница цвета**, и хватать пальцем нужно её.
 *
 * Ползунка нет намеренно. Кружок Material — деталь чужой панели, он занимает
 * место, попадает под палец и закрывает собой ровно ту точку, которую человек
 * ищет. Перемотка идёт касанием в нужное место и протяжкой.
 *
 * Пока палец ведёт, наружу идёт [onScrub] — время под пальцем; отпущенный
 * палец даёт [onSeek]. Разделено потому, что перематывать на каждое движение
 * — значит десять раз дёрнуть картинку по дороге к нужной секунде.
 */
@Composable
internal fun EchoLine(
    progress: Float,
    durationMs: Long,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Полоса тонкая, а место под палец — толстое: черта в два
                // пиксела читается, но не ловится, и вокруг неё оставлено
                // столько, сколько нужно пальцу.
                .height(28.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var at = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            at = (start.x / size.width).coerceIn(0f, 1f)
                            onScrub(at)
                        },
                        onDragEnd = { onSeek(at) },
                        onDragCancel = { onSeek(at) },
                        onHorizontalDrag = { change, _ ->
                            at = (change.position.x / size.width).coerceIn(0f, 1f)
                            onScrub(at)
                        },
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { tap ->
                        onSeek((tap.x / size.width).coerceIn(0f, 1f))
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NightMuted.copy(alpha = 0.32f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Sunset),
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration((progress * durationMs).toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
