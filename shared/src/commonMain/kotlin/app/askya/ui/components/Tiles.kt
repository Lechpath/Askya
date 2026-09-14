package app.askya.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Полка выкладывается сеткой — тремя карточками в ряд, как расписание дня.
 *
 * Книг, файлов и списков на полке десятки, и колонкой в одну строку они уходили
 * за нижний край экрана уже на первом десятке. Тремя в ряд полка видна разом, а
 * карточка остаётся карточкой — тем же прямоугольником с тенью, что и дело в
 * дне.
 *
 * Лежит в общих компонентах, а не в Библиотеке: этой же сеткой выложены списки
 * Scroll, и разъехаться в ширине колонок им нельзя.
 */
const val SHELF_COLUMNS = 3

/**
 * Ряд карточек: сколько есть, остальное добирается пустотой.
 *
 * Без добора хвост последнего ряда растягивался бы на всю ширину, и две
 * последние карточки оказывались бы вдвое шире тех, что над ними.
 */
@Composable
fun <T> TileRow(
    items: List<T>,
    modifier: Modifier = Modifier,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item -> tile(item, Modifier.weight(1f)) }
        repeat(SHELF_COLUMNS - items.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}
