package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.ai.HologramAspectRatio
import com.example.ai.JarvisAiRepository
import com.example.ai.JarvisCoreMode
import com.example.security.AppCheckRegistry
import com.example.security.configureAppCheck
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JarvisConfigVerificationTest {

    @Test
    fun `verify firebase app and debug app check initialization without network calls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
        assertNotNull("FirebaseApp must initialize from google-services.json", app)
        assertEquals("gen-lang-client-0837830373", app!!.options.projectId)
        assertEquals("1:682470043866:android:a70db06e3468b4d8fb75e3", app.options.applicationId)
        assertEquals("com.aistudio.jarvis.k7x2mq", context.packageName)

        val syntheticTestToken = "local-unit-test-token"
        val launchIntent = Intent().apply {
            putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", syntheticTestToken)
        }
        configureAppCheck(context, launchIntent)

        val prefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.${app.persistenceKey}",
            Context.MODE_PRIVATE
        )
        assertEquals(
            syntheticTestToken,
            prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
        )
        assertNotNull(FirebaseAppCheck.getInstance())
        assertTrue(AppCheckRegistry.status.value.isInitialized)
        assertTrue(AppCheckRegistry.status.value.hasLaunchDebugToken)
    }

    @Test
    fun `verify manifest permissions, model ids, and readable error formatting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = packageInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue("INTERNET permission required", perms.contains(android.Manifest.permission.INTERNET))
        assertTrue("RECORD_AUDIO permission required", perms.contains(android.Manifest.permission.RECORD_AUDIO))

        assertEquals("gemini-3.8-flash", JarvisCoreMode.STANDARD.modelName)
        assertEquals("gemini-3.5-flash-lite", JarvisCoreMode.RAPID.modelName)
        assertEquals("gemini-4-argon", JarvisCoreMode.DEEP_THINK.modelName)
        assertTrue(HologramAspectRatio.entries.isNotEmpty())

        val repository = JarvisAiRepository()
        val authMsg = repository.formatReadableError(
            IllegalStateException("Firebase App Check token is invalid (403)"),
            JarvisCoreMode.STANDARD
        )
        assertTrue(authMsg.contains("App Check"))
        assertTrue(authMsg.contains("403"))

        val networkMsg = repository.formatReadableError(
            UnknownHostException("Unable to resolve host"),
            JarvisCoreMode.STANDARD
        )
        assertTrue(networkMsg.contains("Network connection error"))
    }
}
