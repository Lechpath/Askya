package app.askya.platform

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
actual fun rememberPermissionAsk(onResult: (Boolean) -> Unit): PermissionAsk {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onResult,
    )
    return PermissionAsk { launcher.launch(it) }
}

actual val NOTIFICATIONS_PERMISSION: String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
