package app.askya.ui.components

import androidx.compose.runtime.Composable

/** Распознавания речи у Windows-версии нет — см. [DICTATION_AVAILABLE]. */
@Composable
internal actual fun rememberDictation(
    onHeard: (text: String, done: Boolean) -> Unit,
    onProblem: (String) -> Unit,
): Dictation = Dictation(listening = false, start = {}, stop = {})

internal actual val DICTATION_AVAILABLE: Boolean = false
