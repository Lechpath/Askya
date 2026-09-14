package app.askya.ui.navigation

import app.askya.data.entity.Note
import app.askya.domain.model.DeedLink
import app.askya.domain.model.LinkKind

/**
 * Куда ведёт привязка дела.
 *
 * Отдельно от [DeedLink] и намеренно: сама привязка — это пара «вид + адрес» и
 * ничего больше, она живёт в базе и в тестах, а маршруты — дело навигации.
 * Смешав их, пришлось бы тянуть `Routes` в `domain`.
 *
 * [note] нужен одному виду: запись бывает и текстом, и приложенным файлом, и
 * открываются они разными экранами. Файл идёт в просмотр — там читалка сама
 * встаёт на запомненном месте; текст идёт в правку, потому что смотреть в нём
 * нечего, кроме собственных слов. `null` вместо записи (её удалили) — `null` и
 * маршрут: вести в пустой экран хуже, чем не вести никуда.
 */
fun routeOf(link: DeedLink, note: Note?): String? = when (link.kind) {
    // Мост ведёт наружу, а не по маршруту: его переход — не навигация внутри
    // Askya, а намерение системе (`bridges/BridgeApps.kt`). Здесь у него
    // маршрута нет намеренно.
    LinkKind.BRIDGE -> null
    LinkKind.BOOK -> Routes.topic(link.id)
    LinkKind.NOTE -> when {
        note == null -> null
        note.uri != null -> Routes.view(link.id)
        else -> Routes.noteEdit(link.id)
    }

    LinkKind.YET -> Routes.yetList(link.id)
    LinkKind.ECHO -> Destination.ECHO.route
    LinkKind.VIDEO -> Destination.VIDEO.route
}

/** Нужно ли спрашивать запись, прежде чем считать маршрут. */
fun needsNote(link: DeedLink): Boolean = link.kind == LinkKind.NOTE
