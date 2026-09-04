package app.askya.ui.video

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.echo.formatDuration
import app.askya.ui.echo.EchoCard
import app.askya.ui.echo.EchoGroup
import app.askya.ui.echo.EchoPill
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import app.askya.video.EditResult
import app.askya.video.VideoEdits
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
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

            EchoGroup(title = "Звук", modifier = Modifier.padding(top = 12.dp)) {
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
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Pills(
                values = RATES,
                label = { rate -> rateLabel(rate) },
                chosen = { rate -> kotlin.math.abs(rate - state.rate) < 0.01f },
                onPick = { engine.setRate(it) },
                modifier = Modifier.padding(top = 14.dp),
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

/**
 * Правка видео — карточка поверх плеера.
 *
 * Границы куска берутся с места, на котором сейчас стоит фильм: человек
 * доводит до нужного кадра и говорит «отсюда», потом «досюда». Это точнее и
 * быстрее любого поля ввода — он смотрит на кадр, а не на цифры.
 *
 * Всё, что здесь делается, — пересборка контейнера без пережатия
 * ([VideoEdits]): мгновенно и без потери качества, но и только то, для чего
 * кадры не нужно рисовать заново. Файл, который системный разбор не открывает,
 * говорит об этом сразу, а не после ожидания.
 */
@Composable
fun VideoEditCard(onDismiss: () -> Unit) {
    val container = appContainer()
    val engine = container.videoEngine
    val store = container.videoStore
    val images = container.imageStore
    val state by engine.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val source = state.source
    var trimming by remember { mutableStateOf(false) }
    var withSound by remember { mutableStateOf(true) }
    var turn by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var editable by remember { mutableStateOf<Boolean?>(null) }

    androidx.compose.runtime.LaunchedEffect(source?.uri) {
        val uri = source?.uri ?: return@LaunchedEffect
        editable = VideoEdits.editable(context, uri)
    }

    fun finish(result: EditResult) {
        working = false
        notice = when (result) {
            is EditResult.Done -> "Готово: ${result.name} — в папке ${store.folderName}"
            is EditResult.Failed -> result.reason
        }
    }

    EchoCard(title = "Правка", onDismiss = onDismiss, height = null) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

            if (editable == false) {
                Hint(
                    "Этот файл плеер играет, но системный разбор его не открывает — " +
                        "резать нечем. Кадр снять тоже, скорее всего, не выйдет.",
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EchoPill(
                    label = if (withSound) "Со звуком" else "Без звука",
                    chosen = withSound,
                    onClick = { withSound = !withSound },
                )
            }

            Spacer(Modifier.height(16.dp))

            EchoGroup(title = "Поворот") {
                Pills(
                    values = TURNS,
                    label = { if (it == 0) "как есть" else "$it°" },
                    chosen = { it == turn },
                    onPick = { turn = it },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionRow(
                    label = if (working) "Режем…" else "Обрезать",
                    enabled = !working && editable != false && source != null,
                ) {
                    if (source != null) trimming = true
                }

                ActionRow(
                    label = "Звук отдельным файлом",
                    enabled = !working && editable != false && source != null,
                ) {
                    val uri = source?.uri ?: return@ActionRow
                    working = true
                    notice = null
                    scope.launch {
                        finish(VideoEdits.extractAudio(context, store, uri, source.title))
                    }
                }

                ActionRow(label = "Сохранить кадр", enabled = !working && source != null) {
                    val uri = source?.uri ?: return@ActionRow
                    working = true
                    notice = null
                    scope.launch {
                        val bitmap = VideoEdits.frame(context, uri, state.positionMs)
                        working = false
                        notice = if (bitmap == null) {
                            "Кадр из этого файла системными средствами не снимается"
                        } else {
                            val saved = images.save(bitmap, source.title)
                            if (saved == null) "Кадр не записался"
                            else "Кадр — в папке ${images.folderName}"
                        }
                    }
                }
            }

            notice?.let { text ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sunset,
                )
            }

            Spacer(Modifier.height(14.dp))
            Hint(
                "Кусок пересобирается без пережатия: качество то же, а начало сдвигается " +
                    "назад до ближайшего опорного кадра — обычно меньше чем на две секунды.",
            )
        }
    }

    // Ножницы — та же карточка, что и в лаборатории ([VideoTrimCard]), а не
    // вторая пара полей рядом с плеером. Границы куска ставят в одном месте на
    // весь раздел: два инструмента для одного дела разошлись бы в первый же
    // раз, когда один из них научился бы чему-нибудь новому.
    //
    // Звук и поворот остаются здесь: они относятся не к границам, а к тому,
    // что делать с куском, и отвечает на это карточка правки.
    if (trimming && source != null) {
        VideoTrimCard(
            title = source.title,
            source = source.uri,
            durationMs = state.durationMs,
            onDismiss = { trimming = false },
            onDone = { fromMs, toMs, name ->
                trimming = false
                working = true
                notice = null
                scope.launch {
                    finish(
                        VideoEdits.cut(
                            context = context,
                            store = store,
                            source = source.uri,
                            title = name,
                            fromMs = fromMs,
                            toMs = toMs,
                            keepAudio = withSound,
                            rotateBy = turn,
                        ),
                    )
                }
            },
        )
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
            .padding(horizontal = 10.dp, vertical = 12.dp),
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

/** Кнопка действия во всю ширину карточки. */
@Composable
private fun ActionRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NightPanelSoft)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) NightInk else NightMuted,
        )
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

private val TURNS = listOf(0, 90, 180, 270)
