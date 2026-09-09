package app.askya.data.repository

import app.askya.domain.model.Currency
import app.askya.domain.model.EntryKind
import app.askya.domain.model.formatMoney
import app.askya.domain.trace.DayEvent
import app.askya.domain.trace.TracePart
import app.askya.domain.trace.gistOf
import app.askya.domain.trace.orderedTrace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Сборщик «Что было»: один день из всех разделов сразу.
 *
 * Своей таблицы у него нет и быть не должно — он спрашивает у тех же
 * репозиториев, из которых читают сами разделы, и складывает ответы. Почему
 * так и почему здесь нет Echo с AskyaV, написано в
 * [app.askya.domain.trace.TracePart].
 *
 * Правила складывания — чистые функции в `domain/trace/`, и они покрыты
 * тестами; здесь только запросы и перевод строк базы в события.
 */
class DayTraceRepository(
    private val schedule: ScheduleRepository,
    private val notes: NoteRepository,
    private val ledger: LedgerRepository,
    private val yet: YetRepository,
    private val reminders: ReminderRepository,
) {

    /**
     * След одного дня.
     *
     * Потоки сведены в один: база меняется под рукой — отметил дело, записал
     * трату, — и карточка должна меняться вместе с ней, а не показывать то,
     * что было в миг открытия экрана.
     *
     * Двумя связками, а не одной: `combine` берёт по пять потоков за раз, а их
     * шесть.
     */
    fun trace(date: LocalDate): Flow<List<DayEvent>> =
        combine(sections(date), rung(date)) { fromSections, fromReminders ->
            orderedTrace(fromSections + fromReminders)
        }

    private fun sections(date: LocalDate): Flow<List<DayEvent>> = combine(
        schedule.itemsOn(date),
        notes.createdOn(date),
        ledger.entriesOn(date),
        ledger.categories(),
        yet.lists(),
    ) { deeds, written, spent, categories, lists ->
        val titles = categories.associate { it.id to it.title }

        val fromDay = deeds
            .filter { it.done }
            .map { deed ->
                DayEvent(
                    at = deed.startTime,
                    part = TracePart.DAY,
                    gist = gistOf(deed.title, fallback = "Дело"),
                    aside = "сделано",
                )
            }

        val fromScroll = written.map { note ->
            DayEvent(
                at = note.createdAt.toLocalTime(),
                part = TracePart.SCROLL,
                // У заметки бывает пустой заголовок — тогда сутью служит первая
                // строка написанного, как и в самом Scroll.
                gist = gistOf(note.title.ifBlank { note.body }, fallback = "Запись"),
                aside = when {
                    note.isImage -> "картинка"
                    note.durationMs > 0 -> "голос"
                    else -> ""
                },
            )
        }

        val fromLedger = spent.map { entry ->
            val title = entry.categoryId?.let { titles[it] }
            DayEvent(
                // Час записи, а не час покупки: второго человек не называет, и
                // выдумывать его нельзя.
                at = entry.createdAt.toLocalTime(),
                part = TracePart.LEDGER,
                gist = gistOf(
                    entry.note.ifBlank { title.orEmpty() },
                    fallback = entry.kind.one,
                ),
                // Знак — по виду записи, как в самой книге: трата с минусом,
                // приход и возврат с плюсом, перевод без знака вовсе — он ни
                // расход, ни доход.
                aside = when (entry.kind) {
                    EntryKind.SPEND -> formatMoney(-entry.amount, currency = Currency.RUB)
                    EntryKind.MOVE -> formatMoney(entry.amount, currency = Currency.RUB)
                    else -> formatMoney(entry.amount, withSign = true, currency = Currency.RUB)
                },
            )
        }

        // Списки помечают себя часом, когда их трогают. Часа в событии нет
        // нарочно: «трогали в 14:12» — не то, что человек делал, а то, что
        // случилось с записью; довольно того, что список был в ходу.
        val fromYet = lists
            .filter { it.updatedAt.toLocalDate() == date }
            .map { list ->
                DayEvent(
                    at = null,
                    part = TracePart.YET,
                    gist = gistOf(list.title, fallback = "Список"),
                )
            }

        fromDay + fromScroll + fromLedger + fromYet
    }

    /**
     * Напоминания того дня, которые уже прозвонили.
     *
     * Единственное место, где событие **выведено**, а не прочитано. Askya не
     * записывает, что напоминание сработало: будильник выстрелил, уведомление
     * показалось, и следа в базе от этого не осталось. Здесь считается, что
     * прозвонило всё, чей час прошёл, — и это правда во всём, кроме телефона,
     * выключенного на эти часы.
     *
     * Час сверяется в миг сборки списка, а сама сборка идёт от изменений в
     * базе. Значит, напоминание, прозвонившее прямо сейчас, появится в карточке
     * не сию секунду, а со следующей записью в дне. Заводить ради этой секунды
     * ещё один тикающий поток — платить больше, чем стоит товар.
     */
    private fun rung(date: LocalDate): Flow<List<DayEvent>> = reminders.reminders().map { all ->
        val now = LocalDateTime.now()
        all.filter { it.enabled && it.date == date && it.date.atTime(it.time) <= now }
            .map { reminder ->
                DayEvent(
                    at = reminder.time,
                    part = TracePart.REMINDERS,
                    gist = gistOf(reminder.title, fallback = "Напоминание"),
                    aside = "напомнило",
                )
            }
    }
}
