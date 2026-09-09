package app.askya.ui.video

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.echo.EchoCard
import app.askya.ui.echo.EchoGroup
import app.askya.ui.echo.EchoPill
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.Sunset
import app.askya.video.VideoFormats
import kotlinx.coroutines.launch

/**
 * Дорожки и субтитры — карточка поверх плеера.
 *
 * То, ради чего в фильме вообще лезут в меню: переключить озвучку, включить
 * субтитры, подложить свои и подвинуть их, когда они опаздывают. Всё это есть
 * у VLC, и всё это здесь стоит в одном месте, а не разложено по трём.
 *
 * Сдвиг задаётся кнопками по десятой доле секунды, а не полем ввода: субтитры
 * подгоняют на слух, глядя в экран, — и попадают в такт за три-четыре нажатия,
 * а не за набор числа.
 */
@Composable
fun VideoTracksCard(onDismiss: () -> Unit) {
    val engine = appContainer().videoEngine
    val state by engine.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickSubtitles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        engine.addSubtitles(uri.toString())
    }

    EchoCard(title = "Дорожки", onDismiss = onDismiss, height = null) {
        // Поля карточки — те же, что у всех карточек Askya (20 по бокам, 14
        // сверху): без них строки выбора, кнопки сдвига и пояснения упирались
        // в саму обводку, и карточка читалась как обрезанная по краю.
        Column(
            modifier = Modifier
                .fadingVerticalScroll()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {

            EchoGroup(title = "Звук") {
                if (state.audioTracks.isEmpty()) {
                    Hint("В этом файле звуковых дорожек не нашлось")
                } else {
                    state.audioTracks.forEach { track ->
                        ChoiceRow(
                            label = track.name,
                            chosen = track.id == state.audioTrackId,
                            onClick = { engine.setAudioTrack(track.id) },
                        )
                    }
                }
            }

            if (state.audioTracks.size > 1 || state.audioDelayMs != 0L) {
                DelayLine(
                    title = "Сдвиг звука",
                    valueMs = state.audioDelayMs,
                    onChange = { engine.setAudioDelay(it) },
                )
            }

            Spacer(Modifier.height(18.dp))

            EchoGroup(title = "Субтитры") {
                ChoiceRow(
                    label = "Выключить",
                    chosen = state.subtitleTrackId < 0,
                    onClick = { engine.setSubtitleTrack(-1) },
                )
                state.subtitleTracks
                    // Первой строкой VLC отдаёт «Disable» со своим номером —
                    // своё «Выключить» уже стоит выше, и второе такое же
                    // читалось бы как две разные кнопки для одного дела.
                    .filter { it.id >= 0 }
                    .forEach { track ->
                        ChoiceRow(
                            label = track.name,
                            chosen = track.id == state.subtitleTrackId,
                            onClick = { engine.setSubtitleTrack(track.id) },
                        )
                    }
                ChoiceRow(
                    label = "Подложить файл…",
                    chosen = false,
                    onClick = { pickSubtitles.launch(VideoFormats.subtitlePickTypes) },
                )
            }

            DelayLine(
                title = "Сдвиг субтитров",
                valueMs = state.subtitleDelayMs,
                onChange = { engine.setSubtitleDelay(it) },
            )

            Spacer(Modifier.height(12.dp))
            Hint(
                "Субтитры рядом с фильмом плеер сам не находит: он читает открытый файл, " +
                    "а не папку. Поэтому их подкладывают руками.",
            )
        }
    }
}

/**
 * Скорость и шаг перемотки.
 *
 * Скорость меняется сразу и только для этого файла; «запомнить» делает её той,
 * с которой открывается всякий следующий. Это разные желания: полуторную для
 * одной лекции ставят на время, а тот, кто смотрит так все, не должен ставить
 * её каждый раз заново.
 */
@Composable
fun VideoSpeedCard(onDismiss: () -> Unit) {
    val container = appContainer()
    val engine = container.videoEngine
    val preferences = container.videoPreferences
    val state by engine.state.collectAsStateWithLifecycle()
    val settings by preferences.state.collectAsStateWithLifecycle()

    EchoCard(title = "Скорость", onDismiss = onDismiss, height = null) {
        Column(
            modifier = Modifier
                .fadingVerticalScroll()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Pills(
                values = RATES,
                label = { rate -> rateLabel(rate) },
                chosen = { rate -> kotlin.math.abs(rate - state.rate) < 0.01f },
                onPick = { engine.setRate(it) },
            )

            Row(modifier = Modifier.padding(top = 12.dp)) {
                EchoPill(
                    label = "Запомнить как обычную",
                    chosen = kotlin.math.abs(settings.rate - state.rate) < 0.01f,
                    onClick = { preferences.setRate(state.rate) },
                )
            }

            Spacer(Modifier.height(20.dp))

            EchoGroup(title = "Шаг перемотки") {
                Pills(
                    values = STEPS,
                    label = { "$it с" },
                    chosen = { it == settings.seekStepSeconds },
                    onPick = { preferences.setSeekStep(it) },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            Hint("Столько отматывают двойное касание по краю экрана и кнопки со стрелками.")
        }
    }
}

// ---- Мелочи, общие для карточек ----

/** Строка выбора: подпись слева, галочка у выбранного. */
@Composable
private fun ChoiceRow(label: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (chosen) Sunset else NightInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (chosen) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Sunset,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Сдвиг в миллисекундах: минус, значение, плюс — и сброс в ноль. */
@Composable
private fun DelayLine(title: String, valueMs: Long, onChange: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = NightMuted,
            modifier = Modifier.weight(1f),
        )
        EchoPill(label = "−0,1 с", chosen = false, onClick = { onChange(valueMs - 100) })
        Text(
            text = "%+.1f".format(valueMs / 1000f),
            style = MaterialTheme.typography.labelLarge,
            color = if (valueMs == 0L) NightMuted else Sunset,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onChange(0) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        EchoPill(label = "+0,1 с", chosen = false, onClick = { onChange(valueMs + 100) })
    }
}

/** Ряд слов в рамке — выбор одним касанием. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Pills(
    values: List<T>,
    label: (T) -> String,
    chosen: (T) -> Boolean,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            EchoPill(label = label(value), chosen = chosen(value), onClick = { onPick(value) })
        }
    }
}

/** Пояснение мелким под настройкой. */
@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = NightMuted,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** «1x», «1,5x» — без хвостовых нулей: в кнопке они только мешают. */
private fun rateLabel(rate: Float): String =
    if (rate == rate.toInt().toFloat()) "${rate.toInt()}x"
    else "%.2f".format(rate).trimEnd('0').trimEnd('.', ',').replace('.', ',') + "x"

private val RATES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

private val STEPS = listOf(5, 10, 15, 30, 60)
