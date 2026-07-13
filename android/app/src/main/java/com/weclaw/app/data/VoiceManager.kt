package com.weclaw.app.ui.chat

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ListeningState { IDLE, LISTENING, PROCESSING }

class VoiceManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(ListeningState.IDLE)
    val state: StateFlow<ListeningState> = _state

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText

    private var onResult: ((String) -> Unit)? = null

    fun startListening(onResult: (String) -> Unit) {
        this.onResult = onResult
        stopRecognizer()

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    _state.value = ListeningState.LISTENING
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val results = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!results.isNullOrEmpty()) {
                        _partialText.value = results[0]
                    }
                }

                override fun onResults(results: android.os.Bundle?) {
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!texts.isNullOrEmpty()) {
                        _state.value = ListeningState.PROCESSING
                        onResult(texts[0])
                    }
                    restart()
                }

                override fun onError(error: Int) {
                    // 静默重试
                    restart()
                }

                override fun onEndOfSpeech() {}

                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })

            startListening(createIntent())
        }
    }

    private fun createIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun restart() {
        val cb = onResult
        stopRecognizer()
        if (cb != null) startListening(cb)
    }

    fun stop() {
        onResult = null
        stopRecognizer()
    }

    private fun stopRecognizer() {
        recognizer?.apply {
            try { stopListening() } catch (_: Exception) {}
            try { destroy() } catch (_: Exception) {}
        }
        recognizer = null
        _state.value = ListeningState.IDLE
    }
}
