package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout

private const val TAG = "JarvisAiRepository"

enum class JarvisCoreMode(
    val modelName: String,
    val displayLabel: String,
    val shortBadge: String,
    val description: String,
    val attemptTimeoutMs: Long
) {
    STANDARD(
        modelName = "gemini-3.8-flash",
        displayLabel = "Gemini 3.8 Flash",
        shortBadge = "3.8-FLASH",
        description = "Default fast neural core for daily intelligence, multimodal vision, and voice.",
        attemptTimeoutMs = 35_000L
    ),
    RAPID(
        modelName = "gemini-3.5-flash-lite",
        displayLabel = "Gemini 3.5 Flash-Lite",
        shortBadge = "3.5-LITE",
        description = "Ultra-low-latency core optimized for instant tactical commands.",
        attemptTimeoutMs = 25_000L
    ),
    DEEP_THINK(
        modelName = "gemini-4-argon",
        displayLabel = "Gemini 4 Argon",
        shortBadge = "4-ARGON",
        description = "High-capacity reasoning engine for complex architecture, STEM, and code.",
        attemptTimeoutMs = 50_000L
    )
}

enum class HologramAspectRatio(
    val label: String,
    val sdkRatio: AspectRatio
) {
    SQUARE("1:1", AspectRatio.SQUARE_1x1),
    LANDSCAPE_16_9("16:9", AspectRatio.LANDSCAPE_16x9),
    PORTRAIT_9_16("9:16", AspectRatio.PORTRAIT_9x16),
    LANDSCAPE_4_3("4:3", AspectRatio.LANDSCAPE_4x3),
    PORTRAIT_3_4("3:4", AspectRatio.PORTRAIT_3x4)
}

sealed interface StreamEvent {
    data class Status(val hint: String) : StreamEvent
    data class Chunk(val text: String) : StreamEvent
}

class JarvisAiRepository {

    private val jarvisSystemInstruction: Content = content {
        text(
            """
            You are J.A.R.V.I.S. (Just A Rather Very Intelligent System), a sleek, ultra-capable futuristic AI assistant.
            Communicate with poised, articulate, razor-sharp precision and subtle dry wit.
            Address the user naturally (occasionally as "Sir" or "Operator" when fitting, never overdone).
            Keep responses structured, crisp, and scannable for a holographic HUD and voice readout.
            When answering voice queries or brief questions, lead with a clear direct answer before expanding into technical detail.
            Never invent fake sensor readings or claim actions were executed on external hardware unless explicitly provided in context.
            """.trimIndent()
        )
    }

    private val standardModel: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = JarvisCoreMode.STANDARD.modelName,
            systemInstruction = jarvisSystemInstruction,
            requestOptions = RequestOptions(timeoutInMillis = 45_000L)
        )
    }

    private val deepThinkModel: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = JarvisCoreMode.DEEP_THINK.modelName,
            systemInstruction = jarvisSystemInstruction,
            requestOptions = RequestOptions(timeoutInMillis = 60_000L)
        )
    }

    private val rapidModel: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = JarvisCoreMode.RAPID.modelName,
            systemInstruction = jarvisSystemInstruction,
            requestOptions = RequestOptions(timeoutInMillis = 30_000L)
        )
    }

    private fun modelFor(mode: JarvisCoreMode): GenerativeModel = when (mode) {
        JarvisCoreMode.STANDARD -> standardModel
        JarvisCoreMode.DEEP_THINK -> deepThinkModel
        JarvisCoreMode.RAPID -> rapidModel
    }

    fun streamChatTurnWithRetry(
        mode: JarvisCoreMode,
        history: List<Content>,
        prompt: String,
        attachedImage: Bitmap? = null,
        maxAttempts: Int = 3
    ): Flow<StreamEvent> = flow {
        val outgoing = content("user") {
            if (attachedImage != null) {
                image(attachedImage)
            }
            text(prompt.ifBlank { "Analyze this visual input and report key observations." })
        }

        var attempt = 1
        var lastError: Throwable? = null

        while (attempt <= maxAttempts) {
            var emittedAnyToken = false
            try {
                emit(
                    StreamEvent.Status(
                        if (attempt == 1) {
                            "Connecting to ${mode.displayLabel}…"
                        } else {
                            "Reconnecting to ${mode.displayLabel} (attempt $attempt/$maxAttempts)…"
                        }
                    )
                )

                val chat = modelFor(mode).startChat(history = history)
                withTimeout(mode.attemptTimeoutMs) {
                    chat.sendMessageStream(outgoing)
                        .mapNotNull { chunk -> chunk.text }
                        .collect { token ->
                            if (token.isNotEmpty()) {
                                emittedAnyToken = true
                                emit(StreamEvent.Chunk(token))
                            }
                        }
                }
                return@flow
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    lastError = e
                    Log.w(TAG, "Attempt $attempt timed out after ${mode.attemptTimeoutMs}ms", e)
                    if (!emittedAnyToken && attempt < maxAttempts) {
                        val backoffMs = computeBackoffMs(attempt)
                        emit(
                            StreamEvent.Status(
                                "Request timed out — retrying in ${String.format("%.1fs", backoffMs / 1000.0)} ($attempt/$maxAttempts)…"
                            )
                        )
                        delay(backoffMs)
                        attempt++
                        continue
                    }
                    throw e
                } else {
                    throw e
                }
            } catch (e: Exception) {
                lastError = e
                val retryInfo = classifyRetryableError(e)
                Log.w(
                    TAG,
                    "Attempt $attempt failed (retryable=${retryInfo != null}): ${e.message}",
                    e
                )
                if (!emittedAnyToken && retryInfo != null && attempt < maxAttempts) {
                    val backoffMs = computeBackoffMs(attempt)
                    emit(
                        StreamEvent.Status(
                            "$retryInfo — retrying in ${String.format("%.1fs", backoffMs / 1000.0)} (attempt ${attempt + 1}/$maxAttempts)…"
                        )
                    )
                    delay(backoffMs)
                    attempt++
                    continue
                }
                throw e
            }
        }

        throw lastError ?: IllegalStateException("Unable to complete Gemini request.")
    }

    suspend fun analyzeImageWithGemini(
        mode: JarvisCoreMode = JarvisCoreMode.STANDARD,
        bitmap: Bitmap,
        instruction: String = "Analyze this visual in detail. Identify key elements, structure, text, and notable details."
    ): String {
        val maxAttempts = 3
        var attempt = 1
        while (true) {
            try {
                val response = withTimeout(mode.attemptTimeoutMs) {
                    modelFor(mode).generateContent(
                        content {
                            image(bitmap)
                            text(instruction)
                        }
                    )
                }
                return response.text?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("Model returned an empty visual analysis.")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException && attempt < maxAttempts) {
                    delay(computeBackoffMs(attempt))
                    attempt++
                    continue
                }
                throw e
            } catch (e: Exception) {
                if (classifyRetryableError(e) != null && attempt < maxAttempts) {
                    delay(computeBackoffMs(attempt))
                    attempt++
                    continue
                }
                throw e
            }
        }
    }

    suspend fun verifyGeminiLiveLink(
        mode: JarvisCoreMode = JarvisCoreMode.STANDARD
    ): Pair<String, Long> {
        val start = System.currentTimeMillis()
        val response = withTimeout(mode.attemptTimeoutMs) {
            modelFor(mode).generateContent("Respond in one short sentence confirming your neural link is online.")
        }
        val elapsed = System.currentTimeMillis() - start
        val text = response.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("Gemini returned an empty diagnostic response.")
        return text to elapsed
    }

    suspend fun generateOrEditHologram(
        prompt: String,
        sourceImage: Bitmap?,
        aspectRatio: HologramAspectRatio,
        onStatusUpdate: (String) -> Unit = {}
    ): Bitmap? {
        val imageConfig = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(aspectRatio.sdkRatio)
                .build()
        }
        val imageModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = imageConfig,
            requestOptions = RequestOptions(timeoutInMillis = 60_000L)
        )

        val maxAttempts = 3
        var attempt = 1
        while (true) {
            try {
                onStatusUpdate(
                    if (attempt == 1) {
                        "Synthesizing hologram…"
                    } else {
                        "Retrying hologram synthesis (attempt $attempt/$maxAttempts)…"
                    }
                )
                val response = withTimeout(60_000L) {
                    if (sourceImage != null) {
                        imageModel.generateContent(
                            content {
                                image(sourceImage)
                                text(prompt)
                            }
                        )
                    } else {
                        imageModel.generateContent(prompt)
                    }
                }
                return response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()
                    ?.image
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException && attempt < maxAttempts) {
                    val backoffMs = computeBackoffMs(attempt)
                    onStatusUpdate("Synthesis timed out — retrying in ${backoffMs / 1000}s…")
                    delay(backoffMs)
                    attempt++
                    continue
                }
                throw e
            } catch (e: Exception) {
                val retryInfo = classifyRetryableError(e)
                if (retryInfo != null && attempt < maxAttempts) {
                    val backoffMs = computeBackoffMs(attempt)
                    onStatusUpdate("$retryInfo — retrying in ${backoffMs / 1000}s…")
                    delay(backoffMs)
                    attempt++
                    continue
                }
                throw e
            }
        }
    }

    fun formatReadableError(error: Throwable, mode: JarvisCoreMode): String {
        if (error is TimeoutCancellationException) {
            return "Connection timed out on ${mode.displayLabel} after retries. Tap Retry or switch to 3.5-LITE."
        }
        if (error is PromptBlockedException || error is ResponseStoppedException) {
            return "Request could not be completed due to safety constraints: ${error.localizedMessage ?: "Try rephrasing your prompt."}"
        }

        val combined = buildMessageChain(error).lowercase()
        return when {
            combined.contains("app check") ||
                combined.contains("attestation") ||
                combined.contains("403") ||
                combined.contains("permission_denied") ||
                combined.contains("unauthenticated") ||
                combined.contains("401") ->
                "Authentication error (App Check / HTTP 403): Firebase App Check attestation was rejected or no registered debug token is present. Launch via the Streaming Emulator or register this device's debug secret in Firebase App Check."

            error is QuotaExceededException ||
                combined.contains("429") ||
                combined.contains("resource_exhausted") ||
                combined.contains("rate limit") ->
                "Rate limit exceeded (HTTP 429) for ${mode.displayLabel}. Wait briefly and tap Retry, or switch to 3.5-LITE."

            error is ServerException ||
                combined.contains("503") ||
                combined.contains("unavailable") ||
                combined.contains("overloaded") ->
                "Gemini service temporarily unavailable (HTTP 503). Tap Retry in a moment."

            error is UnknownHostException ||
                error is ConnectException ||
                error is SocketTimeoutException ||
                error is IOException ||
                combined.contains("network") ||
                combined.contains("unable to resolve host") ->
                "Network connection error: Unable to reach Firebase AI Logic servers. Verify internet connectivity and tap Retry."

            else -> error.localizedMessage ?: "Neural link error while communicating with ${mode.displayLabel}."
        }
    }

    private fun computeBackoffMs(attempt: Int): Long {
        val baseDelayMs = 1_000L * (1L shl (attempt - 1)) // 1s, 2s, 4s
        val jitterMs = Random.nextLong(100L, 400L)
        return baseDelayMs + jitterMs
    }

    private fun classifyRetryableError(error: Throwable): String? {
        if (error is PromptBlockedException || error is ResponseStoppedException) return null

        val message = buildMessageChain(error).lowercase()
        // Never retry App Check / authentication rejections
        if (message.contains("app check") ||
            message.contains("403") ||
            message.contains("401") ||
            message.contains("unauthenticated") ||
            message.contains("permission_denied")
        ) {
            return null
        }

        return when {
            error is QuotaExceededException ||
                message.contains("429") ||
                message.contains("resource_exhausted") ||
                message.contains("rate limit") -> "Rate limited (429)"

            error is ServerException ||
                message.contains("503") ||
                message.contains("unavailable") ||
                message.contains("overloaded") -> "Service temporarily busy (503)"

            error is SocketTimeoutException ||
                error is IOException ||
                message.contains("timeout") -> "Network hiccup"

            error is FirebaseAIException && (message.contains("500") || message.contains("502")) ->
                "Transient backend error"

            else -> null
        }
    }

    private fun buildMessageChain(error: Throwable): String = buildString {
        var current: Throwable? = error
        while (current != null) {
            append(current::class.java.simpleName)
            append(": ")
            append(current.message.orEmpty())
            append(' ')
            current = current.cause
        }
    }
}
