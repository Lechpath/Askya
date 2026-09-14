package app.askya.data.repository

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Корзина на сутки — то, что заменило собой вопрос «вы уверены?».
 *
 * Подтверждение не отменяет ошибку, оно перекладывает её на человека, который
 * торопится: он читает вопрос первые три раза, а через месяц жмёт «да» не
 * глядя — и ошибается ровно так же, только теперь виноват сам. Возврат отменяет
 * ошибку по-настоящему и стоит одного нажатия вместо одного нажатия.
 *
 * ## Почему это одно место, а не четыре
 *
 * Убранное дело, убранная запись, строка Yet и трата — одно и то же событие
 * для человека, и полоска «Вернуть» у них должна быть одна. К тому же запись
 * убирают с её собственного экрана, а уходят с него сразу: полоске, живущей
 * внутри экрана, было бы негде появиться. Здесь она переживает переход.
 *
 * Само убранное лежит сутки в своей таблице, помеченное `removedAt`; здесь —
 * только память о последнем, ради полоски. Пропала она — убранное всё равно
 * лежит, просто вернуть его уже нечем: и это правда, которую полоске незачем
 * произносить.
 */
class Trash(
    private val schedule: ScheduleRepository,
    private val deedTasks: DeedTaskRepository,
    private val notes: NoteRepository,
    private val yet: YetRepository,
    private val ledger: LedgerRepository,
) {

    enum class Kind(val what: String) {
        DEED("Дело убрано"),
        /** Строка списка внутри дела — та же полоска, что у строки Yet. */
        DEED_ROW("Строка убрана"),
        NOTE("Запись убрана"),
        YET_ROW("Строка убрана"),
        MONEY("Запись убрана"),
    }

    /** Последнее убранное — то, о чём говорит полоска внизу экрана. */
    data class Removed(val kind: Kind, val id: Long)

    val last = MutableStateFlow<Removed?>(null)

    /** Отметить, что убрали: полоска покажется сама. */
    fun remembered(kind: Kind, id: Long) {
        last.value = Removed(kind, id)
    }

    /** Вернуть последнее убранное. */
    suspend fun restore() {
        val removed = last.value ?: return
        last.value = null
        when (removed.kind) {
            Kind.DEED -> schedule.restore(removed.id)
            Kind.DEED_ROW -> deedTasks.restore(removed.id)
            Kind.NOTE -> notes.restore(removed.id)
            Kind.YET_ROW -> yet.restoreItem(removed.id)
            Kind.MONEY -> ledger.restore(removed.id)
        }
    }

    fun forget() {
        last.value = null
    }

    /**
     * Выбросить всё, что пролежало убранным сутки.
     *
     * При запуске приложения и без фоновой службы: службу ради уборки пяти
     * таблиц заводить не за что, а запускают Askya чаще, чем раз в сутки. Кто
     * не запускал неделю — у того и убранное неделю никому не мешало.
     */
    suspend fun purge() {
        schedule.purgeTrash()
        deedTasks.purgeTrash()
        notes.purgeTrash()
        yet.purgeTrash()
        ledger.purgeTrash()
    }
}
