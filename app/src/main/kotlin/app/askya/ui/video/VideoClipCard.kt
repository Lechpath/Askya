package app.askya.ui.video

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.androidContainer
import app.askya.echo.formatDuration
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.echo.EchoCard
import app.askya.ui.echo.EchoField
import app.askya.ui.echo.EchoPill
import app.askya.ui.echo.systemAsksBeforeDelete
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightDanger
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import app.askya.video.Clip
import app.askya.video.formatSize
import kotlinx.coroutines.launch

/** Что показывает карточка ролика: действия, имя, выбор плейлиста, сведения или вопрос об удалении. */
private enum class ClipStep { ACTIONS, RENAME, PLAYLIST, NEW_PLAYLIST, DETAILS, DELETE }

/**
 * Ролик, раскрытый карточкой: всё, что с файлом можно сделать, не открывая его.
 *
 * Устроена как карточка песни в Echo ([app.askya.ui.echo.EchoTrackCard]) и по
 * той же причине: до сих пор строку списка можно было только нажать — и файл
 * начинал играть. Всё остальное, чего ждут от библиотеки, — «назови его
 * по-человечески», «положи к тем, что смотрю подряд», — делать было негде.
 *
 * Карточка, а не выпадающее меню: в Askya раскрытое — это карточка, и серый
 * список Material выглядел бы здесь чужим. Сверху кадр и подписи: видно, о
 * каком файле речь, а не «действия для того, на что ты только что попал
 * пальцем».
 *
 * [queue] — список, в котором ролик нашли: «смотреть» ставит его вместе с
 * соседями, чтобы в плейлисте следом пошёл следующий.
 *
 * [onRemoveFromPlaylist] есть только у строк, открытых из плейлиста: убирать
 * из плейлиста ролик, который в нём не лежит, нечем и незачем.
 *
 * [onErased] случается, когда файл стёрт с телефона: библиотеке пора
 * перечитать список — ролика в нём больше нет.
 */
@Composable
fun VideoClipCard(
    clip: Clip,
    queue: List<Clip>,
    onDismiss: () -> Unit,
    onPlay: (List<Clip>, Clip) -> Unit,
    onOpenFolder: ((String) -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onErased: () -> Unit = {},
) {
    val container = androidContainer()
    val preferences = container.videoPreferences
    val repository = container.videoRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val erase = rememberClipRemover {
        onErased()
        onDismiss()
    }

    var step by remember { mutableStateOf(ClipStep.ACTIONS) }
    var name by remember(clip.uri) { mutableStateOf(clip.title) }
    var fresh by remember { mutableStateOf("") }

    val watchedMs by remember(preferences, clip.uri) { preferences.spot(clip.uri) }
        .collectAsStateWithLifecycle(initialValue = 0L)
    val playlists by remember(repository) { repository.playlists() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Имя ложится в двух местах сразу: в хранилище раздела — там оно живёт для
    // библиотеки — и в строках плейлистов, где подпись хранится рядом со
    // ссылкой. Иначе переименованный ролик звался бы по-разному в зависимости
    // от того, откуда на него смотрят.
    fun rename(title: String) {
        val clean = title.trim()
        preferences.rename(clip.uri, clean)
        scope.launch { repository.renamed(clip.uri, clean.ifBlank { clip.fileName }) }
        onDismiss()
    }

    fun startNew(title: String) {
        scope.launch { repository.add(repository.addPlaylist(title), clip) }
        onDismiss()
    }

    EchoCard(
        title = clip.title,
        subtitle = listOfNotNull(
            formatDuration(clip.durationMs).takeIf { clip.durationMs > 0 },
            clip.resolution.takeIf { it.isNotEmpty() },
        ).joinToString(" · ").ifBlank { null },
        onDismiss = { if (step == ClipStep.ACTIONS) onDismiss() else step = ClipStep.ACTIONS },
        back = step != ClipStep.ACTIONS,
        width = 0.9f,
        // По написанному, а не долей экрана: у ролика из папки действий шесть,
        // у ролика из плейлиста семь, а у окна имени — одно поле, и подбирать
        // каждому свою долю значило бы держать четыре числа, которые врут при
        // первой же новой строчке.
        height = null,
    ) {
        when (step) {
            ClipStep.ACTIONS -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingVerticalScroll()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClipFrame(clip = clip, modifier = Modifier.size(width = 116.dp, height = 66.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            text = clip.folder.ifBlank { "Открытый файл" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = NightInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatSize(clip.sizeBytes).ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = NightMuted,
                        )
                    }
                }

                ClipAction(
                    icon = Icons.Outlined.PlayArrow,
                    title = if (watchedMs > 0) "Продолжить" else "Смотреть",
                    about = if (watchedMs > 0) {
                        "С ${formatDuration(watchedMs)} — там и остановились"
                    } else {
                        "Сейчас же, с этого места списка"
                    },
                    tint = Sunset,
                ) {
                    onPlay(queue, clip)
                    onDismiss()
                }

                // «Сначала» есть только у начатых: у нетронутого файла эта
                // строка означала бы то же, что строка над ней.
                if (watchedMs > 0) {
                    ClipAction(
                        icon = Icons.Outlined.Replay,
                        title = "Смотреть сначала",
                        about = "Забыть место, на котором закрыли",
                    ) {
                        preferences.forget(clip.uri)
                        onPlay(queue, clip)
                        onDismiss()
                    }
                }

                ClipAction(
                    icon = Icons.Outlined.DriveFileRenameOutline,
                    title = "Переименовать",
                    about = "Имя в разделе; файл на телефоне останется собой",
                ) {
                    name = clip.title
                    step = ClipStep.RENAME
                }

                ClipAction(
                    icon = Icons.Outlined.PlaylistAdd,
                    title = "В плейлист",
                    about = "К тем, что смотрятся подряд",
                ) { step = ClipStep.PLAYLIST }

                if (onRemoveFromPlaylist != null) {
                    ClipAction(
                        icon = Icons.Outlined.VideoLibrary,
                        title = "Убрать из плейлиста",
                        about = "Файл останется на телефоне",
                    ) {
                        onRemoveFromPlaylist()
                        onDismiss()
                    }
                }

                if (onOpenFolder != null && clip.folder.isNotBlank()) {
                    ClipAction(
                        icon = Icons.Outlined.FolderOpen,
                        title = "Показать папку",
                        about = clip.folder,
                    ) { onOpenFolder(clip.folder) }
                }

                ClipAction(
                    icon = Icons.Outlined.Share,
                    title = "Поделиться",
                    about = "Отправить файл, как он лежит на телефоне",
                ) {
                    share(context, clip)
                    onDismiss()
                }

                ClipAction(
                    icon = Icons.Outlined.Info,
                    title = "Сведения",
                    about = "Имя файла, длительность, кадр, вес",
                ) { step = ClipStep.DETAILS }

                // Удаление стоит последним и отмечено красным — как у песни в
                // Echo: промах пальцем по дороге к «сведениям» не должен
                // попадать в необратимое.
                ClipAction(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Удалить с телефона",
                    about = "Файл будет стёрт, а не убран из AskyaV",
                    tint = NightDanger,
                ) {
                    // С Android 11 спрашивает система, и наше окно было бы
                    // вторым вопросом об одном и том же.
                    if (systemAsksBeforeDelete()) erase(listOf(clip)) else step = ClipStep.DELETE
                }
            }

            ClipStep.DELETE -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Удалить файл с телефона?",
                    style = MaterialTheme.typography.titleMedium,
                    color = NightInk,
                )
                Text(
                    text = "«${clip.title}» исчезнет из памяти телефона — не только из " +
                        "AskyaV. Вернуть не получится.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NightMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EchoPill(
                        label = "Оставить",
                        chosen = false,
                        onClick = { step = ClipStep.ACTIONS },
                        modifier = Modifier.weight(1f),
                    )
                    EchoPill(
                        label = "Удалить",
                        chosen = false,
                        danger = true,
                        onClick = { erase(listOf(clip)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Имя. Поле открыто на том, как ролик зовётся сейчас, а под ним
            // написано, как называется сам файл: переименование с файлом
            // ничего не делает, и человек должен видеть это сразу, а не
            // узнавать потом из проводника.
            ClipStep.RENAME -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EchoField(
                    value = name,
                    onValueChange = { name = it },
                    hint = clip.fileName,
                    onDone = { rename(name) },
                )
                Text(
                    text = "Файл на телефоне называется «${clip.fileName}» и останется таким: " +
                        "своё имя живёт в AskyaV и видно только здесь.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NightMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        EchoPill(
                            label = "Назвать",
                            chosen = true,
                            onClick = { rename(name) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // Возврат к настоящему имени — не «отмена», а отдельное
                    // действие: стереть своё имя иначе было бы нечем.
                    if (clip.title != clip.fileName) {
                        Box(modifier = Modifier.weight(1f)) {
                            EchoPill(
                                label = "Вернуть исходное",
                                chosen = false,
                                onClick = { rename("") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            ClipStep.PLAYLIST -> Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    ClipAction(
                        icon = Icons.Outlined.Add,
                        title = "Новый плейлист",
                        about = "Завести и сразу положить в него этот ролик",
                        tint = Sunset,
                    ) {
                        fresh = ""
                        step = ClipStep.NEW_PLAYLIST
                    }
                }

                if (playlists.isEmpty()) {
                    Text(
                        text = "Плейлистов пока нет: первый заводится строчкой выше.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NightMuted,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                } else {
                    FadingColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            ClipAction(
                                icon = Icons.Outlined.VideoLibrary,
                                title = playlist.title.ifBlank { "Без названия" },
                                about = "Положить в конец",
                            ) {
                                scope.launch { repository.add(playlist.id, clip) }
                                onDismiss()
                            }
                        }
                    }
                }
            }

            ClipStep.NEW_PLAYLIST -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EchoField(
                    value = fresh,
                    onValueChange = { fresh = it },
                    hint = "Как назвать",
                    onDone = { startNew(fresh) },
                )
                EchoPill(
                    label = "Завести и положить",
                    chosen = true,
                    onClick = { startNew(fresh) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ClipStep.DETAILS -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingVerticalScroll()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Detail("Название в AskyaV", clip.title)
                Detail("Имя файла", clip.fileName)
                Detail("Папка", clip.folder.ifBlank { "—" })
                Detail("Длительность", formatDuration(clip.durationMs))
                Detail("Кадр", clip.resolution.ifBlank { "—" })
                Detail("Вес", formatSize(clip.sizeBytes).ifBlank { "—" })
                Detail("Досмотрено до", if (watchedMs > 0) formatDuration(watchedMs) else "—")
                Detail("Файл", clip.uri)
            }
        }
    }
}

/** Строка действия: знак, что случится, и строчка пояснения под ним. */
@Composable
private fun ClipAction(
    icon: ImageVector,
    title: String,
    about: String,
    modifier: Modifier = Modifier,
    tint: Color = NightMuted,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NightPanelSoft)
            .border(1.dp, NightBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = NightInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = about,
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Строка сведений: сверху чем является, снизу что записано. */
@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NightMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = NightInk,
        )
    }
}

/**
 * Отдать ролик наружу.
 *
 * Отправляется ссылка на файл, а не копия: файл и так лежит у системы, и
 * второй его экземпляр в папке приложения не нужен никому. Право на чтение
 * выдаётся вместе с намерением и только тому, кого выберут. Заголовком идёт
 * имя, которым ролик зовут здесь: это единственное место, где своё имя выходит
 * за пределы раздела, — и выходит оно подписью, а не подменой файла.
 */
private fun share(context: android.content.Context, clip: Clip) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/*"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(clip.uri))
        putExtra(Intent.EXTRA_TITLE, clip.title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, clip.title)) }
}
