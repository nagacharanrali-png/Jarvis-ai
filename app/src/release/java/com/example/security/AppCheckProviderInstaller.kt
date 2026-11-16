package com.example.security

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

private const val TAG = "AppCheckInit"

internal fun installVariantAppCheckProvider(context: Context, intent: Intent?): AppCheckStatus {
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        PlayIntegrityAppCheckProviderFactory.getInstance()
    )
    Log.d(TAG, "PlayIntegrityAppCheckProviderFactory installed for release build.")

    return AppCheckStatus(
        isInitialized = true,
        providerLabel = "Play Integrity",
        hasLaunchDebugToken = false,
        errorSummary = null
    )
}
