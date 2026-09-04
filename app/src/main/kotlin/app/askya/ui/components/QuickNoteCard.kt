package app.askya.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.app.appContainer
import app.askya.ui.theme.Accent
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge
import kotlinx.coroutines.launch

/**
 * Быстрая заметка — карточка прямо из меню.
 *
 * Записать пришедшее в голову стоило четырёх шагов: меню → Scroll →
 * Библиотека → «new file» → «Заметку». К четвёртому шагу мысль обычно уже
 * уходила. Теперь строка для неё открывается там же, где меню и открыли.
 *
 * Быстрая она только в том, как её завели: ложится заметка туда же, куда и
 * все остальные, — в «Библиотеку», к записям без книги. Второго места для
 * записанного в Askya нет и заводить его незачем: иначе человеку пришлось бы
 * помнить, писал он «быстро» или «как следует».
 *
 * Заголовок можно не писать: если строка пуста, именем становится первая
 * строчка текста — то же, что человек и назвал бы этой заметкой, если бы его
 * спросили.
 *
 * [onOpenFull] — «развернуть»: заметка записывается и тут же открывается
 * правкой со всей разметкой. Быстрая запись иногда оказывается началом
 * длинной, и бросать её ради этого не нужно.
 */
@Composable
fun QuickNoteCard(onDismiss: () -> Unit, onOpenFull: (Long) -> Unit) {
    val notes = appContainer().noteRepository
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    BackHandler(onBack = onDismiss)

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrim by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(200),
        label = "scrim",
    )
    // Карточка вырастает, а не проявляется, — тем же движением, что и карточка
    // дела в AskyaDay: нажали на кнопку, из неё выросла карточка.
    val grow by animateFloatAsState(
        targetValue = if (shown) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 330f),
        label = "grow",
    )

    val ready = title.isNotBlank() || body.isNotBlank()

    /** Имя: написанное или первая строчка текста, если строку не трогали. */
    fun name(): String = title.trim().ifBlank {
        body.trim().lineSequence().firstOrNull()?.take(60).orEmpty()
    }

    fun save(then: (Long) -> Unit) {
        if (!ready) return
        scope.launch {
            val id = notes.quickNote(name(), body)
            then(id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(scrim)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.56f)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .cardEdge(RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(22.dp)) {
                Text(
                    text = "Быстрая заметка",
                    style = MaterialTheme.typography.labelLarge,
                    color = Muted,
                )
                Text(
                    text = "Ляжет в Библиотеку, к остальным записям",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )

                Line(
                    value = title,
                    onValueChange = { title = it },
                    hint = "Название — можно не писать",
                    fontSize = 22.sp,
                    weight = FontWeight.SemiBold,
                    modifier = Modifier.focusRequester(focus),
                )

                Spacer(modifier = Modifier.padding(top = 8.dp))

                Line(
                    value = body,
                    onValueChange = { body = it },
                    hint = "Что записать?",
                    fontSize = 17.sp,
                    weight = FontWeight.Normal,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ActionButton(
                        icon = Icons.Outlined.Close,
                        label = "Закрыть",
                        onClick = onDismiss,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ActionButton(
                        icon = Icons.Outlined.OpenInFull,
                        label = "Развернуть",
                        enabled = ready,
                        onClick = { save { id -> onOpenFull(id) } },
                    )
                    ActionButton(
                        icon = Icons.Outlined.Check,
                        label = "Записать",
                        accent = ready,
                        enabled = ready,
                        onClick = { save { onDismiss() } },
                    )
                }
            }
        }
    }
}

/**
 * Строка карточки: своё поле, а не `OutlinedTextField`.
 *
 * У того рамка и плавающая подпись чужого оформления; здесь нужен просто
 * набранный текст на кремовом листе — как в карточке дела и в карточке
 * альбома.
 */
@Composable
private fun Line(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Default,
        fontSize = fontSize,
        lineHeight = fontSize * 1.35f,
        fontWeight = weight,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Box(modifier = modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
            Text(text = hint, style = style.copy(color = Muted.copy(alpha = 0.7f)))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
