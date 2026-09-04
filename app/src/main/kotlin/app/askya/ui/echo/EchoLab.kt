package app.askya.ui.echo

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.data.entity.EchoPlaylistTrack
import app.askya.data.repository.asTrack
import app.askya.echo.EchoChordScore
import app.askya.echo.EchoChords
import app.askya.echo.EchoLibrary
import app.askya.echo.EchoStudio
import app.askya.echo.Track
import app.askya.echo.formatDuration
import app.askya.ui.components.BreathingFlower
import app.askya.ui.components.EmptyState
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanel
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import kotlinx.coroutines.launch

/**
 * Лаборатория Echo — то, что делают с файлами, а не со списками.
 *
 * ## Одна дверь вместо трёх вкладок
 *
 * Под шапкой плеера стояли три кнопки — «Вся музыка», «Плейлисты», «Папки», —
 * и каждая открывала свой список поверх экрана. Три двери в один и тот же дом:
 * за всеми тремя лежит одно и то же — музыка телефона, разложенная по-разному.
 * Теперь дверь одна, называется Lab, а те три стали её страницами, листаемыми
 * вбок.
 *
 * Выиграно этим не место в шапке (его хватало), а честность устройства: раньше
 * из списка можно было только включить песню, а всё, что с ней делают ещё,
 * жило в отдельной спрятанной комнате — и человек, стоя в «Папках», не имел
 * способа оттуда что-нибудь перенести. Теперь список и работа с ним — одно
 * место: **нажатие на строку включает песню, отметка справа берёт её в
 * работу**. Ровно как в проводнике, где файл открывают одним касанием, а
 * галочкой набирают в стопку.
 *
 * ## Почему страницами, а не одной кучей
 *
 * Три страницы, листаемые вбок, — как в Ledger: там счета, месяц и статистика,
 * здесь музыка, списки и папки. Разница между ними не в наборе кнопок, а в
 * том, **что считается вещью**: на первой странице вещь — файл, на второй —
 * список, на третьей — место. Свалив их вместе, пришлось бы объяснять, почему
 * «переименовать» у плейлиста мгновенно, а у файла спрашивает разрешение
 * системы.
 *
 * ## Не только над файлом, но и над записанным
 *
 * Пятое действие полки — «Аккорды» — файла не касается: оно слушает песню и
 * говорит, чем её сыграть ([EchoChords]). Место ему здесь по той же причине,
 * по которой здесь ножницы: это работа над одной отмеченной записью, а не
 * выбор того, что играть. Списку музыки такое действие пришлось бы приделывать
 * шестым пунктом в карточку песни, где и без него тесно.
 *
 * ## Чего лаборатория не делает
 *
 * Не трогает исходники. Обрезка и склейка кладут **новый** файл в `Music/Askya`
 * (см. [EchoStudio]), а прежний остаётся на месте: это те два действия, где
 * ошибку замечают через неделю, и отменять её тогда уже нечем. Переименование и
 * перенос исходник меняют — но они обратимы тем же движением.
 */
@Composable
fun EchoLabCard(
    library: List<Track>?,
    onClose: () -> Unit,
    onChanged: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit,
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Что играет сейчас — этим подсвечена строка списка. Плеер спрашивается
    // здесь, а не передаётся снаружи: лаборатория и так живёт в его разделе, а
    // лишний параметр пришлось бы тащить через три страницы.
    val current by appContainer().echoPlayer.state.collectAsStateWithLifecycle()

    val pages = LabPage.entries
    val pager = rememberPagerState(pageCount = { pages.size })

    // Отмеченные файлы — ссылками, а не дорожками: библиотека перечитывается
    // после каждой правки, и сами объекты дорожек после этого другие.
    val picked = remember { mutableStateListOf<String>() }
    val chosen = remember(library, picked.toList()) {
        library.orEmpty().filter { it.uri in picked }
    }

    var work by remember { mutableStateOf<LabWork?>(null) }

    // Разобранная песня. Отдельно от [work], потому что это не работа, а её
    // итог: окна выбора у аккордов нет — есть готовый песенник, который либо
    // получился, либо нет.
    var picking by remember { mutableStateOf<LabChords?>(null) }

    // Что сейчас делается и чем это кончилось. Работа идёт секундами, и
    // молчащий экран на это время читался бы как зависание.
    var doing by remember { mutableStateOf<String?>(null) }
    var said by remember { mutableStateOf<LabWord?>(null) }

    /**
     * Чем кончилось: сказать об этом и убрать за собой.
     *
     * Слова о неудаче разные у правки исходника и у нарезки, и это не
     * придирка: в первом случае отказала система, и повторить попытку имеет
     * смысл, во втором не прочитался файл, и повторять нечего. [made] — легло
     * ли сделанное новым файлом; от этого зависит, куда человека отправлять
     * его искать.
     */
    val ended: (Boolean, Boolean) -> Unit = { ok, made ->
        doing = null
        work = null
        picked.clear()
        said = when {
            ok && made -> LabWord(
                "Готово",
                "Новая запись лежит в папке «${EchoStudio.HOME}». Исходники остались на месте.",
            )

            ok -> LabWord("Готово", "Библиотека перечитана — правка уже в списках.")

            made -> LabWord(
                "Не вышло",
                "Записи не прочитались до конца. Так бывает с битыми файлами и с теми, " +
                    "что телефон не умеет распаковывать.",
            )

            else -> LabWord(
                "Не вышло",
                "Система не дала переписать файл. Она спрашивает об этом сама — " +
                    "и, если окно закрыли, ничего не изменилось.",
            )
        }
        onChanged()
    }

    val write = rememberFileWriter { ok -> ended(ok, false) }

    Box(modifier = Modifier.fillMaxSize().sunsetBackground()) {
        // Те же отступы под часы и кнопки системы, что у разделов: лаборатория
        // занимает весь лист и своей шапки от системной ничем не отделена.
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
                // Страницы начинаются сверху, а не по середине листа: короткий
                // список — одиннадцать папок — Pager по умолчанию вешает в
                // воздухе посреди экрана, и страница читается как обрывок.
                verticalAlignment = Alignment.Top,
                modifier = Modifier.weight(1f),
            ) { at ->
                when (pages[at]) {
                    LabPage.MUSIC -> MusicLabPage(
                        library = library,
                        picked = picked,
                        playing = current.track?.uri,
                        onPlay = onPlay,
                    )

                    LabPage.PLAYLISTS -> PlaylistsLabPage(
                        onOpen = { id, title -> work = LabWork.Playlist(id, title) },
                    )

                    LabPage.FOLDERS -> FoldersLabPage(
                        library = library,
                        picked = picked,
                        playing = current.track?.uri,
                        onPlay = onPlay,
                    )
                }
            }

            // Полка действий: та же на всех страницах, потому что действие
            // относится к отмеченному, а не к странице. Гаснут те, для которых
            // отмечено не то: «склеить» одну запись не с чем.
            if (pages[pager.currentPage] != LabPage.PLAYLISTS) {
                LabActions(
                    chosen = chosen,
                    onRename = { work = LabWork.Rename(chosen.first()) },
                    onMove = { work = LabWork.Move(chosen.toList()) },
                    onTrim = { work = LabWork.Trim(chosen.first()) },
                    onJoin = { work = LabWork.Join(chosen.toList()) },
                    onChords = {
                        val track = chosen.first()
                        // Доля прочитанного стоит прямо в подписи: разбор идёт
                        // десятки секунд, и «Слушаю аккорды» без числа через
                        // полминуты читается как зависание.
                        var shown = -1
                        doing = "Слушаю аккорды"
                        scope.launch {
                            val heard = EchoChords.read(context, track) { part ->
                                val percent = (part * 100).toInt() / 5 * 5
                                if (percent != shown) {
                                    shown = percent
                                    doing = "Слушаю аккорды · $percent%"
                                }
                            }
                            doing = null
                            picked.clear()
                            if (heard == null) {
                                said = LabWord(
                                    "Аккордов не слышно",
                                    "В записи не нашлось трезвучий: так бывает с речью, " +
                                        "с барабанной дорожкой и с тем, что телефон не " +
                                        "сумел распаковать.",
                                )
                            } else {
                                picking = LabChords(track, heard)
                            }
                        }
                    },
                )
            }

            // Играющее — последней строкой лаборатории, под полкой действий.
            // Полкой правят файлы, панелью — звук; и второе не должно стоять
            // между рукой и первым. См. [EchoBar].
            EchoBar()
        }
    }

    when (val open = work) {
        null -> Unit

        is LabWork.Rename -> LabNameDialog(
            title = "Новое имя",
            hint = "Как назвать запись",
            initial = open.track.title,
            onDismiss = { work = null },
            onDone = { name ->
                doing = "Переименовываю"
                write(listOf(open.track)) { EchoStudio.rename(context, open.track, name) }
            },
        )

        is LabWork.Move -> LabFolderDialog(
            folders = remember(library) {
                library.orEmpty().map { it.folder }.distinct().sorted()
            },
            onDismiss = { work = null },
            onDone = { folder ->
                doing = "Переношу"
                write(open.tracks) {
                    // Все или ничего было бы вреднее: перенеслась половина —
                    // значит, половина уже на месте, и повторять надо только
                    // остальное.
                    var moved = false
                    for (track in open.tracks) {
                        if (EchoStudio.move(context, track, folder)) moved = true
                    }
                    moved
                }
            },
        )

        is LabWork.Trim -> LabTrimDialog(
            track = open.track,
            onDismiss = { work = null },
            onDone = { fromMs, toMs, name ->
                doing = "Режу"
                scope.launch {
                    ended(EchoStudio.trim(context, open.track, fromMs, toMs, name), true)
                }
            },
        )

        is LabWork.Join -> LabJoinDialog(
            tracks = open.tracks,
            onDismiss = { work = null },
            onDone = { order, name ->
                doing = "Склеиваю"
                scope.launch { ended(EchoStudio.join(context, order, name), true) }
            },
        )

        is LabWork.Playlist -> LabPlaylistCard(
            id = open.id,
            title = open.title,
            playing = current.track?.uri,
            onPlay = onPlay,
            onDismiss = { work = null },
        )
    }

    picking?.let { found ->
        val player = appContainer().echoPlayer
        // Разобранная песня и играющая — не одно и то же: разобрать можно
        // одну, а слушать в это время другую. Пюпитр ведут только по своей.
        val itsOwn = current.track?.uri == found.track.uri

        EchoChordsCard(
            track = found.track,
            score = found.score,
            // Ту же песню не переставляют заново: `play` на играющей дорожке
            // значит «пауза», и нажатие на время выключало бы музыку вместо
            // того, чтобы перевести её на нужное место.
            onPlayAt = { ms ->
                if (!itsOwn) player.play(listOf(found.track), found.track)
                player.seekTo(ms)
            },
            onDismiss = { picking = null },
            playing = itsOwn && current.playing,
            position = player::position,
            // «Сыграть вместе» — с начала, а не с того места, где песню
            // бросили в прошлый раз: под запись играют от первого аккорда.
            // Начатую тем же нажатием ставят на паузу — это одна кнопка.
            onToggle = {
                when {
                    !itsOwn -> {
                        player.play(listOf(found.track), found.track)
                        player.seekTo(0)
                    }

                    current.playing -> player.pause()
                    else -> player.toggle()
                }
            },
        )
    }

    doing?.let { caption ->
        LabWorking(caption)
    }

    said?.let { word ->
        // Одним ответом, а не двумя: это не вопрос, а сообщение о том, что уже
        // случилось, и второй ответ здесь означал бы отмену, которой нет.
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

/** Что сказать по итогу: заголовок и объяснение под ним. */
private data class LabWord(val title: String, val text: String)

/** Разобранная песня и то, у чего её разобрали. */
private data class LabChords(val track: Track, val score: EchoChordScore)

/**
 * Три страницы лаборатории: файл, список, место.
 *
 * Названия — те самые, что стояли на прежних вкладках: за дверью человек
 * должен найти то, что искал в шапке, и под тем же именем.
 */
private enum class LabPage(val title: String, val about: String) {
    MUSIC("Вся музыка", "Нажать — играет, отметить — в работу"),
    PLAYLISTS("Плейлисты", "Порядок и названия своих списков"),
    FOLDERS("Папки", "Куда что разложено"),
}

/** Что сейчас делают. Открытая работа — одна: два окна разом бессмысленны. */
private sealed interface LabWork {
    data class Rename(val track: Track) : LabWork
    data class Move(val tracks: List<Track>) : LabWork
    data class Trim(val track: Track) : LabWork
    data class Join(val tracks: List<Track>) : LabWork
    data class Playlist(val id: Long, val title: String) : LabWork
}

/** Точки страниц — те же, что в Ledger: сколько всего и где стоишь. */
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

/**
 * Вся музыка телефона: нажатие включает, отметка берёт в работу.
 *
 * Тот самый список, что раньше открывался вкладкой «Вся музыка», — с той
 * разницей, что у каждой строки теперь есть и вторая половина. Очередь при
 * включении берётся вся страница целиком: включённая песня должна вести за
 * собой соседние, а не замолкать одна посреди библиотеки.
 */
@Composable
private fun MusicLabPage(
    library: List<Track>?,
    picked: SnapshotStateList<String>,
    playing: String?,
    onPlay: (List<Track>, Track) -> Unit,
) {
    when {
        library == null -> EmptyState(title = "Ищу музыку", hint = "Смотрю, что есть на телефоне.")

        library.isEmpty() -> EmptyState(
            title = "Музыки не нашлось",
            hint = "Echo играет файлы с телефона. Скачай что-нибудь — и он их увидит.",
        )

        else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            items(library, key = { it.id }) { track ->
                PickRow(
                    title = track.title,
                    about = "${track.artist} · ${formatDuration(track.durationMs)} · ${track.folder}",
                    albumId = track.albumId,
                    uri = track.uri,
                    marked = track.uri in picked,
                    current = track.uri == playing,
                    onPlay = { onPlay(library, track) },
                    onMark = {
                        if (track.uri in picked) picked.remove(track.uri) else picked.add(track.uri)
                    },
                )
            }
        }
    }
}

/**
 * Папки как они лежат, и в каждой — свои записи.
 *
 * Раскрытая папка не уводит на другую страницу: перенос делают **из** папки,
 * и уйти из неё ради этого значило бы потерять из виду то, что переносишь.
 */
@Composable
private fun FoldersLabPage(
    library: List<Track>?,
    picked: SnapshotStateList<String>,
    playing: String?,
    onPlay: (List<Track>, Track) -> Unit,
) {
    val folders = remember(library) { library?.let { EchoLibrary.folders(it) } }
    var opened by remember { mutableStateOf<String?>(null) }

    when {
        folders == null -> EmptyState(title = "Ищу музыку", hint = "Смотрю, что есть на телефоне.")

        folders.isEmpty() -> EmptyState(
            title = "Папок с музыкой нет",
            hint = "Как только на телефоне появятся песни, здесь появятся их папки.",
        )

        else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
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
                            // Стрелка показывает, что случится по нажатию, а не
                            // то, в каком папка состоянии: закрытая раскроется
                            // вниз, раскрытая сложится вверх.
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
                            text = songs(folder.tracks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = NightMuted,
                        )
                    }
                }

                if (here) {
                    items(folder.tracks, key = { "in:${it.id}" }) { track ->
                        PickRow(
                            title = track.title,
                            about = "${track.artist} · ${formatDuration(track.durationMs)}",
                            albumId = track.albumId,
                            uri = track.uri,
                            marked = track.uri in picked,
                            current = track.uri == playing,
                            // Очередь — папка, а не вся библиотека: включив
                            // песню из папки, слушают эту папку.
                            onPlay = { onPlay(folder.tracks, track) },
                            onMark = {
                                if (track.uri in picked) picked.remove(track.uri) else picked.add(track.uri)
                            },
                            inset = true,
                        )
                    }
                }
            }
        }
    }
}

/** Плейлисты: здесь их переименовывают и переставляют в них песни. */
@Composable
private fun PlaylistsLabPage(onOpen: (Long, String) -> Unit) {
    val repository = appContainer().echoRepository
    val playlists by remember(repository) { repository.playlists() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sizes by remember(repository) { repository.sizes() }
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    if (playlists.isEmpty()) {
        EmptyState(
            title = "Своих списков нет",
            hint = "Плейлисты заводят в разделе «Плейлисты»; сюда приходят их править.",
        )
        return
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpen(playlist.id, playlist.title) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title.ifBlank { "Без названия" },
                        style = MaterialTheme.typography.titleSmall,
                        color = NightInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = songs(sizes[playlist.id] ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = NightMuted,
                    )
                }
            }
        }
    }
}

/**
 * Строка песни: обложка, подписи и квадратик отметки справа.
 *
 * Половины строки делают разное, и это единственное место в Askya, где так.
 * Нажатие на саму строку включает песню — того же и ждут от списка музыки.
 * Квадратик справа берёт её в работу и стоит отдельной кнопкой с полем шире
 * самого квадратика: промахнуться галочкой по «играть» — значит оборвать
 * музыку, и пальцу надо дать место.
 *
 * Играющая подписана закатом, отмеченная — залитым квадратом. Два разных
 * состояния и две разных краски: песня может быть и той, и другой разом.
 */
@Composable
private fun PickRow(
    title: String,
    about: String,
    albumId: Long,
    uri: String,
    marked: Boolean,
    current: Boolean,
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
        CoverThumb(albumId = albumId, uri = uri, modifier = Modifier.size(40.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (current) Sunset else NightInk,
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
 * догадываться, что нужно отметить, чтобы они вернулись, — а надпись под
 * гаснущей говорит об этом прямо.
 */
@Composable
private fun LabActions(
    chosen: List<Track>,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onTrim: () -> Unit,
    onJoin: () -> Unit,
    onChords: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NightPanel)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = when (chosen.size) {
                0 -> "Отметь запись — и здесь загорятся действия"
                1 -> "Отмечена одна"
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
                icon = Icons.Outlined.DriveFileMove,
                label = "Перенести",
                ready = chosen.isNotEmpty(),
                onClick = onMove,
            )
            LabAction(
                icon = Icons.Outlined.ContentCut,
                label = "Обрезать",
                ready = chosen.size == 1,
                onClick = onTrim,
            )
            LabAction(
                icon = Icons.Outlined.Merge,
                label = "Склеить",
                ready = chosen.size > 1,
                onClick = onJoin,
            )
            // «Аккорды» стоят последними и файла не трогают вовсе: это
            // единственное действие полки, которое ничего не меняет, а только
            // слушает. Место у края — по тому же правилу, по которому в
            // мастерской читающий прибор не лежит между ножницами и клеем.
            LabAction(
                icon = Icons.Outlined.MusicNote,
                label = "Аккорды",
                ready = chosen.size == 1,
                onClick = onChords,
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
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (ready) Sunset else NightBorder,
                RoundedCornerShape(12.dp),
            )
            .clickable(enabled = ready, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (ready) Sunset else NightMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (ready) NightInk else NightMuted,
        )
    }
}

/** Окно с одним полем — имя записи или папки. */
@Composable
private fun LabNameDialog(
    title: String,
    hint: String,
    initial: String,
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
        EchoPill(
            label = "Сохранить",
            chosen = ready,
            onClick = { if (ready) onDone(name) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

/**
 * Куда перенести: одна из папок, что уже есть, или новая.
 *
 * Готовые папки стоят первыми и нажимаются сразу: девять переносов из десяти —
 * это «положить туда же, где остальное», и заставлять набирать имя знакомой
 * папки значило бы просить человека вспомнить то, что и так у него перед
 * глазами.
 */
@Composable
private fun LabFolderDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    EchoDialog(title = "В какую папку", onDismiss = onDismiss) {
        Text(
            text = "Внутри «Music». Новую заведёт само имя.",
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            modifier = Modifier.padding(top = 6.dp),
        )

        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            folders.take(FOLDERS_SHOWN).forEach { folder ->
                Text(
                    text = folder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NightInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDone(folder) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
        }

        EchoField(
            value = name,
            onValueChange = { name = it },
            hint = "Или новая — например, «Записи»",
            modifier = Modifier.padding(top = 8.dp),
            onDone = { if (name.isNotBlank()) onDone(name) },
        )
        EchoPill(
            label = "Перенести",
            chosen = name.isNotBlank(),
            onClick = { if (name.isNotBlank()) onDone(name) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

/**
 * Ножницы: откуда и докуда.
 *
 * Двумя отдельными ползунками, а не одним двухголовым: концы отрезка ставят
 * порознь и по одному — сперва находят начало, потом конец, — и общая
 * перетяжка, где второй конец уезжает вслед за первым, тут только мешает.
 * Ползунки не дают им перепутаться местами: конец не уходит левее начала.
 */
@Composable
private fun LabTrimDialog(
    track: Track,
    onDismiss: () -> Unit,
    onDone: (Long, Long, String) -> Unit,
) {
    val whole = track.durationMs.coerceAtLeast(1_000)
    var from by remember { mutableStateOf(0f) }
    var to by remember { mutableStateOf(whole.toFloat()) }
    var name by remember { mutableStateOf(track.title + " (кусок)") }

    val ready = name.isNotBlank() && to - from > 1_000

    EchoDialog(title = "Обрезать", onDismiss = onDismiss) {
        Text(
            text = "Новый файл ляжет в «${EchoStudio.HOME}». Исходник останется как был.",
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )

        TrimLine(
            label = "Начало",
            value = from,
            whole = whole.toFloat(),
            onChange = { from = it.coerceAtMost(to - 1_000f) },
        )
        TrimLine(
            label = "Конец",
            value = to,
            whole = whole.toFloat(),
            onChange = { to = it.coerceAtLeast(from + 1_000f) },
        )

        Text(
            text = "Останется ${formatDuration((to - from).toLong())}",
            style = MaterialTheme.typography.bodyMedium,
            color = Sunset,
            modifier = Modifier.padding(top = 4.dp),
        )

        EchoField(
            value = name,
            onValueChange = { name = it },
            hint = "Как назвать кусок",
            modifier = Modifier.padding(top = 12.dp),
            onDone = { if (ready) onDone(from.toLong(), to.toLong(), name) },
        )
        EchoPill(
            label = "Отрезать",
            chosen = ready,
            onClick = { if (ready) onDone(from.toLong(), to.toLong(), name) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

@Composable
private fun TrimLine(label: String, value: Float, whole: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDuration(value.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = NightInk,
                fontFamily = FontFamily.Monospace,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..whole,
            colors = SliderDefaults.colors(
                thumbColor = Sunset,
                activeTrackColor = Sunset,
                inactiveTrackColor = NightBorder,
            ),
        )
    }
}

/**
 * Склейка: порядок и имя.
 *
 * Порядок правится стрелками, а не перетаскиванием: склеивают две-три записи,
 * и стрелка вверх понятнее, чем удержание строки пальцем, — тем более что
 * список тут же, в окне, и тащить его некуда.
 */
@Composable
private fun LabJoinDialog(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onDone: (List<Track>, String) -> Unit,
) {
    val order = remember(tracks) { tracks.toMutableStateList() }
    var name by remember { mutableStateOf("Склейка") }
    val ready = name.isNotBlank() && order.size > 1

    EchoDialog(title = "Склеить", onDismiss = onDismiss) {
        Text(
            text = "Пойдут подряд, сверху вниз. Новый файл ляжет в «${EchoStudio.HOME}».",
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )

        order.forEachIndexed { at, track ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${at + 1}. ${track.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NightInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LabStep(
                    icon = Icons.Outlined.KeyboardArrowUp,
                    label = "Выше",
                    ready = at > 0,
                    onClick = { order.add(at - 1, order.removeAt(at)) },
                )
                LabStep(
                    icon = Icons.Outlined.KeyboardArrowDown,
                    label = "Ниже",
                    ready = at < order.lastIndex,
                    onClick = { order.add(at + 1, order.removeAt(at)) },
                )
            }
        }

        EchoField(
            value = name,
            onValueChange = { name = it },
            hint = "Как назвать склейку",
            modifier = Modifier.padding(top = 12.dp),
            onDone = { if (ready) onDone(order.toList(), name) },
        )
        EchoPill(
            label = "Склеить",
            chosen = ready,
            onClick = { if (ready) onDone(order.toList(), name) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

@Composable
private fun LabStep(
    icon: ImageVector,
    label: String,
    ready: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = ready, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (ready) Sunset else NightMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Один плейлист: его песни, их порядок и имя списка.
 *
 * Нажатие на строку включает песню со всем списком в очередь — как и на
 * странице музыки. Стрелки и крестик стоят справа отдельными кнопками: править
 * порядок приходят реже, чем слушать, и место под пальцем отдано слушанию.
 */
@Composable
private fun LabPlaylistCard(
    id: Long,
    title: String,
    playing: String?,
    onPlay: (List<Track>, Track) -> Unit,
    onDismiss: () -> Unit,
) {
    val repository = appContainer().echoRepository
    val scope = rememberCoroutineScope()

    val rows by remember(repository, id) { repository.tracks(id) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by remember(repository) { repository.playlists() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var naming by remember { mutableStateOf(false) }

    EchoCard(
        title = title.ifBlank { "Плейлист" },
        subtitle = songs(rows.size),
        onDismiss = onDismiss,
        back = true,
        width = 0.94f,
        height = 0.86f,
        actions = {
            EchoIcon(
                icon = Icons.Outlined.Science,
                label = "Переименовать список",
                tint = Sunset,
                onClick = { naming = true },
            )
        },
    ) {
        if (rows.isEmpty()) {
            EmptyState(title = "Список пуст", hint = "Переставлять нечего.")
        } else {
            val queue = rows.map { it.asTrack() }

            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                items(rows, key = { it.id }) { row ->
                    val at = rows.indexOf(row)
                    val track = row.asTrack()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPlay(queue, track) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverThumb(
                            albumId = row.albumId,
                            uri = row.uri,
                            modifier = Modifier.size(36.dp),
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (row.uri == playing) Sunset else NightInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = row.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = NightMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        LabStep(
                            icon = Icons.Outlined.KeyboardArrowUp,
                            label = "Выше",
                            ready = at > 0,
                            onClick = { scope.launch { repository.reorder(moved(rows, at, at - 1)) } },
                        )
                        LabStep(
                            icon = Icons.Outlined.KeyboardArrowDown,
                            label = "Ниже",
                            ready = at < rows.lastIndex,
                            onClick = { scope.launch { repository.reorder(moved(rows, at, at + 1)) } },
                        )
                        EchoIcon(
                            icon = Icons.Outlined.Close,
                            label = "Убрать из списка",
                            onClick = { scope.launch { repository.remove(row.id) } },
                        )
                    }
                }
            }
        }
    }

    if (naming) {
        LabNameDialog(
            title = "Имя списка",
            hint = "Например, «В дорогу»",
            initial = title,
            onDismiss = { naming = false },
            onDone = { name ->
                naming = false
                val playlist = playlists.firstOrNull { it.id == id } ?: return@LabNameDialog
                scope.launch { repository.rename(playlist, name) }
            },
        )
    }
}

/** Тот же список, но одна строка переставлена. */
private fun moved(
    rows: List<EchoPlaylistTrack>,
    from: Int,
    to: Int,
): List<EchoPlaylistTrack> = rows.toMutableList().apply { add(to, removeAt(from)) }

/**
 * Пока идёт работа.
 *
 * Дышащий цветок, а не полоска: сколько осталось, никто не знает — перекодировка
 * идёт со скоростью, которую задаёт сам телефон, — и полоска, ползущая наугад,
 * врала бы. Экран под ней закрыт целиком: трогать список, пока правится файл из
 * него же, нельзя.
 */
@Composable
private fun LabWorking(caption: String) {
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
                text = "Это занимает секунды на каждую минуту записи",
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Сколько готовых папок показывать в окне переноса, чтобы оно не выросло в список. */
private const val FOLDERS_SHOWN = 6
