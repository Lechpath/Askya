package app.askya.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.rememberNavController
import app.askya.app.LocalAppContainer
import app.askya.data.account.Gate
import app.askya.ui.account.LockScreen
import app.askya.platform.DesktopBack
import app.askya.platform.Notices
import app.askya.resources.Res
import app.askya.resources.ic_flower
import app.askya.ui.navigation.AskyaApp
import app.askya.ui.theme.AskyaTheme
import app.askya.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import java.io.File

/**
 * Askya для Windows: одно окно и значок у часов.
 *
 * Закрытое окно Askya не завершает, а прячет к часам: напоминания звонят,
 * пока она запущена (см. [DesktopAlarms]). Выйти совсем — из меню значка.
 * Запущенная при входе в Windows ([DesktopAutostart]) — сразу у часов, без окна.
 */
fun main(args: Array<String>) {
    val home = DesktopContainer.defaultHome()
    // Одна Askya на компьютер: две копии открыли бы одну базу вдвоём, и
    // напоминания звонили бы дважды. Вторая просит первую показать окно и
    // уходит — так Askya из «Пуска» открывается и тогда, когда уже сидит у
    // часов с утра.
    if (!holdSingleInstance(home, patient = RESTARTED in args)) {
        if (!askFirstToShow(home)) {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "Askya уже открыта — её значок у часов, внизу справа.",
                "Askya",
                javax.swing.JOptionPane.INFORMATION_MESSAGE,
            )
        }
        return
    }
    File(home, SHOW).delete()
    val container = DesktopContainer(home)
    // Прочитанный в прошлый раз слепок встаёт на место раньше, чем откроется
    // база: под открытым соединением её не подменить.
    container.snapshots.applyPending()

    application {
        var shown by remember { mutableStateOf(DesktopAutostart.AT_LOGIN !in args) }
        // Растёт на каждую просьбу показаться: окно, уже открытое, но под
        // другими, тоже должно выйти наверх, а не только стать видимым.
        var raise by remember { mutableStateOf(0) }
        val tray = rememberTrayState()
        val flower = painterResource(Res.drawable.ic_flower)

        LaunchedEffect(container) {
            container.alarms.notify = { title, text ->
                tray.sendNotification(Notification(title, text, Notification.Type.Info))
            }
            container.alarms.rescheduleAll()
            container.snapshots.finishImages()
            container.trash.purge()
        }

        LaunchedEffect(home) {
            val request = File(home, SHOW)
            while (true) {
                delay(300)
                if (withContext(Dispatchers.IO) { request.delete() }) {
                    shown = true
                    raise++
                }
            }
        }

        Tray(
            icon = flower,
            state = tray,
            tooltip = "Askya",
            onAction = { shown = true; raise++ },
            menu = {
                Item("Открыть Askya", onClick = { shown = true; raise++ })
                Separator()
                Item("Выйти", onClick = { exitApplication() })
            },
        )

        val windowState = rememberWindowState(
            size = DpSize(560.dp, 920.dp),
            position = WindowPosition(Alignment.Center),
        )
        val nav = rememberNavController()

        Window(
            onCloseRequest = { shown = false },
            visible = shown,
            state = windowState,
            title = "Askya",
            icon = flower,
            onPreviewKeyEvent = { event ->
                // Esc — «назад» телефона: сперва закрывается верхнее окно или
                // карточка, а когда их нет — шаг назад по экранам. С первого
                // экрана назад некуда: телефон бы вышел, а окну выходить по Esc
                // незачем — последний экран со стопки не снимается.
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    if (!DesktopBack.back() && nav.previousBackStackEntry != null) nav.popBackStack()
                    true
                } else {
                    false
                }
            },
        ) {
            window.minimumSize = java.awt.Dimension(420, 640)
            LaunchedEffect(raise) {
                if (raise == 0) return@LaunchedEffect
                windowState.isMinimized = false
                window.toFront()
            }
            // Замок отсчитывает от ухода окна: спрятанное к часам или
            // свёрнутое — ушло, показанное снова — вернулось. Просто окно
            // под другими уходом не считается: на него всё ещё смотрят.
            val away = !shown || windowState.isMinimized
            LaunchedEffect(away) {
                if (away) container.gate.wentAway() else container.gate.cameBack()
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                val settings by container.settings.settings
                    .collectAsState(initial = container.settings.state.value)
                val dark = when (settings.theme) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                AskyaTheme(dark = dark, palette = settings.palette, flower = settings.flower) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AskyaApp(
                            shell = rememberDesktopShell(
                                container = container,
                                // Слепок разложен: база и настройки встанут на
                                // место при следующем запуске, а этот
                                // заканчивается — Askya открывается уже с ним.
                                onRestored = { restartOrExit { exitApplication() } },
                            ),
                            navController = nav,
                        )
                        NoticeBar(modifier = Modifier.align(Alignment.BottomCenter))
                        val gate by container.gate.state.collectAsState()
                        // Esc на входе не делает ничего: окну уходить некуда,
                        // а открыть Askya мимо пароля он не должен.
                        if (gate != Gate.OPEN) LockScreen(onLeave = {})
                    }
                }
            }
        }
    }
}

/**
 * Строчка внизу окна вместо тоста телефона: короткое «не вышло» или «готово».
 * Сама уходит через три секунды.
 */
@Composable
private fun NoticeBar(modifier: Modifier = Modifier) {
    val notice by Notices.last.collectAsState()
    LaunchedEffect(notice?.id) {
        val id = notice?.id ?: return@LaunchedEffect
        delay(3_000)
        Notices.forget(id)
    }
    AnimatedVisibility(visible = notice != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            text = notice?.text.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.inverseSurface)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

/**
 * Открыть Askya заново — если её запустил установщик, он оставил путь к
 * себе (`jpackage.app-path`). Из-под Gradle такого пути нет, и тогда Askya
 * просто закрывается: откроют её уже со слепком.
 */
private fun restartOrExit(exit: () -> Unit) {
    System.getProperty("jpackage.app-path")?.let { launcher ->
        runCatching { ProcessBuilder(launcher, RESTARTED).start() }
    }
    exit()
}

/**
 * Замок на папку данных — на всё время работы. `false` — его держит другая
 * запущенная Askya. Отпускает его сама система, когда процесс кончается.
 *
 * Открытая заново после слепка ([patient]) ждёт замок несколько секунд: она
 * успевает спросить раньше, чем старая закрылась. Остальные спрашивают почти
 * сразу — иначе Askya из «Пуска» при уже запущенной показывалась бы с
 * трёхсекундной заминкой.
 */
private fun holdSingleInstance(home: File, patient: Boolean): Boolean {
    home.mkdirs()
    val channel = runCatching {
        java.io.RandomAccessFile(File(home, "askya.lock"), "rw").channel
    }.getOrNull() ?: return true
    repeat(if (patient) 30 else 3) {
        val lock = runCatching { channel.tryLock() }.getOrNull()
        if (lock != null) {
            heldLock = lock
            return true
        }
        Thread.sleep(100)
    }
    return false
}

/** Держится в поле, чтобы сборщик мусора не отпустил замок раньше времени. */
private var heldLock: java.nio.channels.FileLock? = null

/**
 * Попросить уже запущенную Askya показать окно: файл-просьба в папке данных,
 * который та забирает раз в треть секунды. `false` — за две секунды не
 * забрала (зависла или это вовсе не Askya держит замок), и тогда вторая копия
 * говорит словами, где искать первую.
 */
private fun askFirstToShow(home: File): Boolean {
    val request = File(home, SHOW)
    if (runCatching { request.writeText("1") }.isFailure) return false
    repeat(20) {
        Thread.sleep(100)
        if (!request.exists()) return true
    }
    request.delete()
    return false
}

/** Файл-просьба «покажи окно» — от второй копии Askya первой. */
private const val SHOW = "show-window"

/** Ключ запуска: Askya открыта заново самой собой после чтения слепка. */
private const val RESTARTED = "--restarted"
