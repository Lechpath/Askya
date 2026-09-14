package app.askya.ui.theme

import androidx.compose.ui.graphics.Color
import app.askya.domain.model.MarkColor

/**
 * Краски-метки в цвете: одна таблица на всё приложение.
 *
 * Сами краски, а не роли темы. Метка — это лицо вещи, и выбранная гамма
 * приложения её не касается: иначе «коралловая» книга становилась бы зелёной
 * от настройки, к книге отношения не имеющей, а счета в Ledger перекрашивались
 * бы все разом и переставали различаться.
 *
 * Тон один на обе темы. Восемь красок выбраны так, чтобы читаться и на
 * кремовой бумаге, и на ночном фоне: метка стоит кружком или полоской, а не
 * буквами по фону, и подгонять её под тему незачем.
 */
fun markColor(color: MarkColor): Color = when (color) {
    MarkColor.CORAL -> CoralAccent
    MarkColor.AMBER -> ModeYellow
    MarkColor.ROSE -> SpineRose
    MarkColor.GREEN -> ModeGreen
    MarkColor.BLUE -> SpineBlue
    MarkColor.PLUM -> SpinePlum
    MarkColor.BROWN -> CoralInk
    MarkColor.INK -> PaperInk
}

/**
 * Краска вещи: выбранная, а если не выбирали — выведенная из названия.
 *
 * Выведенная не случайна: от запуска к запуску она одна и та же, поэтому счёт
 * и книга узнаются по цвету и до того, как им его назначили. Иначе пришлось бы
 * либо красить всё серым, либо требовать выбора у каждой заведённой вещи.
 */
fun markColor(chosen: MarkColor?, title: String): Color =
    chosen?.let(::markColor) ?: markColor(derivedMark(title))

/** Краска по названию — для тех, кому её не выбирали. */
fun derivedMark(title: String): MarkColor {
    val palette = MarkColor.entries
    // hashCode бывает отрицательным, а Int.MIN_VALUE не переворачивается
    // сменой знака — отсюда переход в Long перед взятием модуля.
    val hash = title.hashCode().toLong()
    val index = ((if (hash < 0) -hash else hash) % palette.size).toInt()
    return palette[index]
}
