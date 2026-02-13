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
        private const val KEY_VAPI_API_KEY = "vapi_api_key"
        private const val KEY_VAPI_SCREENING_AGENT_ID = "vapi_screening_agent_id"
        private const val KEY_VAPI_TAKEOVER_AGENT_ID = "vapi_takeover_agent_id"
        
        const val DEFAULT_WS_URL = "wss://detectscam.onrender.com"
        const val DEFAULT_AI_NUMBER = "+19793418014"
        const val DEFAULT_VAPI_API_KEY = "547befdc-a9de-4e5e-9e76-6b693b256228"
        const val DEFAULT_VAPI_SCREENING_AGENT_ID = "548d630b-c654-4532-98e3-d28918b736d5"
        const val DEFAULT_VAPI_TAKEOVER_AGENT_ID = "80ed987f-9d37-4bfe-aee1-87d6feb569a6"
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

    var vapiApiKey: String
        get() = prefs.getString(KEY_VAPI_API_KEY, DEFAULT_VAPI_API_KEY) ?: DEFAULT_VAPI_API_KEY
        set(value) = prefs.edit().putString(KEY_VAPI_API_KEY, value).apply()

    var vapiScreeningAgentId: String
        get() = prefs.getString(KEY_VAPI_SCREENING_AGENT_ID, DEFAULT_VAPI_SCREENING_AGENT_ID) ?: DEFAULT_VAPI_SCREENING_AGENT_ID
        set(value) = prefs.edit().putString(KEY_VAPI_SCREENING_AGENT_ID, value).apply()

    var vapiTakeoverAgentId: String
        get() = prefs.getString(KEY_VAPI_TAKEOVER_AGENT_ID, DEFAULT_VAPI_TAKEOVER_AGENT_ID) ?: DEFAULT_VAPI_TAKEOVER_AGENT_ID
        set(value) = prefs.edit().putString(KEY_VAPI_TAKEOVER_AGENT_ID, value).apply()
}
