package app.askya.agent

import app.askya.data.preferences.AppSettings
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * То, от чего зависит, как понимать просьбу, — и ничего больше.
 *
 * «Завтра», «в пятницу», «через час» без даты, часа и пояса не значат ничего;
 * «на этой неделе» — без того, с какого дня она начинается. Собрался ли день
 * сам ([autoFillDay]) — от этого зависит, пуст ли он на самом деле или его
 * просто ещё не открывали.
 *
 * Данных человека здесь нет и быть не должно: дела, заметки и напоминания
 * модель получает только инструментами и только когда спросила.
 *
 * Нет здесь и ничего о клиенте модели: локальный он или облачный и можно ли
 * в сеть, инструменты не знают. Это решает тот, кто отправляет разговор
 * ([AgentPolicy.allowsClient]).
 */
data class AgentContext(
    val currentDate: LocalDate,
    /** С точностью до минуты: секунды просьбу не меняют, а контекст менять будут. */
    val currentTime: LocalTime,
    val timezone: ZoneId,
    val weekStartsMonday: Boolean,
    val autoFillDay: Boolean,
) {
    companion object {

        /** Контекст на сейчас — из настроек Askya и часов. */
        fun of(settings: AppSettings, clock: Clock = Clock.systemDefaultZone()): AgentContext {
            val now = clock.instant().atZone(clock.zone)
            return AgentContext(
                currentDate = now.toLocalDate(),
                currentTime = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                timezone = clock.zone,
                weekStartsMonday = settings.weekStartsMonday,
                autoFillDay = settings.autoFillDay,
            )
        }
    }
}
