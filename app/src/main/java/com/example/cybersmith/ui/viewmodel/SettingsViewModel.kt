package com.example.cybersmith.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.cybersmith.data.SettingsManager

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)

    var webSocketUrl by mutableStateOf(settingsManager.webSocketUrl)
    var aiNumber by mutableStateOf(settingsManager.aiNumber)
    var voiceAlertsEnabled by mutableStateOf(settingsManager.voiceAlertsEnabled)
    var hapticFeedbackEnabled by mutableStateOf(settingsManager.hapticFeedbackEnabled)

    fun updateWebSocketUrl(url: String) {
        webSocketUrl = url
        settingsManager.webSocketUrl = url
    }

    fun updateAiNumber(number: String) {
        aiNumber = number
        settingsManager.aiNumber = number
    }

    fun toggleVoiceAlerts(enabled: Boolean) {
        voiceAlertsEnabled = enabled
        settingsManager.voiceAlertsEnabled = enabled
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        hapticFeedbackEnabled = enabled
        settingsManager.hapticFeedbackEnabled = enabled
    }
}
