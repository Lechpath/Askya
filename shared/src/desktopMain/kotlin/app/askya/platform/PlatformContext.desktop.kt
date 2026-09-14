package app.askya.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.net.URI

/**
 * У Windows-версии контекста нет: всё, что телефон спрашивает у системы через
 * него, она знает сама. Один объект на всё приложение — заместитель, чтобы
 * общий код оставался тем же.
 */
actual abstract class PlatformContext private constructor() {
    companion object Desktop : PlatformContext()
}

actual val LocalPlatformContext: ProvidableCompositionLocal<PlatformContext> =
    staticCompositionLocalOf { PlatformContext.Desktop }

actual fun PlatformContext.openStream(uri: String): InputStream? =
    runCatching { fileOf(uri)?.takeIf { it.isFile }?.inputStream() }.getOrNull()

actual fun PlatformContext.toast(text: String) {
    Notices.show(text)
}

/**
 * Файл по ссылке записи. Ссылки у Windows-версии — `file:/…`; голый путь тоже
 * принимается, а `content://` телефона, пришедший в Слепке, файлом не
 * является и даёт `null`.
 */
fun fileOf(uri: String?): File? {
    if (uri.isNullOrBlank()) return null
    return runCatching {
        when {
            uri.startsWith("file:") -> File(URI(uri))
            uri.contains("://") -> null
            else -> File(uri)
        }
    }.getOrNull()
}

/** Ссылка записи на файл — в том виде, в каком её хранит база. */
fun File.asUri(): String = toURI().toString()

/**
 * Строчка внизу окна вместо тоста: последняя сказанная и её номер. Номер
 * нужен, чтобы два одинаковых «не вышло» подряд показались дважды.
 */
object Notices {
    data class Notice(val id: Long, val text: String)

    private val _last = MutableStateFlow<Notice?>(null)
    val last: StateFlow<Notice?> = _last.asStateFlow()

    fun show(text: String) {
        _last.value = Notice((_last.value?.id ?: 0L) + 1, text)
    }

    fun forget(id: Long) {
        if (_last.value?.id == id) _last.value = null
    }
}
