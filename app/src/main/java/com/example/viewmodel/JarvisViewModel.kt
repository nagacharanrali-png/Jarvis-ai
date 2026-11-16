package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.HologramAspectRatio
import com.example.ai.JarvisAiRepository
import com.example.ai.JarvisCoreMode
import com.example.ai.StreamEvent
import com.example.data.JarvisPreferences
import com.example.data.JarvisSettings
import com.example.security.AppCheckRegistry
import com.example.security.AppCheckStatus
import com.example.voice.JarvisSpeechEngine
import com.example.voice.JarvisTtsEngine
import com.example.voice.TtsState
import com.example.voice.VoiceInputState
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "JarvisViewModel"

enum class JarvisTab {
    COMMS,
    HOLOGRAM
}

enum class JarvisRole {
    USER,
    JARVIS
}

data class JarvisMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: JarvisRole,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val attachedImage: Bitmap? = null,
    val isStreaming: Boolean = false,
    val statusHint: String? = null,
    val isError: Boolean = false,
    val modelUsed: String? = null,
    val latencyMs: Long? = null,
    val retryPrompt: String? = null,
    val retryImage: Bitmap? = null
)

sealed interface HologramUiState {
    data object Idle : HologramUiState
    data class Loading(val statusHint: String = "RENDERING HOLOGRAM…") : HologramUiState
    data class Success(
        val bitmap: Bitmap,
        val prompt: String,
        val aspectRatio: HologramAspectRatio
    ) : HologramUiState

    data class Error(val message: String) : HologramUiState
}

sealed interface HologramAnalysisState {
    data object Idle : HologramAnalysisState
    data object Loading : HologramAnalysisState
    data class Success(val analysis: String) : HologramAnalysisState
    data class Error(val message: String) : HologramAnalysisState
}

sealed interface LiveDiagnosticState {
    data object Idle : LiveDiagnosticState
    data class Running(val step: String) : LiveDiagnosticState
    data class Passed(
        val appCheckSummary: String,
        val geminiReply: String,
        val latencyMs: Long
    ) : LiveDiagnosticState

    data class Failed(
        val stage: String,
        val message: String
    ) : LiveDiagnosticState
}

data class JarvisUiState(
    val messages: List<JarvisMessage> = emptyList(),
    val activeTab: JarvisTab = JarvisTab.COMMS,
    val inputText: String = "",
    val pendingAttachment: Bitmap? = null,
    val isGenerating: Boolean = false,
    val hologramPrompt: String = "",
    val hologramSourceImage: Bitmap? = null,
    val selectedAspectRatio: HologramAspectRatio = HologramAspectRatio.SQUARE,
    val hologramState: HologramUiState = HologramUiState.Idle,
    val hologramAnalysisState: HologramAnalysisState = HologramAnalysisState.Idle,
    val settings: JarvisSettings = JarvisSettings(),
    val voiceState: VoiceInputState = VoiceInputState.Idle,
    val isSpeechRecognitionAvailable: Boolean = true,
    val ttsState: TtsState = TtsState(),
    val appCheckStatus: AppCheckStatus = AppCheckStatus(),
    val liveDiagnosticState: LiveDiagnosticState = LiveDiagnosticState.Idle,
    val showSettingsSheet: Boolean = false,
    val lastLatencyMs: Long? = null
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val aiRepository = JarvisAiRepository()
    private val preferences = JarvisPreferences(application.applicationContext)
    private val ttsEngine = JarvisTtsEngine(application.applicationContext)
    private val speechEngine = JarvisSpeechEngine(
        context = application.applicationContext,
        onFinalTranscript = { transcript ->
            handleVoiceTranscript(transcript)
        }
    )

    private val _uiState = MutableStateFlow(
        JarvisUiState(isSpeechRecognitionAvailable = speechEngine.isAvailable)
    )
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var activeHologramJob: Job? = null
    private var activeAnalysisJob: Job? = null
    private var activeDiagnosticJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            speechEngine.state.collect { voiceState ->
                _uiState.update { it.copy(voiceState = voiceState) }
            }
        }
        viewModelScope.launch {
            ttsEngine.state.collect { ttsState ->
                _uiState.update { it.copy(ttsState = ttsState) }
            }
        }
        viewModelScope.launch {
            AppCheckRegistry.status.collect { status ->
                _uiState.update { it.copy(appCheckStatus = status) }
            }
        }
    }

    fun selectTab(tab: JarvisTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun updateHologramPrompt(prompt: String) {
        _uiState.update { it.copy(hologramPrompt = prompt) }
    }

    fun selectAspectRatio(ratio: HologramAspectRatio) {
        _uiState.update { it.copy(selectedAspectRatio = ratio) }
    }

    fun setSettingsSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showSettingsSheet = visible) }
    }

    fun attachChatImage(uri: Uri) {
        viewModelScope.launch {
            val bitmap = decodeScaledBitmap(uri)
            if (bitmap != null) {
                _uiState.update { it.copy(pendingAttachment = bitmap) }
            }
        }
    }

    fun clearChatAttachment() {
        _uiState.update { it.copy(pendingAttachment = null) }
    }

    fun attachHologramSourceImage(uri: Uri) {
        viewModelScope.launch {
            val bitmap = decodeScaledBitmap(uri)
            if (bitmap != null) {
                _uiState.update {
                    it.copy(
                        hologramSourceImage = bitmap,
                        hologramAnalysisState = HologramAnalysisState.Idle
                    )
                }
            }
        }
    }

    fun clearHologramSourceImage() {
        _uiState.update {
            it.copy(
                hologramSourceImage = null,
                hologramAnalysisState = HologramAnalysisState.Idle
            )
        }
    }

    fun startVoiceListening() {
        ttsEngine.stop()
        speechEngine.startListening()
    }

    fun stopVoiceListening() {
        speechEngine.stopListening()
    }

    fun dismissVoiceError() {
        speechEngine.clearError()
    }

    fun toggleSpeechForMessage(message: JarvisMessage) {
        val tts = _uiState.value.ttsState
        if (tts.isSpeaking && tts.speakingMessageId == message.id) {
            ttsEngine.stop()
        } else {
            val settings = _uiState.value.settings
            ttsEngine.speak(
                messageId = message.id,
                rawText = message.text,
                rate = settings.speechRate,
                pitch = settings.speechPitch
            )
        }
    }

    fun speakText(utteranceId: String, text: String) {
        val settings = _uiState.value.settings
        ttsEngine.speak(
            messageId = utteranceId,
            rawText = text,
            rate = settings.speechRate,
            pitch = settings.speechPitch
        )
    }

    fun stopSpeaking() {
        ttsEngine.stop()
    }

    fun testVoiceCalibration() {
        val settings = _uiState.value.settings
        ttsEngine.speak(
            messageId = "calibration_test",
            rawText = "All systems nominal. Jarvis neural audio calibration online and ready for your command.",
            rate = settings.speechRate,
            pitch = settings.speechPitch
        )
    }

    fun setAutoSpeak(enabled: Boolean) {
        if (!enabled) ttsEngine.stop()
        viewModelScope.launch { preferences.setAutoSpeak(enabled) }
    }

    fun setAutoSendVoice(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoSendVoice(enabled) }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { preferences.setSpeechRate(rate) }
    }

    fun setSpeechPitch(pitch: Float) {
        viewModelScope.launch { preferences.setSpeechPitch(pitch) }
    }

    fun setCoreMode(mode: JarvisCoreMode) {
        viewModelScope.launch { preferences.setCoreMode(mode) }
    }

    fun clearConversation() {
        activeGenerationJob?.cancel()
        ttsEngine.stop()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                isGenerating = false,
                lastLatencyMs = null
            )
        }
    }

    fun submitQuickPrompt(prompt: String) {
        sendMessage(overridePrompt = prompt, speakResponseOverride = null)
    }

    fun retryFailedMessage(failedMessage: JarvisMessage, useRapidFallback: Boolean = false) {
        val prompt = failedMessage.retryPrompt ?: return
        if (_uiState.value.isGenerating) return

        val targetMode = if (useRapidFallback) {
            JarvisCoreMode.RAPID
        } else {
            _uiState.value.settings.coreMode
        }
        if (useRapidFallback) {
            setCoreMode(JarvisCoreMode.RAPID)
        }

        val historyWithoutError = _uiState.value.messages.filterNot { it.id == failedMessage.id }
        val assistantMessageId = UUID.randomUUID().toString()
        val streamingPlaceholder = JarvisMessage(
            id = assistantMessageId,
            role = JarvisRole.JARVIS,
            text = "",
            isStreaming = true,
            statusHint = "Connecting to ${targetMode.displayLabel}…",
            modelUsed = targetMode.shortBadge,
            retryPrompt = prompt,
            retryImage = failedMessage.retryImage
        )

        val historyMessages = historyWithoutError.dropLastWhile {
            it.role == JarvisRole.USER && it.text == prompt
        }
        val historySnapshot = buildConversationHistory(historyMessages)

        _uiState.update {
            it.copy(
                messages = historyWithoutError + streamingPlaceholder,
                isGenerating = true
            )
        }

        launchStreamingInference(
            assistantMessageId = assistantMessageId,
            mode = targetMode,
            historySnapshot = historySnapshot,
            prompt = prompt,
            attachedImage = failedMessage.retryImage,
            shouldAutoSpeak = _uiState.value.settings.autoSpeak
        )
    }

    fun sendMessage(
        overridePrompt: String? = null,
        speakResponseOverride: Boolean? = null
    ) {
        val currentState = _uiState.value
        val promptText = (overridePrompt ?: currentState.inputText).trim()
        val imageBitmap = if (overridePrompt == null) currentState.pendingAttachment else null

        if (promptText.isEmpty() && imageBitmap == null) return
        if (currentState.isGenerating) return

        ttsEngine.stop()

        val displayPrompt = promptText.ifEmpty { "Analyze attached visual." }
        val userMessage = JarvisMessage(
            role = JarvisRole.USER,
            text = displayPrompt,
            attachedImage = imageBitmap
        )
        val assistantMessageId = UUID.randomUUID().toString()
        val mode = currentState.settings.coreMode
        val streamingPlaceholder = JarvisMessage(
            id = assistantMessageId,
            role = JarvisRole.JARVIS,
            text = "",
            isStreaming = true,
            statusHint = "Connecting to ${mode.displayLabel}…",
            modelUsed = mode.shortBadge,
            retryPrompt = displayPrompt,
            retryImage = imageBitmap
        )

        val historySnapshot = buildConversationHistory(currentState.messages)

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + streamingPlaceholder,
                inputText = if (overridePrompt == null) "" else it.inputText,
                pendingAttachment = if (overridePrompt == null) null else it.pendingAttachment,
                isGenerating = true
            )
        }

        val shouldAutoSpeak = speakResponseOverride ?: currentState.settings.autoSpeak
        launchStreamingInference(
            assistantMessageId = assistantMessageId,
            mode = mode,
            historySnapshot = historySnapshot,
            prompt = displayPrompt,
            attachedImage = imageBitmap,
            shouldAutoSpeak = shouldAutoSpeak
        )
    }

    private fun launchStreamingInference(
        assistantMessageId: String,
        mode: JarvisCoreMode,
        historySnapshot: List<Content>,
        prompt: String,
        attachedImage: Bitmap?,
        shouldAutoSpeak: Boolean
    ) {
        activeGenerationJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val accumulated = StringBuilder()
            try {
                aiRepository.streamChatTurnWithRetry(
                    mode = mode,
                    history = historySnapshot,
                    prompt = prompt,
                    attachedImage = attachedImage
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Status -> {
                            _uiState.update { state ->
                                state.copy(
                                    messages = state.messages.map { msg ->
                                        if (msg.id == assistantMessageId) {
                                            msg.copy(statusHint = event.hint)
                                        } else {
                                            msg
                                        }
                                    }
                                )
                            }
                        }
                        is StreamEvent.Chunk -> {
                            accumulated.append(event.text)
                            val partial = accumulated.toString()
                            _uiState.update { state ->
                                state.copy(
                                    messages = state.messages.map { msg ->
                                        if (msg.id == assistantMessageId) {
                                            msg.copy(
                                                text = partial,
                                                isStreaming = true,
                                                statusHint = null
                                            )
                                        } else {
                                            msg
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                val finalText = accumulated.toString().trim()
                val elapsed = System.currentTimeMillis() - startTime
                if (finalText.isEmpty()) {
                    markAssistantError(
                        assistantMessageId,
                        "Neural core returned an empty response. Tap Retry to try again."
                    )
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isGenerating = false,
                            lastLatencyMs = elapsed,
                            messages = state.messages.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(
                                        text = finalText,
                                        isStreaming = false,
                                        statusHint = null,
                                        isError = false,
                                        latencyMs = elapsed,
                                        modelUsed = mode.shortBadge
                                    )
                                } else {
                                    msg
                                }
                            }
                        )
                    }
                    if (shouldAutoSpeak) {
                        val settings = _uiState.value.settings
                        ttsEngine.speak(
                            messageId = assistantMessageId,
                            rawText = finalText,
                            rate = settings.speechRate,
                            pitch = settings.speechPitch
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini stream failed", e)
                markAssistantError(
                    assistantMessageId,
                    aiRepository.formatReadableError(e, mode)
                )
            }
        }
    }

    fun generateHologram() {
        val state = _uiState.value
        val prompt = state.hologramPrompt.trim()
        if (prompt.isEmpty() || state.hologramState is HologramUiState.Loading) return

        val sourceImage = state.hologramSourceImage
        val ratio = state.selectedAspectRatio

        activeHologramJob?.cancel()
        activeHologramJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    hologramState = HologramUiState.Loading("RENDERING HOLOGRAM (30–60s)…"),
                    hologramAnalysisState = HologramAnalysisState.Idle
                )
            }
            try {
                val bitmap = aiRepository.generateOrEditHologram(
                    prompt = prompt,
                    sourceImage = sourceImage,
                    aspectRatio = ratio,
                    onStatusUpdate = { status ->
                        _uiState.update {
                            it.copy(hologramState = HologramUiState.Loading(status))
                        }
                    }
                )
                if (bitmap != null) {
                    _uiState.update {
                        it.copy(
                            hologramState = HologramUiState.Success(
                                bitmap = bitmap,
                                prompt = prompt,
                                aspectRatio = ratio
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            hologramState = HologramUiState.Error(
                                "No holographic image was returned by the model."
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Hologram synthesis failed", e)
                _uiState.update {
                    it.copy(
                        hologramState = HologramUiState.Error(
                            aiRepository.formatReadableError(e, JarvisCoreMode.STANDARD)
                        )
                    )
                }
            }
        }
    }

    fun analyzeHologramVisual() {
        val state = _uiState.value
        val targetBitmap = (state.hologramState as? HologramUiState.Success)?.bitmap
            ?: state.hologramSourceImage
            ?: return

        if (state.hologramAnalysisState is HologramAnalysisState.Loading) return

        val mode = state.settings.coreMode
        val customPrompt = state.hologramPrompt.trim()
        val instruction = if (customPrompt.isNotBlank()) {
            "Analyze this visual with context: \"$customPrompt\". Summarize key objects, layout, colors, and technical observations."
        } else {
            "Analyze this visual in crisp detail. Identify key elements, composition, text, and notable characteristics."
        }

        activeAnalysisJob?.cancel()
        activeAnalysisJob = viewModelScope.launch {
            _uiState.update { it.copy(hologramAnalysisState = HologramAnalysisState.Loading) }
            try {
                val analysis = aiRepository.analyzeImageWithGemini(
                    mode = mode,
                    bitmap = targetBitmap,
                    instruction = instruction
                )
                _uiState.update {
                    it.copy(hologramAnalysisState = HologramAnalysisState.Success(analysis))
                }
                if (_uiState.value.settings.autoSpeak) {
                    speakText("hologram_analysis", analysis)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Hologram image understanding failed", e)
                _uiState.update {
                    it.copy(
                        hologramAnalysisState = HologramAnalysisState.Error(
                            aiRepository.formatReadableError(e, mode)
                        )
                    )
                }
            }
        }
    }

    fun runLiveSelfTest() {
        if (_uiState.value.liveDiagnosticState is LiveDiagnosticState.Running) return

        activeDiagnosticJob?.cancel()
        activeDiagnosticJob = viewModelScope.launch {
            _uiState.update {
                it.copy(liveDiagnosticState = LiveDiagnosticState.Running("Verifying App Check token…"))
            }
            val attestation = AppCheckRegistry.verifyAttestation()
            if (attestation.isFailure) {
                val err = attestation.exceptionOrNull()
                    ?: IllegalStateException("App Check attestation failed.")
                _uiState.update {
                    it.copy(
                        liveDiagnosticState = LiveDiagnosticState.Failed(
                            stage = "Firebase App Check",
                            message = aiRepository.formatReadableError(err, JarvisCoreMode.STANDARD)
                        )
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    liveDiagnosticState = LiveDiagnosticState.Running(
                        "Testing live Gemini 3.8 Flash response…"
                    )
                )
            }
            try {
                val (reply, latencyMs) = aiRepository.verifyGeminiLiveLink(JarvisCoreMode.STANDARD)
                _uiState.update {
                    it.copy(
                        lastLatencyMs = latencyMs,
                        liveDiagnosticState = LiveDiagnosticState.Passed(
                            appCheckSummary = attestation.getOrDefault("App Check attestation verified."),
                            geminiReply = reply,
                            latencyMs = latencyMs
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live Gemini diagnostic failed", e)
                _uiState.update {
                    it.copy(
                        liveDiagnosticState = LiveDiagnosticState.Failed(
                            stage = "Gemini 3.8 Flash",
                            message = aiRepository.formatReadableError(e, JarvisCoreMode.STANDARD)
                        )
                    )
                }
            }
        }
    }

    private fun handleVoiceTranscript(transcript: String) {
        val settings = _uiState.value.settings
        if (settings.autoSendVoice && _uiState.value.activeTab == JarvisTab.COMMS) {
            sendMessage(overridePrompt = transcript, speakResponseOverride = true)
        } else if (_uiState.value.activeTab == JarvisTab.HOLOGRAM) {
            _uiState.update {
                val merged = if (it.hologramPrompt.isBlank()) transcript else "${it.hologramPrompt} $transcript"
                it.copy(hologramPrompt = merged)
            }
        } else {
            _uiState.update {
                val merged = if (it.inputText.isBlank()) transcript else "${it.inputText} $transcript"
                it.copy(inputText = merged)
            }
        }
    }

    private fun markAssistantError(messageId: String, errorText: String) {
        _uiState.update { state ->
            state.copy(
                isGenerating = false,
                messages = state.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(
                            text = errorText,
                            isStreaming = false,
                            statusHint = null,
                            isError = true
                        )
                    } else {
                        msg
                    }
                }
            )
        }
    }

    private fun buildConversationHistory(messages: List<JarvisMessage>): List<Content> {
        return messages
            .filter { !it.isError && !it.isStreaming && it.text.isNotBlank() }
            .takeLast(16)
            .map { msg ->
                val role = if (msg.role == JarvisRole.USER) "user" else "model"
                val textBody = if (msg.attachedImage != null && msg.role == JarvisRole.USER) {
                    "[Operator attached an image] ${msg.text}"
                } else {
                    msg.text
                }
                content(role) {
                    text(textBody)
                }
            }
    }

    private suspend fun decodeScaledBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val resolver = getApplication<Application>().contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val maxDim = 1280
            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDim ||
                boundsOptions.outHeight / sampleSize > maxDim
            ) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode image uri", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechEngine.destroy()
        ttsEngine.shutdown()
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { JarvisViewModel(application) }
        }
    }
}
