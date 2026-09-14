package app.askya.domain.lived

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Дела, за которыми человек следит сам, — единственное, что считает «Прожитое».
 *
 * ## Почему по названию
 *
 * Дела в дне не связаны между собой — каждое живёт своей строкой, и «то же
 * самое дело в другой день» опознаётся только по названию. Сравнение идёт по
 * очищенному и приведённому к нижнему регистру названию: «Бег» и «бег » — одно
 * дело, «Бег» и «Бегать» — разные, и угадывать здесь опаснее, чем считать
 * буквально.
 *
 * Показывается при этом то написание, которым дело записано в последний раз:
 * ключ нужен счёту, а человеку нужно его слово.
 *
 * ## Почему по месяцам
 *
 * Год по дням для одного дела не читается вовсе: у дела, случившегося тридцать
 * раз, триста тридцать дней пустые, а тридцать штрихов в волос толщиной не
 * складываются ни во что. Месяц — та мера, в которой человек и думает о
 * повторяющемся: «в мае бегал четыре раза, в июне ни разу».
 */
data class WatchedDeed(
    /** Название, как человек написал его в последний раз. */
    val title: String,
    /** Сколько раз стояло в дне за год. */
    val planned: Int,
    /** Из них отмечено сделанными. */
    val done: Int,
    /**
     * Двенадцать месяцев подряд, последний — нынешний.
     *
     * Пустые месяцы в списке остаются: провал в полгода — такой же ответ, как и
     * череда, и выбросив пустые месяцы, мы показали бы ровный ряд там, где его
     * не было.
     */
    val months: List<MonthTally>,
    /** Самая длинная череда отмеченных дней подряд. */
    val streak: Int,
    /** Когда отмечено в последний раз. `null` — ни разу за год. */
    val lastDone: LocalDate?,
)

/** Один месяц дела: сколько раз стояло и сколько из этого отмечено. */
data class MonthTally(val month: YearMonth, val planned: Int, val done: Int)

/**
 * Счёт по выбранным делам, в порядке самого выбора.
 *
 * Порядок не по числу и не по алфавиту: человек сам сложил этот список, и
 * перестраивать его под наши правила значило бы каждый раз заново искать в нём
 * своё.
 *
 * Выбранное дело, которого за год не случилось ни разу, из ответа не
 * выпадает — пустой счёт и есть ответ на вопрос «как часто», и молчание вместо
 * него человек прочтёт как поломку.
 *
 * [today] передаётся, а не спрашивается у часов: правило, которое смотрит на
 * системное время само, нельзя проверить тестом дважды одинаково.
 */
fun watched(rows: List<LivedRow>, titles: List<String>, today: LocalDate): List<WatchedDeed> {
    if (titles.isEmpty()) return emptyList()
    val byTitle = rows.groupBy { key(it.title) }
    val span = months(today)

    return titles.map { chosen ->
        val same = byTitle[key(chosen)].orEmpty()
        val byMonth = same.groupBy { YearMonth.from(it.date) }

        WatchedDeed(
            title = same.maxByOrNull { it.date }?.title?.trim() ?: chosen.trim(),
            planned = same.size,
            done = same.count { it.done },
            months = span.map { month ->
                val inMonth = byMonth[month].orEmpty()
                MonthTally(month, inMonth.size, inMonth.count { it.done })
            },
            streak = longestStreak(same),
            lastDone = same.filter { it.done }.maxOfOrNull { it.date },
        )
    }
}

/**
 * Что вообще можно выбрать: названия дел за год и как часто они встречались.
 *
 * Списком того, что уже записано, а не строкой для ввода: дело, набранное в
 * окне выбора с опечаткой, не совпало бы ни с одним днём и молчало бы вечно, и
 * человек винил бы счёт, а не опечатку.
 *
 * Частые сверху: следят обычно за тем, что делают или собираются делать
 * постоянно, а разовое «Позвонить в сервис» из года в счёт не берут.
 */
fun deedNames(rows: List<LivedRow>): List<DeedName> =
    rows.groupBy { key(it.title) }
        .map { (_, same) ->
            DeedName(
                title = same.maxByOrNull { it.date }?.title?.trim().orEmpty(),
                planned = same.size,
                done = same.count { it.done },
            )
        }
        .filter { it.title.isNotBlank() }
        .sortedWith(compareByDescending<DeedName> { it.planned }.thenBy { it.title.lowercase() })

/** Строка окна выбора: название и его счёт за год. */
data class DeedName(val title: String, val planned: Int, val done: Int)

/**
 * Счёт одной строкой: «Стояло 24 раза, отмечено 9».
 *
 * Числа обоих родов сказаны рядом и в одном порядке всегда, потому что весь
 * смысл этой страницы — в расстоянии между ними. Отдельной строкой «выполнено
 * 37 %» не говорится: доля выравнивает разные годы к одному числу и тем
 * скрывает как раз то, ради чего человек сюда пришёл.
 */
fun WatchedDeed.tally(): String = when {
    planned == 0 -> "За год ни разу не стояло в дне"
    done == 0 -> "Стояло " + planned + " " + timesWord(planned) + ", ни разу не отмечено"
    else -> "Стояло " + planned + " " + timesWord(planned) + ", отмечено " + done
}

/**
 * Череда, если она есть: «дольше всего — 24 дня подряд».
 *
 * Короче трёх дней молчит: два дня подряд — это не череда, а совпадение.
 */
fun WatchedDeed.streakWord(): String? {
    if (streak < 3) return null
    return "дольше всего — " + streak + " " + dayWord(streak) + " подряд"
}

/** Двенадцать месяцев до нынешнего включительно, от давнего к свежему. */
private fun months(today: LocalDate): List<YearMonth> {
    val last = YearMonth.from(today)
    return (0 until MONTHS_SHOWN).map { last.minusMonths((MONTHS_SHOWN - 1 - it).toLong()) }
}

/**
 * Самая длинная череда отмеченных дней подряд.
 *
 * Считается по отмеченным: поставить дело двадцать раз подряд умеет и сборка
 * дня из списка, а сделать — только человек. День, в котором дело стояло
 * дважды, остаётся одним днём.
 */
private fun longestStreak(rows: List<LivedRow>): Int {
    val marked = rows.filter { it.done }.map { it.date }.distinct().sorted()
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    marked.forEach { day ->
        run = if (previous != null && ChronoUnit.DAYS.between(previous, day) == 1L) run + 1 else 1
        previous = day
        if (run > best) best = run
    }
    return best
}

/** Одно и то же дело в разных днях опознаётся только по названию. */
private fun key(title: String) = title.trim().lowercase()

/** Год — двенадцать месяцев, и в ряд их встаёт ровно столько, сколько влезает. */
private const val MONTHS_SHOWN = 12
