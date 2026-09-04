package app.askya.ui.bridges

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.askya.bridges.BridgeApps
import app.askya.bridges.InstalledApp
import app.askya.ui.components.AskyaDialog
import app.askya.ui.theme.AccentInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Выбор приложения для моста.
 *
 * Список приложений, как на рабочем столе: значок и имя, взятые у самой
 * системы. Своих названий Askya не держит — она показывает то, что человек
 * видит каждый день.
 *
 * Сверху — поиск: приложений на телефоне полторы сотни, и листать их до
 * нужного дольше, чем набрать три буквы. Ещё выше — те, что подключали недавно:
 * мосты чинят и переподключают чаще, чем заводят новые, и второй раз человек
 * ищет то же самое.
 *
 * Список читается в фоне: `queryIntentActivities` со значками на полутора сотнях
 * приложений занимает заметную долю секунды, и на главном потоке она видна
 * рывком.
 */
@Composable
fun AppPickCard(
    recent: List<String>,
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(context) {
        apps = withContext(Dispatchers.IO) { BridgeApps.installed(context) }
    }

    AskyaDialog(onDismiss = onDismiss, width = 0.9f) {
        Text(
            text = "Чем открывать",
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "Askya не знает, чем вы читаете и где созваниваетесь. Выберите сами — " +
                "и мост поведёт туда из всех дел, к которым он подключён.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Найти приложение") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        val all = apps
        if (all == null) {
            Text(
                text = "Смотрю, что стоит на телефоне…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
            return@AskyaDialog
        }

        val found = all.filter { it.label.contains(query.trim(), ignoreCase = true) }
        // Недавние — только пока не ищут: во время поиска человек уже знает,
        // что ему нужно, и переставленные наверх строки только мешают.
        val ordered = if (query.isBlank()) {
            found.sortedByDescending { recent.indexOf(it.packageName).takeIf { i -> i >= 0 } ?: -1 }
        } else {
            found
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(ordered, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPick(app) }
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (app.packageName in recent) {
                            Text(
                                text = "недавно",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentInk,
                            )
                        }
                    }
                }
            }

            if (ordered.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Ничего не нашлось.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Значок приложения — тот же, что на рабочем столе.
 *
 * Через растр, а не `rememberDrawablePainter`: значки в Android бывают
 * адаптивными (`AdaptiveIconDrawable`), и в Compose они рисуются напрямую не
 * везде. Один раз растеризовать 40 точек дешевле, чем разбираться, какой из
 * видов значка пришёл.
 */
@Composable
private fun AppIcon(app: InstalledApp) {
    val painter = remember(app.packageName) {
        app.icon?.let { drawable ->
            runCatching { BitmapPainter(drawable.toBitmap(SIZE, SIZE).asImageBitmap()) }.getOrNull()
        }
    }
    if (painter != null) {
        Image(painter = painter, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        // Приложение без значка — не повод не показать его строку.
        Spacer(modifier = Modifier.size(40.dp))
    }
}

/** Растр значка. Сорок точек в плотности mdpi хватает на строку списка. */
private const val SIZE = 128
