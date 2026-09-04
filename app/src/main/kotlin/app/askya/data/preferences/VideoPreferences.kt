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
import app.askya.video.VideoScale
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
 * Как человек смотрит видео.
 *
 * Одно на все файлы, а не на каждый: шаг перемотки и жесты — это привычка
 * руки, и подбирать её заново к каждому фильму незачем. Скорость и способ
 * вписать кадр, наоборот, живут только на время просмотра — их правят посреди
 * фильма и под этот фильм, — но начальное значение всё же помнится: тот, кто
 * смотрит лекции на полуторной, смотрит так все.
 */
data class VideoSettings(
    /** Скорость, с которой открывается всякий новый файл. */
    val rate: Float = 1f,
    /** Как кадр ложится в экран у нового файла. */
    val scale: VideoScale = VideoScale.FIT,
    /** Шаг двойного касания и жеста перемотки, секунд. */
    val seekStepSeconds: Int = 10,
    /** Продолжать с места, на котором закрыли. */
    val resume: Boolean = true,
    /** Яркость слева, громкость справа — как в VLC. */
    val gestures: Boolean = true,
    /** Не гасить экран, пока идёт фильм. */
    val keepAwake: Boolean = true,
    /**
     * Ставить экран по кадру самому: широкий — поперёк, снятый стоя — стоя.
     * Выключенное, оно оставляет телефон как есть; кнопка поворота в шапке
     * плеера работает в обоих случаях.
     */
    val autoRotate: Boolean = true,
    /**
     * Сколько коробок стоит в ряду на полке.
     *
     * Полка — не таблица с настраиваемой плотностью, а вещь, и число здесь
     * значит размер коробки: две — витрина с афишами, шесть — полка, какие
     * бывают у собравшего сотню фильмов. Четыре — то, при котором обложку ещё
     * узнаёшь, а ряд уже читается как ряд.
     *
     * Хранится в привычках, а не в состоянии экрана: калибруют полку один раз
     * и под свой телефон, и заново при каждом входе в раздел этого не делают.
     */
    val shelfColumns: Int = SHELF_COLUMNS,
)

/** Сколько коробок в ряду по умолчанию. */
const val SHELF_COLUMNS = 4

/** Между чем и чем выбирают при калибровке полки. */
val SHELF_RANGE = 2..6

private val Context.videoStore: DataStore<Preferences> by preferencesDataStore(name = "video")

/**
 * Хранилище AskyaV: привычки просмотра и место остановки в каждом файле.
 *
 * Своё, отдельно от настроек приложения и от читалки, — по той же причине, по
 * которой у читалки своё: место в фильме пишется на каждой остановке, и
 * складывать это в один файл с остальными настройками значило бы переписывать
 * его без конца.
 *
 * Место помнится по отпечатку ссылки, а не по ней самой: ссылки на документы
 * бывают длиной в сотни знаков. Ровно как в [ReaderPreferences] — и по той же
 * причине: один и тот же фильм открывают и из списка, и из проводника, и это
 * одно и то же место.
 */
class VideoPreferences(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: Flow<VideoSettings> = context.videoStore.data.map { it.toSettings() }

    val state: StateFlow<VideoSettings> =
        settings.stateIn(scope, SharingStarted.Eagerly, VideoSettings())

    fun setRate(value: Float) = put { it[KEY_RATE] = value.coerceIn(0.25f, 4f) }

    fun setScale(scale: VideoScale) = put { it[KEY_SCALE] = scale.name }

    fun setSeekStep(seconds: Int) = put { it[KEY_STEP] = seconds.coerceIn(5, 60) }

    fun setResume(value: Boolean) = put { it[KEY_RESUME] = value }

    fun setGestures(value: Boolean) = put { it[KEY_GESTURES] = value }

    fun setKeepAwake(value: Boolean) = put { it[KEY_AWAKE] = value }

    fun setAutoRotate(value: Boolean) = put { it[KEY_ROTATE] = value }

    /** Калибровка полки: сколько коробок в ряду. */
    fun setShelfColumns(value: Int) = put {
        it[KEY_COLUMNS] = value.coerceIn(SHELF_RANGE.first, SHELF_RANGE.last)
    }

    /** Место, на котором закрыли этот файл, в миллисекундах. */
    fun spot(uri: String): Flow<Long> = context.videoStore.data.map { it[spotKey(uri)] ?: 0L }

    /**
     * Места сразу для целого списка — по нему в библиотеке рисуется полоска
     * досмотренного.
     *
     * Одним потоком на весь список, а не по [spot] на строчку: строчек бывает
     * сотня, и сотня подписок на одно и то же хранилище — сотня чтений файла
     * при каждой его правке. Обратно из ключа ссылку не достать (ключ —
     * отпечаток), поэтому спрашивается наоборот: для каждой известной ссылки
     * считается её ключ и берётся из уже прочитанного.
     */
    fun spotsOf(uris: List<String>): Flow<Map<String, Long>> = context.videoStore.data.map { all ->
        uris.mapNotNull { uri -> all[spotKey(uri)]?.let { uri to it } }.toMap()
    }

    /**
     * Запомнить место.
     *
     * Начало и конец не помнятся вовсе: у файла, закрытого на первых секундах,
     * запоминать нечего, а досмотренный до конца должен открыться сначала —
     * иначе «продолжить» упирается в титры. Отсюда и стирание записи вместо
     * нуля: пустой ключ и «мы тут не были» — это одно и то же.
     */
    fun remember(uri: String, positionMs: Long, durationMs: Long) = put { preferences ->
        val nearStart = positionMs < START_EDGE_MS
        val nearEnd = durationMs > 0 && positionMs > durationMs - END_EDGE_MS
        if (nearStart || nearEnd) preferences.remove(spotKey(uri))
        else preferences[spotKey(uri)] = positionMs
    }

    /** Забыть место — по кнопке «смотреть сначала». */
    fun forget(uri: String) = put { it.remove(spotKey(uri)) }

    /**
     * Имя, которым человек назвал этот файл сам.
     *
     * Пустой ответ означает «своего имени нет» — тогда ролик зовётся так, как
     * называется файл.
     */
    fun nameOf(uri: String): Flow<String> = context.videoStore.data.map { it[nameKey(uri)] ?: "" }

    /**
     * Свои имена сразу для целого списка — тем же одним потоком, что и места
     * остановки, и ровно по той же причине: сотня подписок на одно хранилище
     * означала бы сотню чтений файла при каждой его правке.
     */
    fun namesOf(uris: List<String>): Flow<Map<String, String>> =
        context.videoStore.data.map { all ->
            uris.mapNotNull { uri ->
                all[nameKey(uri)]?.takeIf { it.isNotBlank() }?.let { uri to it }
            }.toMap()
        }

    /**
     * Назвать файл по-своему.
     *
     * **Переименовывается ролик в разделе, а не файл на телефоне.** Файл
     * принадлежит не приложению: чтобы переписать его имя в `MediaStore`,
     * Android 11+ требует отдельного согласия на каждый файл — системное окно
     * поверх раздела на каждое переименование, — а файлам, отданным на один
     * заход через системный выбор, имени не поменять вовсе. Правило то же, что
     * и с доступом ко всему хранилищу: раздел не берёт над чужими файлами
     * власти больше, чем нужно, чтобы их показать.
     *
     * Поэтому имя живёт рядом с местом остановки — в хранилище раздела, по
     * отпечатку ссылки. В проводнике и в галерее файл остаётся собой; в Askya
     * он зовётся так, как его назвали. Пустое имя стирает запись: «назвать
     * пустым» и «вернуть настоящее имя» — это одно и то же.
     */
    fun rename(uri: String, title: String) = put { preferences ->
        val clean = title.trim()
        if (clean.isBlank()) preferences.remove(nameKey(uri))
        else preferences[nameKey(uri)] = clean
    }

    private fun put(edit: (MutablePreferences) -> Unit) {
        scope.launch { context.videoStore.edit(edit) }
    }

    private fun Preferences.toSettings() = VideoSettings(
        rate = this[KEY_RATE] ?: 1f,
        scale = this[KEY_SCALE]?.let { name -> VideoScale.entries.firstOrNull { it.name == name } }
            ?: VideoScale.FIT,
        seekStepSeconds = this[KEY_STEP] ?: 10,
        resume = this[KEY_RESUME] ?: true,
        gestures = this[KEY_GESTURES] ?: true,
        keepAwake = this[KEY_AWAKE] ?: true,
        autoRotate = this[KEY_ROTATE] ?: true,
        shelfColumns = (this[KEY_COLUMNS] ?: SHELF_COLUMNS)
            .coerceIn(SHELF_RANGE.first, SHELF_RANGE.last),
    )

    private fun spotKey(uri: String) = longPreferencesKey("at_" + fingerprint(uri))

    private fun nameKey(uri: String) = stringPreferencesKey("name_" + fingerprint(uri))

    /** Отпечаток ссылки: по нему в хранилище ищется и место, и своё имя. */
    private fun fingerprint(uri: String) = Integer.toHexString(uri.hashCode())

    /**
     * Последние ссылки, по которым смотрели, — свежая первой.
     *
     * Открытое по ссылке видео нигде не оставалось: «ни записи, ни строчки в
     * списке». Для файла из проводника это правильно — он пришёл и ушёл, — а
     * для потока с домашней камеры или записи с сервера нет: её открывают
     * каждый вечер и каждый раз набирают заново.
     *
     * Пять, а не двадцать: это не история просмотров, а «то, что открывают
     * снова». Строкой через перевод строки — как теги в записи: в адресе
     * перевода строки не бывает, и разбор обратно однозначен.
     */
    val recentLinks: Flow<List<String>> = context.videoStore.data.map { preferences ->
        (preferences[KEY_LINKS] ?: "").split('\n').filter { it.isNotBlank() }
    }

    suspend fun rememberLink(link: String) {
        context.videoStore.edit { preferences ->
            val kept = (preferences[KEY_LINKS] ?: "")
                .split('\n')
                .filter { it.isNotBlank() && it != link }
            preferences[KEY_LINKS] = (listOf(link) + kept).take(5).joinToString("\n")
        }
    }

    suspend fun forgetLink(link: String) {
        context.videoStore.edit { preferences ->
            preferences[KEY_LINKS] = (preferences[KEY_LINKS] ?: "")
                .split('\n')
                .filter { it.isNotBlank() && it != link }
                .joinToString("\n")
        }
    }

    private companion object {
        val KEY_RATE = floatPreferencesKey("rate")
        val KEY_SCALE = stringPreferencesKey("scale")
        val KEY_STEP = intPreferencesKey("seek_step")
        val KEY_RESUME = booleanPreferencesKey("resume")
        val KEY_GESTURES = booleanPreferencesKey("gestures")
        val KEY_AWAKE = booleanPreferencesKey("keep_awake")
        val KEY_ROTATE = booleanPreferencesKey("auto_rotate")
        val KEY_COLUMNS = intPreferencesKey("shelf_columns")
        val KEY_LINKS = stringPreferencesKey("recent_links")

        /** Первые полминуты — ещё не «место, где остановились». */
        const val START_EDGE_MS = 30_000L

        /** Последние полминуты — уже титры. */
        const val END_EDGE_MS = 30_000L
    }
}
