package app.askya.ui.video

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.askya.app.androidContainer
import app.askya.video.Clip
import kotlinx.coroutines.launch

/**
 * Удаление роликов с телефона — из карточки ролика и из лаборатории.
 *
 * Договор с системой тот же, что у песен в Echo ([app.askya.ui.echo.rememberTrackRemover]):
 * файл принадлежит не Askya, и с Android 11 стереть его можно только через
 * системное окно, где человек видит, что именно исчезнет. На Android 10 окно
 * приходит ответом на попытку, ниже — старым разрешением на запись.
 *
 * Отличие одно — роликов бывает несколько: лаборатория берёт в работу
 * отмеченное пачкой. С Android 11 система спрашивает про всю пачку одним окном;
 * на Android 10 окно выдаёт право на один файл, и тогда пачка идёт по одному —
 * после каждого согласия удаление продолжается с того, на чём споткнулось.
 *
 * Следы уходят здесь же, а не у зовущего: строки плейлистов, место остановки и
 * своё имя. Ссылка на стёртый файл — строка, которая молча не играет, и
 * убирать её должен тот, кто стёр, а не каждый, кто позвал.
 *
 * [onRemoved] зовётся только с тем, что действительно стёрто.
 */
@Composable
fun rememberClipRemover(onRemoved: (List<String>) -> Unit): (List<Clip>) -> Unit {
    val context = LocalContext.current
    val container = androidContainer()
    val scope = rememberCoroutineScope()

    // Пачка ждёт ответа системного окна: результат приходит отдельным
    // событием, и к тому времени о ней уже некому вспомнить.
    var pending by remember { mutableStateOf<List<String>>(emptyList()) }
    // Что уже стёрто по дороге — на Android 10 пачка идёт в несколько окон.
    var gone by remember { mutableStateOf<List<String>>(emptyList()) }

    fun finish(removed: List<String>) {
        pending = emptyList()
        gone = emptyList()
        if (removed.isEmpty()) return
        removed.forEach { uri ->
            container.videoPreferences.erased(uri)
            scope.launch { container.videoRepository.forget(uri) }
        }
        onRemoved(removed)
    }

    // Отложенная ссылка на «продолжить»: окно согласия объявлено раньше, чем
    // то, что оно продолжает.
    var proceed: (List<String>) -> Unit = {}

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val waiting = pending
        if (result.resultCode != Activity.RESULT_OK || waiting.isEmpty()) {
            finish(gone)
            return@rememberLauncherForActivityResult
        }
        // На Android 11+ система стёрла всё сама, ещё до ответа. На 10 она
        // выдала право на один файл — стирать всё равно нам, и дальше по пачке.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            finish(gone + waiting)
        } else {
            proceed(waiting)
        }
    }

    val askWrite = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val waiting = pending
        if (granted) finish(waiting.filter { erase(context, it) }) else finish(emptyList())
    }

    /** Android 10: по одному, пока система не попросит согласия на очередной. */
    proceed = { uris ->
        var at = 0
        var asked = false
        while (at < uris.size) {
            val uri = uris[at]
            val outcome = runCatching { erase(context, uri) }
            val ask = outcome.exceptionOrNull() as? RecoverableSecurityException
            if (ask != null) {
                pending = uris.drop(at)
                consent.launch(IntentSenderRequest.Builder(ask.userAction.actionIntent.intentSender).build())
                asked = true
                break
            }
            if (outcome.getOrDefault(false)) gone = gone + uri
            at++
        }
        if (!asked) finish(gone)
    }

    return { clips ->
        val uris = clips.map { it.uri }.distinct()
        gone = emptyList()
        when {
            uris.isEmpty() -> Unit

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Системное окно берёт только ссылки `MediaStore`. Файл,
                // открытый системным выбором, стирается у своего хозяина
                // напрямую — если тот позволит.
                val (media, other) = uris.partition(::isMedia)
                gone = other.filter { runCatching { erase(context, it) }.getOrDefault(false) }
                if (media.isEmpty()) {
                    finish(gone)
                } else {
                    pending = media
                    val request = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        media.map(Uri::parse),
                    )
                    consent.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> proceed(uris)

            canWrite(context) -> finish(uris.filter { erase(context, it) })

            else -> {
                pending = uris
                askWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}

private fun isMedia(uri: String): Boolean = Uri.parse(uri).authority == MediaStore.AUTHORITY

private fun erase(context: Context, uri: String): Boolean {
    val parsed = Uri.parse(uri)
    if (!isMedia(uri) && DocumentsContract.isDocumentUri(context, parsed)) {
        return DocumentsContract.deleteDocument(context.contentResolver, parsed)
    }
    return context.contentResolver.delete(parsed, null, null) > 0
}

private fun canWrite(context: Context): Boolean = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
) == PackageManager.PERMISSION_GRANTED
