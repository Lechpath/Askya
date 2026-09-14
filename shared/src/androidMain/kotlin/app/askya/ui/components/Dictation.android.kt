package app.askya.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

@Composable
internal actual fun rememberDictation(
    onHeard: (text: String, done: Boolean) -> Unit,
    onProblem: (String) -> Unit,
): Dictation {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }

    val heard by rememberUpdatedState(onHeard)
    val problem by rememberUpdatedState(onProblem)

    // Распознаватель заводится один раз на экран: он подключается к чужой
    // службе, и создавать его на каждое нажатие значило бы ждать этого
    // подключения перед каждым словом. `null` — распознавания на телефоне нет.
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                // Микрофон гасит не конец речи, а пришедший текст: между ними
                // распознаватель ещё дочитывает сказанное, и погасший на
                // полуслове значок выглядел бы потерянной фразой.
                override fun onEndOfSpeech() = Unit

                override fun onPartialResults(partialResults: Bundle?) {
                    spokenIn(partialResults)?.let { heard(it, false) }
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    spokenIn(results)?.let { heard(it, true) }
                }

                override fun onError(error: Int) {
                    listening = false
                    complaintOf(error)?.let(problem)
                }
            },
        )
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    fun listen() {
        val engine = recognizer
        if (engine == null) {
            problem(
                "На телефоне нет распознавания речи. Обычно его приносит " +
                    "приложение Google — без него надиктовать нечем.",
            )
            return
        }
        listening = true
        engine.startListening(request(context))
    }

    // Микрофон спрашивается в первый раз и только по нажатию значка: до него
    // надиктовка не нужна, а разрешение, спрошенное на входе в заметку,
    // выглядит как подслушивание.
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        if (allowed) {
            listen()
        } else {
            problem("Без доступа к микрофону надиктовать не получится.")
        }
    }

    return Dictation(
        listening = listening,
        start = {
            if (micAllowed(context)) listen() else ask.launch(Manifest.permission.RECORD_AUDIO)
        },
        stop = {
            listening = false
            // `stopListening`, а не `cancel`: сказанное до нажатия должно
            // дойти буквами, а не пропасть вместе с микрофоном.
            recognizer?.stopListening()
        },
    )
}

private fun micAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * О чём просим распознаватель: свободная речь на языке телефона, с
 * промежуточным текстом по ходу.
 */
private fun request(context: Context): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // Некоторые распознаватели отказываются работать без имени зовущего.
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        // Паузу терпим дольше обычного: в заметке думают вслух, и полторы
        // секунды тишины — это ещё не конец фразы.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE)
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            SILENCE,
        )
    }

/** Сколько тишины считается концом фразы. Подсказка: движки её вольны не слушать. */
private const val SILENCE = 2500L

/** Лучшее из услышанного — распознаватель отдаёт варианты по убыванию веры. */
private fun spokenIn(bundle: Bundle?): String? = bundle
    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    ?.firstOrNull()
    ?.takeIf { it.isNotBlank() }

/**
 * Что сказать про неудачу. `null` означает «молча погасить микрофон»: тишина и
 * нераспознанное — не поломка, и окно с извинениями после каждой оговорки
 * раздражало бы сильнее самой оговорки.
 */
private fun complaintOf(error: Int): String? = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        "Микрофон закрыт для Askya. Открыть его можно в настройках телефона."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        "Распознавание речи на этом телефоне работает только с интернетом, а его сейчас нет."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
        "Распознавание занято другим приложением. Попробуйте ещё раз."
    SpeechRecognizer.ERROR_AUDIO -> "Микрофон занят — похоже, его слушает что-то ещё."
    else -> "Не расслышал. Попробуйте ещё раз."
}

internal actual val DICTATION_AVAILABLE: Boolean = true
