package app.askya.ui.echo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.echo.EqualizerBand
import app.askya.echo.formatFrequency
import app.askya.echo.formatGain
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.Sunset

/**
 * Эквалайзер — карточка поверх плеера.
 *
 * Раньше он выезжал шторкой Material снизу; шторка — единственное место в
 * Askya, где раскрытое приходило не карточкой, и на фоне остального раздела
 * это читалось как чужой экран. Теперь он открывается тем же движением, что
 * альбом на полке и песня в списке: карточка вырастает поверх плеера, а плеер
 * остаётся под ней — полосы крутят на слух, слушая играющее.
 *
 * Подписи английские, и это не небрежность: слова эквалайзера — «Rock»,
 * «Treble», «Flat» — на любом устройстве и в любом плеере пишутся так, их
 * узнают в лицо. Русский «Высокие частоты» пришлось бы читать, а не узнавать,
 * и он вдвое длиннее полосы, над которой стоит.
 *
 * Полосы вертикальные, как во всех эквалайзерах: подъём — это вверх, и
 * горизонтальный ползунок заставляет каждый раз вспоминать, в какую сторону
 * громче. Ползунка Material здесь нет — только линия, засветка от середины и
 * точка: рисунок эквалайзера и должен читаться силуэтом, а не деталями.
 */
@Composable
fun EchoEqualizerCard(onDismiss: () -> Unit) {
    val equalizer = appContainer().echoEqualizer
    val state by equalizer.state.collectAsStateWithLifecycle()

    // Эффект заводится при первом открытии карточки: держать системный ресурс
    // ради человека, который в эквалайзер не заглядывал, незачем.
    LaunchedEffect(Unit) { equalizer.attach() }

    EchoCard(
        title = "Equalizer",
        subtitle = state.preset ?: "Custom",
        onDismiss = onDismiss,
        width = 0.94f,
        // Ниже прочих карточек: полосы стоят в ряд, и сколько бы их ни было —
        // пять или десять, — высота карточки от этого не растёт.
        height = 0.52f,
        actions = {
            if (state.available) {
                // Слово, а не выключатель Material: тот же язык, что у кнопок
                // плеера, где режим тоже назван словом и подчёркнут.
                Text(
                    text = if (state.enabled) "On" else "Off",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    color = if (state.enabled) Sunset else NightMuted,
                    textDecoration = if (state.enabled) TextDecoration.Underline else null,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { equalizer.setEnabled(!state.enabled) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        },
    ) {
        if (!state.available) {
            Text(
                text = "This phone does not let apps tune the sound — no bands here.",
                style = MaterialTheme.typography.bodyMedium,
                color = NightMuted,
                modifier = Modifier.padding(24.dp),
            )
            return@EchoCard
        }

        // Заготовки: длинный ряд, который двигают пальцем. Двадцать четыре
        // имени в столбик заняли бы карточку целиком, а нужны они по одному.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            equalizer.presets.forEach { name ->
                EchoPill(
                    label = name,
                    chosen = state.preset == name,
                    onClick = { equalizer.setPreset(name) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Шкала слева: сколько это в децибелах, спрашивают редко, но
            // ответить на это должно быть чем.
            Column(
                modifier = Modifier.height(BAND_HEIGHT.dp).width(28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Scale(formatGain(state.maxMb))
                Scale("0")
                Scale(formatGain(state.minMb))
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                state.bands.forEach { band ->
                    Band(
                        band = band,
                        minMb = state.minMb,
                        maxMb = state.maxMb,
                        onChange = { equalizer.setLevel(band.index, it) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Your tuning stays with the music — closing this card keeps it.",
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

/**
 * Одна полоса: подъём сверху, линия с точкой посередине, частота снизу.
 *
 * Тянется пальцем вверх-вниз, а тап ставит точку сразу в нужное место: полосу
 * чаще не подкручивают, а бросают туда, где она должна быть.
 */
@Composable
private fun Band(band: EqualizerBand, minMb: Int, maxMb: Int, onChange: (Int) -> Unit) {
    val span = (maxMb - minMb).toFloat().coerceAtLeast(1f)
    val share = (band.levelMb - minMb) / span

    /** Место пальца по высоте — в миллибелы: верх это максимум, низ минимум. */
    fun levelAt(y: Float, height: Float): Int {
        val fraction = (1f - (y / height)).coerceIn(0f, 1f)
        return (minMb + fraction * span).toInt()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatGain(band.levelMb),
            style = MaterialTheme.typography.labelSmall,
            color = if (band.levelMb == 0) NightMuted else Sunset,
        )

        Box(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .width(36.dp)
                .height(BAND_HEIGHT.dp)
                .pointerInput(minMb, maxMb, band.index) {
                    detectVerticalDragGestures { change, _ ->
                        onChange(levelAt(change.position.y, size.height.toFloat()))
                    }
                }
                .pointerInput(minMb, maxMb, band.index) {
                    detectTapGestures { tap ->
                        onChange(levelAt(tap.y, size.height.toFloat()))
                    }
                }
                .drawBehind {
                    val x = size.width / 2
                    val middle = size.height / 2
                    val y = size.height * (1f - share)
                    val stroke = 3.dp.toPx()

                    // Вся полоса — тусклая линия: видно, докуда её можно
                    // двигать, даже когда она стоит на нуле.
                    drawLine(
                        color = NightBorder,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = stroke,
                    )
                    // Подъём — засветка от середины: сразу видно и сколько, и
                    // в какую сторону.
                    drawLine(
                        color = Sunset,
                        start = Offset(x, middle),
                        end = Offset(x, y),
                        strokeWidth = stroke,
                    )
                    drawCircle(color = Sunset, radius = 5.dp.toPx(), center = Offset(x, y))
                },
        )

        Text(
            text = formatFrequency(band.frequencyHz),
            style = MaterialTheme.typography.labelSmall,
            color = NightMuted,
        )
    }
}

@Composable
private fun Scale(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = NightMuted.copy(alpha = 0.7f),
    )
}

/** Высота полосы: столько, чтобы шаг в децибел был различим пальцем. */
private const val BAND_HEIGHT = 168
