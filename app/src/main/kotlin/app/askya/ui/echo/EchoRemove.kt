package app.askya.ui.echo

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.askya.echo.Track

/**
 * Удаление песни с телефона.
 *
 * Файл лежит не у Askya, а у системы, и убрать его молча приложение не вправе:
 * начиная с Android 11 удаление чужого файла из `MediaStore` идёт только через
 * системное окно — человек видит, что именно исчезнет, и подтверждает это уже
 * не нам, а телефону. На Android 10 то же самое приходит ответом на попытку
 * ([RecoverableSecurityException]); до неё — старым разрешением на запись.
 *
 * Три ветки — не прихоть: это три разных договора приложения с системой за
 * последние годы, и обойтись одной из них нельзя, пока в minSdk стоит 26.
 *
 * Возвращает то, что нужно позвать с дорожкой; [onRemoved] случится, только
 * если файл и правда удалён, — на нём и висит уборка следов (плейлисты,
 * избранное, очередь плеера).
 */
@Composable
fun rememberTrackRemover(onRemoved: (Track) -> Unit): (Track) -> Unit {
    val context = LocalContext.current

    // Дорожка ждёт ответа системного окна: результат приходит отдельным
    // событием, и к тому времени о ней уже некому вспомнить.
    var pending by remember { mutableStateOf<Track?>(null) }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val track = pending
        pending = null
        if (result.resultCode != Activity.RESULT_OK || track == null) return@rememberLauncherForActivityResult

        // На Android 11+ система удалила файл сама, ещё до ответа. На 10 она
        // лишь выдала право — удалять всё равно нам.
        val gone = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || erase(context, track)
        if (gone) onRemoved(track)
    }

    val askWrite = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val track = pending
        pending = null
        if (granted && track != null && erase(context, track)) onRemoved(track)
    }

    return { track ->
        pending = track
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val request = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(Uri.parse(track.uri)),
                )
                consent.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val outcome = runCatching { erase(context, track) }
                val ask = outcome.exceptionOrNull() as? RecoverableSecurityException
                when {
                    ask != null -> consent.launch(
                        IntentSenderRequest.Builder(
                            ask.userAction.actionIntent.intentSender,
                        ).build(),
                    )

                    outcome.getOrDefault(false) -> {
                        pending = null
                        onRemoved(track)
                    }

                    else -> pending = null
                }
            }

            canWrite(context) -> {
                pending = null
                if (erase(context, track)) onRemoved(track)
            }

            else -> askWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}

/**
 * Спросит ли система сама, прежде чем удалить.
 *
 * С Android 11 окно с именем файла показывает она, и своё «точно удалить?»
 * перед ним было бы вторым вопросом об одном и том же. На версиях постарше
 * такого окна нет, и спросить обязана Askya — иначе файл исчезнет от одного
 * касания.
 */
fun systemAsksBeforeDelete(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

private fun erase(context: Context, track: Track): Boolean =
    context.contentResolver.delete(Uri.parse(track.uri), null, null) > 0

private fun canWrite(context: Context): Boolean = ContextCompat.checkSelfPermission(
    context,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
) == PackageManager.PERMISSION_GRANTED
