package app.askya.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun screenHeight(): Dp = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.height.toDp()
}

@Composable
actual fun SystemBarIcons(dark: Boolean) = Unit
