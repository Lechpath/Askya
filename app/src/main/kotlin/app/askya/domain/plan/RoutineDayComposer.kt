package app.askya.domain.plan

/**
 * Сборка дня без модели: распорядок как есть плюс то, что человек уже записал
 * в день сам.
 *
 * Последний в цепочке. Ничего не решает и решать не может — ни про день
 * недели, ни про самочувствие; это ровно то же разворачивание распорядка,
 * которое происходит при первом открытии дня. Смысл в том, чтобы нажатие
 * «собрать день» без сети или без ключа всё равно чем-то заканчивалось.
 */
class RoutineDayComposer : DayComposer {

    override suspend fun compose(request: DayRequest): DayLayout {
        val fromRoutine = request.routine.map { item ->
            PlannedItem(
                title = item.title,
                startTime = item.startTime,
                endTime = item.endTime,
            )
        }

        // Записанное вручную не теряется: распорядок его не знает, а человек
        // ставил осознанно. Совпадения по названию отсеиваются, иначе после
        // сборки день бы задвоился.
        val known = fromRoutine.map { it.title.lowercase() }.toSet()
        val ownHand = request.existing
            .filter { it.title.lowercase() !in known }
            .map { item ->
                PlannedItem(
                    title = item.title,
                    startTime = item.startTime,
                    endTime = item.endTime,
                    note = item.note,
                )
            }

        return DayLayout(
            items = (fromRoutine + ownHand).sortedBy { it.startTime },
            source = SOURCE,
        )
    }

    companion object {
        const val SOURCE = "Распорядок"
    }
}
