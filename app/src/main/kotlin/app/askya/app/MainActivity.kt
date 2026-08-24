package app.askya.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.askya.ui.launch.AskyaSplash
import app.askya.ui.navigation.AskyaApp
import app.askya.ui.theme.AskyaTheme

/** Единственная Activity: вся навигация внутри Compose. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handOverSplash()
        // Тема только светлая, поэтому иконки системных панелей всегда тёмные.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            AskyaTheme {
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

                Box(modifier = Modifier.fillMaxSize()) {
                    if (awake) AskyaApp()
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

    private companion object {
        /** Столько тает системный лист над своим. */
        const val HAND_OVER_MS = 260L
    }
}
