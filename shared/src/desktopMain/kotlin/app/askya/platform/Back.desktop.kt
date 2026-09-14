package app.askya.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    val handler = remember { DesktopBack.Handler { current() } }
    DisposableEffect(enabled) {
        if (enabled) DesktopBack.push(handler)
        onDispose { DesktopBack.remove(handler) }
    }
}

/**
 * Стопка «назад» Windows-версии. Окно Askya ловит Esc и отдаёт его верхнему
 * обработчику — последнему открытому окну или карточке, как это делает жест
 * «назад» на телефоне. Никого нет — Esc уходит навигации: шаг назад по
 * экранам (см. `Main.kt`).
 */
object DesktopBack {
    fun interface Handler {
        fun back()
    }

    private val stack = ArrayList<Handler>()

    fun push(handler: Handler) {
        stack.remove(handler)
        stack.add(handler)
    }

    fun remove(handler: Handler) {
        stack.remove(handler)
    }

    /** Отдать «назад» верхнему; `false` — обработчиков нет. */
    fun back(): Boolean {
        val top = stack.lastOrNull() ?: return false
        top.back()
        return true
    }
}
