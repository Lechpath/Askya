package app.askya.ui.scroll

import androidx.compose.ui.graphics.Color
import app.askya.data.entity.ScrollTopic
import app.askya.domain.model.MarkColor
import app.askya.ui.theme.markColor

/**
 * Цвет корешка книги.
 *
 * Выбранный цвет живёт в самой книге ([ScrollTopic.color]). Если его не
 * выбирали, цвет выводится из названия: полка всё равно окрашена, а человек не
 * обязан выбирать цвет каждой заведённой книге.
 *
 * Сама таблица красок лежит в теме ([markColor]) и общая с Ledger: вопрос у
 * корешка и у счёта один и тот же — «которая из них моя?».
 */
fun spineColor(book: ScrollTopic): Color = markColor(book.color, book.title)

/** Цвет одной краски палитры — им же красятся кружки выбора. */
fun spineColor(color: MarkColor): Color = markColor(color)
