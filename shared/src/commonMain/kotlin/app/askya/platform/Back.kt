package app.askya.platform

import androidx.compose.runtime.Composable

/**
 * «Назад» — жест и кнопка телефона, клавиша Esc у компьютера.
 *
 * Своё объявление поверх `BackHandler` из Activity: окна Askya закрываются
 * «назад» на обеих системах, а Activity есть только у телефона. Там это он и
 * есть; у Windows-версии — стопка обработчиков, верхний из которых получает
 * Esc (см. `DesktopBack`).
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
