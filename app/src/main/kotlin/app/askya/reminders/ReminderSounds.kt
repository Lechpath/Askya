package app.askya.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.askya.echo.EchoLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Мелодия напоминания: чем звонить и как это называется.
 *
 * [uri] пусто — обычный звук напоминания, тот, что телефон играет по умолчанию.
 * Хранится и показывается [title] — человек выбирал название, а не ссылку.
 */
data class ReminderSound(val uri: String?, val title: String)

/**
 * Что можно поставить на напоминание: звуки телефона и своя музыка.
 *
 * Своя музыка берётся из той же [EchoLibrary], по которой играет AskyaEcho:
 * второй список тех же песен пришлось бы держать в согласии с первым, а он уже
 * прочитан и разобран. Системные мелодии — из `RingtoneManager`: будильники и
 * звуки уведомлений, то есть ровно то, что телефон и так считает пригодным для
 * звонка.
 *
 * Читается по открытию выбора, не заранее: список песен на телефоне длинный и
 * до того, как о мелодии спросили, не нужен ни разу.
 */
object ReminderSounds {

    /** Обычный звук напоминания — то, чем оно звучало всегда. */
    val Default = ReminderSound(uri = null, title = "Обычный звук")

    /** Мелодии телефона: будильники и звуки уведомлений, без повторов. */
    suspend fun system(context: Context): List<ReminderSound> = withContext(Dispatchers.IO) {
        val types = listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION)
        types.flatMap { ringtones(context, it) }.distinctBy { it.uri }
    }

    /**
     * Музыка с телефона. Без разрешения на аудио — пусто: спрашивать его молча
     * неоткуда, это делает сам выбор мелодии.
     */
    suspend fun music(context: Context): List<ReminderSound> {
        if (!hasMusicAccess(context)) return emptyList()
        return EchoLibrary.load(context).map { track ->
            ReminderSound(uri = track.uri, title = track.title)
        }
    }

    private fun ringtones(context: Context, type: Int): List<ReminderSound> {
        val found = mutableListOf<ReminderSound>()

        // Список мелодий читает чужой провайдер, и на части телефонов он
        // отвечает отказом. Своя мелодия — не то, ради чего стоит падать:
        // тогда остаётся обычный звук и музыка.
        runCatching {
            val manager = RingtoneManager(context).apply { setType(type) }
            manager.cursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val position = cursor.position
                    val uri = manager.getRingtoneUri(position) ?: continue
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                    found += ReminderSound(uri.toString(), title)
                }
            }
        }

        return found
    }

    /** Куда звонить, если мелодию не выбирали. */
    fun uriOf(sound: String?): Uri =
        sound?.let(Uri::parse) ?: Settings.System.DEFAULT_NOTIFICATION_URI

    /**
     * Разрешение на музыку — то же, что у AskyaEcho: с Android 13 у аудио своё,
     * до неё общее чтение хранилища.
     */
    fun musicPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasMusicAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, musicPermission()) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Проба мелодии: как она прозвучит, слышно сразу при выборе.
 *
 * Играет по-будильничьи ([AudioAttributes.USAGE_ALARM]) — тем же путём, каким
 * зазвучит само напоминание. Значит, и проба слышна в беззвучном режиме: иначе
 * выбор мелодии в тишине выглядел бы как поломка.
 *
 * Проигрыватель один на всё приложение: вторая проба обрывает первую — две
 * песни разом это не выбор, а каша.
 */
object ReminderSoundPreview {

    private var player: MediaPlayer? = null

    fun play(context: Context, sound: String?) {
        stop()

        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context.applicationContext, ReminderSounds.uriOf(sound))
                setOnCompletionListener { ReminderSoundPreview.stop() }
                prepare()
                start()
            }
        }.onFailure { stop() }
    }

    fun stop() {
        val current = player ?: return
        player = null
        runCatching {
            current.stop()
            current.release()
        }
    }
}
