package app.askya.domain.plan

/**
 * Сборка дня: список дел как есть плюс то, что человек уже записал в день сам.
 *
 * Единственный сборщик и единственный способ собрать день. Раньше их было два —
 * «Собрать день» и «Заполнить по распорядку», — и после того как модель из
 * приложения ушла, они делали ровно одно и то же: разворачивали список дел в
 * дату. Две кнопки под одним действием заставляют выбирать там, где выбора
 * нет, поэтому осталась одна, и стоит она цветком в шапке AskyaDay.
 *
 * Одинаковые дела списка сюда не попадают дважды: из совпавших по названию или
 * описанию остаётся самое раннее — [keepEarliestOfSame].
 */
class RoutineDayComposer : DayComposer {

    override suspend fun compose(request: DayRequest): DayLayout {
        val fromRoutine = keepEarliestOfSame(
            items = request.routine,
            at = { it.startTime },
            title = { it.title },
        ).map { item ->
            PlannedItem(
                title = item.title,
                startTime = item.startTime,
                endTime = item.endTime,
            )
        }

        // Записанное вручную не теряется: список дел его не знает, а человек
        // ставил осознанно. Совпадения по названию отсеиваются, иначе после
        // сборки день бы задвоился.
        val known = fromRoutine.map { it.title.trim().lowercase() }.toSet()
        val ownHand = request.existing
            .filter { it.title.trim().lowercase() !in known }
            .map { item ->
                PlannedItem(
                    title = item.title,
                    startTime = item.startTime,
                    endTime = item.endTime,
                    note = item.note,
                )
            }

        // Второй отбор — уже по всему дню: у записанного руками бывает та же
        // заметка, что у дела списка, и по ней это одно и то же дело.
        return DayLayout(
            items = keepEarliestOfSame(
                items = fromRoutine + ownHand,
                at = { it.startTime },
                title = { it.title },
                note = { it.note },
            ),
            source = SOURCE,
        )
    }

    companion object {
        const val SOURCE = "Список дел"
    }
}
