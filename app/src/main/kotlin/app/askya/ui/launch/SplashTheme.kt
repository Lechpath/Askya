package app.askya.ui.launch

import androidx.annotation.StyleRes
import app.askya.R
import app.askya.ui.theme.FlowerColor

/**
 * Какой темой красить цветок на **системной** заставке Android 12+.
 *
 * Своя заставка красит цветок сама — она уже внутри Compose и читает настройки
 * напрямую. Системная показывается до неё, в тот же миг, когда приложение
 * запускают, и краску туда можно передать только темой: рисунок берёт её из
 * `?attr/askyaFlowerInk`, а тему называет Activity
 * (`splashScreen.setSplashScreenTheme`).
 *
 * Восемь тем на восемь красок — не расточительство, а единственный способ:
 * тема выбирается системой в момент запуска, когда приложения ещё нет и
 * спросить у него нечего. Сами темы — по строке каждая (`values/themes.xml`).
 *
 * [FlowerColor.SUNSET] возвращает `0`: закат — это сама `Theme.Askya`, и
 * подменять её нечем. Ноль здесь и означает «оставить как в манифесте» —
 * `setSplashScreenTheme(Resources.ID_NULL)` отменяет прежнюю подмену, а это
 * ровно то, что нужно вернувшемуся к закату.
 */
@StyleRes
fun splashThemeOf(flower: FlowerColor): Int = when (flower) {
    FlowerColor.SUNSET -> 0
    FlowerColor.CORAL -> R.style.Theme_Askya_Splash_Coral
    FlowerColor.ROSE -> R.style.Theme_Askya_Splash_Rose
    FlowerColor.FOREST -> R.style.Theme_Askya_Splash_Forest
    FlowerColor.SEA -> R.style.Theme_Askya_Splash_Sea
    FlowerColor.PLUM -> R.style.Theme_Askya_Splash_Plum
    FlowerColor.AMBER -> R.style.Theme_Askya_Splash_Amber
    FlowerColor.GRAPHITE -> R.style.Theme_Askya_Splash_Graphite
}
