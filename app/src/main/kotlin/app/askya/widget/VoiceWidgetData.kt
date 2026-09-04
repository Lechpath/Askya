package app.askya.widget

import android.content.Context
import app.askya.app.AskyaApplication
import app.askya.echo.formatDuration
import kotlinx.coroutines.flow.first

/**
 * Что написано на виджете голосовой заметки под кнопкой.
 *
 * [last] — одна строка: имя последней записи и её длина. Не список: виджет
 * заводят, чтобы **сказать**, а не чтобы перебирать сказанное, и список из
 * пяти строк под кнопкой превратил бы кнопку в подпись к списку.
 *
 * Строка всё же нужна, и вот зачем. Кнопка, которая ничего не показывает, —
 * это кнопка, о которой не знаешь, работает ли она: наговорил на бегу, и
 * записалось ли — видно только зайдя в раздел. Имя последней заметки на
 * рабочем столе и есть ответ «да, записалось», не стоящий ни одного нажатия.
 */
data class VoiceWidgetState(val last: String)

/** Последняя голосовая заметка — для виджета на рабочем столе. */
object VoiceWidgetData {

    suspend fun read(context: Context): VoiceWidgetState {
        val container = (context.applicationContext as AskyaApplication).container
        val voices = runCatching { container.noteRepository.voices().first() }.getOrDefault(emptyList())

        val last = voices.firstOrNull()
            ?: return VoiceWidgetState(last = "Пока ничего не записано")

        // Длина — только у той, у которой она известна: у заметки, поднятой
        // из старой базы, её может не быть, и «· 0:00» врало бы про пустую
        // запись там, где запись есть.
        val length = formatDuration(last.durationMs).takeIf { last.durationMs > 0 }
        return VoiceWidgetState(
            last = listOfNotNull(last.title.ifBlank { "Голос" }, length).joinToString(" · "),
        )
    }
}
