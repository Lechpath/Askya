package app.askya.reminders

import app.askya.platform.PlatformContext

/**
 * Мелодия напоминания: чем звонить и как это называется.
 *
 * [uri] пусто — обычный звук напоминания, тот, что телефон играет по умолчанию.
 * Хранится и показывается [title] — человек выбирал название, а не ссылку.
 */
data class ReminderSound(val uri: String?, val title: String)

/** Обычный звук напоминания — то, чем оно звучало всегда. */
val DefaultReminderSound = ReminderSound(uri = null, title = "Обычный звук")

/**
 * Откуда брать мелодии для напоминания и как дать их послушать.
 *
 * На телефоне — мелодии системы и своя музыка из той же библиотеки, по
 * которой играет AskyaEcho (`ReminderSounds`). У Windows-версии своих мелодий
 * нет: напоминание звучит звуком уведомлений Windows, и выбирать там можно
 * только «молча» или «со звуком».
 */
interface ReminderSoundSource {
    /** Мелодии системы; пусто — их нет. */
    suspend fun system(context: PlatformContext): List<ReminderSound>

    /** Своя музыка; пусто — нет или не дали доступа. */
    suspend fun music(context: PlatformContext): List<ReminderSound>

    fun hasMusicAccess(context: PlatformContext): Boolean

    /** Разрешение на музыку; `null` — своей музыки у системы нет вовсе. */
    val musicPermission: String?

    /** Дать послушать; [uri] пусто — обычный звук. */
    fun preview(context: PlatformContext, uri: String?)

    fun stopPreview()
}
