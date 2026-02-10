package com.example.cybersmith.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybersmith.data.SettingsManager
import com.example.cybersmith.data.db.CallLogDatabase
import com.example.cybersmith.data.model.CallLogRecord
import com.example.cybersmith.data.repository.CallLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val callLogDao = CallLogDatabase.getDatabase(application).callLogDao()
    private val settingsManager = SettingsManager(application)
    private val repository = CallLogRepository(application, callLogDao)

    val recentActivity: StateFlow<List<CallLogRecord>> = repository.getRecentMergedLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCalls: StateFlow<Int> = callLogDao.getTotalCallCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val threatsBlocked: StateFlow<Int> = callLogDao.getThreatCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isProtectionEnabled: Boolean
        get() = settingsManager.isProtectionEnabled

    fun toggleProtection(enabled: Boolean) {
        settingsManager.isProtectionEnabled = enabled
    }
}
