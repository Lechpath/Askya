package app.askya.echo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.askya.data.entity.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Голосовая заметка, которая звучит сейчас, — то, что показывает карточка. */
data class Aside(
    val noteId: Long,
    val title: String,
    val durationMs: Long,
    val playing: Boolean,
)

/**
 * Echo в стороне: голосовая заметка, играющая поверх того, чем человек занят.
 *
 * ## Зачем отдельно от плеера
 *
 * Голосовую заметку слушают не так, как музыку. Музыку включают и уходят
 * заниматься своим; заметку включают **посреди** своего — стоя в списке дел,
 * в записи, в расписании, — чтобы вспомнить, что наговорили вчера. Уводить
 * ради двадцати секунд в раздел с занавесом, цветком и очередью значило бы
 * прерывать дело ради одной фразы.
 *
 * Поэтому заметка звучит без раздела, а над экраном на это время висит
 * карточка в три строки: что играет, полоска и кнопка. Кончилась заметка —
 * карточка ушла сама. Ничего закрывать не нужно: то, что кончилось, не
 * должно требовать внимания.
 *
 * ## Почему не через [EchoPlayer]
 *
 * Соблазн был: плеер уже есть, он один на приложение и умеет всё. Но у него
 * есть очередь, и очередь — это то, что человек собрал слушать. Проиграть
 * заметку плеером значит выбросить её на место собранного вечера, а заодно
 * подставить заметку под «повтор дорожки», «вперемешку» и скорость, которые
 * настраивали для музыки. Двадцать секунд голоса, зациклённые повтором, — это
 * не связь с Echo, а поломка.
 *
 * Связь с Echo здесь другая и настоящая: заметка **уступает и уступают ей**.
 * Играющая музыка на время заметки замолкает и потом сама возвращается —
 * слушать голос под музыку нельзя; звук берётся у системы фокусом, как это
 * делает и плеер, так что чужой проигрыватель тоже притихнет. И живёт эта
 * штука в контейнере рядом с плеером, а не в экране: заметка не должна
 * обрываться от того, что человек перешёл в другой раздел.
 *
 * ## Одна на всё приложение
 *
 * Вторая заметка обрывает первую и встаёт на её место. Две говорящие разом
 * головы не слушает никто, а карточек поверх экрана бывает одна.
 */
class EchoAside(private val context: Context, private val player: EchoPlayer) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow<Aside?>(null)
    val state: StateFlow<Aside?> = _state.asStateFlow()

    private var sound: MediaPlayer? = null

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Играла ли музыка, когда заметку включили.
     *
     * Помнится ради одного: вернуть её, когда заметка кончится. Человек, у
     * которого от голосовой заметки навсегда замолчала музыка, второй раз
     * заметку не включит.
     */
    private var musicWaits = false

    private val focusChange = AudioManager.OnAudioFocusChangeListener { change ->
        // Позвонили или заговорил чужой плеер — заметка не ставится на паузу,
        // а заканчивается совсем. Пауза оставила бы карточку висеть поверх
        // разговора, а вернуться к двадцати секундам проще, чем убрать её
        // потом руками.
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            close()
        }
    }

    private val request: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener(focusChange)
                .build()
        } else {
            null
        }

    /**
     * Включить заметку. Та же самая — это пауза-возобновление, как и у плеера:
     * нажатие на играющую строку значит «погоди», а не «начни сначала».
     */
    fun play(note: Note) {
        val uri = note.uri ?: return
        if (_state.value?.noteId == note.id && sound != null) {
            toggle()
            return
        }

        stopSound()

        // Музыка замолкает раньше, чем заговорит заметка: иначе первое слово
        // приходится на последний такт.
        musicWaits = player.state.value.playing
        if (musicWaits) player.pause()

        if (!takeFocus()) return

        val made = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(context, Uri.parse(uri))
                prepare()
                start()
            }
        }.getOrNull()

        if (made == null) {
            // Файла нет или он битый: карточка не показывается вовсе. Пустая
            // карточка с полоской, которая не движется, обещает звук, которого
            // не будет.
            giveBackFocus()
            resumeMusic()
            return
        }

        // Закрытие откладывается на следующий круг: освобождать плеер прямо
        // из его же обработчика конца — верный способ уронить его на части
        // устройств.
        made.setOnCompletionListener { handler.post { close() } }
        sound = made
        _state.value = Aside(
            noteId = note.id,
            title = note.title,
            // Длина у самого файла вернее записанной: у заметок, пришедших не
            // от диктофона, её в записи может не быть вовсе.
            durationMs = runCatching { made.duration.toLong() }.getOrDefault(note.durationMs),
            playing = true,
        )
    }

    fun toggle() {
        val going = sound ?: return
        val shown = _state.value ?: return
        if (going.isPlaying) {
            runCatching { going.pause() }
            _state.value = shown.copy(playing = false)
        } else {
            if (!takeFocus()) return
            runCatching { going.start() }
            _state.value = shown.copy(playing = true)
        }
    }

    /** Где сейчас — по запросу карточки, а не потоком: см. [EchoPlayer.position]. */
    fun position(): Long = runCatching { sound?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)

    /** Заметка кончилась или её закрыли. Карточка уходит, музыка возвращается. */
    fun close() {
        stopSound()
        _state.value = null
        giveBackFocus()
        resumeMusic()
    }

    private fun stopSound() {
        sound?.let { going -> runCatching { going.release() } }
        sound = null
    }

    private fun resumeMusic() {
        if (!musicWaits) return
        musicWaits = false
        player.resume()
    }

    private fun takeFocus(): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request?.let { audio.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(
                focusChange,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        }
        return granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun giveBackFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request?.let { audio.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(focusChange)
        }
    }
}
