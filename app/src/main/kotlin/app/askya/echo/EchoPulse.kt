package app.askya.echo

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Сколько сейчас звука — тремя полосами и ударом.
 *
 * Три полосы, а не одна громкость: бас, середина и верх живут в песне по-своему
 * — бочка бьёт, голос тянется, тарелки сыплются, — и вспышка, собранная из
 * одной цифры, пульсировала бы одинаково под любую музыку.
 *
 * [hit] — не «сейчас удар», а счётчик ударов: состояние читают кадры экрана, и
 * поднятый на двадцатую долю секунды флажок между двумя кадрами пропадал бы.
 * Растущее число пропустить нельзя — по нему видно, что удар был, даже если
 * кадр опоздал.
 *
 * Все значения приведены к 0…1 бегущим потолком (см. [EchoPulse]): тихая
 * запись должна пульсировать так же, как громкая, иначе половина музыки не
 * даёт вспышек вовсе.
 */
data class EchoBeat(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val high: Float = 0f,
    /** Общая громкость — ею дышит свечение между ударами. */
    val level: Float = 0f,
    /** Сколько ударов насчитано с начала слушания. */
    val hit: Int = 0,
)

/**
 * Пульс Echo — то, чем обложка отвечает музыке.
 *
 * Читает не файл, а то, что уже звучит: системный `Visualizer` на сессии
 * плеера отдаёт спектр того, что идёт в динамик, — вместе с эквалайзером,
 * басом и залом. Разбирать mp3 самим значило бы показывать не то, что человек
 * слышит.
 *
 * Android считает чтение звука своей же сессии доступом к микрофону и требует
 * `RECORD_AUDIO`. Разрешение в Askya уже объявлено — им живёт надиктовка
 * заметок, — но выдают его отдельно, и без него `Visualizer` не заводится:
 * [attach] вернёт `false`, а обложка будет дышать сама по себе (см.
 * `CoverPulse`).
 *
 * Заводится и отпускается вслед за музыкой, а не живёт всё время: слушание
 * звука держит системный ресурс, и на паузе оно не нужно ни экрану, ни
 * телефону.
 */
class EchoPulse(private val sessionId: Int) {

    private var visualizer: Visualizer? = null

    private val _beat = MutableStateFlow(EchoBeat())
    val beat: StateFlow<EchoBeat> = _beat.asStateFlow()

    /** Слышит ли пульс звук на самом деле — или обложка дышит вхолостую. */
    private val _hearing = MutableStateFlow(false)
    val hearing: StateFlow<Boolean> = _hearing.asStateFlow()

    /**
     * Бегущий потолок каждой полосы.
     *
     * Спектр приходит в единицах, у которых нет верха: у громкой записи бас
     * втрое выше, чем у тихой, и постоянный делитель показал бы одну стеной, а
     * другую ровной линией. Потолок поднимается мгновенно и оседает медленно —
     * так вспышка подстраивается под песню, а не под самый громкий её удар за
     * вечер.
     */
    private var bassCeiling = MIN_CEILING
    private var midCeiling = MIN_CEILING
    private var highCeiling = MIN_CEILING

    /** Средний бас последних мгновений — от него отсчитывается удар. */
    private var bassAverage = 0f

    private var lastHitAt = 0L
    private var hits = 0

    /**
     * Заводит слушание. `false` означает «нечем»: нет разрешения на микрофон
     * или устройство не отдаёт спектр сессии.
     *
     * Повторный вызов ничего не делает — экран зовёт его на каждой смене
     * дорожки, и пересоздавать `Visualizer` ради этого незачем.
     */
    fun attach(): Boolean {
        if (visualizer != null) return true

        val started = runCatching {
            val reader = Visualizer(sessionId)
            // Окно поменьше — грубее по частоте: 1024 точки дают полосы около
            // 40 Гц шириной, чего для «бас, середина, верх» хватает с запасом.
            // Больше некоторые телефоны и не берут.
            reader.captureSize = min(CAPTURE_SIZE, Visualizer.getCaptureSizeRange()[1].toInt())
            reader.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (fft != null) read(fft, samplingRate)
                    }
                },
                min(Visualizer.getMaxCaptureRate(), CAPTURE_RATE),
                // Волна не нужна: вспышка считается по спектру, а второй поток
                // с той же частотой — работа впустую.
                false,
                true,
            )
            reader.enabled = true
            reader
        }.getOrNull()

        visualizer = started
        _hearing.value = started != null
        return started != null
    }

    /**
     * Отпускает слушание и гасит пульс.
     *
     * Гасит в ноль, а не оставляет последнее значение: иначе обложка замирала
     * бы на паузе с той яркостью, на которой музыку остановили.
     */
    fun release() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        _hearing.value = false
        _beat.value = EchoBeat()
        bassCeiling = MIN_CEILING
        midCeiling = MIN_CEILING
        highCeiling = MIN_CEILING
        bassAverage = 0f
    }

    /**
     * Разбор спектра: из байтов `Visualizer` — три полосы и удар.
     *
     * Байты лежат парами «действительная, мнимая» на каждую частоту, кроме
     * первых двух: `fft[0]` — постоянная составляющая, `fft[1]` — самая
     * высокая частота. Сила частоты — длина этой пары.
     *
     * Границы полос заданы в герцах, а не в номерах отсчётов: частота
     * дискретизации у телефонов разная (44,1 и 48 кГц), и «двадцатый отсчёт»
     * на них означал бы разные звуки.
     */
    private fun read(fft: ByteArray, samplingRateMilliHz: Int) {
        val bins = fft.size / 2
        if (bins < 2) return

        // Частота приходит в миллигерцах; половина её — верх спектра,
        // разложенный по bins отсчётам.
        val nyquist = samplingRateMilliHz / 2000f
        if (nyquist <= 0f) return
        val perBin = nyquist / bins

        var bass = 0f
        var mid = 0f
        var high = 0f

        for (index in 1 until bins) {
            val real = fft[index * 2].toFloat()
            val imaginary = fft[index * 2 + 1].toFloat()
            val magnitude = hypot(real, imaginary)
            val frequency = index * perBin
            when {
                frequency < BASS_TOP -> bass += magnitude
                frequency < MID_TOP -> mid += magnitude
                frequency < HIGH_TOP -> high += magnitude
            }
        }

        // Корень вместо суммы: слух отзывается на громкость не прямой линией, и
        // сырая сумма даёт вспышку, которая почти всё время лежит у самого низа.
        bass = sqrt(bass)
        mid = sqrt(mid)
        high = sqrt(high)

        bassCeiling = ceiling(bassCeiling, bass)
        midCeiling = ceiling(midCeiling, mid)
        highCeiling = ceiling(highCeiling, high)

        val bassLevel = (bass / bassCeiling).coerceIn(0f, 1f)
        val midLevel = (mid / midCeiling).coerceIn(0f, 1f)
        val highLevel = (high / highCeiling).coerceIn(0f, 1f)

        // Удар — это бас, выскочивший над своим же средним. Не «громче
        // порога»: порог, поставленный числом, в громкой песне срабатывает
        // непрерывно, а в тихой не срабатывает никогда.
        val now = System.currentTimeMillis()
        val loud = bassLevel > bassAverage * BEAT_JUMP && bassLevel > BEAT_FLOOR
        // Пауза между ударами: без неё один удар бочки, растянутый на три окна
        // спектра, считался бы тремя.
        if (loud && now - lastHitAt > BEAT_GAP_MS) {
            hits++
            lastHitAt = now
        }
        bassAverage = bassAverage * (1 - AVERAGE_STEP) + bassLevel * AVERAGE_STEP

        _beat.value = EchoBeat(
            bass = bassLevel,
            mid = midLevel,
            high = highLevel,
            // Середина весит больше: в ней голос и мелодия, и общее свечение
            // должно идти за песней, а не за одной бочкой.
            level = (bassLevel * 0.35f + midLevel * 0.45f + highLevel * 0.2f).coerceIn(0f, 1f),
            hit = hits,
        )
    }

    /** Потолок: вверх сразу, вниз медленно и не ниже разумного низа. */
    private fun ceiling(current: Float, value: Float): Float =
        max(MIN_CEILING, max(value, current * CEILING_DECAY))
}

/** Точек в окне спектра. Больше — точнее по частоте и тяжелее для телефона. */
private const val CAPTURE_SIZE = 1024

/** Сколько раз в секунду спрашивать спектр, в милигерцах: двадцать. */
private const val CAPTURE_RATE = 20_000

/** Верх баса, середины и верхов в герцах — по тому, что в них слышно. */
private const val BASS_TOP = 200f
private const val MID_TOP = 2_000f
private const val HIGH_TOP = 10_000f

/** Ниже этого потолок не опускается: иначе тишина разгоняется до вспышек. */
private const val MIN_CEILING = 8f

/** Насколько потолок оседает за одно окно — около секунды до половины. */
private const val CEILING_DECAY = 0.97f

/** Во сколько раз бас должен выскочить над средним, чтобы это был удар. */
private const val BEAT_JUMP = 1.3f

/** Тишину ударом не считаем, как бы она ни выскакивала над собой. */
private const val BEAT_FLOOR = 0.18f

/** Ближе этого удары не различаются — быстрее 300 в минуту не бывает. */
private const val BEAT_GAP_MS = 200L

/** Шаг, которым среднее подтягивается к текущему басу. */
private const val AVERAGE_STEP = 0.12f
