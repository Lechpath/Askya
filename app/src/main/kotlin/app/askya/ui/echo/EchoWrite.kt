package app.askya.ui.echo

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.askya.echo.Track
import kotlinx.coroutines.launch

/**
 * Правка чужого файла: сперва право, потом само дело.
 *
 * Музыка на телефоне принадлежит не Askya, а системе, и переписать её молча
 * приложение не вправе — ровно как и удалить (см. [rememberTrackRemover]).
 * Разница в том, что для удаления система показывает своё окно **до** попытки,
 * а для правки — **после**: попробовал, получил отказ, показал окно, повторил.
 *
 * Поэтому дело здесь передаётся замыканием, а не выполняется на месте: между
 * первой попыткой и второй стоит системное окно с ответом, приходящим отдельным
 * событием, и к тому времени о том, что человек хотел сделать, вспомнить некому.
 *
 * Три ветки — те же три договора с системой, что и у удаления. С Android 11
 * право просится сразу на список файлов (`createWriteRequest`) и остаётся за
 * приложением, поэтому второй раз тот же файл не спрашивают. На Android 10
 * система прикладывает окно к самому отказу ([RecoverableSecurityException]).
 * Ниже — старое разрешение на запись, и другого способа нет.
 *
 * [onDone] зовётся ровно один раз на каждое дело: `true` — получилось,
 * `false` — отказали или не вышло. Молчание было бы хуже отказа: человек
 * смотрит на список, в котором ничего не изменилось, и не знает почему.
 */
@Composable
fun rememberFileWriter(onDone: (Boolean) -> Unit): (List<Track>, suspend () -> Boolean) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Дело ждёт ответа системного окна: результат приходит отдельным событием,
    // и к тому времени о нём уже некому вспомнить.
    var pending by remember { mutableStateOf<(suspend () -> Boolean)?>(null) }

    val again: () -> Unit = {
        val work = pending
        pending = null
        if (work == null) {
            onDone(false)
        } else {
            scope.launch { onDone(runCatching { work() }.getOrDefault(false)) }
        }
    }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            again()
        } else {
            pending = null
            onDone(false)
        }
    }

    val askWrite = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            again()
        } else {
            pending = null
            onDone(false)
        }
    }

    return { tracks, work ->
        scope.launch {
            try {
                onDone(work())
            } catch (denied: SecurityException) {
                pending = work
                val uris = tracks.map { Uri.parse(it.uri) }
                val patch = (denied as? RecoverableSecurityException)
                    ?.userAction
                    ?.actionIntent
                    ?.intentSender

                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        val request = MediaStore.createWriteRequest(context.contentResolver, uris)
                        consent.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }

                    patch != null -> consent.launch(IntentSenderRequest.Builder(patch).build())

                    else -> askWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            } catch (failure: Exception) {
                pending = null
                onDone(false)
            }
        }
    }
}
