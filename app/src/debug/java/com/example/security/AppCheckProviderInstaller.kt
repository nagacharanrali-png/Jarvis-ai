package com.example.security

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

internal fun installVariantAppCheckProvider(context: Context, intent: Intent?): AppCheckStatus {
    val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
    val hasIntentToken = !debugToken.isNullOrEmpty()

    if (hasIntentToken) {
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        // Synchronous commit() ensures the secret is written before DebugAppCheckProviderFactory reads it.
        prefs.edit()
            .putString(DEBUG_SECRET_KEY, debugToken)
            .commit()
        Log.d(TAG, "App Check debug secret configured from launch intent.")
    } else {
        Log.w(TAG, "No debug token in launch intent; using DebugAppCheckProviderFactory store.")
    }

    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance()
    )
    Log.d(TAG, "DebugAppCheckProviderFactory installed for debug build.")

    return AppCheckStatus(
        isInitialized = true,
        providerLabel = if (hasIntentToken) {
            "Debug Provider (Intent Token)"
        } else {
            "Debug Provider (Local Secret)"
        },
        hasLaunchDebugToken = hasIntentToken,
        errorSummary = null
    )
}
