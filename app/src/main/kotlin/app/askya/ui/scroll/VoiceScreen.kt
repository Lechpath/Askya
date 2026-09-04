package app.askya.ui.scroll

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import app.askya.app.appContainer
import app.askya.data.entity.Note
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogField
import app.askya.ui.components.DialogTitle
import app.askya.ui.components.EmptyState
import app.askya.ui.components.SHELF_COLUMNS
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.TileRow
import app.askya.ui.components.fadingEdges
import app.askya.widget.VoiceWidgetProvider
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Danger
import app.askya.ui.theme.Ink
import app.askya.ui.theme.cardEdge

/**
 * «Голос» — подраздел Scroll: то, что проще сказать, чем написать.
 *
 * ## Зачем он в Scroll, а не в Echo
 *
 * Заметка голосом — это заметка. Она лежит рядом с написанным, ищется там же,
 * убирается той же корзиной на сутки и живёт по тем же правилам; то, что она
 * звучит, а не читается, — свойство записи, а не повод для раздела. В Echo
 * лежит музыка, которую человек собрал слушать, и класть между двух альбомов
 * двадцать секунд «купить масло» — значит испортить и полку, и заметку.
 *
 * ## Как её слушают
 *
 * Не переходя никуда: нажали карточку — над экраном появилась карточка плеера,
 * и она уходит сама, когда заметка кончилась (см.
 * [app.askya.echo.EchoAside]). Отдельного экрана прослушивания здесь нет
 * намеренно: у заметки в двадцать секунд нет содержимого, ради которого стоит
 * открывать страницу.
 *
 * ## Пишется, пока экран открыт
 *
 * Запись останавливается вместе с уходом из подраздела и сохраняется. Askya не
 * держит открытый микрофон в фоне и не заводит ради этого службу в шторке:
 * приложение, которое может слушать, когда его не видно, — это ровно то, чем
 * Askya быть не хочет. Потерять полминуты неудобно; привыкнуть к тому, что
 * блокнот слушает комнату, — хуже.
 */
/**
 * [sayNow] — пришли сюда кружком виджета с рабочего стола, и запись должна
 * начаться сама. [onSaid] гасит просьбу: исполнять её второй раз — например,
 * вернувшись сюда «назад» через час, — значит включить микрофон без спроса.
 */
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    sayNow: Boolean = false,
    onSaid: () -> Unit = {},
) {
    val container = appContainer()
    val viewModel: ScrollViewModel = viewModel(factory = ScrollViewModel.factory(container))
    val voices by viewModel.voices.collectAsStateWithLifecycle()

    val recorder = container.voiceRecorder
    val recording by recorder.state.collectAsStateWithLifecycle()

    val aside by container.echoAside.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // Открытая карточка заметки: там её называют и оттуда убирают. Открывается
    // долгим нажатием — как удаление файла на полке.
    var opened by remember { mutableStateOf<Note?>(null) }

    // Разрешение спрашивается по нажатию на кнопку, а не на входе: до неё
    // микрофон не нужен, а спрошенный заранее выглядит как подслушивание —
    // ровно так же, как это сделано у надиктовки в заметках.
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed -> if (allowed) viewModel.startRecording() }

    // Уходя с экрана, запись заканчиваем и сохраняем. См. рассуждение выше:
    // микрофон в фоне Askya не держит.
    DisposableEffect(Unit) {
        onDispose { viewModel.stopRecording() }
    }

    // Пришли кружком виджета — пишем сразу. Разрешение всё равно спрашивает
    // это окно: у виджета окна нет, а просить микрофон без лица нельзя.
    LaunchedEffect(sayNow) {
        if (!sayNow) return@LaunchedEffect
        onSaid()
        if (micAllowed(context)) viewModel.startRecording() else ask.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Список записей изменился — виджет на рабочем столе показывает последнюю,
    // и она у него теперь другая. Толчок отсюда, а не из модели: модель не
    // знает про рабочий стол и знать не должна.
    LaunchedEffect(voices.firstOrNull()?.id, voices.size) {
        VoiceWidgetProvider.refresh(context)
    }

    ScreenScaffold(
        title = "Голос",
        onNavigationClick = onBack,
        navigationIsBack = true,
        floatingActionButton = {
            if (!recording.going) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (micAllowed(context)) {
                            viewModel.startRecording()
                        } else {
                            ask.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    shape = RoundedCornerShape(percent = 50),
                    containerColor = Ink,
                    contentColor = Accent,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "сказать",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (recording.going) {
                RecordingCard(
                    seconds = recording.seconds,
                    level = recording.level,
                    onStop = { viewModel.stopRecording() },
                    onCancel = { viewModel.cancelRecording() },
                )
            }

            if (voices.isEmpty() && !recording.going) {
                EmptyState(
                    title = "Голосовых заметок нет",
                    hint = "Кнопкой внизу наговаривается заметка — то, что быстрее " +
                        "сказать, чем набрать. Слушается она прямо отсюда, не уходя " +
                        "с того, чем вы заняты.",
                )
                return@Column
            }

            val heard = rememberLazyListState()
            LazyColumn(
                state = heard,
                modifier = Modifier.fadingEdges(heard),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(voices.chunked(SHELF_COLUMNS), key = { row -> row.first().id }) { row ->
                    TileRow(row) { note, tileModifier ->
                        VoiceTile(
                            note = note,
                            sounding = aside?.noteId == note.id,
                            playing = aside?.noteId == note.id && aside?.playing == true,
                            onClick = { container.echoAside.play(note) },
                            onLongClick = { opened = note },
                            modifier = tileModifier,
                        )
                    }
                }
                item(key = "tail") { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    opened?.let { note ->
        VoiceCard(
            note = note,
            onDismiss = { opened = null },
            onSave = { title ->
                viewModel.renameVoice(note, title)
                opened = null
            },
            onRemove = {
                // Играющую заметку убирают вместе с её карточкой плеера:
                // полоска, которая едет по стёртому, — обещание, что оно
                // ещё здесь.
                if (aside?.noteId == note.id) container.echoAside.close()
                viewModel.removeVoice(note)
                opened = null
            },
        )
    }
}

private fun micAllowed(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Идёт запись: время, полоска громкости и две кнопки.
 *
 * Полоска дышит от голоса, а не мигает сама по себе: она отвечает на «меня
 * слышно?» — единственный вопрос, который есть у человека, пока он говорит.
 * Красная точка на этот вопрос не отвечает никак.
 *
 * Кнопок две и они разные: «Готово» сохраняет, «Отмена» не оставляет ничего.
 * Одной кнопкой обойтись нельзя — оговорившийся не должен искать, где потом
 * убрать неудачную запись.
 */
@Composable
private fun RecordingCard(
    seconds: Int,
    level: Float,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val loud by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(140),
        label = "level",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .cardEdge(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Danger),
            )
            Text(
                text = "Идёт запись",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Text(
                text = formatClock(seconds * 1000L),
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                color = Ink,
            )
        }

        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AccentSoft),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(loud)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Accent),
            )
        }

        DialogButtons(modifier = Modifier.padding(top = 10.dp)) {
            ActionButton(
                icon = Icons.Outlined.Close,
                label = "Отмена",
                color = MaterialTheme.colorScheme.error,
                onClick = onCancel,
            )
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Готово",
                accent = true,
                onClick = onStop,
            )
        }
    }
}

/**
 * Карточка заметки: как назвать и убрать ли.
 *
 * Имя по времени («27 августа, 14:32») годится, пока заметок три; на тридцатой
 * человек ищет ту, где про масло, а не ту, что была во вторник. Поэтому
 * назвать заметку можно — но не нужно: спрашивать имя сразу после записи
 * значило бы задерживать человека вопросом ровно там, где он торопился.
 *
 * «Убрать» без подтверждения: заметка уходит в ту же корзину на сутки, что
 * запись и дело, и снизу появляется «Вернуть».
 */
@Composable
private fun VoiceCard(
    note: Note,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var title by remember(note.id) { mutableStateOf(note.title) }

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Mic) }) {
        DialogTitle("Голосовая заметка")
        DialogCaption("Как назовём")
        DialogField(
            value = title,
            onValueChange = { title = it },
            hint = "Голос",
            onDone = { onSave(title) },
        )
        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.DeleteOutline,
                label = "Убрать",
                color = MaterialTheme.colorScheme.error,
                onClick = onRemove,
            )
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Готово",
                accent = true,
                onClick = { onSave(title) },
            )
        }
    }
}

/**
 * Время звука словами: «0:07», «1:24», «12:03».
 *
 * Часов не бывает: сорок минут — потолок записи (см.
 * [app.askya.data.audio.VoiceRecorder]), и разряд, который никогда не
 * заполняется, только сдвигает столбец.
 */
internal fun formatClock(milliseconds: Long): String {
    val total = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60, total % 60)
}
