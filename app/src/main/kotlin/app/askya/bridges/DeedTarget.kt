package app.askya.bridges

import app.askya.data.entity.Bridge
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.BlockIcons
import app.askya.domain.model.DeedLink
import app.askya.domain.model.LinkKind

/**
 * Куда ведёт дело: внутрь Askya или наружу через мост.
 *
 * Одним ответом на оба случая, потому что вопрос у человека один — «чем это
 * делается». Два разных перехода, разведённые по коду, разошлись бы и по
 * поведению: где-то знак горел бы, где-то нет.
 */
sealed interface DeedTarget {

    /** Внутрь: книга, запись, список, раздел. */
    data class Inside(val link: DeedLink) : DeedTarget

    /** Наружу: приложение телефона или ссылка. */
    data class Outside(val bridge: Bridge) : DeedTarget
}

/**
 * Чем делается дело — три слоя, от частного к общему.
 *
 * 1. **привязка самого дела** — разовое решение на один день, и оно перебивает
 *    оба остальных: человек сказал про это дело прямо;
 * 2. **привязка строки списка дел** — для повторяющегося дела, у которого своё
 *    приложение, не совпадающее со знаком;
 * 3. **мост на знаке дела** — главный слой и тот, ради которого всё затевалось.
 *    Знак угадывается по названию ([BlockIcons]): «Чтение Библии» и «Читать
 *    Достоевского» одинаково опознаются как READ, и подключённая к «чтению»
 *    читалка ведёт из всех дел про чтение, включая те, что появятся завтра.
 *    Один раз на всю жизнь приложения, и это покрывает большинство случаев.
 *
 * Порядок «от частного к общему», а не наоборот: слой тем главнее, чем точнее
 * человек указал. Частные слои нужны там, где общий промахнулся.
 *
 * Привязка на мост, которого больше нет (мост убрали, а строка осталась), не
 * останавливает поиск, а пропускается: следующий слой — лучше, чем ничего.
 */
fun deedTarget(
    bridges: List<Bridge>,
    link: String?,
    routineLink: String?,
    title: String,
    icon: BlockIcon?,
): DeedTarget? =
    targetOf(bridges, link)
        ?: targetOf(bridges, routineLink)
        ?: bridges.firstOrNull { it.icon != null && it.icon == (icon ?: BlockIcons.of(title)) }
            ?.let { DeedTarget.Outside(it) }

private fun targetOf(bridges: List<Bridge>, link: String?): DeedTarget? {
    val parsed = DeedLink.of(link) ?: return null
    if (parsed.kind != LinkKind.BRIDGE) return DeedTarget.Inside(parsed)
    return bridges.firstOrNull { it.id == parsed.id }?.let { DeedTarget.Outside(it) }
}
