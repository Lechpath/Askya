package app.askya.data.audio

/**
 * Папка голосовых заметок — то, что о ней знает общий код: убрать файл вместе
 * с записью. Пишет в неё только телефон; у Windows-версии микрофона нет, и
 * заметки, пришедшие Слепком, лежат строками без звука (см. README).
 */
interface VoiceFiles {
    suspend fun remove(uri: String?)
}

/** Во что пишется голос: AAC в mp4 — это играют все плееры телефона. */
const val VOICE_MIME = "audio/mp4"
