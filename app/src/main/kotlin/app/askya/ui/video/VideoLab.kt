package app.askya.ui.video

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.echo.formatDuration
import app.askya.ui.components.BreathingFlower
import app.askya.ui.components.EmptyState
import app.askya.ui.components.FadingColumn
import app.askya.ui.echo.EchoAsk
import app.askya.ui.echo.EchoDialog
import app.askya.ui.echo.EchoField
import app.askya.ui.echo.EchoIcon
import app.askya.ui.echo.EchoPill
import app.askya.ui.echo.sunsetBackground
import app.askya.ui.echo.systemAsksBeforeDelete
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightDanger
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanel
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import app.askya.video.Clip
import app.askya.video.EditResult
import app.askya.video.VideoDownload
import app.askya.video.VideoEdits
import app.askya.video.VideoLibrary
import app.askya.video.formatSize
import kotlinx.coroutines.launch

/**
 * Лаборатория AskyaV — то, что делают с роликами, а не то, чем их смотрят.
 *
 * ## Та же дверь, что в Echo
 *
 * Устроена она в точности как лаборатория плеера ([app.askya.ui.echo.EchoLabCard]),
 * и это не подражание ради единообразия: задача та же самая. В разделе есть
 * список файлов, и над файлом есть работа — переименовать, отрезать кусок,
 * повернуть, снять звук. Прежде эта работа была разбросана: имя менялось в
 * карточке ролика, ножницы жили в плеере и только для открытого файла, а
 * повернуть три ролика подряд было нельзя вовсе — каждый пришлось бы открыть.
 *
 * Отсюда то же устройство: **нажатие на строку включает, отметка справа берёт
 * в работу**, а внизу полка действий, где гаснут не подходящие к отмеченному.
 * Кто научился этому в Echo, здесь ничему не учится заново.
 *
 * ## Три страницы, и третья — не список
 *
 * «Всё видео» и «Папки» — то же, что и в плеере: на первой странице вещь —
 * файл, на второй — место. Третья, «Скачать», списком не является вовсе, и
 * стоит она здесь по той же причине, по которой ножницы стоят в мастерской:
 * это работа, а не выбор того, что смотреть.
 *
 * Плейлистов среди страниц нет, и это единственное расхождение с Echo. Там их
 * в лабораторию пришлось перенести — в плеере для них не осталось места; здесь
 * они как были вкладкой раздела, так ею и остались, со своим плюсом в шапке и
 * своим переименованием. Заводить им вторую дверь значило бы разложить одно и
 * то же дело по двум местам.
 *
 * ## Что делается с исходником, а что рядом с ним
 *
 * Имя ролика меняется **в разделе, а не на диске**: файл принадлежит не
 * приложению, и Android спрашивает согласие на каждое переименование чужого
 * файла отдельным окном. Правило то же, что и в карточке ролика, — см.
 * `VideoPreferences.rename`.
 *
 * Обрезка, поворот и снятие звука кладут **новый файл** в «Movies/Askya», а
 * исходник остаётся на месте. Это те действия, где ошибку замечают через
 * неделю, и отменять её тогда уже нечем.
 *
 * Единственное, что трогает сам исходник, — «Удалить», и стоит оно на полке
 * последним и красным. Стирает оно через системное окно, как песню в Echo
 * (см. [rememberClipRemover]): отметили после обрезки исходник с куском —
 * убрали оба разом.
 */
@Composable
fun VideoLabCard(
    library: List<Clip>,
    onClose: () -> Unit,
    onChanged: () -> Unit,
    onPlay: (List<Clip>, Clip) -> Unit,
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()

    val pages = VideoLabPage.entries
    val pager = rememberPagerState(pageCount = { pages.size })

    // Отмеченные — ссылками, а не самими роликами: библиотека перечитывается
    // после каждой правки, и объекты после этого другие.
    val picked = remember { mutableStateListOf<String>() }
    val chosen = remember(library, picked.toList()) { library.filter { it.uri in picked } }

    var work by remember { mutableStateOf<VideoLabWork?>(null) }
    var doing by remember { mutableStateOf<String?>(null) }
    var said by remember { mutableStateOf<VideoLabWord?>(null) }

    // Стёртое уходит из отметок и из списка сразу. Слова по итогу нет:
    // согласие уже дано в окне системы, а исчезнувшие строки говорят сами.
    val erase = rememberClipRemover { removed ->
        picked.removeAll(removed)
        onChanged()
    }

    /**
     * Чем кончилось. [made] — легло ли сделанное новым файлом: от этого
     * зависит, куда отправлять человека его искать.
     */
    val ended: (EditResult, Boolean) -> Unit = { result, made ->
        doing = null
        work = null
        picked.clear()
        said = when (result) {
            is EditResult.Done -> if (made) {
                VideoLabWord(
                    "Готово",
                    "Новый ролик лежит в папке «${container.videoStore.folderName}». " +
                        "Исходник остался на месте.",
                )
            } else {
                VideoLabWord("Готово", "Имя записано — оно уже во всех списках раздела.")
            }

            is EditResult.Failed -> VideoLabWord("Не вышло", result.reason)
        }
        onChanged()
    }

    Box(modifier = Modifier.fillMaxSize().sunsetBackground()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Лаборатория",
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        letterSpacing = (-0.3).sp,
                        color = NightInk,
                    )
                    Text(
                        text = pages[pager.currentPage].about,
                        style = MaterialTheme.typography.bodySmall,
                        color = NightMuted,
                    )
                }
                LabDots(
                    count = pages.size,
                    current = pager.currentPage,
                    onSelect = { at -> scope.launch { pager.animateScrollToPage(at) } },
                )
                EchoIcon(icon = Icons.Outlined.Close, label = "Закрыть", onClick = onClose)
            }

            HorizontalPager(
                state = pager,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.weight(1f),
            ) { at ->
                when (pages[at]) {
                    VideoLabPage.ALL -> ClipsLabPage(
                        clips = library,
                        picked = picked,
                        onPlay = onPlay,
                    )

                    VideoLabPage.FOLDERS -> FoldersLabPage(
                        clips = library,
                        picked = picked,
                        onPlay = onPlay,
                    )

                    VideoLabPage.FETCH -> FetchLabPage()
                }
            }

            // На странице закачек полки нет: там работают не с отмеченным, а с
            // адресом, и гаснущие кнопки над чужой работой только мешали бы.
            if (pages[pager.currentPage] != VideoLabPage.FETCH) {
                VideoLabActions(
                    chosen = chosen,
                    onRename = { work = VideoLabWork.Rename(chosen.first()) },
                    onTrim = { work = VideoLabWork.Trim(chosen.first()) },
                    onTurn = { work = VideoLabWork.Turn(chosen.toList()) },
                    onMute = {
                        doing = "Снимаю звук"
                        scope.launch {
                            var last: EditResult = EditResult.Failed("Резать оказалось нечего")
                            for (clip in chosen) {
                                last = VideoEdits.cut(
                                    context = context,
                                    store = container.videoStore,
                                    source = clip.uri,
                                    title = "${clip.title}-без-звука",
                                    keepAudio = false,
                                )
                            }
                            ended(last, true)
                        }
                    },
                    onSound = {
                        val clip = chosen.first()
                        doing = "Вынимаю звук"
                        scope.launch {
                            ended(
                                VideoEdits.extractAudio(
                                    context = context,
                                    store = container.videoStore,
                                    source = clip.uri,
                                    title = clip.title,
                                ),
                                true,
                            )
                        }
                    },
                    // С Android 11 про удаление спрашивает система — одним
                    // окном на всю пачку; наше было бы вторым вопросом.
                    onDelete = {
                        if (systemAsksBeforeDelete()) {
                            erase(chosen.toList())
                        } else {
                            work = VideoLabWork.Delete(chosen.toList())
                        }
                    },
                )
            }
        }
    }

    when (val open = work) {
        null -> Unit

        is VideoLabWork.Rename -> VideoLabNameDialog(
            title = "Новое имя",
            hint = "Как назвать ролик",
            initial = open.clip.title,
            about = "Имя живёт в разделе: в проводнике и в галерее файл остаётся " +
                "собой. Настоящее имя — «${open.clip.fileName}».",
            onDismiss = { work = null },
            onDone = { name ->
                container.videoPreferences.rename(open.clip.uri, name)
                ended(EditResult.Done(open.clip.uri, name), false)
            },
        )

        is VideoLabWork.Trim -> VideoTrimCard(
            title = open.clip.title,
            source = open.clip.uri,
            durationMs = open.clip.durationMs,
            onDismiss = { work = null },
            onDone = { fromMs, toMs, name ->
                doing = "Режу"
                scope.launch {
                    ended(
                        VideoEdits.cut(
                            context = context,
                            store = container.videoStore,
                            source = open.clip.uri,
                            title = name,
                            fromMs = fromMs,
                            toMs = toMs,
                        ),
                        true,
                    )
                }
            },
        )

        is VideoLabWork.Turn -> VideoLabTurnDialog(
            count = open.clips.size,
            onDismiss = { work = null },
            onDone = { degrees ->
                doing = "Поворачиваю"
                scope.launch {
                    var last: EditResult = EditResult.Failed("Поворачивать оказалось нечего")
                    for (clip in open.clips) {
                        last = VideoEdits.cut(
                            context = context,
                            store = container.videoStore,
                            source = clip.uri,
                            title = "${clip.title}-$degrees",
                            rotateBy = degrees,
                        )
                    }
                    ended(last, true)
                }
            },
        )

        is VideoLabWork.Delete -> EchoAsk(
            title = "Удалить с телефона?",
            text = if (open.clips.size == 1) {
                "«${open.clips.first().title}» исчезнет из памяти телефона — не только " +
                    "из AskyaV. Вернуть не получится."
            } else {
                "Все ${open.clips.size} отмеченных исчезнут из памяти телефона — не " +
                    "только из AskyaV. Вернуть не получится."
            },
            confirm = "Удалить",
            onConfirm = {
                work = null
                erase(open.clips)
            },
            onDismiss = { work = null },
        )
    }

    doing?.let { caption -> VideoLabWorking(caption) }

    said?.let { word ->
        EchoDialog(title = word.title, onDismiss = { said = null }) {
            Text(
                text = word.text,
                style = MaterialTheme.typography.bodyMedium,
                color = NightMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            EchoPill(
                label = "Ясно",
                chosen = true,
                onClick = { said = null },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}

/** Что сказать по итогу. */
private data class VideoLabWord(val title: String, val text: String)

/** Страницы лаборатории: файл, место и сеть. */
private enum class VideoLabPage(val title: String, val about: String) {
    ALL("Всё видео", "Нажать — смотреть, отметить — в работу"),
    FOLDERS("Папки", "Куда что разложено"),
    FETCH("Скачать", "Забрать видео по ссылке к себе"),
}

/** Открытая работа — одна: два окна разом бессмысленны. */
private sealed interface VideoLabWork {
    data class Rename(val clip: Clip) : VideoLabWork
    data class Trim(val clip: Clip) : VideoLabWork
    data class Turn(val clips: List<Clip>) : VideoLabWork
    data class Delete(val clips: List<Clip>) : VideoLabWork
}

/** Точки страниц — те же, что в Ledger и в лаборатории Echo. */
@Composable
private fun LabDots(count: Int, current: Int, onSelect: (Int) -> Unit) {
    Row {
        repeat(count) { at ->
            val here = at == current
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(at) },
            ) {
                Box(
                    modifier = Modifier
                        .size(if (here) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (here) Sunset else NightMuted.copy(alpha = 0.4f)),
                )
            }
        }
    }
}

/** Всё видео телефона: нажатие смотрит, отметка берёт в работу. */
@Composable
private fun ClipsLabPage(
    clips: List<Clip>,
    picked: SnapshotStateList<String>,
    onPlay: (List<Clip>, Clip) -> Unit,
) {
    if (clips.isEmpty()) {
        EmptyState(
            title = "Видео не нашлось",
            hint = "Работать пока не с чем: система не видит на телефоне ни одного ролика.",
        )
        return
    }

    FadingColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        items(clips, key = { it.id }) { clip ->
            PickClip(
                clip = clip,
                marked = clip.uri in picked,
                onPlay = { onPlay(clips, clip) },
                onMark = {
                    if (clip.uri in picked) picked.remove(clip.uri) else picked.add(clip.uri)
                },
            )
        }
    }
}

/**
 * Папки как они лежат, и в каждой — свои ролики.
 *
 * Раскрытая папка не уводит на другую страницу: работают **из** папки, и
 * уходить из неё ради этого значило бы потерять из виду то, что отметил.
 */
@Composable
private fun FoldersLabPage(
    clips: List<Clip>,
    picked: SnapshotStateList<String>,
    onPlay: (List<Clip>, Clip) -> Unit,
) {
    val folders = remember(clips) { VideoLibrary.folders(clips) }
    var opened by remember { mutableStateOf<String?>(null) }

    if (folders.isEmpty()) {
        EmptyState(
            title = "Папок с видео нет",
            hint = "Как только на телефоне появятся ролики, здесь появятся их папки.",
        )
        return
    }

    FadingColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        folders.forEach { folder ->
            val here = opened == folder.name

            item(key = "folder:${folder.name}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { opened = if (here) null else folder.name }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (here) {
                            Icons.Outlined.KeyboardArrowUp
                        } else {
                            Icons.Outlined.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = Sunset,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = folder.name,
                        fontFamily = FontFamily.Serif,
                        fontSize = 17.sp,
                        color = NightInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                    Text(
                        text = "${folder.clips.size} ${clipsWord(folder.clips.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NightMuted,
                    )
                }
            }

            if (here) {
                items(folder.clips, key = { "in:${it.id}" }) { clip ->
                    PickClip(
                        clip = clip,
                        marked = clip.uri in picked,
                        // Очередь — папка, а не вся библиотека: включив ролик
                        // из папки, смотрят эту папку.
                        onPlay = { onPlay(folder.clips, clip) },
                        onMark = {
                            if (clip.uri in picked) picked.remove(clip.uri) else picked.add(clip.uri)
                        },
                        inset = true,
                    )
                }
            }
        }
    }
}

/**
 * Скачать по ссылке.
 *
 * Поле заполняется из буфера обмена, если там лежит ссылка: её приносят
 * скопированной — из браузера, из письма, из чужого приложения, — и набирать
 * сорок знаков адреса пальцем не должен никто.
 *
 * Под полем — последние пять адресов, откуда уже качали: домашняя камера и
 * свой сервер отдают одно и то же каждый вечер. Ниже — сами закачки: сколько
 * скачано, чем кончилось, и слово, чтобы бросить или убрать строчку.
 *
 * Сказано прямо и на самом видном месте, чего здесь не бывает: страницы,
 * которые собирают адрес ролика скриптом, не качаются. Узнать это, простояв
 * над полоской три минуты, обиднее, чем прочесть строчкой.
 */
@Composable
private fun FetchLabPage() {
    val container = appContainer()
    val downloads = container.videoDownloads
    val preferences = container.videoPreferences
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var link by remember {
        val copied = clipboard.getText()?.text?.trim().orEmpty()
        mutableStateOf(if (copied.startsWith("http")) copied else "")
    }
    val ready = link.trim().startsWith("http")

    val going by downloads.state.collectAsStateWithLifecycle()
    val recent by preferences.recentLinks.collectAsStateWithLifecycle(initialValue = emptyList())

    val begin: (String) -> Unit = { address ->
        downloads.start(address)
        scope.launch { preferences.rememberLink(address) }
        link = ""
    }

    FadingColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item(key = "field") {
            EchoField(
                value = link,
                onValueChange = { link = it },
                hint = "https://…",
                onDone = { if (ready) begin(link.trim()) },
            )
            EchoPill(
                label = "Скачать",
                chosen = ready,
                onClick = { if (ready) begin(link.trim()) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Text(
                text = "Берётся прямая ссылка на файл, поток m3u8 и страница, на которой " +
                    "видео лежит открыто. Сайты, собирающие адрес ролика своим скриптом, " +
                    "и защищённое видео не качаются ничем, кроме их собственного плеера.",
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = "Скачанное ложится в «${container.videoStore.folderName}» и дальше " +
                    "живёт обычным роликом раздела.",
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (going.isNotEmpty()) {
            item(key = "going") {
                Text(
                    text = "Закачки",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    color = NightInk,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
            }
            items(going, key = { it.id }) { one ->
                FetchRow(
                    download = one,
                    onStop = { downloads.stop(one.id) },
                    onForget = { downloads.forget(one.id) },
                )
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recent") {
                Text(
                    text = "Откуда качали",
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    color = NightInk,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
            }
            items(recent, key = { "was:$it" }) { saved ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = saved,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sunset,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { begin(saved) }
                            .padding(vertical = 8.dp),
                    )
                    Text(
                        text = "убрать",
                        style = MaterialTheme.typography.labelMedium,
                        color = NightMuted,
                        modifier = Modifier
                            .clickable { scope.launch { preferences.forgetLink(saved) } }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Одна закачка строчкой: что качается, сколько уже и чем кончилось. */
@Composable
private fun FetchRow(download: VideoDownload, onStop: () -> Unit, onForget: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NightPanelSoft)
            .border(1.dp, NightBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = download.link,
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = download.note,
                style = MaterialTheme.typography.titleSmall,
                color = if (download.ended && !download.ok) NightMuted else NightInk,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (download.ended) "убрать" else "бросить",
                style = MaterialTheme.typography.labelMedium,
                color = NightMuted,
                modifier = Modifier
                    .clickable { if (download.ended) onForget() else onStop() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }

        if (!download.ended) {
            val part = download.part
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NightBorder),
            ) {
                // Полоска рисуется только тогда, когда известно, из чего она:
                // сервер, не сказавший длины, оставляет её пустой — врущая
                // полоска хуже, чем никакой.
                if (part >= 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(part)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Sunset),
                    )
                }
            }
            Text(
                text = when {
                    download.total <= 0 && download.done <= 0 -> ""
                    download.counted -> "кусок ${download.done} из ${download.total}"
                    download.total > 0 ->
                        "${formatSize(download.done)} из ${formatSize(download.total)}"

                    else -> formatSize(download.done)
                },
                style = MaterialTheme.typography.labelMedium,
                color = NightMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (download.word.isNotBlank()) {
            Text(
                text = download.word,
                style = MaterialTheme.typography.bodySmall,
                color = if (download.ok) Sunset else NightMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Строка ролика: кадр, подписи и квадратик отметки справа.
 *
 * Половины строки делают разное — так же, как в лаборатории Echo. Нажатие на
 * строку смотрит, квадратик справа берёт в работу и стоит отдельной кнопкой с
 * полем шире самого квадратика: промахнуться галочкой по «смотреть» — значит
 * открыть фильм вместо того, чтобы его отметить.
 */
@Composable
private fun PickClip(
    clip: Clip,
    marked: Boolean,
    onPlay: () -> Unit,
    onMark: () -> Unit,
    inset: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (inset) 22.dp else 0.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onPlay)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClipFrame(clip = clip, modifier = Modifier.size(width = 74.dp, height = 42.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = clip.title,
                style = MaterialTheme.typography.titleSmall,
                color = NightInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    formatDuration(clip.durationMs).takeIf { clip.durationMs > 0 },
                    clip.resolution.takeIf { it.isNotEmpty() },
                    formatSize(clip.sizeBytes).takeIf { it.isNotEmpty() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onMark),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (marked) Sunset else NightPanelSoft)
                    .border(1.dp, if (marked) Sunset else NightBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (marked) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Отмечено",
                        tint = NightPanel,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Полка действий внизу.
 *
 * Гаснут не спрятанные, а неподходящие: пропадающие кнопки заставляют
 * догадываться, что нужно отметить, чтобы они вернулись, — а надпись над
 * гаснущей говорит об этом прямо.
 */
@Composable
private fun VideoLabActions(
    chosen: List<Clip>,
    onRename: () -> Unit,
    onTrim: () -> Unit,
    onTurn: () -> Unit,
    onMute: () -> Unit,
    onSound: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NightPanel)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = when (chosen.size) {
                0 -> "Отметь ролик — и здесь загорятся действия"
                1 -> "Отмечен один"
                else -> "Отмечено: ${chosen.size}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabAction(
                icon = Icons.Outlined.DriveFileRenameOutline,
                label = "Переименовать",
                ready = chosen.size == 1,
                onClick = onRename,
            )
            LabAction(
                icon = Icons.Outlined.ContentCut,
                label = "Обрезать",
                ready = chosen.size == 1,
                onClick = onTrim,
            )
            LabAction(
                icon = Icons.AutoMirrored.Outlined.RotateRight,
                label = "Повернуть",
                ready = chosen.isNotEmpty(),
                onClick = onTurn,
            )
            LabAction(
                icon = Icons.AutoMirrored.Outlined.VolumeOff,
                label = "Без звука",
                ready = chosen.isNotEmpty(),
                onClick = onMute,
            )
            LabAction(
                icon = Icons.Outlined.MusicNote,
                label = "Вынуть звук",
                ready = chosen.size == 1,
                onClick = onSound,
            )
            // Последней и красной: всё остальное на полке кладёт новый файл
            // рядом с исходником, а это — единственное, что стирает сам исходник.
            LabAction(
                icon = Icons.Outlined.DeleteOutline,
                label = "Удалить",
                ready = chosen.isNotEmpty(),
                onClick = onDelete,
                danger = true,
            )
        }
    }
}

@Composable
private fun LabAction(
    icon: ImageVector,
    label: String,
    ready: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val accent = if (danger) NightDanger else Sunset
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (ready) accent else NightBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = ready, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (ready) accent else NightMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (ready) NightInk else NightMuted,
        )
    }
}

/** Окно с одним полем — имя ролика. */
@Composable
private fun VideoLabNameDialog(
    title: String,
    hint: String,
    initial: String,
    about: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val ready = name.isNotBlank()

    EchoDialog(title = title, onDismiss = onDismiss) {
        EchoField(
            value = name,
            onValueChange = { name = it },
            hint = hint,
            modifier = Modifier.padding(top = 14.dp),
            onDone = { if (ready) onDone(name) },
        )
        Text(
            text = about,
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        EchoPill(
            label = "Сохранить",
            chosen = ready,
            onClick = { if (ready) onDone(name) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

/**
 * Куда повернуть.
 *
 * Три ответа и никакого «на сколько градусов» полем: поворачивают всегда на
 * четверть, половину или три четверти круга, а число вводом означало бы
 * пережатие кадров — того, чего этот редактор не делает.
 */
@Composable
private fun VideoLabTurnDialog(count: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    EchoDialog(title = "Повернуть", onDismiss = onDismiss) {
        Text(
            text = if (count == 1) {
                "Поворот — это пометка в заголовке файла, а не перерисованные кадры: " +
                    "он мгновенный и без потери качества."
            } else {
                "Все $count отмеченных повернутся одинаково. Поворот — пометка в " +
                    "заголовке, а не перерисованные кадры."
            },
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(90, 180, 270).forEach { degrees ->
                EchoPill(
                    label = "$degrees°",
                    chosen = false,
                    onClick = { onDone(degrees) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Пока идёт работа.
 *
 * Дышащий цветок, а не полоска: пересборка контейнера идёт со скоростью,
 * которую задаёт сам телефон, и полоска, ползущая наугад, врала бы. Экран под
 * ней закрыт целиком: трогать список, пока правится файл из него же, нельзя.
 */
@Composable
private fun VideoLabWorking(caption: String) {
    BackHandler {}

    Box(
        modifier = Modifier.fillMaxSize().background(NightPanel.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BreathingFlower(size = 64.dp)
            Text(
                text = caption,
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                color = NightInk,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Кадры переписываются как есть — это секунды, а не минуты",
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
