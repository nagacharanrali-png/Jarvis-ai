package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TtsState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val speakingMessageId: String? = null,
    val errorMessage: String? = null
)

class JarvisTtsEngine(context: Context) {

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val ukResult = engine.setLanguage(Locale.UK)
                if (ukResult == TextToSpeech.LANG_MISSING_DATA ||
                    ukResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    val usResult = engine.setLanguage(Locale.US)
                    if (usResult == TextToSpeech.LANG_MISSING_DATA ||
                        usResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        engine.language = Locale.getDefault()
                    }
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            isSpeaking = true,
                            speakingMessageId = utteranceId,
                            errorMessage = null
                        )
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            isSpeaking = false,
                            speakingMessageId = null
                        )
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            isSpeaking = false,
                            speakingMessageId = null,
                            errorMessage = "Speech synthesis interrupted."
                        )
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _state.value = _state.value.copy(
                            isSpeaking = false,
                            speakingMessageId = null,
                            errorMessage = "Speech synthesis error ($errorCode)."
                        )
                    }
                })
                _state.value = _state.value.copy(isReady = true, errorMessage = null)
            } else {
                _state.value = _state.value.copy(
                    isReady = false,
                    errorMessage = "Text-to-Speech engine unavailable on this device."
                )
            }
        }
    }

    fun speak(messageId: String, rawText: String, rate: Float, pitch: Float) {
        val engine = tts ?: return
        if (!_state.value.isReady) {
            _state.value = _state.value.copy(
                errorMessage = "Voice synthesizer is initializing. Try again momentarily."
            )
            return
        }
        val cleanText = sanitizeForSpeech(rawText)
        if (cleanText.isBlank()) return

        engine.setSpeechRate(rate.coerceIn(0.6f, 1.6f))
        engine.setPitch(pitch.coerceIn(0.6f, 1.4f))
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(isSpeaking = false, speakingMessageId = null)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
        }
    }

    private fun sanitizeForSpeech(input: String): String {
        return input
            .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("\\*\\*([^*]*)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]*)\\*"), "$1")
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^[-*]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
