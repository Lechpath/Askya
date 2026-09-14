package app.askya.platform

import androidx.compose.runtime.Composable

/** Просьба о разрешении системы; ответ приходит в `onResult` того, кто её завёл. */
fun interface PermissionAsk {
    fun launch(permission: String)
}

/**
 * Окно системы «разрешить?». На телефоне это `RequestPermission`; у Windows
 * таких разрешений нет, и ответ приходит сразу — «да».
 */
@Composable
expect fun rememberPermissionAsk(onResult: (Boolean) -> Unit): PermissionAsk

/**
 * Разрешение на уведомления, если его вообще надо спрашивать: на Android 13
 * и новее — да, на старших Android и на Windows — `null`, спрашивать нечего.
 */
expect val NOTIFICATIONS_PERMISSION: String?
