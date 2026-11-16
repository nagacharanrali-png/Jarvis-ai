package com.example.security

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

private const val TAG = "AppCheckInit"

data class AppCheckStatus(
    val isInitialized: Boolean = false,
    val providerLabel: String = "Initializing…",
    val hasLaunchDebugToken: Boolean = false,
    val errorSummary: String? = null
)

object AppCheckRegistry {
    private val _status = MutableStateFlow(AppCheckStatus())
    val status: StateFlow<AppCheckStatus> = _status.asStateFlow()

    internal fun updateStatus(status: AppCheckStatus) {
        _status.value = status
    }

    suspend fun verifyAttestation(): Result<String> {
        return try {
            withTimeout(15_000L) {
                suspendCancellableCoroutine { continuation ->
                    FirebaseAppCheck.getInstance()
                        .getAppCheckToken(false)
                        .addOnSuccessListener { tokenResult ->
                            if (continuation.isActive) {
                                val valid = tokenResult.token.isNotBlank()
                                if (valid) {
                                    continuation.resume(
                                        Result.success("App Check attestation verified.")
                                    )
                                } else {
                                    continuation.resume(
                                        Result.failure(
                                            IllegalStateException("App Check returned an empty token.")
                                        )
                                    )
                                }
                            }
                        }
                        .addOnFailureListener { exception ->
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(exception))
                            }
                        }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val status = installVariantAppCheckProvider(context, intent)
        AppCheckRegistry.updateStatus(status)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        AppCheckRegistry.updateStatus(
            AppCheckStatus(
                isInitialized = false,
                providerLabel = "Initialization Failed",
                hasLaunchDebugToken = false,
                errorSummary = e.localizedMessage ?: "App Check initialization error."
            )
        )
    }
}
