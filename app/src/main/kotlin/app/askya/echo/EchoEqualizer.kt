package app.askya.echo

import android.media.audiofx.Equalizer
import app.askya.data.preferences.EchoPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Полоса эквалайзера: частота, на которую она влияет, и её текущий подъём. */
data class EqualizerBand(
    val index: Int,
    /** Центральная частота в герцах — подпись под ползунком. */
    val frequencyHz: Int,
    /** Подъём в миллибелах: система считает в них, человек видит децибелы. */
    val levelMb: Int,
)

/** Что сейчас с эквалайзером — состояние одним значением, как у плеера. */
data class EqualizerState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    /** Имя выбранной заготовки; `null` — полосы набраны руками. */
    val preset: String? = null,
    val minMb: Int = -1_500,
    val maxMb: Int = 1_500,
)

/**
 * Эквалайзер AskyaEcho — системный `AudioEffect` поверх сессии плеера.
 *
 * Своего звукового движка нет и не нужно: полосы считает сам Android, а
 * приложение только двигает их. Цепляется к постоянной сессии `EchoPlayer`, а
 * не к конкретному `MediaPlayer`, — иначе настройка сбрасывалась бы на каждой
 * следующей песне.
 *
 * Заготовки свои ([ECHO_PRESETS]), а не вендорские: набор системных на разных
 * телефонах разный — от четырёх до ни одной, — и привычный «Vocal» на новом
 * устройстве просто исчезал. Свои задаются кривой и подгоняются под те полосы,
 * какие у телефона есть.
 *
 * Настройка переживает не только уход с экрана, но и закрытие приложения: она
 * пишется в [EchoPreferences] и восстанавливается при первом же [attach].
 *
 * На некоторых устройствах эффекта нет вовсе — тогда [EqualizerState.available]
 * остаётся `false`, и карточка честно говорит об этом вместо мёртвых ползунков.
 */
class EchoEqualizer(
    private val player: EchoPlayer,
    private val preferences: EchoPreferences,
) {

    private var effect: Equalizer? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    /** Имена заготовок для карточки — в том же порядке, в каком они заданы. */
    val presets: List<String> = ECHO_PRESETS.map { it.name }

    /**
     * Заводится при первом обращении, а не в конструкторе: `AudioEffect`
     * занимает системный ресурс, и держать его ради человека, который в
     * эквалайзер ни разу не заглянул, незачем.
     *
     * При первом заводе восстанавливает сохранённую настройку: полосы,
     * выключатель и имя заготовки под ними.
     */
    fun attach() {
        if (effect != null) {
            refresh()
            return
        }

        // Приоритет 0 — обычное приложение, не системное. Ошибка здесь
        // означает «эффекта на устройстве нет»: это не сбой, просто карточка
        // окажется пустой.
        effect = runCatching { Equalizer(0, player.audioSessionId) }.getOrNull()
        restore()
        refresh()
    }

    fun setEnabled(enabled: Boolean) {
        val eq = effect ?: return
        runCatching { eq.enabled = enabled }
        refresh()
        keep()
    }

    /** Двигает одну полосу. Ручная правка снимает выбранную заготовку. */
    fun setLevel(band: Int, levelMb: Int) {
        val eq = effect ?: return
        runCatching {
            if (!eq.enabled) eq.enabled = true
            eq.setBandLevel(band.toShort(), levelMb.toShort())
        }
        refresh(preset = null)
        keep()
    }

    /** Готовая кривая: «Rock», «Vocal boost» — подогнанная под полосы телефона. */
    fun setPreset(name: String) {
        val eq = effect ?: return
        val preset = ECHO_PRESETS.firstOrNull { it.name == name } ?: return
        val current = _state.value

        runCatching {
            if (!eq.enabled) eq.enabled = true
            val frequencies = (0 until eq.numberOfBands).map {
                eq.getCenterFreq(it.toShort()) / 1000
            }
            preset.levelsFor(frequencies, current.minMb, current.maxMb)
                .forEachIndexed { band, level -> eq.setBandLevel(band.toShort(), level.toShort()) }
        }
        refresh(preset = name)
        keep()
    }

    /** Всё по нулям: не «выключить», а «ничего не подкручено». */
    fun flat() = setPreset(ECHO_PRESETS.first().name)

    fun release() {
        runCatching { effect?.release() }
        effect = null
        _state.value = EqualizerState()
    }

    /**
     * Восстанавливает сохранённое.
     *
     * Полосы записываются числами, а не именем заготовки: заготовку человек
     * мог подправить руками, и вернуть её кривую значило бы стереть правку.
     * Имя рядом — только подсветка, и если оно не сходится с полосами, оно
     * пересчитывается в [refresh].
     */
    private fun restore() {
        val eq = effect ?: return
        val saved = preferences.state.value
        val levels = saved.bands.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (levels.isEmpty()) return

        runCatching {
            eq.enabled = saved.equalizer
            levels.take(eq.numberOfBands.toInt()).forEachIndexed { band, level ->
                eq.setBandLevel(band.toShort(), level.toShort())
            }
        }
    }

    /** Записать настройку, чтобы она пережила закрытие приложения. */
    private fun keep() {
        val current = _state.value
        preferences.setEqualizer(
            enabled = current.enabled,
            bands = current.bands.map { it.levelMb },
            preset = current.preset.orEmpty(),
        )
    }

    private fun refresh(preset: String? = _state.value.preset) {
        val eq = effect
        if (eq == null) {
            _state.value = EqualizerState(available = false)
            return
        }

        val read = runCatching {
            val range = eq.bandLevelRange
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()
            val bands = (0 until eq.numberOfBands).map { band ->
                EqualizerBand(
                    index = band,
                    // Система отдаёт частоту в миллигерцах.
                    frequencyHz = eq.getCenterFreq(band.toShort()) / 1000,
                    levelMb = eq.getBandLevel(band.toShort()).toInt(),
                )
            }
            EqualizerState(
                available = true,
                enabled = eq.enabled,
                bands = bands,
                // Имя подтверждается полосами: после перезапуска его неоткуда
                // взять, кроме как узнать кривую в том, что стоит.
                preset = preset ?: presetMatching(
                    levelsMb = bands.map { it.levelMb },
                    frequencies = bands.map { it.frequencyHz },
                    minMb = minMb,
                    maxMb = maxMb,
                ),
                minMb = minMb,
                maxMb = maxMb,
            )
        }.getOrNull()

        _state.value = read ?: EqualizerState(available = false)
    }
}

/** Подъём словами: «+3 дБ». Ноль показывается ровно нулём, без знака. */
fun formatGain(levelMb: Int): String {
    val db = levelMb / 100f
    return when {
        db > 0.05f -> "+%.0f".format(db)
        db < -0.05f -> "%.0f".format(db)
        else -> "0"
    }
}

/** Частота словами: до килогерца — в герцах, дальше — «4k». */
fun formatFrequency(hz: Int): String =
    if (hz >= 1000) "${hz / 1000}k" else "$hz"
