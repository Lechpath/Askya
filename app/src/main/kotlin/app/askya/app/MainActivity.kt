package app.askya.app

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.askya.ui.launch.AskyaSplash
import app.askya.ui.launch.splashThemeOf
import app.askya.ui.launch.AskyaTour
import app.askya.ui.open.OpenedFileScreen
import app.askya.ui.navigation.AskyaApp
import app.askya.ui.theme.AskyaTheme
import app.askya.ui.theme.FlowerColor
import app.askya.ui.theme.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Единственная Activity: вся навигация внутри Compose. */
class MainActivity : ComponentActivity() {

    /**
     * Файл, с которым приложение открыли снаружи.
     *
     * Состоянием Compose, а не полем: приходит он и на запуске, и в уже
     * открытое приложение ([onNewIntent]), и экран должен отзываться на второе
     * так же, как на первое.
     */
    private var incoming by mutableStateOf<IncomingFile?>(null)

    /**
     * Раздел, в который просят открыться, — приходит с виджета погоды.
     *
     * Тоже состоянием и по той же причине, что и [incoming]: просьба приходит
     * и на запуске, и в уже открытое приложение, и отвечать на второе нужно
     * так же, как на первое.
     */
    private var opening by mutableStateOf<String?>(null)

    /**
     * Просят ли вместе с разделом начать запись — кружок виджета голоса.
     *
     * Отдельным полем, а не третьим значением [opening]: это не «куда идти», а
     * «что там сделать», и мешать одно с другим значило бы заводить маршрут на
     * каждое действие в разделе.
     */
    private var saying by mutableStateOf(false)

    /**
     * Дело, которое просят раскрыть, — приходит из шторки уведомлений со
     * списком. Состоянием и по той же причине, что [incoming] и [opening]:
     * просьба приходит и на запуске, и в уже открытое приложение.
     */
    private var deed by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handOverSplash()
        incoming = incomingFileOf(this, intent)
        opening = openRouteOf(intent)
        saying = sayNowOf(intent)
        deed = openDeedOf(intent)
        setContent {
            val settings by container().settings.settings
                .collectAsStateWithLifecycle(initialValue = container().settings.state.value)

            // «Как в системе» решается здесь, а не в теме: систему спрашивает
            // Compose, а полосы наверху и внизу экрана красит Activity, и
            // ответ нужен обоим.
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Значки часов и кнопок системы: на кремовом листе тёмные, на
            // ночном — светлые. Перекрашиваются вслед за настройкой, а не
            // один раз на запуске: тему меняют, не выходя из приложения, и
            // час в углу не должен пропасть на белом.
            // Краска цветка на системной заставке — на следующий запуск.
            //
            // Здесь, а не в onCreate: настройки читаются с диска, и в первые
            // миллисекунды запуска `state.value` ещё говорит «по умолчанию» —
            // подмена темы снималась бы на каждом запуске, и заставка навсегда
            // осталась бы закатной. Ключом стоит сама краска: её меняют, не
            // выходя из приложения, и следующий запуск должен знать о новой.
            LaunchedEffect(settings.splashFlower, settings.flower) {
                paintSystemSplash(settings.splashFlower ?: settings.flower)
            }

            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            AskyaTheme(dark = dark, palette = settings.palette, flower = settings.flower) {
                // Заставка переживает поворот экрана: она открывает
                // приложение, а не сопровождает каждую пересборку композиции.
                var splash by rememberSaveable { mutableStateOf(true) }

                // Приложение собирается не раньше, чем сказано приветствие.
                //
                // Собранное сразу, оно задерживало первый кадр на секунду с
                // лишним: цветка всё это время не было, и запуск начинался
                // пустым кремовым листом — ровно тем, чего заставка и должна
                // была не допустить. А собранное посреди письма — сбивало бы
                // само письмо: первая сборка экрана идёт в том же потоке, что
                // и анимация.
                //
                // Полторы секунды, что держится приветствие, уходят на неё:
                // на этом шаге на экране только дыхание, и занять его сборкой
                // дешевле всего.
                var awake by remember { mutableStateOf(!splash) }

                // Файл, открытый снаружи, не ждёт церемонии: человек нажал
                // его в проводнике и хочет увидеть его, а не заставку.
                val opened = incoming
                LaunchedEffect(opened) {
                    if (opened != null) {
                        splash = false
                        awake = true
                    }
                }

                // Знакомство: показывается один раз, на первом запуске, и по
                // просьбе из настроек. Ответ хранилища ждём: пока оно молчит,
                // значение — null, и это не «не видел», а «ещё не знаем».
                // Иначе знакомство мигало бы на каждом запуске в те доли
                // секунды, пока читается файл настроек.
                val toured by container().settings.tourSeen
                    .collectAsStateWithLifecycle(initialValue = null)

                Box(modifier = Modifier.fillMaxSize()) {
                    if (awake) {
                        AskyaApp(
                            openRoute = opening,
                            saying = saying,
                            openDeed = deed,
                            onOpened = {
                                opening = null
                                saying = false
                                deed = null
                            },
                        )
                    }

                    if (opened != null) {
                        OpenedFileScreen(file = opened, onClose = { incoming = null })
                    }

                    // Поверх приложения, но под заставкой и под открытым
                    // снаружи файлом: церемония запуска идёт первой, а человек,
                    // нажавший видео в проводнике, пришёл смотреть его, а не
                    // знакомиться.
                    if (toured == false && opened == null && !splash) {
                        AskyaTour(onDone = { container().settings.setTourSeen(true) })
                    }

                    if (splash) {
                        AskyaSplash(
                            onGreeted = { awake = true },
                            onDone = {
                                splash = false
                                // Церемонию пропустили касанием — приложение
                                // нужно прямо сейчас.
                                awake = true
                            },
                        )
                    }
                }
            }
        }
    }

    /**
     * Приложение уже открыто, и в него прислали файл.
     *
     * Без этого система завела бы вторую копию экрана поверх первой — с
     * потерянным местом в дне, закрытым меню и заново собранной навигацией.
     * `launchMode="singleTop"` в манифесте и этот обработчик — две половины
     * одного правила: приложение одно, а файлов в него присылают сколько
     * угодно.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = incomingFileOf(this, intent)
        openRouteOf(intent)?.let {
            opening = it
            saying = sayNowOf(intent)
            deed = openDeedOf(intent)
        }
    }

    /**
     * Передача цветка от системной заставки своей.
     *
     * Системная (`values-v31/themes.xml`) держит экран, пока идёт запуск, и
     * уходит сама — своей анимацией: значок уезжает и гаснет. Под ней в этот
     * момент уже стоит свой цветок, и два цветка, разъезжающиеся друг с другом,
     * и были тем стыком, которого на запуске быть не должно.
     *
     * Поэтому уход берётся на себя: системный лист просто растворяется за
     * четверть секунды поверх своего. Свой цветок стоит там же и той же
     * стороны, поэтому за время растворения меняется только то, что цветок
     * начинает дышать уже своими средствами.
     *
     * Обработчик зовётся, когда первый кадр приложения нарисован, — то есть
     * когда своей заставке уже есть чем подхватить. До Android 12 системной
     * заставки нет вовсе, и передавать нечего.
     */
    private fun handOverSplash() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        splashScreen.setOnExitAnimationListener { view ->
            view.animate()
                .alpha(0f)
                .setDuration(HAND_OVER_MS)
                .withEndAction { view.remove() }
                .start()
        }
    }

    /**
     * Краска цветка на системной заставке — темой на следующий запуск.
     *
     * Заставку рисует система, и рисует она её раньше, чем приложение получит
     * управление: к моменту, когда сюда доходит очередь, цветок на экране уже
     * стоит. Поэтому выбранная краска встаёт со следующего запуска — своя
     * заставка показывает её сразу, и первое время два цветка разного цвета
     * сменяют друг друга. Один раз: со второго запуска они совпадают.
     *
     * Обратной дороги терять нельзя: вернувшемуся к закату подставляется
     * `ID_NULL`, и система берёт тему из манифеста. Без этого выбранная
     * однажды краска осталась бы на заставке навсегда.
     */
    private fun paintSystemSplash(flower: FlowerColor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        splashScreen.setSplashScreenTheme(splashThemeOf(flower))
    }

    /** Контейнер приложения — из него берутся настройки темы. */
    private fun container(): AppContainer = (application as AskyaApplication).container

    private companion object {
        /** Столько тает системный лист над своим. */
        const val HAND_OVER_MS = 260L
    }
}
