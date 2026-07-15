package com.weclaw.app.ui.recorder

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class RecorderState { IDLE, RECORDING, TRANSCRIBING, DONE }

data class RecorderUiState(
    val state: RecorderState = RecorderState.IDLE,
    val elapsedSeconds: Int = 0,
    val transcript: String = "",
    val partialText: String = "",
    val audioFile: File? = null,
)

class RecorderViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(RecorderUiState())
    val state: StateFlow<RecorderUiState> = _state

    private var mediaRecorder: MediaRecorder? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioFile: File? = null
    private var timerJob: kotlinx.coroutines.Job? = null
    private var onResult: ((String) -> Unit)? = null

    fun startRecording(onTranscriptResult: (String) -> Unit) {
        this.onResult = onTranscriptResult

        // Prepare audio file
        audioFile = File(context.cacheDir, "recording_${System.currentTimeMillis()}.mp3")

        // MediaRecorder
        mediaRecorder = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setOutputFile(audioFile!!.absolutePath)
            try { prepare(); start() } catch (_: Exception) {}
        }

        // SpeechRecognizer for real-time partial transcription
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!texts.isNullOrEmpty()) {
                        val text = texts[0]
                        _state.update { it.copy(transcript = text, partialText = "") }
                        // Auto-stop on 结束
                        if (text.contains("结束")) {
                            stopRecording()
                        }
                    }
                }
                override fun onPartialResults(partial: android.os.Bundle?) {
                    val texts = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!texts.isNullOrEmpty()) {
                        _state.update { it.copy(partialText = texts[0]) }
                    }
                }
                override fun onReadyForSpeech(_b: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(_b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(_e: Int) {}
                override fun onEvent(_e: Int, _b: android.os.Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
        }

        _state.update { it.copy(state = RecorderState.RECORDING, elapsedSeconds = 0, transcript = "") }

        // Timer
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    fun stopRecording() {
        timerJob?.cancel()
        mediaRecorder?.apply { try { stop(); release() } catch (_: Exception) {} }
        mediaRecorder = null
        speechRecognizer?.apply { try { stopListening(); destroy() } catch (_: Exception) {} }
        speechRecognizer = null

        val text = _state.value.transcript
        if (text.isNotBlank()) {
            _state.update { it.copy(state = RecorderState.DONE, audioFile = audioFile) }
            onResult?.invoke(text)
        } else {
            _state.update { it.copy(state = RecorderState.IDLE) }
        }
    }

    fun reset() {
        _state.value = RecorderUiState()
        audioFile?.delete()
        audioFile = null
    }

    override fun onCleared() {
        stopRecording()
        reset()
    }
}
