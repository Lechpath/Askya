package app.askya.ui.echo

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.echo.EchoService
import app.askya.echo.REVERB_NAMES
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.Sunset
import kotlin.math.roundToInt

/**
 * Настройки AskyaEcho — карточка поверх плеера.
 *
 * То, что в плеерах обычно лежит под шестерёнкой и чего Echo до сих пор не
 * умел: звук (бас, объём, догромкость, зал), скорость, затухание, пауза на
 * выдернутых наушниках и таймер сна.
 *
 * Всё это делает сам Android — системными эффектами на сессии плеера, — и
 * никаких сторонних движков ради этого не появилось: то же самое звучание
 * тянуло бы за собой мегабайты чужой библиотеки.
 *
 * Ползунки здесь такие же, как полосы эквалайзера: линия, засветка и точка.
 * Одно и то же движение пальцем во всём разделе — и ни одной детали Material
 * поверх ночной палитры.
 *
 * Чего телефон не умеет — того здесь и нет: набор `AudioEffect` у каждого
 * производителя свой, и мёртвый ползунок хуже отсутствующего.
 */
@Composable
fun EchoSettingsCard(onDismiss: () -> Unit, onEqualizer: () -> Unit, onLab: () -> Unit) {
    val container = appContainer()
    val preferences = container.echoPreferences
    val effects = container.echoEffects
    val player = container.echoPlayer

    val context = LocalContext.current

    val settings by preferences.settings.collectAsStateWithLifecycle(initialValue = preferences.state.value)
    val available by effects.state.collectAsStateWithLifecycle()
    val playing by player.state.collectAsStateWithLifecycle()

    // Право на микрофон — то, чем Android меряет чтение звука своей же
    // сессии. Спрашивается здесь и только здесь: это единственное место, где
    // человек сам говорит, что хочет видеть вспышки.
    var mic by remember { mutableStateOf(hasMic(context)) }
    val askMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        mic = allowed
        // Отказ не выключает саму настройку: обложка продолжит дышать ровно, а
        // разрешение можно будет дать позже.
        if (allowed) preferences.setPulse(true)
    }

    // Что телефон умеет, спрашивается при открытии карточки: до неё этот
    // список никому не нужен.
    LaunchedEffect(Unit) { effects.probe() }

    EchoCard(
        title = "Настройки",
        subtitle = "Звук, воспроизведение, сон",
        onDismiss = onDismiss,
        width = 0.94f,
        height = 0.86f,
    ) {
        // Прокрутка забирает себе то, что осталось от карточки, а не всё:
        // ниже стоит панель играющего, и без доли ей не досталось бы места.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .fadingVerticalScroll()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Шторка стоит первой: это не украшение звука, а единственная
            // кнопка плеера за пределами приложения, и человек, который сюда
            // зашёл, чаще всего зашёл именно из-за неё.
            EchoGroup("Шторка") { ShadeLine() }

            EchoGroup("Звук") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onEqualizer)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = Sunset,
                        modifier = Modifier.width(24.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = "Equalizer",
                            style = MaterialTheme.typography.titleSmall,
                            color = NightInk,
                        )
                        Text(
                            text = settings.preset.ifBlank { "Полосы по вкусу и готовые кривые" },
                            style = MaterialTheme.typography.bodySmall,
                            color = NightMuted,
                        )
                    }
                }

                if (available.bass) {
                    Level(
                        label = "Бас",
                        about = "Подъём низов",
                        value = settings.bass / 1000f,
                        caption = percent(settings.bass, 1000),
                        onChange = { preferences.setBass((it * 1000).roundToInt()) },
                    )
                }
                if (available.surround) {
                    Level(
                        label = "Объём",
                        about = "Шире стереобаза — звук перестаёт сидеть в голове",
                        value = settings.surround / 1000f,
                        caption = percent(settings.surround, 1000),
                        onChange = { preferences.setSurround((it * 1000).roundToInt()) },
                    )
                }
                if (available.loudness) {
                    Level(
                        label = "Догромкость",
                        about = "Для тихих записей, когда громкости телефона мало",
                        value = settings.loudness / 1500f,
                        caption = "+${settings.loudness / 100} дБ",
                        onChange = { preferences.setLoudness((it * 1500).roundToInt()) },
                    )
                }
                if (available.reverb) {
                    Choice(
                        label = "Зал",
                        about = "Отражения вокруг звука — от сухого до арены",
                        options = REVERB_NAMES,
                        chosen = REVERB_NAMES.getOrElse(settings.reverb) { REVERB_NAMES.first() },
                        onChoose = { preferences.setReverb(REVERB_NAMES.indexOf(it)) },
                    )
                }

                Text(
                    text = "Сбросить звук",
                    style = MaterialTheme.typography.labelLarge,
                    color = NightMuted,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { preferences.resetSound() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            EchoGroup("Воспроизведение") {
                Choice(
                    label = "Скорость",
                    about = "Для лекций и подкастов, а не только для песен",
                    options = SPEEDS.map { speedLabel(it) },
                    chosen = speedLabel(settings.speed),
                    onChoose = { chosen ->
                        val rate = SPEEDS.first { speedLabel(it) == chosen }
                        preferences.setSpeed(rate)
                        player.setSpeed(rate)
                    },
                )
                Switch(
                    label = "Затухание",
                    about = "Пауза мягкая, а не обрывом на полуслове",
                    on = settings.fade,
                    onToggle = { preferences.setFade(!settings.fade) },
                )
                Switch(
                    label = "Пауза без наушников",
                    about = "Выдернули — музыка молчит, а не играет в динамик",
                    on = settings.pauseOnUnplug,
                    onToggle = { preferences.setPauseOnUnplug(!settings.pauseOnUnplug) },
                )
                Switch(
                    label = "Помнить последнее",
                    about = "Назавтра плеер откроется той же песней",
                    on = settings.resumeLast,
                    onToggle = { preferences.setResumeLast(!settings.resumeLast) },
                )
                Switch(
                    label = "Спрашивать на входе",
                    about = "Карточки: продолжить, плейлист, папка, исполнитель, альбом, жанр",
                    on = settings.askOnStart,
                    onToggle = { preferences.setAskOnStart(!settings.askOnStart) },
                )
            }

            // Лаборатория — единственное место в Echo, где файл меняется, а не
            // читается, и потому она стоит последней и отдельной строкой, а не
            // среди выключателей звука. Главный вход у неё другой — долгое
            // нажатие на вкладку; здесь она на случай, если о нём не знают.
            EchoGroup("Файлы") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onLab)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Science,
                        contentDescription = null,
                        tint = Sunset,
                        modifier = Modifier.width(24.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = "Лаборатория",
                            style = MaterialTheme.typography.titleSmall,
                            color = NightInk,
                        )
                        Text(
                            text = "Переименовать, перенести, обрезать, склеить",
                            style = MaterialTheme.typography.bodySmall,
                            color = NightMuted,
                        )
                    }
                }
            }

            EchoGroup("Обложка") {
                Switch(
                    label = "Пульс обложки",
                    about = when {
                        !settings.pulse -> "Вспышки цветами обложки в ритм музыки"
                        mic -> "Вспышки цветами обложки в ритм музыки"
                        // Прямо о том, почему плеер просит микрофон: иначе
                        // просьба выглядит подслушиванием.
                        else -> "Ритм читается со звука плеера, а это Android " +
                            "считает микрофоном. Без разрешения обложка просто дышит"
                    },
                    on = settings.pulse,
                    // Пока разрешения нет, слово справа зовёт его дать, а не
                    // предлагает выключить то, что и так не работает.
                    caption = if (settings.pulse && !mic) "Разрешить" else null,
                    onToggle = {
                        when {
                            settings.pulse && !mic -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                            else -> preferences.setPulse(!settings.pulse)
                        }
                    },
                )
            }

            EchoGroup("Таймер сна") {
                Choice(
                    label = "Уснуть через",
                    about = sleepAbout(playing.sleepAt),
                    options = SLEEP.map { sleepLabel(it) },
                    chosen = sleepLabel(currentSleep(playing.sleepAt)),
                    onChoose = { chosen ->
                        player.sleepAfter(SLEEP.first { sleepLabel(it) == chosen })
                    },
                )
            }
        }

        // Играющее — последней строкой карточки, под прокруткой, а не поверх
        // неё: настройки правят на слух, и то, на чём их слышно, должно быть
        // под рукой, ничего собой не закрывая. См. [EchoBar].
        EchoBar()
    }
}

/**
 * Ползунок Echo: подпись, линия с точкой и число справа.
 *
 * Тот же рисунок, что у полосы эквалайзера, только лёжа: во всём разделе один
 * жест и одна форма, и ни одного чужого ползунка Material.
 */
@Composable
private fun Level(
    label: String,
    about: String,
    value: Float,
    caption: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = NightInk,
                )
                Text(
                    text = about,
                    style = MaterialTheme.typography.bodySmall,
                    color = NightMuted,
                )
            }
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = if (value > 0f) Sunset else NightMuted,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(label) {
                    detectHorizontalDragGestures { change, _ ->
                        onChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(label) {
                    detectTapGestures { tap ->
                        onChange((tap.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .drawBehind {
                    val y = size.height / 2
                    val x = size.width * value.coerceIn(0f, 1f)
                    val stroke = 3.dp.toPx()
                    drawLine(
                        color = NightBorder,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                    )
                    drawLine(
                        color = Sunset,
                        start = Offset(0f, y),
                        end = Offset(x, y),
                        strokeWidth = stroke,
                    )
                    drawCircle(color = Sunset, radius = 5.dp.toPx(), center = Offset(x, y))
                },
        )
    }
}

/** Выбор из нескольких слов: скорость, зал, длина сна. */
@Composable
private fun Choice(
    label: String,
    about: String,
    options: List<String>,
    chosen: String,
    onChoose: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = NightInk,
        )
        Text(
            text = about,
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                EchoPill(label = option, chosen = option == chosen, onClick = { onChoose(option) })
            }
        }
    }
}

/**
 * Выключатель — словом, а не рычажком.
 *
 * Тот же язык, что у кнопок плеера: включённое горит закатом и подчёркнуто, и
 * это видно краем глаза, не вглядываясь в положение рычажка.
 */
@Composable
private fun Switch(
    label: String,
    about: String,
    on: Boolean,
    onToggle: () -> Unit,
    caption: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = NightInk,
            )
            Text(
                text = about,
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
            )
        }
        Text(
            text = caption ?: if (on) "Вкл" else "Выкл",
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            color = if (on) Sunset else NightMuted,
            textDecoration = if (on) TextDecoration.Underline else null,
        )
    }
}

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** Длины сна. `null` — «выключить»: он стоит первым, чтобы снять его сразу. */
private val SLEEP = listOf<Int?>(null, 15, 30, 45, 60, 90)

/** Подписи скорости заданы словами: «%.2f» на русской раскладке даёт запятую. */
private fun speedLabel(rate: Float): String = when (rate) {
    0.75f -> "0.75×"
    1.25f -> "1.25×"
    1.5f -> "1.5×"
    2f -> "2×"
    else -> "1×"
}

private fun sleepLabel(minutes: Int?): String = minutes?.let { "$it мин" } ?: "Выкл"

/** Сколько осталось до сна — из будущего момента обратно в минуты. */
private fun currentSleep(sleepAt: Long?): Int? {
    val left = sleepAt?.minus(SystemClock.elapsedRealtime()) ?: return null
    if (left <= 0) return null
    val minutes = (left / 60_000f).roundToInt()
    return SLEEP.filterNotNull().minByOrNull { kotlin.math.abs(it - minutes) }
}

private fun sleepAbout(sleepAt: Long?): String {
    val left = sleepAt?.minus(SystemClock.elapsedRealtime())
    return if (left != null && left > 0) {
        "Музыка стихнет через ${(left / 60_000) + 1} мин"
    } else {
        "Музыка стихнет сама и не разбудит"
    }
}

private fun percent(value: Int, max: Int): String = "${(value * 100) / max}%"

/** Дано ли право на микрофон — им читается спектр играющего. */
private fun hasMic(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

/**
 * Управление плеером за пределами приложения — и почему его может не быть.
 *
 * ## Зачем эта строка
 *
 * Карточку плеера в шторке и в центре управления рисует система — из
 * уведомления и сессии, которые держит [app.askya.echo.EchoService]. Всё это
 * заводится само и не спрашивает ни о чём, но упирается в одно чужое условие:
 * разрешение показывать уведомления. С Android 13 его спрашивают, отказ
 * запоминают навсегда, а на MIUI новому приложению его нередко не дают вовсе
 * — и тогда музыка играет, а остановить её из кармана нечем.
 *
 * Хуже всего, что молча: приложение выглядит исправным, шторка пуста, и
 * связать одно с другим человеку не по чему. Поэтому здесь стоит строка,
 * которая говорит правду про нынешнее состояние и ведёт туда, где его
 * меняют, — в системные настройки уведомлений самой Askya. Своего окна
 * «дайте разрешение» второй раз не открыть: отказавшему дважды система его
 * больше не показывает.
 *
 * ## Что она проверяет
 *
 * Два условия, и оба чужие: разрешение на уведомления и то, не выключены ли
 * они у приложения целиком (или у канала плеера — его человек мог погасить
 * долгим нажатием на само уведомление). Спрашивается это при каждом открытии
 * карточки, а не однажды: настройки меняют снаружи, и запомненный ответ
 * устарел бы к следующему разу.
 */
@Composable
private fun ShadeLine() {
    val context = LocalContext.current
    // Ответ пересчитывается на каждый показ карточки: разрешение могли дать в
    // системных настройках, куда эта же строка и уводит.
    val allowed = shadeAllowed(context)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { openNotificationSettings(context) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Notifications,
            contentDescription = null,
            tint = if (allowed) Sunset else NightMuted,
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = if (allowed) "Плеер в шторке включён" else "Плеера в шторке нет",
                style = MaterialTheme.typography.titleSmall,
                color = NightInk,
            )
            Text(
                text = if (allowed) {
                    "Играющая песня показывается в шторке и в центре управления: " +
                        "пауза и «дальше» под рукой, не открывая Askya. Нажми, чтобы " +
                        "открыть системные настройки уведомлений."
                } else {
                    "Askya не разрешено показывать уведомления — а карточку плеера " +
                        "система рисует именно из него. Нажми и включи уведомления " +
                        "Askya: музыка тогда появится в шторке и на экране блокировки."
                },
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
            )
        }
    }
}

/**
 * Появится ли карточка плеера в шторке.
 *
 * Разрешение на уведомления и включённость канала — оба ответа нужны: право
 * дают один раз, а канал гасят долгим нажатием на само уведомление, и второе
 * встречается чаще первого.
 */
private fun shadeAllowed(context: android.content.Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    val channel = runCatching { manager.getNotificationChannel(EchoService.CHANNEL) }.getOrNull()
        ?: return true
    return channel.importance != NotificationManager.IMPORTANCE_NONE
}

/**
 * Открыть системные настройки уведомлений Askya.
 *
 * Канал не называется: страница приложения показывает и общий переключатель, и
 * список каналов, а прицельная страница канала на выключенных уведомлениях
 * приложения открывается пустой — и человек видит переключатель, который ни на
 * что не влияет.
 *
 * Не открылось — не беда: страница настроек есть не на всякой прошивке, и
 * ронять из-за неё плеер незачем.
 */
private fun openNotificationSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

