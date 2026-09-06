package app.askya.ui.bridges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.bridges.BridgeApps
import app.askya.data.entity.Bridge
import app.askya.data.entity.BridgeKind
import app.askya.domain.model.BlockIcon
import app.askya.ui.components.AskyaAsk
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.EmptyState
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.blockIconOf
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.ModeRed
import app.askya.ui.theme.cardEdge
import kotlinx.coroutines.launch

/**
 * «Мосты» — чем дела дня делаются за пределами Askya.
 *
 * Своя страница, а не кучка в настройках: у моста три вещи (название, куда
 * ведёт, к какому знаку подключён), их правят, и мостов бывает с десяток.
 * Строкой настроек это не показать.
 *
 * Здесь же видно, какие знаки заняты, а какие свободны: знак — главный слой,
 * и человек должен видеть карту целиком, а не вспоминать, подключал ли он уже
 * «чтение».
 *
 * Мост со снесённым приложением помечен красным прямо в списке — не дожидаясь,
 * пока человек на него нажмёт и упрётся в стену.
 */
@Composable
fun BridgesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bridges by container.bridgeRepository.bridges()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var editing by remember { mutableStateOf<Bridge?>(null) }
    var deleting by remember { mutableStateOf<Bridge?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenScaffold(
            title = "Мосты",
            onNavigationClick = onBack,
            actions = {
                IconButton(onClick = { editing = Bridge() }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Новый мост",
                        tint = Accent,
                    )
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingVerticalScroll()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = "Половина дел дня делается не в Askya, и это нормально. Мост — " +
                        "пустая рамка: как он называется и куда ведёт, решаете вы. Askya не " +
                        "знает и не должна знать, чем вы читаете.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )

                if (bridges.isEmpty()) {
                    EmptyState(
                        title = "Мостов пока нет.",
                        hint = "Плюс в шапке заведёт первый.",
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    )
                } else {
                    bridges.forEach { bridge ->
                        BridgeRow(
                            bridge = bridge,
                            alive = BridgeApps.alive(context, bridge),
                            subtitle = describe(context, bridge),
                            onClick = { editing = bridge },
                            onDelete = { deleting = bridge },
                        )
                    }
                }

                Text(
                    text = "Знаки",
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    color = AccentInk,
                    modifier = Modifier.padding(top = 26.dp, bottom = 4.dp),
                )
                Text(
                    text = "Мост на знаке — главный слой: знак угадывается по названию дела, " +
                        "и подключённая к «чтению» читалка ведёт из всех дел про чтение, " +
                        "включая завтрашние. Занятые знаки горят краской.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                TakenIcons(taken = bridges.mapNotNull { it.icon }.toSet())
            }
        }

        editing?.let { current ->
            BridgeCard(
                bridge = current,
                takenIcons = bridges.filter { it.id != current.id }.mapNotNull { it.icon }.toSet(),
                onSave = { saved ->
                    scope.launch { container.bridgeRepository.save(saved) }
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }

        deleting?.let { current ->
            AskyaAsk(
                title = "Убрать мост «" + current.name + "»?",
                text = "Дела, которые вели через него, снова будут открываться внутри Askya. " +
                    "Само приложение на телефоне никуда не денется.",
                confirm = "Убрать",
                onConfirm = {
                    scope.launch { container.bridgeRepository.delete(current) }
                    deleting = null
                },
                onDismiss = { deleting = null },
            )
        }
    }
}

/** Строка списка: к чему подключён, живо ли, каким знаком занят. */
@Composable
private fun BridgeRow(
    bridge: Bridge,
    alive: Boolean,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .cardEdge(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bridge.icon?.let { icon ->
            Icon(
                imageVector = blockIconOf("", icon),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(26.dp).padding(end = 2.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp)) {
            Text(
                text = bridge.name.ifBlank { "Без названия" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (alive) subtitle else "Приложения больше нет — переподключите",
                style = MaterialTheme.typography.bodySmall,
                color = if (alive) MaterialTheme.colorScheme.onSurfaceVariant else ModeRed,
            )
        }
        if (!alive) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = ModeRed,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Убрать мост",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Карта знаков: что уже занято мостом, а что свободно. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TakenIcons(taken: Set<BlockIcon>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
    ) {
        BlockIcon.entries.forEach { icon ->
            Icon(
                imageVector = blockIconOf("", icon),
                contentDescription = null,
                tint = if (icon in taken) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/**
 * Карточка моста: название, куда ведёт, к какому знаку подключён.
 *
 * Знак необязателен: мост без знака подключают руками — к строке списка дел
 * или к отдельному делу. Занятый другим мостом знак не выбирается: один знак —
 * один мост, иначе «какой из двух» пришлось бы спрашивать в тот момент, когда
 * человек просто нажал на дело.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BridgeCard(
    bridge: Bridge,
    takenIcons: Set<BlockIcon>,
    onSave: (Bridge) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(bridge.name) }
    var kind by remember { mutableStateOf(bridge.kind) }
    var target by remember { mutableStateOf(bridge.target) }
    var icon by remember { mutableStateOf(bridge.icon) }
    var picking by remember { mutableStateOf(false) }

    val recent by container.settings.settings
        .collectAsStateWithLifecycle(initialValue = container.settings.state.value)

    if (picking) {
        AppPickCard(
            recent = recent.recentApps,
            onPick = { app ->
                target = app.packageName
                // Название подставляется, если человек его ещё не написал:
                // «Telegram» лучше пустой строки, а «Созвон» он напишет сам,
                // если захочет.
                if (name.isBlank()) name = app.label
                container.settings.rememberApp(app.packageName)
                picking = false
            },
            onDismiss = { picking = false },
        )
        return
    }

    AskyaDialog(onDismiss = onDismiss, width = 0.88f) {
        Text(
            text = if (bridge.id == 0L) "Новый мост" else "Мост",
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Field(value = name, onChange = { name = it }, hint = "Название: Читалка, Созвон")

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            BridgeKind.entries.forEach { option ->
                Word(
                    text = option.title,
                    picked = option == kind,
                    onClick = {
                        kind = option
                        target = ""
                    },
                )
            }
        }

        if (kind == BridgeKind.APP) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { picking = true }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = target.takeIf { it.isNotBlank() }
                        ?.let { BridgeApps.labelOf(context, it).ifBlank { it } }
                        ?: "Выбрать приложение",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (target.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else AccentInk,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (target.isBlank()) "Выбрать" else "Сменить",
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent,
                )
            }
        } else {
            Field(
                value = target,
                onChange = { target = it },
                hint = "Адрес: приложение откроет его само",
            )
            Text(
                text = "Ссылка нужна там, где приложение умеет открыться не на главной, а в " +
                    "нужном месте. Какая именно — ваше дело: Askya в адрес не заглядывает.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Text(
            text = "На каком знаке",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        IconRow(
            chosen = icon,
            taken = takenIcons,
            onPick = { picked -> icon = if (picked == icon) null else picked },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            val ready = name.isNotBlank() && target.isNotBlank()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = ready) {
                        onSave(
                            bridge.copy(
                                name = name.trim(),
                                target = target.trim(),
                                kind = kind,
                                icon = icon,
                            ),
                        )
                        if (kind == BridgeKind.APP) {
                            scope.launch { container.settings.rememberApp(target.trim()) }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = if (ready) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Готово",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ready) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * Ряд знаков внутри карточки моста.
 *
 * Занятые другим мостом приглушены и не нажимаются: правило «один знак — один
 * мост» видно глазами, а не всплывает ошибкой после нажатия «готово».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconRow(chosen: BlockIcon?, taken: Set<BlockIcon>, onPick: (BlockIcon) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BlockIcon.entries.forEach { option ->
            val busy = option in taken
            Icon(
                imageVector = blockIconOf("", option),
                contentDescription = null,
                tint = when {
                    option == chosen -> Accent
                    busy -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (option == chosen) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                    )
                    .clickable(enabled = !busy) { onPick(option) }
                    .padding(5.dp)
                    .size(24.dp),
            )
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String) {
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(hint) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun Word(text: String, picked: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (picked) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (picked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Куда ведёт мост — словами человека, а не именем пакета. */
private fun describe(context: android.content.Context, bridge: Bridge): String =
    when (bridge.kind) {
        BridgeKind.LINK -> bridge.target
        BridgeKind.APP -> BridgeApps.labelOf(context, bridge.target).ifBlank { bridge.target }
    }
