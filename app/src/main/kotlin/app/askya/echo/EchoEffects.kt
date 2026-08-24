package app.askya.echo

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import app.askya.data.preferences.EchoPreferences
import app.askya.data.preferences.EchoSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Что из обработки звука телефон вообще умеет.
 *
 * Проверяется не по версии Android, а по тому, завёлся ли эффект: набор
 * `AudioEffect` у каждого производителя свой, и на части устройств половины
 * этого списка нет. Чего нет — то в настройках и не показывается, вместо
 * мёртвого ползунка.
 */
data class EchoEffectsState(
    val bass: Boolean = false,
    val surround: Boolean = false,
    val loudness: Boolean = false,
    val reverb: Boolean = false,
)

/**
 * Обработка звука Echo — системные эффекты поверх сессии плеера.
 *
 * Никаких сторонних движков: всё, чем можно улучшить звучание mp3 на телефоне,
 * Android уже умеет сам — подъём низов (`BassBoost`), расширение стереобазы
 * (`Virtualizer`), догромкость тихих записей (`LoudnessEnhancer`) и зал
 * (`PresetReverb`). Библиотека вроде ExoPlayer с собственными фильтрами
 * добавила бы мегабайты ради того же самого.
 *
 * Цепляются к постоянной сессии [EchoPlayer], как и эквалайзер: сессия одна на
 * всё приложение, поэтому настройка переживает смену дорожки.
 *
 * Заводятся при первом ненулевом значении, а не на старте: держать четыре
 * системных ресурса ради человека, который в настройки не заглядывал, незачем.
 * Ноль на всех ползунках отпускает их обратно.
 */
class EchoEffects(private val sessionId: Int, private val preferences: EchoPreferences) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var bass: BassBoost? = null
    private var surround: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var reverb: PresetReverb? = null

    /**
     * Что телефон умеет — отдельно от того, что сейчас заведено.
     *
     * Заведённый эффект на нуле отпускается обратно, но ползунок при этом
     * никуда деться не должен: «умеет» — это про устройство и выясняется раз,
     * а живой эффект приходит и уходит вслед за ползунком.
     */
    private var supported = EchoEffectsState()

    private val _state = MutableStateFlow(EchoEffectsState())
    val state: StateFlow<EchoEffectsState> = _state.asStateFlow()

    init {
        // Настройки применяются сами, как только их поменяли: карточка
        // настроек пишет значение, а не дёргает эффекты руками.
        scope.launch {
            preferences.settings.collect { apply(it) }
        }
    }

    /**
     * Что телефон умеет — узнаётся попыткой завести каждый эффект.
     *
     * Спрашивается один раз, при открытии настроек: до него список
     * возможностей никому не нужен.
     */
    fun probe() {
        ensureBass()
        ensureSurround()
        ensureLoudness()
        ensureReverb()
        // Заведённое на пробу тут же отпускается, если ползунок на нуле:
        // живой эффект держит обработку звука за собой, и один заход в
        // настройки не должен менять звучание.
        apply(preferences.state.value)
    }

    /** Применяет настройки к живым эффектам, заводя нужные и отпуская лишние. */
    fun apply(settings: EchoSettings) {
        applyBass(settings.bass)
        applySurround(settings.surround)
        applyLoudness(settings.loudness)
        applyReverb(settings.reverb)
        publish()
    }

    /** Заново применить то, что настроено: зовётся плеером на новой дорожке. */
    fun refresh() = apply(preferences.state.value)

    fun release() {
        runCatching { bass?.release() }
        runCatching { surround?.release() }
        runCatching { loudness?.release() }
        runCatching { reverb?.release() }
        bass = null
        surround = null
        loudness = null
        reverb = null
        supported = EchoEffectsState()
        _state.value = EchoEffectsState()
    }

    private fun applyBass(strength: Int) {
        if (strength <= 0) {
            bass = released(bass)
            return
        }
        val effect = ensureBass() ?: return
        runCatching {
            effect.setStrength(audible(strength))
            effect.enabled = true
        }
    }

    private fun applySurround(strength: Int) {
        if (strength <= 0) {
            surround = released(surround)
            return
        }
        val effect = ensureSurround() ?: return
        runCatching {
            effect.setStrength(audible(strength))
            effect.enabled = true
        }
    }

    private fun applyLoudness(gainMb: Int) {
        if (gainMb <= 0) {
            loudness = released(loudness)
            return
        }
        val effect = ensureLoudness() ?: return
        runCatching {
            effect.setTargetGain(gainMb)
            effect.enabled = true
        }
    }

    private fun applyReverb(preset: Int) {
        if (preset <= 0) {
            reverb = released(reverb)
            return
        }
        val effect = ensureReverb() ?: return
        runCatching {
            effect.preset = preset.toShort()
            effect.enabled = true
        }
    }

    /**
     * Ноль отпускает эффект, а не глушит его.
     *
     * Погашенный, но живой эффект держит обработку звука за собой, а вернуть
     * её телефону — это как раз то, чего человек хочет, сводя ползунок к нулю:
     * «как было». Отпущенный заводится обратно на первом же проценте.
     */
    private fun <T : AudioEffect> released(effect: T?): T? {
        runCatching { effect?.release() }
        return null
    }

    /**
     * Проценты ползунка — в силу, которую слышно.
     *
     * Эффект, заведённый на сессии плеера, забирает обработку звука у самого
     * телефона: у производителя она своя — «объёмный звук», «улучшение», —
     * и выключается она в тот же миг, как эффект завёлся. Поэтому линейная
     * шкала читалась наоборот: на нуле играло лучше всего, а первые проценты
     * не добавляли баса, а снимали то, что уже было, и подставляли взамен
     * почти ничего.
     *
     * Отсчёт начинается не с нуля, а с той силы, которую уже слышно: первый
     * процент добавляет к звучанию, а не отнимает у него, и дальше ползунок
     * доводит до полной. Ноль остаётся нулём — там эффекта нет вовсе.
     */
    private fun audible(strength: Int): Short =
        (FLOOR + (FULL - FLOOR) * strength / FULL).coerceIn(FLOOR, FULL).toShort()

    // Приоритет 0 — обычное приложение, не системное. Ошибка означает «этого
    // эффекта на устройстве нет»: это не сбой, просто ползунка не будет.
    private fun ensureBass(): BassBoost? {
        if (bass == null) bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
        if (bass != null) supported = supported.copy(bass = true)
        return bass
    }

    private fun ensureSurround(): Virtualizer? {
        if (surround == null) surround = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        if (surround != null) supported = supported.copy(surround = true)
        return surround
    }

    private fun ensureLoudness(): LoudnessEnhancer? {
        if (loudness == null) loudness = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        if (loudness != null) supported = supported.copy(loudness = true)
        return loudness
    }

    private fun ensureReverb(): PresetReverb? {
        if (reverb == null) reverb = runCatching { PresetReverb(0, sessionId) }.getOrNull()
        if (reverb != null) supported = supported.copy(reverb = true)
        return reverb
    }

    private fun publish() {
        _state.value = supported
    }
}

/** Ниже этой силы эффект не слышно — с неё и начинается первый процент. */
private const val FLOOR = 350

/** Полная сила системных эффектов: в этих единицах их считает Android. */
private const val FULL = 1000

/** Названия залов — те же, что у системных заготовок `PresetReverb`. */
val REVERB_NAMES = listOf(
    "Off",
    "Small room",
    "Medium room",
    "Large room",
    "Medium hall",
    "Large hall",
    "Plate",
)
