package app.askya.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Высота экрана — на телефоне всего экрана, на компьютере окна Askya.
 * Нужна тем, кто меряет себя долей экрана: превью картинки не выше половины.
 */
@Composable
expect fun screenHeight(): Dp

/**
 * Светлые или тёмные значки системных панелей. Есть только у телефона —
 * у окна Windows панелей поверх страницы нет, и там это ничего не делает.
 */
@Composable
expect fun SystemBarIcons(dark: Boolean)
