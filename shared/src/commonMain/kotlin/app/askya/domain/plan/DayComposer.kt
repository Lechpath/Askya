package app.askya.domain.plan

import app.askya.data.entity.RoutineItem
import app.askya.data.entity.ScheduleItem
import java.time.LocalDate
import java.time.LocalTime

/**
 * Всё, из чего складывается день, — в одном месте.
 *
 * Источник один — список дел. Профиль из разговора с моделью отсюда убран
 * вместе с самой моделью: день собирается из того, что человек записал сам.
 *
 * [existing] передаётся не для порядка: день пересобирают, когда в нём уже
 * что-то есть, и молча выкинуть записанное человеком нельзя.
 */
data class DayRequest(
    val date: LocalDate,
    val routine: List<RoutineItem>,
    val existing: List<ScheduleItem>,
)

/**
 * Дело в собранном дне. Время здесь уже названо: в отличие от разбора рассказа
 * раскладывать нечего — модель получила весь день целиком и сама решила, что
 * когда стоит.
 *
 * [note] — короткое «почему так»: перенёс бег на утро, потому что вечером
 * встреча. Это единственное место, где Askya объясняет своё решение, и без
 * него пересборка выглядит как самоуправство.
 */
data class PlannedItem(
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime? = null,
    val note: String = "",
    /**
     * Имя строки для облака ([app.askya.data.sync.Uid]).
     *
     * У дела из списка оно считается из даты и имени строки распорядка, и
     * потому совпадает у двух устройств, заполнивших день порознь. У дела,
     * записанного руками, здесь его собственное имя: пересборка дня стирает
     * несделанное и записывает заново, и без этого одно и то же дело каждый
     * раз выглядело бы для облака новым.
     *
     * Пусто — имя выдаётся новое.
     */
    val uid: String = "",
)

/**
 * Собранный день вместе с тем, кто его собрал, и общим словом от Askya.
 *
 * [source] показывается человеку: день, собранный из списка дел, — это не то
 * же, что день, записанный руками, и прятать разницу за одинаковым списком
 * нечестно.
 */
data class DayLayout(
    val items: List<PlannedItem>,
    val source: String,
    val comment: String = "",
)

/** Сборка конкретного дня. */
interface DayComposer {
    suspend fun compose(request: DayRequest): DayLayout
}
