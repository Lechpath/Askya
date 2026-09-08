package app.askya.ui.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.data.entity.ThreadItem
import app.askya.data.entity.ThreadNode
import app.askya.data.repository.ThreadRow
import app.askya.data.repository.ThreadWeb
import app.askya.domain.model.NodeSize
import app.askya.domain.model.ThreadPulse
import app.askya.domain.model.ThreadState
import app.askya.domain.model.suggestState
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.NewButton
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.theme.cardEdge
import app.askya.ui.theme.cardShade
import java.time.LocalDate

/**
 * Карта замысла: одна нить во весь экран.
 *
 * ## Почему карта, а не список
 *
 * Список отвечает на вопрос «что у меня есть», а замысел живёт другим вопросом
 * — «из чего это выросло и куда ведёт». В списке нельзя показать, что от
 * результата расходятся два пути, что один из них упёрся в камень, а обход
 * камня родился из вопроса, заданного месяц назад. В карте это видно молча,
 * одним взглядом, и ради этого она и заведена.
 *
 * Поэтому у карты нет ни колонок, ни уровней, ни порядка. Есть место: узел
 * лежит там, куда его положили. Приложение раскладывает только новые узлы и
 * только чтобы они не легли друг на друга ([app.askya.domain.model.placeNear]).
 *
 * ## Чем двигают
 *
 * Пальцем по пустому месту — вся карта; двумя пальцами — приближение. Узел
 * берут долгим нажатием: короткое открывает карточку, и если бы карточка
 * ездила от обычного касания, читать карту было бы нельзя, не сдвинув её.
 *
 * Приближение зажато между 0,45 и 1,8. Дальше первого предела подписи
 * превращаются в грязь, ближе второго на экране остаётся полтора узла — оба
 * края бесполезны, и упираться в них лучше, чем проваливаться.
 *
 * ## Связи тянутся не пальцем
 *
 * Рисовать линию от узла к узлу пальцем — приём из редакторов схем, и он
 * требует точности, которой на телефоне нет. Здесь связь заводится словами:
 * «Вырастить» делает новый узел уже связанным с этим, «Связать» просит указать
 * второй узел. Обе линии появляются сами, и ни одну не надо вести рукой.
 */
@Composable
fun ThreadMapScreen(threadId: Long, onBack: () -> Unit, onOpenLink: (String) -> Unit) {
    val container = appContainer()
    val viewModel: ThreadsViewModel = viewModel(factory = ThreadsViewModel.factory(container))

    val thread by remember(threadId) { viewModel.thread(threadId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val web by remember(threadId) { viewModel.web(threadId) }
        .collectAsStateWithLifecycle(initialValue = ThreadWeb())
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val parts by viewModel.parts.collectAsStateWithLifecycle()

    LaunchedEffect(threadId) { viewModel.show(threadId) }

    // Что открыто поверх карты. Всё по отдельности: у окна правки и у окна
    // выбора типа разные жизни, и одно поле «что показано» пришлось бы
    // разбирать в разметке.
    var opened by remember(threadId) { mutableLongStateOf(0L) }
    var editing by remember(threadId) { mutableStateOf<ThreadNode?>(null) }
    var growing by remember(threadId) { mutableStateOf<ThreadNode?>(null) }
    var picking by remember(threadId) { mutableStateOf(false) }
    var linking by remember(threadId) { mutableStateOf<ThreadNode?>(null) }
    var whole by remember(threadId) { mutableStateOf(false) }
    var mending by remember(threadId) { mutableStateOf<ThreadItem?>(null) }
    var notice by remember(threadId) { mutableStateOf<String?>(null) }

    // Кого связывают. 0 — не связывают никого.
    var tying by remember(threadId) { mutableLongStateOf(0L) }

    // Как карта сдвинута и приближена. Живёт в разметке и нарочно не
    // сохраняется: возвращаясь к нити, человек хочет увидеть её целиком, а не
    // тот угол, в котором он копался неделю назад.
    var shift by remember(threadId) { mutableStateOf(Offset.Zero) }
    var zoom by remember(threadId) { mutableFloatStateOf(1f) }
    var view by remember { mutableStateOf(IntSize.Zero) }
    var centred by remember(threadId) { mutableStateOf(false) }

    // Узел, который тянут, и насколько он уже сдвинут. В базу это уходит один
    // раз — когда палец отпустили: писать место на каждый кадр значило бы
    // сотню запросов на один жест.
    var dragged by remember { mutableLongStateOf(0L) }
    var dragBy by remember { mutableStateOf(Offset.Zero) }

    // Куда узел положили, пока база не догнала. Без этого карточка на кадр
    // отскакивает на старое место: палец отпущен, смещение сброшено, а запись
    // ещё не дошла — и человек видит, что узел «не послушался».
    var landing by remember(threadId) { mutableStateOf<Pair<Long, Offset>?>(null) }

    LaunchedEffect(web.nodes) {
        val (id, at) = landing ?: return@LaunchedEffect
        val node = web.node(id)
        if (node == null || (kotlin.math.abs(node.x - at.x) < 0.5f &&
                kotlin.math.abs(node.y - at.y) < 0.5f)
        ) {
            landing = null
        }
    }

    val density = LocalDensity.current
    val today = LocalDate.now()

    // Один раз на заход карта встаёт так, чтобы замысел оказался в середине.
    LaunchedEffect(web.nodes.size, view) {
        if (centred || view == IntSize.Zero || web.nodes.isEmpty()) return@LaunchedEffect
        val midX = (web.nodes.minOf { it.x } + web.nodes.maxOf { it.x }) / 2f
        val midY = (web.nodes.minOf { it.y } + web.nodes.maxOf { it.y }) / 2f
        val px = with(density) { Offset(midX.dp.toPx(), midY.dp.toPx()) }
        shift = Offset(view.width / 2f - px.x, view.height / 2.6f - px.y)
        centred = true
    }

    val item = thread
    ScreenScaffold(
        title = item?.title?.ifBlank { "Нить" } ?: "Нить",
        onNavigationClick = onBack,
        navigationIsBack = true,
        actions = {
            item?.let { known ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { whole = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = stateIcon(known.state),
                        contentDescription = null,
                        tint = stateColor(known.state),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = known.state.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Muted,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        },
        floatingActionButton = {
            if (item != null && tying == 0L) {
                ExtendedFloatingActionButton(
                    onClick = {
                        growing = null
                        picking = true
                    },
                    shape = RoundedCornerShape(percent = 50),
                    containerColor = Ink,
                    contentColor = Accent,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = if (web.empty) "искра" else "узел",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (web.empty) {
                FirstNode(
                    ending = item?.ending.orEmpty(),
                    onStart = {
                        growing = null
                        picking = true
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { view = it }
                        .pointerInput(threadId) {
                            detectTransformGestures { centroid, pan, pinch, _ ->
                                val next = (zoom * pinch).coerceIn(NEAR, FAR)
                                // Точка карты под пальцами остаётся под ними:
                                // иначе приближение уводит замысел за край, и
                                // человек ищет его заново после каждого щипка.
                                shift = centroid + pan - (centroid - shift) / zoom * next
                                zoom = next
                            }
                        },
                ) {
                    // Краски снимаются с темы заранее: внутри рисования темы
                    // уже нет — она читается только в разметке.
                    val lit = Accent
                    val plain = Muted

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = shift.x
                                translationY = shift.y
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            web.edges.forEach { edge ->
                                val from = web.node(edge.fromId)
                                val to = web.node(edge.toId)
                                if (from != null && to != null) {
                                    // Связи открытого узла и того, который
                                    // связывают, видно ярче: иначе на карте из
                                    // двадцати линий не найти свою.
                                    val near = tying == from.id || tying == to.id ||
                                        opened == from.id || opened == to.id
                                    tie(
                                        from = centreOf(from, this),
                                        to = centreOf(to, this),
                                        lit = near,
                                        colour = if (near) lit else plain,
                                    )
                                }
                            }
                        }

                        web.nodes.forEach { node ->
                            val put = landing?.takeIf { it.first == node.id }?.second
                            NodeCard(
                                node = node,
                                at = (put ?: Offset(node.x, node.y)) +
                                    if (dragged == node.id) dragBy else Offset.Zero,
                                done = web.done(node),
                                dated = web.deedOf(node) != null,
                                dimmed = item?.state?.closed == true,
                                chosen = tying == node.id,
                                onTap = {
                                    if (tying != 0L && tying != node.id) {
                                        viewModel.tie(threadId, tying, node.id)
                                        tying = 0L
                                    } else if (tying == node.id) {
                                        tying = 0L
                                    } else {
                                        opened = node.id
                                    }
                                },
                                onTakeUp = {
                                    dragged = node.id
                                    dragBy = Offset.Zero
                                },
                                onMove = { delta ->
                                    // Палец ходит в пикселях, а место узла
                                    // хранится в тех же единицах, что и карта.
                                    dragBy += Offset(
                                        x = delta.x / density.density,
                                        y = delta.y / density.density,
                                    )
                                },
                                onPutDown = {
                                    val to = Offset(node.x + dragBy.x, node.y + dragBy.y)
                                    viewModel.moveNode(node.id, to.x, to.y)
                                    landing = node.id to to
                                    dragged = 0L
                                    dragBy = Offset.Zero
                                },
                            )
                        }
                    }
                }
            }

            // Подсказки поверх карты: они не часть замысла и потому не ездят
            // вместе с ним.
            if (tying != 0L) {
                Tip(
                    text = "Нажми на второй узел — они свяжутся. Ещё раз по этому же — отмена.",
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else if (item != null && !web.empty) {
                val guess = suggestState(
                    state = item.state,
                    pulse = rows.firstOrNull { it.thread.id == threadId }?.pulse
                        ?: ThreadPulse(),
                    signs = web.signs(today),
                    today = today,
                )
                if (guess != null) {
                    Guess(
                        state = guess,
                        onTake = { viewModel.setState(item, guess) },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }

    // ---- Окна поверх карты ----

    if (picking && item != null) {
        ThreadKindCard(
            from = growing,
            onPick = { kind ->
                picking = false
                editing = ThreadNode(threadId = threadId, kind = kind)
            },
            onDismiss = {
                picking = false
                growing = null
            },
        )
    }

    editing?.let { draft ->
        ThreadNodeEditCard(
            node = draft,
            onSave = { made ->
                if (made.id == 0L) {
                    viewModel.addNode(
                        threadId = threadId,
                        kind = made.kind,
                        title = made.title,
                        note = made.note,
                        from = growing,
                    ) { id -> opened = id }
                } else {
                    viewModel.saveNode(made)
                }
                editing = null
                growing = null
            },
            onDismiss = {
                editing = null
                growing = null
            },
        )
    }

    if (opened != 0L) {
        val node = web.node(opened)
        if (node == null) {
            opened = 0L
        } else {
            ThreadNodeCard(
                node = node,
                web = web,
                done = web.done(node),
                onEdit = { editing = node },
                onGrow = {
                    growing = node
                    picking = true
                    opened = 0L
                },
                onTie = {
                    tying = node.id
                    opened = 0L
                },
                onUntie = { other -> web.edgeBetween(node.id, other)?.let { viewModel.untie(it.id) } },
                onOpenNode = { id -> opened = id },
                onMark = { done -> viewModel.markNode(node, done) },
                onDeed = {
                    viewModel.stepToDeed(node)
                    notice = "Дело стоит в сегодняшнем дне и тянет эту нить. Отметишь его " +
                        "там — шаг на карте станет сделанным сам."
                },
                onLine = { item?.let { viewModel.stepToLine(it, node) } },
                onLink = { linking = node },
                onOpenLink = onOpenLink,
                onDelete = {
                    viewModel.deleteNode(node.id)
                    opened = 0L
                },
                onDismiss = { opened = 0L },
            )
        }
    }

    linking?.let { node ->
        ThreadNodeLinkCard(
            node = node,
            onPick = { link ->
                viewModel.linkNode(node, link)
                linking = null
            },
            onDismiss = { linking = null },
        )
    }

    if (whole && item != null) {
        ThreadCard(
            row = rows.firstOrNull { it.thread.id == threadId }
                ?: ThreadRow(thread = item),
            parts = parts,
            web = web,
            onEdit = {
                whole = false
                mending = item
            },
            onAddDeed = { title -> viewModel.addDeed(threadId, title) },
            onAddLine = { text -> viewModel.addLine(item, text) },
            onState = { state -> viewModel.setState(item, state) },
            onDismiss = { whole = false },
        )
    }

    mending?.let { known ->
        ThreadEditCard(
            thread = known,
            onSave = {
                viewModel.save(it)
                mending = null
            },
            // Стереть нить отсюда нельзя: на карте у неё уже есть узлы, а нить,
            // через которую что-то прошло, не стирается — её бросают. Стирают
            // пустую, и делают это из ленты, где она рядом с остальными.
            onDelete = null,
            onDismiss = { mending = null },
        )
    }

    notice?.let { text ->
        AskyaNotice(title = "Записано", text = text, onDismiss = { notice = null })
    }
}

/** Ближе этого карту не приближают и дальше не отодвигают. */
private const val NEAR = 0.45f
private const val FAR = 1.8f

/** Середина карточки узла в пикселях — от неё тянутся линии. */
private fun centreOf(node: ThreadNode, scope: DrawScope): Offset = with(scope) {
    Offset(
        x = node.x.dp.toPx() + nodeWidth(node.size).toPx() / 2f,
        y = node.y.dp.toPx() + nodeHeight(node.size).toPx() / 2f,
    )
}

/**
 * Линия между узлами — с прогибом, а не по линейке.
 *
 * Прямая читается как стрелка схемы; нить провисает. Прогиб взят долей от
 * длины, поэтому короткая связь почти пряма, а длинная заметно тянется — как
 * настоящая нить между двумя точками.
 */
private fun DrawScope.tie(from: Offset, to: Offset, lit: Boolean, colour: Color) {
    val middle = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
    val away = to - from
    // Прогиб поперёк линии: повёрнутый на четверть оборота вектор длины.
    val bend = Offset(-away.y, away.x) * 0.12f
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(middle.x + bend.x, middle.y + bend.y, to.x, to.y)
    }
    drawPath(
        path = path,
        color = colour.copy(alpha = if (lit) 0.85f else 0.35f),
        style = Stroke(width = if (lit) 3.dp.toPx() else 2.dp.toPx()),
    )
}

/** Высота карточки узла. Постоянная: от неё считаются концы линий. */
fun nodeHeight(size: app.askya.domain.model.NodeSize): androidx.compose.ui.unit.Dp = when (size) {
    NodeSize.SMALL -> 78.dp
    app.askya.domain.model.NodeSize.PLAIN -> 94.dp
    app.askya.domain.model.NodeSize.BIG -> 108.dp
}

/**
 * Узел на карте.
 *
 * Всё, что о нём известно с одного взгляда: тип — цветом, формой и значком,
 * сам узел — двумя-тремя строками, и внизу отметки: сделан, стоит делом в дне,
 * к чему-то привязан. Больше на карточку не влезет, да и не надо: остальное
 * открывается касанием.
 */
@Composable
private fun NodeCard(
    node: ThreadNode,
    /** Где карточка сейчас: место из базы, из-под пальца или только что данное. */
    at: Offset,
    done: Boolean,
    dated: Boolean,
    dimmed: Boolean,
    chosen: Boolean,
    onTap: () -> Unit,
    onTakeUp: () -> Unit,
    onMove: (Offset) -> Unit,
    onPutDown: () -> Unit,
) {
    val colour = nodeColor(node.kind)
    val shape = nodeShape(node.kind)
    val faded = dimmed && !chosen

    Box(
        modifier = Modifier
            .offset { IntOffset(x = at.x.dp.roundToPx(), y = at.y.dp.roundToPx()) }
            .width(nodeWidth(node.size))
            .height(nodeHeight(node.size))
            .cardShade(shape, elevation = if (chosen) 10.dp else 4.dp)
            .clip(shape)
            .background(
                if (done) colour.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = if (chosen) 2.5.dp else 1.5.dp,
                color = when {
                    chosen -> Accent
                    faded -> colour.copy(alpha = 0.3f)
                    else -> colour.copy(alpha = 0.75f)
                },
                shape = shape,
            )
            .cardEdge(shape)
            .pointerInput(node.id) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(node.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onTakeUp() },
                    onDrag = { change, delta ->
                        change.consume()
                        onMove(delta)
                    },
                    onDragEnd = { onPutDown() },
                    onDragCancel = { onPutDown() },
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = nodeIcon(node.kind),
                    contentDescription = null,
                    tint = if (faded) colour.copy(alpha = 0.4f) else colour,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = node.kind.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 5.dp).weight(1f),
                )
            }
            Text(
                text = node.title.ifBlank { "Без слов" },
                style = MaterialTheme.typography.bodySmall,
                color = if (faded) Muted else Ink,
                maxLines = if (node.size == NodeSize.SMALL) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp).weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colour,
                        modifier = Modifier.size(13.dp),
                    )
                }
                if (dated) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(13.dp),
                    )
                }
                if (node.link != null) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

/**
 * Пустая карта: не «ничего нет», а приглашение начать с искры.
 *
 * Пустой экран в Askya всегда объясняет, чем он станет, — здесь это особенно
 * важно: человек, впервые открывший карту, видит белое поле и не догадывается,
 * что оно про его собственные мысли, а не про задачи.
 */
@Composable
private fun FirstNode(ending: String, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Карта пуста",
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            color = Ink,
        )
        Text(
            text = if (ending.isNotBlank()) {
                "Ты уже сказал, чем эта нить закончится: «$ending». Начни с искры — с " +
                    "того, откуда всё пошло, — а результат, пути и подводные камни вырастут " +
                    "из неё."
            } else {
                "Здесь замысел живёт до того, как станет делами: искра, из неё вопросы и " +
                    "пути, между ними подводные камни, из шагов — открытия. Начни с искры: " +
                    "с того, что тебя зацепило."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(20.dp))
        NewButton(label = "Первый узел", onClick = onStart)
    }
}

/** Полоска подсказки поверх карты — на время, пока идёт связывание. */
@Composable
private fun Tip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = AccentInk,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AccentSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/**
 * Догадка о состоянии нити: «похоже, она тлеет».
 *
 * Догадка, а не решение: приложение не переставляет состояние само (см.
 * [suggestState]). Одна строка, тихая плашка, и её можно не заметить — ровно
 * как «тишина» в ленте раздела.
 */
@Composable
private fun Guess(state: ThreadState, onTake: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AccentSoft)
            .clickable(onClick = onTake)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = stateIcon(state),
            contentDescription = null,
            tint = stateColor(state),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Похоже, нить ${state.title.lowercase()}",
            style = MaterialTheme.typography.bodySmall,
            color = AccentInk,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Text(
            text = "Сменить",
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
        )
    }
}
