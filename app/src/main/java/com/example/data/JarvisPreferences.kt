package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai.JarvisCoreMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore by preferencesDataStore(name = "jarvis_hud_prefs")

data class JarvisSettings(
    val autoSpeak: Boolean = true,
    val autoSendVoice: Boolean = true,
    val speechRate: Float = 1.05f,
    val speechPitch: Float = 0.94f,
    val coreMode: JarvisCoreMode = JarvisCoreMode.STANDARD
)

class JarvisPreferences(private val context: Context) {

    private object Keys {
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val AUTO_SEND_VOICE = booleanPreferencesKey("auto_send_voice")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val CORE_MODE = stringPreferencesKey("core_mode")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        val modeName = prefs[Keys.CORE_MODE] ?: JarvisCoreMode.STANDARD.name
        val mode = runCatching { JarvisCoreMode.valueOf(modeName) }
            .getOrDefault(JarvisCoreMode.STANDARD)
        JarvisSettings(
            autoSpeak = prefs[Keys.AUTO_SPEAK] ?: true,
            autoSendVoice = prefs[Keys.AUTO_SEND_VOICE] ?: true,
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.05f,
            speechPitch = prefs[Keys.SPEECH_PITCH] ?: 0.94f,
            coreMode = mode
        )
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setAutoSendVoice(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.AUTO_SEND_VOICE] = enabled }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.jarvisDataStore.edit { it[Keys.SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setSpeechPitch(pitch: Float) {
        context.jarvisDataStore.edit { it[Keys.SPEECH_PITCH] = pitch.coerceIn(0.6f, 1.4f) }
    }

    suspend fun setCoreMode(mode: JarvisCoreMode) {
        context.jarvisDataStore.edit { it[Keys.CORE_MODE] = mode.name }
    }
}
