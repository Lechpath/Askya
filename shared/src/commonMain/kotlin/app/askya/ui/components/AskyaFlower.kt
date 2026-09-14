package app.askya.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import app.askya.resources.Res
import app.askya.resources.ic_flower
import app.askya.ui.theme.FlowerInk

/**
 * Цветок Askya — знак приложения, нарисованный выбранной краской.
 *
 * Один вектор на всё приложение (`ic_flower.xml`), перекрашенный на месте.
 * Восьми копий картинки по числу красок в `res/` не заводится: рисунок один и
 * тот же, разнится в нём ровно заливка, а восемь файлов разъехались бы на
 * первой же правке лепестков.
 *
 * Краска берётся из темы ([FlowerInk]) — её выбирают в настройках, и она
 * нарочно не та же, что цветовая гамма: гамма красит письмо внутри листа,
 * цветок — лицо приложения. Раздел может попросить свою ([tint]): так на
 * месте недостающей обложки цветок стоит приглушённым, чтобы заглушка не
 * притворялась картинкой.
 *
 * Заливку внутри вектора это не отменяет, а перекрывает: `ic_flower.xml`
 * остаётся закатно-оранжевым и таким уходит в места, где Compose не
 * запущен, — в иконку запуска, в системную заставку и в значок уведомления.
 * Там краску выбирать не у кого.
 */
@Composable
fun AskyaFlower(
    modifier: Modifier = Modifier,
    tint: Color = FlowerInk,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(Res.drawable.ic_flower),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}
