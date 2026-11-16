package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceInputState {
    data object Idle : VoiceInputState
    data class Listening(
        val partialTranscript: String = "",
        val amplitude: Float = 0.15f
    ) : VoiceInputState

    data class Error(val message: String) : VoiceInputState
}

class JarvisSpeechEngine(
    private val context: Context,
    private val onFinalTranscript: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isAvailable) {
            _state.value = VoiceInputState.Error(
                "On-device speech recognizer is unavailable on this device."
            )
            return
        }
        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            _state.value = VoiceInputState.Listening(partialTranscript = "", amplitude = 0.18f)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _state.value = VoiceInputState.Error(
                e.localizedMessage ?: "Failed to initialize voice recognition."
            )
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        if (_state.value is VoiceInputState.Listening) {
            _state.value = VoiceInputState.Idle
        }
    }

    fun clearError() {
        if (_state.value is VoiceInputState.Error) {
            _state.value = VoiceInputState.Idle
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceInputState.Listening(partialTranscript = "", amplitude = 0.22f)
        }

        override fun onBeginningOfSpeech() {
            val current = (_state.value as? VoiceInputState.Listening)?.partialTranscript.orEmpty()
            _state.value = VoiceInputState.Listening(partialTranscript = current, amplitude = 0.35f)
        }

        override fun onRmsChanged(rmsdB: Float) {
            val current = _state.value as? VoiceInputState.Listening ?: return
            // Map typical SpeechRecognizer RMS (-2..10 dB) smoothly to 0.1..1.0
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
            _state.value = current.copy(amplitude = normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            val current = _state.value as? VoiceInputState.Listening ?: return
            _state.value = current.copy(amplitude = 0.12f)
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio capture error."
                SpeechRecognizer.ERROR_CLIENT -> "Voice client interrupted."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                SpeechRecognizer.ERROR_NETWORK -> "Network unavailable for speech recognition."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout."
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy. Momentarily resetting."
                SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out with no voice input."
                else -> "Voice input error ($error)."
            }
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                _state.value = VoiceInputState.Idle
            } else {
                _state.value = VoiceInputState.Error(message)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = matches?.firstOrNull().orEmpty().trim()
            _state.value = VoiceInputState.Idle
            if (transcript.isNotEmpty()) {
                onFinalTranscript(transcript)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull().orEmpty()
            val current = _state.value as? VoiceInputState.Listening ?: return
            if (partial.isNotBlank()) {
                _state.value = current.copy(partialTranscript = partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
