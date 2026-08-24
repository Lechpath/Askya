package app.askya.ui.scroll

import androidx.compose.ui.graphics.Color
import app.askya.data.entity.ScrollTopic
import app.askya.domain.model.BookColor
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.Ink
import app.askya.ui.theme.ModeGreen
import app.askya.ui.theme.ModeYellow
import app.askya.ui.theme.SpineBlue
import app.askya.ui.theme.SpinePlum
import app.askya.ui.theme.SpineRose

/**
 * Цвет корешка книги.
 *
 * Выбранный цвет живёт в самой книге ([ScrollTopic.color]). Если его не
 * выбирали, цвет выводится из названия: полка всё равно окрашена, а человек не
 * обязан выбирать цвет каждой заведённой книге. Выведенный цвет от названия не
 * зависит случайно — он один и тот же от запуска к запуску, поэтому книга
 * узнаётся по цвету и до того, как ей его назначили.
 */
fun spineColor(book: ScrollTopic): Color =
    book.color?.let(::spineColor) ?: derivedSpine(book.title)

/** Цвет одной краски палитры — им же красятся кружки выбора. */
fun spineColor(color: BookColor): Color = when (color) {
    BookColor.CORAL -> Accent
    BookColor.AMBER -> ModeYellow
    BookColor.ROSE -> SpineRose
    BookColor.GREEN -> ModeGreen
    BookColor.BLUE -> SpineBlue
    BookColor.PLUM -> SpinePlum
    BookColor.BROWN -> AccentInk
    BookColor.INK -> Ink
}

/** Цвет по названию — для книг, которым его не выбирали. */
private fun derivedSpine(title: String): Color {
    val palette = BookColor.entries
    // hashCode бывает отрицательным, а Int.MIN_VALUE не переворачивается
    // сменой знака — отсюда переход в Long перед взятием модуля.
    val hash = title.hashCode().toLong()
    val index = ((if (hash < 0) -hash else hash) % palette.size).toInt()
    return spineColor(palette[index])
}
