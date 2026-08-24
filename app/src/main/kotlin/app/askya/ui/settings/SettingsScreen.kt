package app.askya.ui.settings

import androidx.compose.runtime.Composable
import app.askya.ui.components.EmptyState
import app.askya.ui.components.ScreenScaffold

/**
 * Настройки: пока пустые.
 *
 * «Обо мне» и «Ключ» отсюда убраны вместе с разговором о себе и моделью,
 * список дел переехал в AskyaDay, напоминания — туда же, под колокольчик в
 * шапке. Строчка «Напоминания» была вторым входом в один и тот же экран, а
 * второй вход в раздел — это лишний вопрос «а тут они те же самые?».
 *
 * Экран остаётся: в нём появятся настройки, которые про приложение целиком, а
 * не про день или заметку. Пустой он честнее, чем убранный: раздел меню,
 * который исчезает и возвращается от версии к версии, сбивает сильнее.
 */
@Composable
fun SettingsScreen(onOpenMenu: () -> Unit) {
    ScreenScaffold(title = "Настройки", onNavigationClick = onOpenMenu) {
        EmptyState(
            title = "Пока настраивать нечего",
            hint = "Напоминания живут в AskyaDay, под колокольчиком в шапке.",
        )
    }
}
