package app.askya.ui.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Заглавие карточки — одна мерка на всё приложение.
 *
 * Засечным и крупно: тем же шрифтом, что заголовки экранов, книг и вордмарк на
 * иконке. Название карточки — не первая строка текста, а её имя, и набранное
 * почти как текст оно этим именем быть перестаёт: открытая запись начиналась
 * бы двумя одинаковыми абзацами, из которых первый почему-то короче.
 *
 * Мерка общая для всех трёх мест, где заглавие видно, — окна записи, карточки
 * на полке и полноэкранного просмотра, — чтобы сохранённая запись выглядела
 * ровно так же, как её писали.
 */
val NOTE_TITLE = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.3).sp,
)
