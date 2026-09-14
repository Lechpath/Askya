package app.askya.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberPermissionAsk(onResult: (Boolean) -> Unit): PermissionAsk {
    val current by rememberUpdatedState(onResult)
    return remember { PermissionAsk { current(true) } }
}

actual val NOTIFICATIONS_PERMISSION: String? = null
