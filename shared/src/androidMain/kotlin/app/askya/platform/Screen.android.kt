package app.askya.platform

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Composable
actual fun screenHeight(): Dp = LocalConfiguration.current.screenHeightDp.dp

/**
 * Свет значков под системными панелями на время экрана — и обратно на выходе:
 * раздел меняет свет, а уходя, возвращает всё как было.
 */
@Composable
actual fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current

    DisposableEffect(view, dark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLightStatus = controller?.isAppearanceLightStatusBars
        val wasLightNavigation = controller?.isAppearanceLightNavigationBars

        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark

        onDispose {
            wasLightStatus?.let { controller?.isAppearanceLightStatusBars = it }
            wasLightNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
}
