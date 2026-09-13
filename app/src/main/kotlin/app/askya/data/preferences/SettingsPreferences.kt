package app.askya.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.askya.ui.theme.AskyaPalette
import app.askya.ui.theme.FlowerColor
import app.askya.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Настройки приложения целиком — то, что не принадлежит ни одному разделу
 * настолько, чтобы жить в его хранилище.
 *
 * Здесь же лежат и настройки тех разделов, которые пишутся редко: у читалки,
 * плеера, видео и погоды свои хранилища не из красоты, а потому что они
 * переписываются десятки раз за вечер. Настройка «какая тренировка предлагается
 * первой» меняется раз в жизни, и заводить ради двух таких целый файл было бы
 * последовательностью в ущерб смыслу.
 */
data class AppSettings(
    /** Начинать ли неделю с понедельника. */
    val weekStartsMonday: Boolean = true,
    /**
     * Когда показывать заставку целиком.
     *
     * Три состояния, а не два, и по счёту заходов. Цветок, дописанное пером имя
     * и приветствие держатся полторы секунды — в первый раз за день это лучшее
     * место в приложении. Но Askya — блокнот: в неё заходят на десять секунд
     * записать мысль и заходят помногу раз, и на двадцатый заход церемония
     * становится турникетом. Прежний выключатель предлагал только «совсем без
     * неё» — то есть выбор между турникетом и пустым кремовым листом.
     *
     * Ни одно состояние не убирает цветок совсем: [SplashWhen.NEVER]
     * пропускает церемонию тем же путём, что и касание по экрану.
     */
    val splash: SplashWhen = SplashWhen.DAILY,
    /** С какого раздела открывается приложение. Маршрут, а не название. */
    val startRoute: String = "today",
    /** Разворачивать ли список дел в новый день сам. */
    val autoFillDay: Boolean = true,
    /**
     * Класть ли список дела в шторку уведомлений.
     *
     * Включено: дело со списком заводят затем, чтобы отмечать в нём на ходу, а
     * шторка — самый короткий путь к отметке. Уведомление тихое и низкой
     * важности; выключенное, оно убирается из шторки сразу
     * ([app.askya.shade.TaskShade]).
     */
    val deedShade: Boolean = true,
    /**
     * Показывать ли дела дня на экране блокировки
     * ([app.askya.widget.DayLockScreen]).
     *
     * Выключено по умолчанию: это уведомление, которое висит всегда, и заводить
     * такое без спроса нельзя — его место на экране блокировки выбирает человек.
     */
    val dayLockScreen: Boolean = false,
    /**
     * Открывать ли дело со списком сразу на весь экран.
     *
     * Включено: список внутри дела — это то, ради чего в этот день и заходят,
     * и показывать его карточкой в треть экрана значит просить открыть его
     * ещё раз. Карточка сворачивается одним касанием, и под ней остаётся то
     * же расписание.
     */
    val deedFullScreen: Boolean = true,
    /**
     * Светлая, тёмная или как в системе.
     *
     * Хранилище знает о теме и гамме потому, что это ответы, которые человек
     * даёт один раз и надолго. Обе перечислимых живут в `ui/theme` — они и
     * есть краски, и заводить в domain их бледную копию значило бы держать два
     * списка вместо одного.
     */
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Цветовая гамма — краска, которой отмечено важное. */
    val palette: AskyaPalette = AskyaPalette.CORAL,
    /**
     * Краска цветка Askya — знака приложения.
     *
     * Своя настройка, а не поле гаммы: гамма красит письмо внутри листа,
     * цветок — лицо приложения, и совпадать они не обязаны. См.
     * [FlowerColor].
     */
    val flower: FlowerColor = FlowerColor.SUNSET,
    /**
     * Краска цветка **на заставке** — отдельно от той, которой он покрашен
     * всюду ещё.
     *
     * `null` значит «как везде» и стоит умолчанием: два цвета у одного знака —
     * это не то, чего человек хочет по умолчанию, а то, о чём он просит,
     * когда хочет.
     *
     * Просьба звучала буквально так: цветок в приложении один, а на заставке
     * хочется другой. Заставка — единственное место, где цветок стоит один и
     * во весь экран; там он не знак раздела, а картинка, и выбирать её порознь
     * осмысленно ровно поэтому.
     */
    val splashFlower: FlowerColor? = null,
    /**
     * Напоминать ли раз в месяц про «Слепок».
     *
     * Включено по умолчанию, и это не самоуправство: слепок — единственная
     * страховка от разбитого телефона, а забывают о нём ровно до того дня,
     * когда он понадобился. Выключается там же, где живёт сам слепок.
     */
    val snapshotReminder: Boolean = true,
    /**
     * Когда сделан последний слепок, в миллисекундах эпохи. Ноль — ни разу.
     *
     * Это не настройка, а память, и лежит она здесь по той же причине, что
     * оборот приветствия: заводить ради одного числа целый файл дороже, чем
     * держать строчку не совсем на своём месте.
     */
    val snapshotAt: Long = 0L,
    /**
     * Приложения, которые недавно выбирали для моста, свежие первыми.
     *
     * Память, а не настройка: она нужна одному окну выбора, где полторы сотни
     * строк и человек второй раз ищет то же самое. Списком строк, склеенным
     * переводом строки, — как теги в записи: в имени пакета перевода строки не
     * бывает, и разбор обратно однозначен.
     */
    val recentApps: List<String> = emptyList(),
    /**
     * Дела, за которыми человек следит в «Прожитом», в порядке выбора.
     *
     * Названиями, а не ссылками на строки: одно и то же дело живёт в году
     * сотней разных записей, и связывать выбор с какой-то одной из них значило
     * бы потерять счёт, как только эту одну уберут.
     *
     * Здесь, а не в своём хранилище, по той же причине, что и остальное в этом
     * файле: список складывают однажды и потом годами не трогают. Склеен
     * переводом строки, как [recentApps]: в названии дела перевода строки не
     * бывает — поле однострочное, — и разбор обратно однозначен.
     */
    val watchedDeeds: List<String> = emptyList(),
    /**
     * Откуда брать обновление — «владелец/репозиторий» на GitHub.
     *
     * Настройкой, а не константой в коде: Askya собирают и раздают из одного
     * места, но место это может смениться, а приложение, у которого адрес
     * зашит, после переезда молча перестало бы находить новые сборки.
     *
     * Пустая строка — «проверять негде»: тогда обновление не спрашивается ни
     * при какой настройке, и в сеть приложение из-за него не выходит.
     */
    val updateSource: String = DEFAULT_UPDATE_SOURCE,
    /**
     * Смотреть, нет ли новой сборки, при запуске.
     *
     * Выключено по умолчанию, и это не осторожность ради осторожности:
     * «в сеть не выходит» — обещание, данное в манифесте, и снимать его молча
     * нельзя. Включённый выключатель — согласие человека на один короткий
     * запрос к GitHub не чаще раза в сутки; выключенный оставляет проверку
     * кнопкой в настройках, то есть делом рук.
     */
    val updateOnStart: Boolean = false,
)

/**
 * Куда Askya смотрит за обновлениями, пока не сказали иначе.
 *
 * Репозиторий заведён под сборки: исходников в нём может не быть вовсе, нужен
 * он ради выпусков, к которым приложены `.apk`. Публичный — приложение ходит
 * туда без ключа, а ключ, зашитый в сборку, был бы ключом у всякого, кто эту
 * сборку разберёт.
 *
 * Умолчанием, а не константой намертво: строка в настройках его перекрывает.
 * Askya собирают и выкладывают из одного места, но место может смениться, а
 * приложение с зашитым адресом после переезда молча перестало бы находить
 * новые сборки. Пустая строка там же выключает проверку совсем.
 */
const val DEFAULT_UPDATE_SOURCE = "Lechpath/Askya"

/**
 * Когда показывать заставку целиком.
 *
 * [DAILY] стоит умолчанием: первый заход за сутки — это и есть тот раз, когда
 * церемония работает, а двадцатый — тот, когда она мешает.
 */
enum class SplashWhen(val title: String) {
    ALWAYS("Каждый раз"),
    DAILY("Раз в день"),
    NEVER("Не показывать"),
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Настройки приложения. Экран настроек читает и пишет их через этот класс. */
class SettingsPreferences(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    /**
     * То же, но готовое к чтению без ожидания.
     *
     * Нужно там, где спросить некогда: стартовый раздел решается в тот момент,
     * когда собирается навигация, и ждать диска там нельзя. Собирается
     * заранее — контейнер создаётся при запуске приложения, задолго до первого
     * экрана.
     */
    val state: StateFlow<AppSettings> =
        settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** Начинать ли неделю с понедельника — влияет на счётчики практик. */
    val weekStartsMonday: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_WEEK_STARTS_MONDAY] ?: true }

    suspend fun setWeekStartsMonday(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WEEK_STARTS_MONDAY] = enabled }
    }

    fun setSplash(value: SplashWhen) = put { it[KEY_SPLASH] = value.name }

    /**
     * Была ли заставка сегодня — и отметить, что была.
     *
     * Отметка ставится тем же вызовом, что и спрашивается: между вопросом и
     * ответом ничего не происходит, а два вызова означали бы, что где-то
     * забудут второй.
     *
     * Днём считается календарный день, а не сутки от прошлого показа: человек
     * думает «сегодня я Askya уже открывал», а не «прошло ли двадцать четыре
     * часа».
     */
    suspend fun splashDueToday(): Boolean {
        val today = LocalDate.now().toString()
        val seen = context.dataStore.data.first()[KEY_SPLASH_DAY]
        if (seen == today) return false
        context.dataStore.edit { it[KEY_SPLASH_DAY] = today }
        return true
    }

    fun setStartRoute(route: String) = put { it[KEY_START] = route }

    fun setAutoFillDay(value: Boolean) = put { it[KEY_AUTOFILL] = value }

    fun setTheme(mode: ThemeMode) = put { it[KEY_THEME] = mode.name }

    fun setPalette(palette: AskyaPalette) = put { it[KEY_PALETTE] = palette.name }

    fun setFlower(flower: FlowerColor) = put { it[KEY_FLOWER] = flower.name }

    /**
     * Краска цветка на заставке. `null` — «как везде»: ключ убирается, а не
     * пишется пустой строкой, иначе «как везде» пришлось бы читать двумя
     * способами.
     */
    fun setSplashFlower(flower: FlowerColor?) = put { preferences ->
        if (flower == null) preferences.remove(KEY_SPLASH_FLOWER)
        else preferences[KEY_SPLASH_FLOWER] = flower.name
    }

    fun setDeedShade(value: Boolean) = put { it[KEY_DEED_SHADE] = value }

    fun setDayLockScreen(value: Boolean) = put { it[KEY_DAY_LOCK_SCREEN] = value }

    fun setDeedFullScreen(value: Boolean) = put { it[KEY_DEED_FULLSCREEN] = value }

    fun setWeek(mondayFirst: Boolean) = put { it[KEY_WEEK_STARTS_MONDAY] = mondayFirst }

    fun setSnapshotReminder(value: Boolean) = put { it[KEY_SNAPSHOT_REMINDER] = value }

    /** Откуда качать обновление. Пустая строка выключает проверку совсем. */
    fun setUpdateSource(value: String) = put { it[KEY_UPDATE_SOURCE] = value.trim() }

    fun setUpdateOnStart(value: Boolean) = put { it[KEY_UPDATE_ON_START] = value }

    /**
     * Пора ли смотреть обновление на запуске — и отметка о том, что смотрели.
     *
     * Одним вызовом, а не «спросить» и «отметить» порознь: Askya открывают по
     * десять раз на дню, и проверка при каждом запуске означала бы десять
     * запросов в сеть вместо одного. Отметка ставится до запроса, а не после
     * его успеха: сеть, которой сейчас нет, не повод спрашивать снова через
     * минуту.
     */
    suspend fun dueForUpdateCheck(): Boolean {
        val was = context.dataStore.data.first()[KEY_UPDATE_ASKED] ?: 0L
        val now = System.currentTimeMillis()
        if (now - was < DAY_MS) return false
        context.dataStore.edit { it[KEY_UPDATE_ASKED] = now }
        return true
    }

    /**
     * Следить за этим делом или перестать.
     *
     * Одним вызовом в обе стороны: в окне выбора тап по строке и есть ответ, а
     * «добавить» и «убрать» порознь потребовали бы от вызывающего знать, что
     * там сейчас, — знание, которое устареет между чтением и записью.
     *
     * Новое дописывается в конец: список — это порядок, сложенный человеком, и
     * свежий выбор, прыгающий наверх, каждый раз перекладывал бы страницу.
     */
    fun toggleWatchedDeed(title: String) = put { preferences ->
        val kept = (preferences[KEY_WATCHED] ?: "").split('\n').filter { it.isNotBlank() }
        val cleaned = title.trim()
        val without = kept.filterNot { it.equals(cleaned, ignoreCase = true) }
        val updated = if (without.size == kept.size) kept + cleaned else without
        preferences[KEY_WATCHED] = updated.joinToString("\n")
    }

    /**
     * Запомнить, что это приложение выбирали для моста.
     *
     * Свежее — первым, повтор поднимается наверх, а не заводит вторую строку.
     * Список короткий: восьми хватает, чтобы «то, что брал недавно» осталось
     * наверху, а память о годичной давности выборе только мешала бы.
     */
    fun rememberApp(packageName: String) = put { preferences ->
        val kept = (preferences[KEY_RECENT_APPS] ?: "")
            .split('\n')
            .filter { it.isNotBlank() && it != packageName }
        preferences[KEY_RECENT_APPS] = (listOf(packageName) + kept).take(8).joinToString("\n")
    }

    /**
     * Отметить, что слепок сделан, — от этой минуты пойдёт следующий месяц.
     *
     * Ставится и тогда, когда напоминание только включили и слепка ещё не
     * было: иначе месяц отсчитывался бы от нуля, и напоминание пришло бы в
     * ту же секунду.
     */
    fun markSnapshot(at: Long = System.currentTimeMillis()) = put { it[KEY_SNAPSHOT_AT] = at }

    /**
     * Стереть то, что осталось от убранной модели: ключ и рассказ о себе.
     *
     * Читателей у обоих значений не осталось в тот же день, когда убрали
     * разговор с моделью, а лежать в хранилище они продолжали — и уезжали в
     * системную резервную копию вместе со всем остальным. Приложение, которое
     * отдельным разделом объясняет, что наружу не уходит ничего, возило наружу
     * ключ от платного аккаунта.
     *
     * Ключи названы здесь, а не в companion, и намеренно: это не настройки, а
     * мусор под уборку, и место им рядом с той единственной строчкой, которая
     * его выносит.
     *
     * Без отдельного флага «уже ли убрано», как и остальные разовые уборки в
     * [app.askya.app.AskyaApplication]: проверка стоит одно чтение, а работает
     * она и после восстановления из старой копии, где ключ снова окажется на
     * месте. Записи без нужды при этом нет — пустое хранилище не переписывается.
     */
    suspend fun dropRemovedModelSecrets() {
        val keyClaude = stringPreferencesKey("claude_key")
        val keyAboutMe = stringPreferencesKey("about_me")
        val stored = context.dataStore.data.first()
        if (stored[keyClaude] == null && stored[keyAboutMe] == null) return
        context.dataStore.edit {
            it.remove(keyClaude)
            it.remove(keyAboutMe)
        }
    }

    /**
     * Оборот приветствия на заставке — см. [app.askya.domain.model.Greeting].
     *
     * Это не настройка, а память: настройки человек меняет, а этот счётчик
     * приложение двигает само. Своего хранилища ему всё же не заводится —
     * целый файл DataStore ради одного числа стоил бы дороже, чем строчка не
     * на своём месте, а живёт число ровно столько же, сколько настройки: от
     * установки до удаления приложения.
     *
     * Увеличивается один раз за запуск, [advanceGreeting], и возвращает уже
     * новое значение: заставке нужно не «какое было», а «какое показывать».
     */
    suspend fun advanceGreeting(): Int {
        // Отсчёт с -1, чтобы первый запуск дал ноль — то есть само приветствие,
        // стоящее первым в наборе. Переполнение Int уводит счётчик в минус;
        // Greeting это переживает, а другого способа считать «без конца» нет.
        val updated = context.dataStore.edit { it[KEY_GREETING_TURN] = (it[KEY_GREETING_TURN] ?: -1) + 1 }
        return updated[KEY_GREETING_TURN] ?: 0
    }

    /**
     * Было ли уже знакомство с приложением.
     *
     * Не в [AppSettings], а отдельным потоком, и намеренно: настройки читаются
     * с готовым значением по умолчанию, и до первого ответа диска они говорят
     * «нет» — то есть показали бы знакомство всякому, кто просто запустил
     * приложение в сотый раз. Здесь же ответа ждут: пока диск молчит, поток не
     * сказал ничего, и показывать нечего.
     *
     * Это не настройка, а память — как оборот приветствия на заставке. Своего
     * хранилища ей не заводится по той же причине: целый файл ради одного
     * «да» стоил бы дороже, чем строчка не на своём месте.
     */
    val tourSeen: Flow<Boolean> = context.dataStore.data.map { it[KEY_TOUR] ?: false }

    /**
     * Знакомство состоялось — или его попросили показать заново.
     *
     * Одним выключателем в обе стороны: «показать снова» из настроек — это
     * ровно то же самое, что «ещё не видел», и второго способа рассказать о
     * приложении у него быть не должно.
     */
    fun setTourSeen(value: Boolean) = put { it[KEY_TOUR] = value }

    private fun put(edit: (MutablePreferences) -> Unit) {
        scope.launch { context.dataStore.edit(edit) }
    }

    private fun Preferences.toSettings() = AppSettings(
        weekStartsMonday = this[KEY_WEEK_STARTS_MONDAY] ?: true,
        splash = this[KEY_SPLASH]
            ?.let { name -> SplashWhen.entries.firstOrNull { it.name == name } }
            // Прежнее значение было выключателем: `false` значило «совсем без
            // неё», всё остальное — «каждый раз». Читается оно и теперь, чтобы
            // выбор человека не сбросился обновлением.
            ?: when (this[KEY_SPLASH_OLD]) {
                false -> SplashWhen.NEVER
                true -> SplashWhen.ALWAYS
                null -> SplashWhen.DAILY
            },
        startRoute = this[KEY_START] ?: "today",
        autoFillDay = this[KEY_AUTOFILL] ?: true,
        deedShade = this[KEY_DEED_SHADE] ?: true,
        dayLockScreen = this[KEY_DAY_LOCK_SCREEN] ?: false,
        deedFullScreen = this[KEY_DEED_FULLSCREEN] ?: true,
        theme = this[KEY_THEME]
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM,
        palette = this[KEY_PALETTE]
            ?.let { name -> AskyaPalette.entries.firstOrNull { it.name == name } }
            ?: AskyaPalette.CORAL,
        flower = this[KEY_FLOWER]
            ?.let { name -> FlowerColor.entries.firstOrNull { it.name == name } }
            ?: FlowerColor.SUNSET,
        // Нет ключа — нет и своей краски у заставки: она берёт общую.
        splashFlower = this[KEY_SPLASH_FLOWER]
            ?.let { name -> FlowerColor.entries.firstOrNull { it.name == name } },
        snapshotReminder = this[KEY_SNAPSHOT_REMINDER] ?: true,
        snapshotAt = this[KEY_SNAPSHOT_AT] ?: 0L,
        recentApps = (this[KEY_RECENT_APPS] ?: "").split('\n').filter { it.isNotBlank() },
        watchedDeeds = (this[KEY_WATCHED] ?: "").split('\n').filter { it.isNotBlank() },
        updateSource = this[KEY_UPDATE_SOURCE] ?: DEFAULT_UPDATE_SOURCE,
        updateOnStart = this[KEY_UPDATE_ON_START] ?: false,
    )

    private companion object {
        val KEY_WEEK_STARTS_MONDAY = booleanPreferencesKey("week_starts_monday")
        val KEY_GREETING_TURN = intPreferencesKey("greeting_turn")
        val KEY_SPLASH = stringPreferencesKey("splash_when")

        /** Прежний выключатель. Читается ради тех, кто его уже трогал. */
        val KEY_SPLASH_OLD = booleanPreferencesKey("splash")

        /** Какой день заставку уже видел — ISO-строкой. */
        val KEY_SPLASH_DAY = stringPreferencesKey("splash_day")
        val KEY_START = stringPreferencesKey("start_route")
        val KEY_AUTOFILL = booleanPreferencesKey("auto_fill_day")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_PALETTE = stringPreferencesKey("palette")
        val KEY_FLOWER = stringPreferencesKey("flower_color")

        /** Краска цветка на заставке. Ключа нет — «как везде». */
        val KEY_SPLASH_FLOWER = stringPreferencesKey("splash_flower_color")
        val KEY_DEED_SHADE = booleanPreferencesKey("deed_shade")
        val KEY_DAY_LOCK_SCREEN = booleanPreferencesKey("day_lock_screen")
        val KEY_DEED_FULLSCREEN = booleanPreferencesKey("deed_fullscreen")
        val KEY_TOUR = booleanPreferencesKey("tour_seen")
        val KEY_SNAPSHOT_REMINDER = booleanPreferencesKey("snapshot_reminder")
        val KEY_SNAPSHOT_AT = longPreferencesKey("snapshot_at")
        val KEY_RECENT_APPS = stringPreferencesKey("recent_apps")
        val KEY_WATCHED = stringPreferencesKey("watched_deeds")
        val KEY_UPDATE_SOURCE = stringPreferencesKey("update_source")
        val KEY_UPDATE_ON_START = booleanPreferencesKey("update_on_start")

        /** Когда в последний раз спрашивали облако, в миллисекундах эпохи. */
        val KEY_UPDATE_ASKED = longPreferencesKey("update_asked")

        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
