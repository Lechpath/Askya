package app.askya.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Что открыто в плеере: ссылка на файл и то, как его назвать в шапке. */
data class VideoSource(val uri: String, val title: String) {

    /**
     * Не файл на телефоне, а поток из сети.
     *
     * Схема, а не догадка по имени: у ссылки на поток расширения часто нет
     * вовсе (`.m3u8` — не всегда, а `?token=` — почти всегда). Проверяется
     * ровно то, что решает дело: открывать ли дескриптором у провайдера или
     * отдать ссылку самому VLC, который в сеть ходить умеет.
     */
    val network: Boolean get() = isNetworkUri(uri)
}

/** Схемы, которые VLC читает сам, — их не через провайдера открывают, а по ссылке. */
private val NETWORK_SCHEMES = setOf(
    "http", "https", "rtsp", "rtmp", "rtmps", "mms", "mmsh", "rtp", "udp", "srt", "ftp",
)

/** Ссылка ведёт в сеть, а не на телефон. */
fun isNetworkUri(uri: String): Boolean =
    runCatching { Uri.parse(uri).scheme?.lowercase() }.getOrNull() in NETWORK_SCHEMES

/** Дорожка внутри файла — звуковая или субтитровая. */
data class VideoTrack(val id: Int, val name: String)

/**
 * Как кадр ложится в экран.
 *
 * Свой набор, а не весь список VLC: у него дюжина отношений сторон, включая
 * 2.21:1 и 5:4, и в кнопке, которую нажимают одним пальцем посреди фильма, они
 * читаются как список ошибок. Оставлено то, ради чего эту кнопку и жмут:
 * вписать целиком, растянуть на весь экран, обрезать края, показать как есть.
 */
enum class VideoScale(val label: String, internal val type: MediaPlayer.ScaleType) {
    FIT("Вписать", MediaPlayer.ScaleType.SURFACE_BEST_FIT),
    CROP("Обрезать", MediaPlayer.ScaleType.SURFACE_FIT_SCREEN),
    FILL("Растянуть", MediaPlayer.ScaleType.SURFACE_FILL),
    ORIGINAL("Как есть", MediaPlayer.ScaleType.SURFACE_ORIGINAL),
    WIDE("16:9", MediaPlayer.ScaleType.SURFACE_16_9),
    OLD("4:3", MediaPlayer.ScaleType.SURFACE_4_3),
    ;

    /** Следующий режим: кнопка одна, а состояний шесть. */
    fun next(): VideoScale = entries[(ordinal + 1) % entries.size]
}

/** Что происходит с видео — состояние плеера одним значением. */
data class VideoState(
    val source: VideoSource? = null,
    val playing: Boolean = false,
    /** Плеер набирает буфер: на экране в этот момент ничего не движется. */
    val buffering: Boolean = false,
    /** Файл не открылся. Текст — человеку, а не в лог. */
    val error: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Скорость: 1 — обычная. */
    val rate: Float = 1f,
    /** Громкость самого плеера, 0–200 %. Системную он не трогает. */
    val volume: Int = 100,
    val scale: VideoScale = VideoScale.FIT,
    val audioTracks: List<VideoTrack> = emptyList(),
    val audioTrackId: Int = -1,
    val subtitleTracks: List<VideoTrack> = emptyList(),
    /** -1 — субтитры выключены. */
    val subtitleTrackId: Int = -1,
    /** Сдвиг субтитров, мс: плюс — позже, минус — раньше. */
    val subtitleDelayMs: Long = 0,
    /** Сдвиг звука, мс. */
    val audioDelayMs: Long = 0,
    val seekable: Boolean = true,
    /** Файл доигран до конца. */
    val ended: Boolean = false,
    /** Размер кадра — по нему плеер решает, разворачивать ли экран. Ноль — ещё не знаем. */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /**
     * Кадр записан на бок — то есть в файле он лежит поперёк, а показывать
     * его надо повёрнутым.
     *
     * Так пишет всякий телефон: снятое стоя лежит в файле как 1920×1080 с
     * меткой «повернуть на 90». Плеер её слушается и рисует кадр правильно, а
     * вот размер из дорожки остаётся тем, каким записан, — и по нему
     * вертикальное видео выглядит фильмом. Из-за этого экран разворачивался
     * поперёк ровно там, где разворачивать его не надо.
     */
    val videoRotated: Boolean = false,
    /**
     * Какой по счёту плеер играет.
     *
     * Меняется только при пересоздании плеера после зависания
     * ([VideoEngine.restart]). Экран держит на этом числе своё место под кадр:
     * замерший VLC поверхность по-хорошему не отдаёт, и отбирать её у него —
     * значит ждать того, кто уже не отвечает. Новый плеер получает новое
     * место, а старое уходит вместе со старым плеером.
     */
    val generation: Int = 0,
) {
    /** Ширина кадра такой, какой её видит человек, — с учётом метки поворота. */
    val shownWidth: Int get() = if (videoRotated) videoHeight else videoWidth

    /** И высота — тоже как на экране, а не как в файле. */
    val shownHeight: Int get() = if (videoRotated) videoWidth else videoHeight

    /** Кадр шире, чем выше, — то есть фильм, а не снятое вертикально видео. */
    val landscape: Boolean get() = shownWidth > 0 && shownWidth > shownHeight

    /** Кадр выше, чем шире, — снятое стоя. Такому экран не разворачивают. */
    val portrait: Boolean get() = shownHeight > 0 && shownHeight > shownWidth

    /** Доля пройденного, 0..1 — по ней рисуется полоса. */
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Плеер AskyaV — обёртка вокруг libVLC.
 *
 * Почему не системный `MediaPlayer`, которым играет Echo: у звука один
 * контейнер на всё (mp3), а у видео их два десятка, и системный знает из них
 * mp4, webm и часть mkv. Раздел, который на avi отвечает «формат не
 * поддерживается», плеером не является. Всё остальное в Askya написано своими
 * руками именно потому, что своими руками это возможно; здесь — нет.
 *
 * Живёт в контейнере приложения, а не на экране: поворот телефона пересобирает
 * композицию целиком, и плеер, живущий в ней, на каждом повороте начинал бы
 * фильм заново. По той же причине состояние отдаётся потоком.
 *
 * В отличие от Echo, за приложение он не выходит: службы переднего плана у
 * видео нет и уведомления тоже. Видео смотрят глазами — играть ему в свёрнутом
 * приложении незачем, а звук из ниоткуда после ухода из раздела человек
 * читает как поломку. Уходя с экрана, плеер останавливается сам.
 *
 * ## Как открывается файл
 *
 * Не по ссылке, а по открытому дескриптору. Ссылка `content://` — это адрес у
 * системного провайдера, и VLC, читающий его своими средствами, часть таких
 * адресов не открывает вовсе; дескриптор же одинаков для всего, что вообще
 * можно прочитать, включая файлы, отданные приложению на один заход. Держать
 * его открытым приходится всё время, пока файл играет, — отсюда [descriptor].
 *
 * ## Ни одного вызова VLC на главном потоке
 *
 * Это главное правило этого класса, и написано оно кровью: приложение
 * зависало намертво и уходило в «не отвечает» именно здесь. Поднять сам VLC —
 * это загрузить сотню мегабайт нативного кода; открыть файл — сходить к чужому
 * провайдеру за дескриптором; а `stop()` и `release()` ждут, пока сойдутся
 * потоки разбора, — и если разбор встал (а он и встаёт, ради этого написан
 * [restart]), они не возвращаются вовсе. Всё это делалось на потоке, который
 * рисует экран: телефон переставал отвечать на касания, и система убивала
 * приложение.
 *
 * Поэтому у плеера свой поток ([onVlc]) — один, чтобы вызовы шли по очереди:
 * VLC не любит, когда его дёргают с двух сторон. Состояние при этом меняется
 * сразу, на месте вызова: экран не должен ждать ответа железа, чтобы
 * перерисовать кнопку «пауза».
 *
 * Исключение одно — работа с местом под кадр ([attach], [detach],
 * [refreshSurfaces]): это Android-разметка, и её трогают только с потока
 * разметки.
 *
 * ## Закрытие — всегда в сторону
 *
 * Закрыть замерший плеер нельзя: он не отвечает и на закрытие. Поэтому
 * закрывается он не на общем потоке, а на своём собственном, одноразовом
 * ([bury]), и никого больше не задерживает — даже если не закроется никогда.
 * Мёртвый плеер уносит с собой немного памяти; заклинивший на нём поток унёс
 * бы весь раздел.
 */
class VideoEngine(private val context: Context) {

    /**
     * Сам VLC. Один на приложение и создаётся при первом открытии файла, а не
     * при запуске: это десятки мегабайт нативного кода, и человеку, который
     * пришёл в заметки, поднимать их незачем.
     */
    private val libVlc: LibVLC by lazy {
        LibVLC(
            context.applicationContext,
            arrayListOf(
                // Растяжение звука вместо перепада тона: на полуторной
                // скорости голос иначе звучит мультяшно.
                "--audio-time-stretch",
                // Опоздавшие кадры выбрасываются — как у самого VLC по
                // умолчанию. Прежде тут стояло обратное («--no-drop-late-frames»,
                // «--no-skip-frames»): лучше, мол, мгновение подождать, чем
                // смотреть рывками. На деле «мгновение» на тяжёлом кадре
                // становилось минутой: картинка замирала совсем, и помогал
                // только выход из раздела. Выброшенный кадр — моргание,
                // замерший — поломка.
                //
                // Сколько держать в запасе, прежде чем показывать: секунда для
                // файла на телефоне избыточна, для потока из сети — мало.
                "--file-caching=300",
                "--network-caching=1500",
                // Молча. По умолчанию VLC пишет в лог по строке на кадр.
                "--quiet",
            ),
        )
    }

    /**
     * Поток, на котором зовётся VLC. Один и по очереди — см. рассказ выше.
     *
     * `var`, а не `val`: если команда на нём не вернулась (VLC встал внутри
     * неё), поток бросается вместе с плеером и заводится новый — иначе раздел
     * молча перестал бы открывать что бы то ни было до перезапуска приложения.
     */
    @Volatile
    private var vlc: ExecutorService = newVlcThread()

    /** Когда началась команда, которая ещё не вернулась. Ноль — свободен. */
    @Volatile
    private var busySince: Long = 0

    private val ui = Handler(Looper.getMainLooper())

    @Volatile
    private var player: MediaPlayer? = null

    /** Открытый файл: закрывается вместе со следующим открытием и с плеером. */
    @Volatile
    private var descriptor: ParcelFileDescriptor? = null

    /** Место на экране, куда отдан кадр. Пусто — плеер сейчас никуда не рисует. */
    @Volatile
    private var surface: VLCVideoLayout? = null

    /** Куда встать, когда файл откроется: место, на котором его закрыли. */
    @Volatile
    private var pendingSeekMs: Long = 0

    /**
     * Разбирать ли кадры железом.
     *
     * Обычно да — иначе телефон греется и на 1080p не успевает. Снимается это
     * само, при пересборке после зависания ([restart]): чаще всего встаёт
     * именно железный разбор, на неудачном кодеке он замирает насмерть, и
     * второй заход тем же способом кончился бы тем же.
     */
    @Volatile
    private var hardware: Boolean = true

    private fun newVlcThread(): ExecutorService =
        Executors.newSingleThreadExecutor { work ->
            Thread(work, "askya-vlc").apply { isDaemon = true }
        }

    /**
     * Сделать это в VLC — по очереди и не на главном потоке.
     *
     * Перед тем как встать в очередь, смотрит, вернулась ли прошлая команда.
     * Не вернулась за [WEDGE_MS] — значит, VLC встал внутри неё и не вернётся:
     * ждать нечего, поток и плеер бросаются, и очередь начинается заново на
     * свежем потоке. Без этого одно зависание закрывало бы раздел до
     * перезапуска приложения.
     */
    private fun onVlc(block: () -> Unit) {
        val since = busySince
        if (since != 0L && SystemClock.elapsedRealtime() - since > WEDGE_MS) {
            val doomed = player
            val file = descriptor
            player = null
            descriptor = null
            surface = null
            busySince = 0
            runCatching { vlc.shutdownNow() }
            vlc = newVlcThread()
            bury(doomed, file)
        }
        runCatching {
            vlc.execute {
                busySince = SystemClock.elapsedRealtime()
                runCatching(block)
                busySince = 0
            }
        }
    }

    /** А это — на потоке разметки: место под кадр живёт там. */
    private fun onUi(block: () -> Unit) {
        ui.post { runCatching(block) }
    }

    /**
     * Проводить плеер — на отдельном потоке, ни о чём его не спрашивая.
     *
     * Слушатель снимается первым: доигрывающий своё событие плеер иначе писал
     * бы в состояние, которое принадлежит уже не ему.
     *
     * Файл уходит вместе с плеером и закрывается последним: дескриптор,
     * закрытый под читающим его VLC, — это уже не уборка, а выдернутый из-под
     * него пол.
     */
    private fun bury(doomed: MediaPlayer?, file: ParcelFileDescriptor?) {
        if (doomed == null && file == null) return
        Thread({
            runCatching { doomed?.setEventListener(null) }
            runCatching { doomed?.stop() }
            runCatching { doomed?.release() }
            runCatching { file?.close() }
        }, "askya-vlc-farewell").apply { isDaemon = true }.start()
    }

    private val _state = MutableStateFlow(VideoState())
    val state: StateFlow<VideoState> = _state.asStateFlow()

    /**
     * Открыть файл и начать играть с [startMs].
     *
     * Прежний файл закрывается целиком — вместе с дескриптором: два открытых
     * фильма в памяти не нужны никогда, а незакрытый дескриптор держит файл от
     * удаления.
     */
    fun open(source: VideoSource, startMs: Long = 0, hardware: Boolean = true) {
        pendingSeekMs = startMs
        this.hardware = hardware

        // Состояние — сразу, на месте вызова: имя файла и ожидание человек
        // должен увидеть в тот же миг, а не когда поднимется VLC.
        _state.value = _state.value.copy(
            source = source,
            error = null,
            ended = false,
            playing = false,
            buffering = true,
            positionMs = startMs,
            durationMs = 0,
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
            subtitleDelayMs = 0,
            audioDelayMs = 0,
            videoWidth = 0,
            videoHeight = 0,
            videoRotated = false,
        )

        onVlc { openHere(source, hardware) }
    }

    /** Само открытие — уже на своём потоке. */
    private fun openHere(source: VideoSource, hardware: Boolean) {
        val vlc = runCatching { libVlc }.getOrElse {
            _state.value = VideoState(source = source, error = "Не удалось поднять плеер")
            return
        }

        closeMedia()

        val media = runCatching { mediaOf(vlc, Uri.parse(source.uri)) }.getOrNull()
        if (media == null) {
            _state.value = VideoState(source = source, error = openingFailure(source))
            return
        }

        // Разбирать кадры железом, а не процессором: без этого телефон греется
        // и на 1080p не успевает. Второй флаг — «падать обратно на процессор,
        // если железо этот кодек не знает». После зависания заходим сюда уже
        // без железа вовсе — см. [hardware].
        media.setHWDecoderEnabled(hardware, false)

        val current = player ?: MediaPlayer(vlc).also { fresh ->
            fresh.setEventListener { event -> onEvent(event) }
            player = fresh
        }

        current.media = media
        // Ссылка отдана плееру — своя нам больше не нужна.
        media.release()
        current.play()

        // Плеер мог родиться после того, как экран отдал место под кадр:
        // открытие идёт своим потоком, а разметка своим. Кто из них успел
        // первым, неважно — оба спрашивают об этом на потоке разметки, и
        // второй доделывает то, чего не смог первый.
        onUi { surface?.let { layout -> player?.attachViews(layout, null, true, false) } }
    }

    /**
     * Медиа из ссылки: у чужого провайдера — дескриптором, у файла и у сети —
     * как есть.
     *
     * Поток из сети дескриптором не открыть вовсе: у `http://` нет провайдера,
     * который отдал бы файл, — ходить по ссылке умеет сам VLC, и ему она и
     * отдаётся.
     */
    private fun mediaOf(vlc: LibVLC, uri: Uri): Media {
        val scheme = uri.scheme?.lowercase()
        if (scheme == null || scheme == "file" || scheme in NETWORK_SCHEMES) return Media(vlc, uri)
        val opened = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("нет дескриптора")
        descriptor = opened
        return Media(vlc, opened.fileDescriptor)
    }

    fun play() {
        val current = player ?: return
        // Доигранный файл кнопкой «играть» начинается сначала: иначе она
        // выглядит нажатой впустую.
        val again = _state.value.ended
        if (again) _state.value = _state.value.copy(ended = false)
        onVlc {
            if (again) current.setTime(0)
            current.play()
        }
    }

    fun pause() {
        val current = player ?: return
        onVlc { current.pause() }
    }

    fun togglePlay() {
        if (_state.value.playing) pause() else play()
    }

    /** Перемотка на место. Недоступную перемотку молча не делаем. */
    fun seekTo(ms: Long) {
        val current = player ?: return
        if (!_state.value.seekable) return
        val duration = _state.value.durationMs
        val target = if (duration > 0) ms.coerceIn(0, duration) else maxOf(0, ms)
        _state.value = _state.value.copy(positionMs = target, ended = false)
        onVlc { current.setTime(target) }
    }

    /** Перемотка на [deltaMs] от текущего места — двойным касанием и жестом. */
    fun seekBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    /** Скорость от четверти до четырёх: дальше речь неразборчива в обе стороны. */
    fun setRate(rate: Float) {
        val clamped = rate.coerceIn(0.25f, 4f)
        _state.value = _state.value.copy(rate = clamped)
        onVlc { player?.rate = clamped }
    }

    /**
     * Громкость самого плеера, 0–200 %.
     *
     * Своя, а не системная: выше ста процентов система не поднимает, а тихую
     * дорожку в фильме иначе не вытянуть — ровно за этим её и крутят в VLC.
     */
    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 200)
        _state.value = _state.value.copy(volume = clamped)
        onVlc { player?.setVolume(clamped) }
    }

    fun setScale(scale: VideoScale) {
        _state.value = _state.value.copy(scale = scale)
        onVlc { player?.videoScale = scale.type }
    }

    /**
     * Выбранная дорожка запоминается сразу, не дожидаясь ответа плеера: ответ
     * придёт со своего потока, а подсветку в списке человек ждёт от пальца.
     * Дорожку из этого же списка плеер принимает всегда — списки он и отдал.
     */
    fun setAudioTrack(id: Int) {
        _state.value = _state.value.copy(audioTrackId = id)
        onVlc { player?.setAudioTrack(id) }
    }

    /** -1 выключает субтитры вовсе. */
    fun setSubtitleTrack(id: Int) {
        _state.value = _state.value.copy(subtitleTrackId = id)
        onVlc { player?.setSpuTrack(id) }
    }

    /**
     * Субтитры отдельным файлом.
     *
     * Их подкладывают рядом с фильмом, и сам VLC нашёл бы их сам — но только
     * если бы читал папку. Он читает дескриптор, папки не видит, и найти
     * субтитры может один человек.
     */
    fun addSubtitles(uri: String, select: Boolean = true) {
        onVlc { player?.addSlave(IMedia.Slave.Type.Subtitle, Uri.parse(uri), select) }
    }

    /** Сдвиг субтитров: плюс — показывать позже. */
    fun setSubtitleDelay(ms: Long) {
        _state.value = _state.value.copy(subtitleDelayMs = ms)
        onVlc { player?.setSpuDelay(ms * 1000) }
    }

    /** Сдвиг звука: плюс — звучать позже. */
    fun setAudioDelay(ms: Long) {
        _state.value = _state.value.copy(audioDelayMs = ms)
        onVlc { player?.setAudioDelay(ms * 1000) }
    }

    /**
     * Показывать кадр в этом месте экрана. Зовётся с потока разметки — оттуда,
     * где это место и заведено.
     *
     * Плеера в этот миг может ещё не быть: открытие идёт своим потоком. Тогда
     * ничего не делается, а картинку отдаст сам [openHere], когда плеер
     * родится, — тоже отсюда, с потока разметки.
     */
    fun attach(layout: VLCVideoLayout) {
        surface = layout
        runCatching { player?.attachViews(layout, null, true, false) }
    }

    /**
     * Экран убрал место под кадр.
     *
     * Спрашивается, то ли это место: после пересборки плеера ([restart])
     * старое место брошено вместе со старым плеером, и отбирать по нему
     * картинку у нового — значит гасить только что открытое.
     */
    fun detach(layout: VLCVideoLayout) {
        if (surface !== layout) return
        surface = null
        runCatching { player?.detachViews() }
    }

    /** Кадр перерисовывается по размеру места — после поворота экрана. */
    fun refreshSurfaces() {
        runCatching { player?.updateVideoSurfaces() }
    }

    /**
     * Открыть то же самое заново, с того же места.
     *
     * Так снимается зависание: разбор иногда встаёт совсем — картинка замирает,
     * время не идёт, и ни пауза, ни перемотка его не трогают. До сих пор
     * помогал только выход из раздела, потому что уход с экрана закрывает файл
     * и отпускает поверхность. Здесь то же самое делается на месте и само
     * (см. сторожа в `VideoPlayerScreen`): плеер пересоздаётся, файл
     * открывается заново, и просмотр продолжается с той секунды, на которой
     * встал.
     *
     * Плеер именно пересоздаётся, а не переоткрывает файл: замерший разбор
     * остаётся замершим и с новым файлом — VLC в этом состоянии не отвечает
     * ни на что, кроме собственного закрытия.
     */
    fun restart() {
        val was = _state.value
        val source = was.source ?: return

        // Ни stop, ни release, ни detachViews: замерший VLC не отвечает ни на
        // что из этого, и вопрос к нему — это и есть то самое зависание всего
        // приложения. Плеер просто перестаёт быть нашим и уходит в сторону
        // ([bury]), а место под кадр бросается вместе с ним: новое заведёт
        // экран, увидев новое [VideoState.generation].
        bury(player, descriptor)
        player = null
        descriptor = null
        surface = null

        _state.value = was.copy(
            generation = was.generation + 1,
            playing = false,
            buffering = true,
            error = null,
        )

        // Программным разбором: встаёт обычно железный, и второй заход тем же
        // способом кончился бы тем же.
        open(source, startMs = was.positionMs, hardware = false)

        // Скорость и способ вписать кадр живут в плеере, а плеер новый:
        // перезапуск не должен возвращать полуторную скорость к обычной, а
        // «обрезать» — к «вписать». Встают в ту же очередь, следом за
        // открытием, — значит, плеер к тому времени уже есть.
        setRate(was.rate)
        setScale(was.scale)
    }

    /**
     * Остановить и закрыть файл, но плеер оставить.
     *
     * Так уходят с экрана: сам VLC поднимается долго, и поднимать его заново
     * ради следующего фильма значило бы ждать дважды.
     */
    fun stop() {
        val was = _state.value
        val doomed = player
        val file = descriptor
        player = null
        descriptor = null
        // Место под кадр экран сейчас уберёт сам, и спрашивать об этом плеер
        // мы не станем: отобрать картинку у того, кто, может быть, уже не
        // отвечает, — это то самое зависание, только теперь на потоке
        // разметки. Плеер уходит целиком, а следующий файл откроет новый:
        // дорог здесь сам VLC, а он остаётся поднятым.
        surface = null
        _state.value = VideoState(
            scale = was.scale,
            volume = was.volume,
            generation = was.generation,
        )
        bury(doomed, file)
    }

    /** Совсем: вместе с плеером. Зовётся, когда приложение уходит из памяти. */
    fun release() {
        val doomed = player
        val file = descriptor
        player = null
        descriptor = null
        surface = null
        _state.value = VideoState()
        bury(doomed, file)
    }

    private fun closeMedia() {
        runCatching { descriptor?.close() }
        descriptor = null
    }

    /**
     * События приходят из VLC, со своего потока.
     *
     * Состояние — `MutableStateFlow`, и писать в него оттуда безопасно; всё,
     * что здесь делается, — чтение дешёвых свойств плеера и подстановка их в
     * состояние. Ничего тяжёлого на этом потоке делать нельзя: он же
     * раскодирует кадры.
     */
    private fun onEvent(event: MediaPlayer.Event) {
        val current = player ?: return
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                // Место, на котором закрыли, ставится только теперь: до начала
                // игры длина файла ещё неизвестна и перемотка не работает.
                if (pendingSeekMs > 0) {
                    current.setTime(pendingSeekMs)
                    pendingSeekMs = 0
                }
                _state.value = _state.value.copy(
                    playing = true,
                    buffering = false,
                    ended = false,
                    durationMs = current.length,
                    seekable = current.isSeekable,
                )
                readTracks()
            }

            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped ->
                _state.value = _state.value.copy(playing = false, buffering = false)

            MediaPlayer.Event.TimeChanged ->
                _state.value = _state.value.copy(positionMs = event.timeChanged)

            MediaPlayer.Event.LengthChanged ->
                _state.value = _state.value.copy(durationMs = event.lengthChanged)

            MediaPlayer.Event.SeekableChanged ->
                _state.value = _state.value.copy(seekable = event.seekable)

            MediaPlayer.Event.Buffering ->
                // Сотня означает «набрал»: показывать ожидание после неё
                // значило бы держать кружок поверх идущего фильма.
                _state.value = _state.value.copy(buffering = event.buffering < 100f)

            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.Vout ->
                readTracks()

            MediaPlayer.Event.EndReached -> {
                val now = _state.value
                // Ссылка, которая открылась, но не отдала ни одного кадра,
                // кончается тем же событием, что и досмотренный фильм, — только
                // смотреть было нечего. Так ведёт себя оборванная раздача: VLC
                // разбирает заголовок, заводит дорожки, а на первом же чтении
                // получает конец файла. Прежде это читалось как «доиграл»:
                // человек оставался перед чёрным экраном с кнопкой «играть»,
                // которая ничего не делала, потому что играть было нечего.
                //
                // Отличает одно от другого пройденное время: у досмотренного
                // оно есть, у не начавшегося — ноль.
                if (now.positionMs <= 0) {
                    _state.value = now.copy(
                        playing = false,
                        buffering = false,
                        error = openingFailure(now.source),
                    )
                } else {
                    _state.value = now.copy(playing = false, ended = true)
                }
            }

            MediaPlayer.Event.EncounteredError ->
                _state.value = _state.value.copy(
                    playing = false,
                    buffering = false,
                    error = openingFailure(_state.value.source),
                )
        }
    }

    /**
     * Чем назвать неудачу — файлом или ссылкой.
     *
     * У них разные причины, и общее «файл не открылся» на ссылке врёт: файла
     * там нет вовсе, а есть чужой сервер, который ответил отказом, отдал
     * страницу вместо видео или оборвал раздачу на середине. Подробностей VLC
     * наружу не отдаёт, но сказать, где искать, можно и без них.
     */
    private fun openingFailure(source: VideoSource?): String =
        if (source?.network == true) "Ссылка не открылась" else "Этот файл не открылся"

    /**
     * Дорожки внутри файла.
     *
     * Спрашиваются заново на каждое их изменение: в mkv дорожки объявляются не
     * сразу, а по мере разбора, и список, прочитанный один раз в начале, у
     * многодорожечного фильма оказывается коротким.
     *
     * Пустое имя бывает у дорожек без подписи — тогда её называют номером:
     * пустая строчка в списке выбора не выбирается.
     */
    private fun readTracks() {
        val current = player ?: return
        val audio = (current.audioTracks ?: emptyArray()).map { it.toTrack("Дорожка") }
        val spu = (current.spuTracks ?: emptyArray()).map { it.toTrack("Субтитры") }
        val picture = runCatching { current.currentVideoTrack }.getOrNull()
        _state.value = _state.value.copy(
            audioTracks = audio,
            audioTrackId = current.audioTrack,
            subtitleTracks = spu,
            subtitleTrackId = current.spuTrack,
            videoWidth = picture?.width ?: _state.value.videoWidth,
            videoHeight = picture?.height ?: _state.value.videoHeight,
            videoRotated = picture?.let { sideways(it.orientation) } ?: _state.value.videoRotated,
        )
    }

    private fun MediaPlayer.TrackDescription.toTrack(fallback: String) = VideoTrack(
        id = id,
        name = name?.takeIf { it.isNotBlank() } ?: "$fallback $id",
    )

    /**
     * Метка поворота из файла: лежит ли кадр на боку.
     *
     * У VLC она названа углом, с которого начинается картинка: четыре первых
     * значения — кадр стоит как записан, четыре последних («Left…», «Right…»)
     * — записан поперёк и должен быть повёрнут на четверть оборота. Нам нужно
     * ровно это различие: у повёрнутого ширина с высотой меняются местами.
     */
    private fun sideways(orientation: Int): Boolean =
        orientation >= IMedia.VideoTrack.Orientation.LeftTop

    private companion object {

        /**
         * Сколько ждать ответа от VLC, прежде чем счесть его поток пропавшим.
         *
         * Четыре секунды: открытие тяжёлого файла на медленной памяти бывает и
         * секундным, и двухсекундным, а всё, что дольше, — уже не работа.
         * Ошибиться здесь не страшно: ценой ошибки будет лишний новый плеер,
         * а не потерянный раздел.
         */
        const val WEDGE_MS = 4_000L
    }
}
