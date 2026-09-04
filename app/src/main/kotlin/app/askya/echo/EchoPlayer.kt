package app.askya.echo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.askya.data.preferences.EchoLastTrack
import app.askya.data.preferences.EchoPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Что делать, когда дорожка доиграла.
 *
 * Три состояния, а не выключатель: «по кругу» — это два разных желания.
 * Одному нужно, чтобы вечер не кончался вместе со списком, другому — чтобы
 * одна песня повторялась, пока не надоест.
 *
 * Ручного «дальше» это не касается: он в конце очереди возвращает к началу
 * при любом режиме, иначе кнопка выглядит сломанной. Режим управляет только
 * тем, что плеер делает сам.
 */
enum class EchoRepeat {
    /** Очередь доигрывает до конца и замолкает. */
    OFF,

    /** Очередь заходит на второй круг. */
    QUEUE,

    /** Дорожка играет заново. */
    TRACK,
    ;

    /** Следующий режим: кнопка одна, а состояний три. */
    fun next(): EchoRepeat = entries[(ordinal + 1) % entries.size]
}

/** Что играет и как — состояние плеера одним значением. */
data class EchoState(
    val track: Track? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Очередь обходится вперемешку. */
    val shuffle: Boolean = false,
    /** Что случится, когда дорожка кончится. */
    val repeat: EchoRepeat = EchoRepeat.OFF,
    /** Очередь в порядке обхода — то, что покажет список «дальше». */
    val queue: List<Track> = emptyList(),
    /** Место играющей дорожки в [queue]; -1 — её там нет. */
    val queueAt: Int = -1,
    /**
     * Когда плеер уснёт, по системным часам от загрузки. `null` — таймера нет.
     * Именно `elapsedRealtime`, а не время суток: перевод часов не должен
     * выключать музыку посреди песни.
     */
    val sleepAt: Long? = null,
)

/**
 * Плеер Echo — один на приложение.
 *
 * `MediaPlayer`, а не ExoPlayer: он входит в систему, а Media3 — это ещё
 * несколько мегабайт зависимости ради того же mp3. Для проигрывания файла с
 * телефона его хватает целиком.
 *
 * Живёт в контейнере, а не в модели экрана: музыка не должна замолкать от
 * того, что человек ушёл в другой раздел. По той же причине состояние отдаётся
 * потоком — на него подписывается и экран, и уведомление.
 *
 * Перемотка спрашивается у самого `MediaPlayer` по запросу экрана, а не
 * пишется потоком раз в секунду: секундный тик держал бы процессор занятым и
 * когда на плеер никто не смотрит.
 *
 * Приличия, которых ждут от плеера, живут здесь же: музыка уступает звонку и
 * чужому проигрывателю (фокус звука), замолкает, когда выдёргивают наушники, и
 * умеет уснуть по таймеру. Наружу — в шторку и в системный плеер — его
 * показывает [EchoService]; сам плеер о ней знает ровно одно: с началом дорожки
 * её нужно поднять. Всё это — не украшения, а то, из-за чего плеер
 * иначе выглядел бы сломанным: играть в динамик на весь автобус после
 * выпавшего наушника не должен никто.
 */
class EchoPlayer(
    private val context: Context,
    private val preferences: EchoPreferences,
) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Своя аудиосессия на весь плеер, а не та, что система выдаёт каждому
     * `MediaPlayer` заново: эквалайзер цепляется к сессии, и без общей его
     * настройки слетали бы на каждой смене дорожки.
     */
    val audioSessionId: Int = audio.generateAudioSessionId()

    private var player: MediaPlayer? = null
    private var queue: List<Track> = emptyList()

    /**
     * Порядок обхода очереди — номера дорожек в [queue], а не сами дорожки.
     *
     * Перемешивается именно он, а не очередь: список на экране должен
     * оставаться тем, каким его собрали, — иначе «вперемешку» переставляет
     * человеку папку, а не только порядок игры.
     */
    private var order: List<Int> = emptyList()

    /** Место в [order], а не в очереди: вперемешку это разные вещи. */
    private var cursor: Int = -1

    private var shuffling = false
    private var repeating = EchoRepeat.OFF

    private val handler = Handler(Looper.getMainLooper())

    /** Для того немногого, что плеер делает не сразу: чтения библиотеки. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Таймер сна: одно отложенное дело, которое можно отменить. */
    private var sleep: Runnable? = null

    /** Затухание: шаги громкости, поставленные в очередь тому же handler'у. */
    private var fading: Runnable? = null

    /** Музыку прервал звонок — вернуть её, когда он кончится. */
    private var pausedByFocus = false

    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener { change -> onFocusChange(change) }
        .build()

    /**
     * Наушники выдернули — пауза.
     *
     * Приёмник заводится вместе с первой дорожкой и живёт, пока жив плеер:
     * снимать и ставить его на каждую паузу значило бы пропустить рывок,
     * случившийся ровно между ними.
     */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            if (!preferences.state.value.pauseOnUnplug) return
            if (_state.value.playing) pause()
        }
    }
    private var listening = false

    private val _state = MutableStateFlow(EchoState())
    val state: StateFlow<EchoState> = _state.asStateFlow()

    /** Играет дорожку из очереди. Та же дорожка — это пауза-возобновление. */
    fun play(tracks: List<Track>, track: Track) {
        queue = tracks
        // Сравнение по ссылке на файл, а не по номеру: у дорожки из плейлиста
        // номер свой, и с номером файла из MediaStore он совпадает случайно.
        val chosen = tracks.indexOfFirst { it.uri == track.uri }

        if (_state.value.track?.uri == track.uri && player != null) {
            toggle()
            return
        }

        reorder(chosen)
        start(track)
    }

    /**
     * Вперемешку или по порядку.
     *
     * Порядок пересобирается на ходу, а дорожка продолжает играть: режим
     * меняют посреди песни, и обрывать её ради этого незачем.
     */
    fun shuffle(on: Boolean) {
        shuffling = on
        reorder(order.getOrElse(cursor) { -1 })
        _state.value = _state.value.copy(shuffle = on).withQueue()
    }

    /** Повтор: без него, кругом по очереди или одной дорожкой. */
    fun repeat(mode: EchoRepeat) {
        repeating = mode
        _state.value = _state.value.copy(repeat = mode)
    }

    fun toggle() {
        val current = player ?: return
        if (current.isPlaying) pause() else resumePlayback()
    }

    /**
     * Пауза с затуханием, если оно включено.
     *
     * Обрыв на полуслове слышен как щелчок, особенно в наушниках; полсекунды
     * спада делают паузу похожей на то, как музыку убавляют рукой.
     */
    fun pause() {
        val current = player ?: return
        if (!current.isPlaying) return

        remember()
        if (preferences.state.value.fade) {
            fade(from = 1f, to = 0f) {
                runCatching { current.pause() }
                runCatching { current.setVolume(1f, 1f) }
            }
        } else {
            runCatching { current.pause() }
        }
        abandonFocus()
        _state.value = _state.value.copy(
            playing = false,
            positionMs = current.currentPosition.toLong(),
        )
    }

    fun next() = step(+1)

    fun previous() {
        // Первые секунды «назад» возвращают к началу дорожки, а не к прошлой:
        // так же ведут себя все плееры, и это ожидаемо.
        val position = player?.currentPosition ?: 0
        if (position > REWIND_MS) seekTo(0) else step(-1)
    }

    fun seekTo(ms: Long) {
        val current = player ?: return
        current.seekTo(ms.toInt())
        _state.value = _state.value.copy(positionMs = ms)
    }

    /** Текущая позиция — по запросу того, кто смотрит. */
    fun position(): Long = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)

    /**
     * Играть следующей, не сбивая того, что играет.
     *
     * Дорожка встаёт сразу за текущей и в очереди, и в порядке обхода: при
     * включённом «вперемешку» иначе она попала бы в случайное место — и
     * «дальше» перестало бы значить «дальше».
     */
    fun playNext(track: Track) = insert(track, next = true)

    /** В конец очереди — обычное «добавить в очередь». */
    fun enqueue(track: Track) = insert(track, next = false)

    /**
     * Убрать песню из очереди — её файла больше нет на телефоне.
     *
     * Если она сейчас играет, плеер переходит к следующей: обрывать вечер
     * из-за одной удалённой песни незачем. Если в очереди больше никого не
     * осталось — замолкает совсем.
     */
    fun forget(uri: String) {
        val playing = _state.value.track?.uri == uri
        val following = if (playing) upcoming(uri) else null
        val current = _state.value.track

        queue = queue.filterNot { it.uri == uri }

        when {
            queue.isEmpty() -> stop()
            playing && following != null -> {
                reorder(queue.indexOfFirst { it.uri == following.uri })
                start(following)
            }
            // Играла последняя, и заменить её нечем: очередь есть, но вся
            // позади — честнее замолчать, чем начинать её сначала.
            playing -> stop()
            else -> {
                reorder(queue.indexOfFirst { it.uri == current?.uri })
                _state.value = _state.value.withQueue()
            }
        }
    }

    /** Следующая за удаляемой по порядку обхода — та, что заиграет вместо неё. */
    private fun upcoming(uri: String): Track? {
        val at = order.indexOfFirst { queue.getOrNull(it)?.uri == uri }
        if (at < 0) return null
        for (step in 1 until order.size) {
            val candidate = queue.getOrNull(order[(at + step) % order.size]) ?: continue
            if (candidate.uri != uri) return candidate
        }
        return null
    }

    /**
     * Что заиграет через [delta] шагов, если ничего не трогать.
     *
     * Нужна обложке: смахивая её, человек видит под ней следующую песню ещё
     * до того, как отпустит палец, — и увидеть он должен именно ту, что
     * заиграет. Считать её на экране из [EchoState.queue] нельзя: вперемешку
     * порядок обхода свой, и «следующая в списке» и «следующая на самом деле»
     * — разные дорожки.
     *
     * `null` — показывать нечего: очередь пуста или в ней одна дорожка, и
     * шаг вперёд упирается в неё же. Обложке это говорит «смахивать некуда»,
     * и она отвечает пружиной, а не подменой.
     */
    fun peek(delta: Int): Track? {
        if (order.isEmpty()) return null
        val here = cursor.takeIf { it in order.indices }
        // Курсора нет — дорожку включили не из этой очереди; тогда шаг в любую
        // сторону начинает её с начала, и показать надо именно первую.
        val at = if (here == null) 0 else ((here + delta) + order.size) % order.size
        val found = queue.getOrNull(order[at]) ?: return null
        return found.takeIf { it.uri != _state.value.track?.uri }
    }

    /** Прыжок по очереди: место — номер в [EchoState.queue], в порядке обхода. */
    fun jump(position: Int) {
        if (position !in order.indices) return
        cursor = position
        start(queue[order[cursor]])
    }

    /**
     * Скорость воспроизведения. Ставится живому `MediaPlayer` на ходу; на
     * паузе только запоминается — `PlaybackParams` на остановленном плеере на
     * части устройств сам запускает воспроизведение.
     */
    fun setSpeed(rate: Float) {
        val current = player ?: return
        if (!current.isPlaying) return
        runCatching { current.playbackParams = current.playbackParams.setSpeed(rate) }
    }

    /**
     * Уснуть через столько-то минут. `null` — отменить таймер.
     *
     * Засыпает с затуханием: таймер сна ставят, засыпая, и обрыв на полуслове
     * будит вернее, чем сама музыка.
     */
    fun sleepAfter(minutes: Int?) {
        sleep?.let { handler.removeCallbacks(it) }
        sleep = null

        if (minutes == null || minutes <= 0) {
            _state.value = _state.value.copy(sleepAt = null)
            return
        }

        val delay = minutes * 60_000L
        val task = Runnable {
            sleep = null
            _state.value = _state.value.copy(sleepAt = null)
            pause()
        }
        sleep = task
        handler.postDelayed(task, delay)
        _state.value = _state.value.copy(sleepAt = SystemClock.elapsedRealtime() + delay)
    }

    /**
     * «Играть» там, где плеера не видно, — из меню приложения.
     *
     * Ничего не играет и не играло — сказать об этом честно (`false`), чтобы
     * меню отправило человека в раздел выбирать музыку, а не делало вид, что
     * нажатие сработало.
     */
    fun resume(): Boolean {
        if (_state.value.track != null) {
            toggle()
            return true
        }

        val last = preferences.last.value?.takeIf { preferences.state.value.resumeLast }
            ?: return false
        val track = last.asTrack()
        queue = listOf(track)
        reorder(0)
        start(track, at = last.positionMs)
        surround(track)
        return true
    }

    /**
     * Достраивает очередь вокруг дорожки, поднятой из памяти.
     *
     * Продолжая вчерашнюю песню кнопкой из меню, плеер знает только её одну —
     * и «дальше» в шторке упиралось бы в неё же, выглядя сломанным. Поэтому
     * следом, уже на ходу, читается музыка телефона, и дорожка встаёт в ней на
     * своё место: соседи те же, что были бы, включи её человек из списка.
     *
     * Тихо и без спешки: список приходит через полсекунды-секунду, к этому
     * времени песня уже играет. Не нашлось (файл убрали, доступ отозвали) —
     * остаётся очередь из одной дорожки, как и была.
     */
    private fun surround(track: Track) {
        scope.launch {
            val library = runCatching { EchoLibrary.load(context) }.getOrNull() ?: return@launch
            val at = library.indexOfFirst { it.uri == track.uri }
            // Пока читали, могли включить что-то другое — тогда не мешаем.
            if (at < 0 || _state.value.track?.uri != track.uri) return@launch
            queue = library
            reorder(at)
            _state.value = _state.value.withQueue()
        }
    }

    fun stop() {
        remember()
        player?.release()
        player = null
        abandonFocus()
        _state.value = idle()
    }

    private fun step(delta: Int) {
        if (order.isEmpty()) return
        // По кругу: в конце списка «дальше» возвращает к началу, иначе кнопка
        // просто перестаёт работать и выглядит сломанной.
        cursor = ((cursor + delta) + order.size) % order.size
        start(queue[order[cursor]])
    }

    private fun insert(track: Track, next: Boolean) {
        // Пустая очередь: «в очередь» на пустом плеере значит «включи это».
        if (queue.isEmpty()) {
            play(listOf(track), track)
            return
        }

        val playing = order.getOrNull(cursor)
        val at = if (next && playing != null) playing + 1 else queue.size
        queue = queue.toMutableList().apply { add(at, track) }
        // Номера дорожек правее вставки уехали на одну вправо — порядок обхода
        // это должен учесть, иначе он покажет соседей вместо выбранных.
        val shifted = order.map { if (it >= at) it + 1 else it }.toMutableList()
        val place = if (next && cursor in order.indices) cursor + 1 else shifted.size
        shifted.add(place, at)
        order = shifted
        _state.value = _state.value.withQueue()
    }

    /**
     * Заново раскладывает порядок обхода, оставляя [current] под курсором.
     *
     * Дорожки может не быть в очереди — её включили из папки, а очередь потом
     * собрали из плейлиста. Тогда курсора нет, и «дальше» просто начинает
     * очередь сначала: подставлять нулевую дорожку значило бы соврать о том,
     * где мы стоим.
     */
    private fun reorder(current: Int) {
        val known = current.takeIf { it in queue.indices }
        val rest = queue.indices.filter { it != known }
        order = when {
            !shuffling -> queue.indices.toList()
            known == null -> rest.shuffled()
            // Текущая дорожка встаёт первой, остальные тасуются: включённое
            // перемешивание не должно перебивать то, что уже играет.
            else -> listOf(known) + rest.shuffled()
        }
        cursor = if (known == null) -1 else order.indexOf(known)
    }

    /** Пустое состояние: режимы переживают и остановку, и пропавший файл. */
    private fun idle() = EchoState(shuffle = shuffling, repeat = repeating)

    /** Очередь в состоянии — та же, что внутри, но уже в порядке обхода. */
    private fun EchoState.withQueue(): EchoState = copy(
        // `this@EchoPlayer.queue` — не описка: у состояния поле называется так
        // же, и без уточнения очередь собиралась бы сама из себя.
        queue = order.mapNotNull { this@EchoPlayer.queue.getOrNull(it) },
        queueAt = cursor,
    )

    private fun start(track: Track, at: Long = 0) {
        player?.release()

        val fade = preferences.state.value.fade
        player = MediaPlayer().apply {
            audioSessionId = this@EchoPlayer.audioSessionId
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            runCatching {
                setDataSource(context, Uri.parse(track.uri))
                prepare()
                if (at > 0) seekTo(at.toInt())
                if (fade) setVolume(0f, 0f)
                start()
            }.onFailure {
                // Файл мог исчезнуть или оказаться битым: плеер не падает, а
                // просто остаётся без дорожки.
                release()
                player = null
                _state.value = idle()
                return
            }
            // Скорость ставится уже играющему: на остановленном
            // `PlaybackParams` на части устройств сам запускает звук.
            runCatching {
                val rate = preferences.state.value.speed
                if (rate != 1f) playbackParams = playbackParams.setSpeed(rate)
            }
            setOnCompletionListener { finished ->
                when {
                    repeating == EchoRepeat.TRACK -> {
                        // Тот же файл — тот же `MediaPlayer`: пересобирать его
                        // ради второго круга значит слышать паузу на стыке.
                        finished.seekTo(0)
                        finished.start()
                        _state.value = _state.value.copy(positionMs = 0, playing = true)
                    }
                    // Конец очереди без повтора: плеер замолкает на последней
                    // дорожке и оставляет её под кнопкой «играть», а не гасит
                    // экран пустым плеером.
                    repeating == EchoRepeat.OFF && cursor == order.lastIndex -> {
                        finished.seekTo(0)
                        _state.value = _state.value.copy(positionMs = 0, playing = false)
                        abandonFocus()
                    }
                    else -> step(+1)
                }
            }
        }

        requestFocus()
        listenForUnplug()
        // Служба поднимается вместе с первой дорожкой: она и держит процесс
        // живым, пока идёт звук, и показывает плеер в шторке. Зовётся отсюда,
        // потому что дорожку всегда включают из приложения на переднем плане —
        // а из фона Android службу переднего плана поднять не даст.
        EchoService.start(context)
        if (fade) fade(from = 0f, to = 1f)

        _state.value = EchoState(
            track = track,
            playing = true,
            positionMs = at,
            durationMs = player?.duration?.toLong() ?: track.durationMs,
            shuffle = shuffling,
            repeat = repeating,
            sleepAt = _state.value.sleepAt,
        ).withQueue()
        remember()
    }

    /** Возобновление после паузы — с тем же затуханием, только наоборот. */
    private fun resumePlayback() {
        val current = player ?: return
        if (!requestFocus()) return

        runCatching {
            if (preferences.state.value.fade) current.setVolume(0f, 0f)
            current.start()
        }
        runCatching {
            val rate = preferences.state.value.speed
            if (rate != 1f) current.playbackParams = current.playbackParams.setSpeed(rate)
        }
        if (preferences.state.value.fade) fade(from = 0f, to = 1f)
        _state.value = _state.value.copy(playing = true)
    }

    /**
     * Плавная громкость шагами по 40 мс.
     *
     * Своими руками, а не средствами `MediaPlayer`: плавного пуска у него нет,
     * а полсекунды спада — вся разница между «музыку убавили» и «оборвалось».
     */
    private fun fade(from: Float, to: Float, onEnd: () -> Unit = {}) {
        fading?.let { handler.removeCallbacks(it) }
        val steps = FADE_MS / FADE_STEP_MS
        var step = 0

        val task = object : Runnable {
            override fun run() {
                val current = player ?: return
                step++
                val share = step.toFloat() / steps
                val volume = from + (to - from) * share
                runCatching { current.setVolume(volume, volume) }
                if (step < steps) {
                    handler.postDelayed(this, FADE_STEP_MS.toLong())
                } else {
                    fading = null
                    onEnd()
                }
            }
        }
        fading = task
        handler.postDelayed(task, FADE_STEP_MS.toLong())
    }

    /**
     * Фокус звука: пока он наш, музыка играет. Отказ означает, что телефон
     * сейчас занят звонком, — тогда не начинаем вовсе.
     */
    private fun requestFocus(): Boolean =
        audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun abandonFocus() {
        pausedByFocus = false
        runCatching { audio.abandonAudioFocusRequest(focus) }
    }

    /**
     * Что делать, когда звук забрали.
     *
     * Насовсем (`LOSS`) — пауза без возврата: включился чужой плеер, и лезть
     * поверх него не наше дело. На время (`LOSS_TRANSIENT`) — пауза с
     * возвратом: кончится звонок, музыка продолжится с того же места. С
     * позволением приглушиться — просто тише: так ведут себя все плееры, и
     * навигатор поверх музыки этого и ждёт.
     */
    private fun onFocusChange(change: Int) {
        val current = player ?: return
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocus = false
                if (_state.value.playing) pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_state.value.playing) {
                    pausedByFocus = true
                    runCatching { current.pause() }
                    _state.value = _state.value.copy(
                        playing = false,
                        positionMs = current.currentPosition.toLong(),
                    )
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                runCatching { current.setVolume(DUCK, DUCK) }

            AudioManager.AUDIOFOCUS_GAIN -> {
                runCatching { current.setVolume(1f, 1f) }
                if (pausedByFocus) {
                    pausedByFocus = false
                    resumePlayback()
                }
            }
        }
    }

    private fun listenForUnplug() {
        if (listening) return
        runCatching {
            // Через ContextCompat: с Android 14 приёмник обязан сказать, ждёт
            // ли он чужих отправителей. Этот ждёт только систему.
            ContextCompat.registerReceiver(
                context,
                noisy,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            listening = true
        }
    }

    /** Запомнить, где остановились: назавтра плеер откроется этой же песней. */
    private fun remember() {
        val track = _state.value.track ?: return
        preferences.remember(
            uri = track.uri,
            title = track.title,
            artist = track.artist,
            albumId = track.albumId,
            durationMs = track.durationMs,
            positionMs = position(),
        )
    }

    private companion object {
        /** До этой секунды «назад» — это «к началу». */
        const val REWIND_MS = 3_000

        /** Полсекунды на затухание: короче слышно щелчком, длиннее — вязко. */
        const val FADE_MS = 480
        const val FADE_STEP_MS = 40

        /** Насколько приглушиться под навигатор. */
        const val DUCK = 0.25f
    }
}

/** Дорожка, на которой остановились в прошлый раз, — обратно в дорожку плеера. */
fun EchoLastTrack.asTrack(): Track = Track(
    // Номера файла в MediaStore здесь нет: он мог смениться, пока телефон
    // пересобирал библиотеку. Ссылка на файл — то, чем дорожка и опознаётся.
    id = uri.hashCode().toLong(),
    uri = uri,
    title = title.ifBlank { "Без названия" },
    artist = artist.ifBlank { "Неизвестный исполнитель" },
    album = "",
    albumId = albumId,
    durationMs = durationMs,
    folder = "",
)
