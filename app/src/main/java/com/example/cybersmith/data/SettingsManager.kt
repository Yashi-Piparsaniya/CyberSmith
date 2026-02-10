package com.example.cybersmith.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cybersmith_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WS_URL = "ws_url"
        private const val KEY_AI_NUMBER = "ai_number"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_VOICE_ALERTS = "voice_alerts"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        
        const val DEFAULT_WS_URL = "ws://your-backend.com/ws/audio"
        const val DEFAULT_AI_NUMBER = "+1234567890"
    }

    var webSocketUrl: String
        get() = prefs.getString(KEY_WS_URL, DEFAULT_WS_URL) ?: DEFAULT_WS_URL
        set(value) = prefs.edit().putString(KEY_WS_URL, value).apply()

    var aiNumber: String
        get() = prefs.getString(KEY_AI_NUMBER, DEFAULT_AI_NUMBER) ?: DEFAULT_AI_NUMBER
        set(value) = prefs.edit().putString(KEY_AI_NUMBER, value).apply()

    var isProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, value).apply()

    var voiceAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ALERTS, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ALERTS, value).apply()

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()
}
