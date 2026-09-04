package app.askya.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Настройки AskyaEcho: то, что в музыкальных плеерах обычно лежит под
 * шестерёнкой, — сила баса, объём, догромкость, скорость и мелкие привычки
 * плеера.
 *
 * [bass], [surround] и [loudness] — не улучшайзеры на свой вкус, а системные
 * эффекты Android (`BassBoost`, `Virtualizer`, `LoudnessEnhancer`). Сила
 * хранится числом, а не выключателем: «немного баса» и «бас до упора» — разные
 * желания, и одним флажком их не выразить.
 */
data class EchoSettings(
    /** Подъём низов, 0…1000 — в этих единицах его считает система. */
    val bass: Int = 0,
    /** Объём — расширение стереобазы, 0…1000. */
    val surround: Int = 0,
    /** Догромкость в миллибелах, 0…1500. */
    val loudness: Int = 0,
    /** Зал: 0 — сухо, дальше заготовки `PresetReverb`. */
    val reverb: Int = 0,
    /** Скорость воспроизведения: 1.0 — как записано. */
    val speed: Float = 1f,
    /** Затухание вместо обрыва на паузе. */
    val fade: Boolean = true,
    /** Пауза, когда выдёргивают наушники. */
    val pauseOnUnplug: Boolean = true,
    /** Возвращаться к тому, что играло, при следующем запуске. */
    val resumeLast: Boolean = true,
    /**
     * Спрашивать на входе в раздел, что поставить.
     *
     * Включено по умолчанию: молчащий плеер с кнопкой «играть» — это вопрос
     * без вариантов ответа, и любой из ответов человек всё равно ищет в
     * списках. Выключатель нужен тому, кто входит в Echo ради одной кнопки
     * «продолжить»: ему шесть карточек — турникет, ровно как заставка на
     * двадцатом заходе.
     */
    val askOnStart: Boolean = true,
    /** Полосы эквалайзера, миллибелами через запятую. */
    val bands: String = "",
    /** Имя выбранной заготовки эквалайзера; пустое — своя настройка. */
    val preset: String = "",
    /** Включён ли эквалайзер вовсе. */
    val equalizer: Boolean = false,
    /**
     * Вспышки вокруг обложки в ритм музыки.
     *
     * Включены по умолчанию: то, ради чего плеер открывают на весь экран, —
     * это музыка, и обложка, отвечающая ей, часть того же. Выключатель нужен
     * для другого: спектр читается через микрофонное право, и человеку,
     * который его не дал, раздел не должен ничего обещать.
     */
    val pulse: Boolean = true,
)

/** Дорожка, на которой плеер остановился в прошлый раз. */
data class EchoLastTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val albumId: Long,
    val durationMs: Long,
    val positionMs: Long,
)

private val Context.echoStore: DataStore<Preferences> by preferencesDataStore(name = "echo")

/**
 * Хранилище настроек Echo.
 *
 * Своё, а не общее с [SettingsPreferences]: настройки звука меняют, стоя в
 * плеере, десятками подряд — ползунок баса пишет значение на каждое движение
 * пальца, — и складывать их в один файл с рассказом о себе значило бы
 * переписывать его целиком по сто раз за вечер.
 *
 * Наружу отдаётся [state] — уже собранное значение, а не поток ключей: и
 * эффекты, и карточка настроек читают всё сразу, а не по одному полю. Эффектам
 * нужно именно последнее известное значение: их применяют в тот момент, когда
 * дорожка уже начала играть, и ждать там первого значения из потока негде.
 */
class EchoPreferences(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: Flow<EchoSettings> = context.echoStore.data.map { it.toSettings() }

    val state: StateFlow<EchoSettings> =
        settings.stateIn(scope, SharingStarted.Eagerly, EchoSettings())

    val lastTrack: Flow<EchoLastTrack?> = context.echoStore.data.map { it.toLastTrack() }

    /**
     * Та же дорожка последним известным значением: плеер спрашивает её в
     * момент нажатия «играть», и ждать там первого значения из потока негде.
     */
    val last: StateFlow<EchoLastTrack?> = lastTrack.stateIn(scope, SharingStarted.Eagerly, null)

    fun setBass(value: Int) = put { it[KEY_BASS] = value.coerceIn(0, 1000) }

    fun setSurround(value: Int) = put { it[KEY_SURROUND] = value.coerceIn(0, 1000) }

    fun setLoudness(value: Int) = put { it[KEY_LOUDNESS] = value.coerceIn(0, 1500) }

    fun setReverb(value: Int) = put { it[KEY_REVERB] = value.coerceIn(0, 6) }

    fun setSpeed(value: Float) = put { it[KEY_SPEED] = value.coerceIn(0.5f, 2f) }

    fun setFade(value: Boolean) = put { it[KEY_FADE] = value }

    fun setPauseOnUnplug(value: Boolean) = put { it[KEY_UNPLUG] = value }

    fun setResumeLast(value: Boolean) = put { it[KEY_RESUME] = value }

    fun setAskOnStart(value: Boolean) = put { it[KEY_ASK] = value }

    fun setPulse(value: Boolean) = put { it[KEY_PULSE] = value }

    /** Эквалайзер целиком: включён ли он, чем набран и как называется. */
    fun setEqualizer(enabled: Boolean, bands: List<Int>, preset: String) = put {
        it[KEY_EQ_ON] = enabled
        it[KEY_EQ_BANDS] = bands.joinToString(",")
        it[KEY_EQ_PRESET] = preset
    }

    /** Где остановились. Пишется на паузе и при смене дорожки, а не по тику. */
    fun remember(
        uri: String,
        title: String,
        artist: String,
        albumId: Long,
        durationMs: Long,
        positionMs: Long,
    ) = put {
        it[KEY_LAST_URI] = uri
        it[KEY_LAST_TITLE] = title
        it[KEY_LAST_ARTIST] = artist
        it[KEY_LAST_ALBUM] = albumId
        it[KEY_LAST_DURATION] = durationMs
        it[KEY_LAST_POSITION] = positionMs
    }

    /** Настройки звука сбрасываются разом — эквалайзера это не касается. */
    fun resetSound() = put {
        it.remove(KEY_BASS)
        it.remove(KEY_SURROUND)
        it.remove(KEY_LOUDNESS)
        it.remove(KEY_REVERB)
        it.remove(KEY_SPEED)
    }

    private fun put(edit: (MutablePreferences) -> Unit) {
        scope.launch { context.echoStore.edit(edit) }
    }

    private fun Preferences.toSettings() = EchoSettings(
        bass = this[KEY_BASS] ?: 0,
        surround = this[KEY_SURROUND] ?: 0,
        loudness = this[KEY_LOUDNESS] ?: 0,
        reverb = this[KEY_REVERB] ?: 0,
        speed = this[KEY_SPEED] ?: 1f,
        fade = this[KEY_FADE] ?: true,
        pauseOnUnplug = this[KEY_UNPLUG] ?: true,
        resumeLast = this[KEY_RESUME] ?: true,
        askOnStart = this[KEY_ASK] ?: true,
        bands = this[KEY_EQ_BANDS].orEmpty(),
        preset = this[KEY_EQ_PRESET].orEmpty(),
        equalizer = this[KEY_EQ_ON] ?: false,
        pulse = this[KEY_PULSE] ?: true,
    )

    private fun Preferences.toLastTrack(): EchoLastTrack? {
        val uri = this[KEY_LAST_URI] ?: return null
        return EchoLastTrack(
            uri = uri,
            title = this[KEY_LAST_TITLE].orEmpty(),
            artist = this[KEY_LAST_ARTIST].orEmpty(),
            albumId = this[KEY_LAST_ALBUM] ?: 0,
            durationMs = this[KEY_LAST_DURATION] ?: 0,
            positionMs = this[KEY_LAST_POSITION] ?: 0,
        )
    }

    private companion object {
        val KEY_BASS = intPreferencesKey("bass")
        val KEY_SURROUND = intPreferencesKey("surround")
        val KEY_LOUDNESS = intPreferencesKey("loudness")
        val KEY_REVERB = intPreferencesKey("reverb")
        val KEY_SPEED = floatPreferencesKey("speed")
        val KEY_FADE = booleanPreferencesKey("fade")
        val KEY_UNPLUG = booleanPreferencesKey("pause_on_unplug")
        val KEY_RESUME = booleanPreferencesKey("resume_last")
        val KEY_ASK = booleanPreferencesKey("ask_on_start")
        val KEY_PULSE = booleanPreferencesKey("cover_pulse")
        val KEY_EQ_ON = booleanPreferencesKey("equalizer_on")
        val KEY_EQ_BANDS = stringPreferencesKey("equalizer_bands")
        val KEY_EQ_PRESET = stringPreferencesKey("equalizer_preset")
        val KEY_LAST_URI = stringPreferencesKey("last_uri")
        val KEY_LAST_TITLE = stringPreferencesKey("last_title")
        val KEY_LAST_ARTIST = stringPreferencesKey("last_artist")
        val KEY_LAST_ALBUM = longPreferencesKey("last_album")
        val KEY_LAST_DURATION = longPreferencesKey("last_duration")
        val KEY_LAST_POSITION = longPreferencesKey("last_position")
    }
}
