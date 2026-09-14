package app.askya.ui.scroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.app.AndroidContainer
import app.askya.data.audio.VoiceRecorder
import app.askya.data.audio.VoiceStore
import app.askya.data.repository.NoteRepository
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Запись голосовой заметки — микрофон, файл под неё и заметка по готовому.
 *
 * Отдельно от [ScrollViewModel], хотя жила в ней: Scroll общий с
 * Windows-версией, а микрофон и папка «Music/Askya» есть только у телефона.
 * Сами голосовые заметки — строки той же таблицы, и переименовывают и убирают
 * их по-прежнему через Scroll.
 */
class VoiceRecordModel(
    private val notes: NoteRepository,
    private val voiceStore: VoiceStore,
    private val recorder: VoiceRecorder,
) : ViewModel() {

    /**
     * Начать запись.
     *
     * Файл заводится здесь, а не в диктофоне: заводит его папка Askya, и она
     * же умеет его убрать, если записи не случилось. Диктофону достаётся уже
     * открытый дескриптор — ровно то, что просит `MediaRecorder`.
     */
    fun startRecording() {
        if (recorder.state.value.going) return
        viewModelScope.launch {
            val place = voiceStore.create("golos") ?: return@launch
            recorder.start(place)
        }
    }

    /**
     * Закончить запись и завести по ней заметку.
     *
     * Заголовок — день и час: заметку наговаривают на бегу, и вопрос «как её
     * назвать» ровно в эту минуту стоил бы той мысли, ради которой её и
     * записывали. Переименовать можно потом, из карточки.
     *
     * Зовётся и по кнопке «Готово», и просто при уходе с экрана: микрофон в
     * фоне Askya не держит, а наговорённое до ухода терять нельзя.
     */
    fun stopRecording() {
        val done = recorder.stop() ?: return
        viewModelScope.launch {
            notes.addVoice(
                uri = done.uri,
                title = VOICE_STAMP.format(LocalDateTime.now()),
                durationMs = done.durationMs,
            )
        }
    }

    /** Бросить запись: ни заметки, ни файла не остаётся. */
    fun cancelRecording() = recorder.cancel()

    companion object {
        fun factory(container: AndroidContainer) = viewModelFactory {
            initializer {
                VoiceRecordModel(
                    container.noteRepository,
                    container.voiceStore,
                    container.voiceRecorder,
                )
            }
        }
    }
}

/**
 * День и час, которыми подписывается только что наговорённая заметка:
 * «27 августа, 14:32».
 *
 * Язык задан явно, а не взят системный: приложение написано по-русски целиком,
 * и заметка, подписанная «27 August» на телефоне с английской системой,
 * выглядела бы чужой строкой в своём же списке.
 */
private val VOICE_STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru"))
