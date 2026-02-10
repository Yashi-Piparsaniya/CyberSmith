package com.example.cybersmith.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybersmith.data.db.CallLogDatabase
import com.example.cybersmith.data.model.CallLogRecord
import com.example.cybersmith.data.repository.CallLogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LogsViewModel(application: Application) : AndroidViewModel(application) {
    private val callLogDao = CallLogDatabase.getDatabase(application).callLogDao()
    private val repository = CallLogRepository(application, callLogDao)

    val allLogs: StateFlow<List<CallLogRecord>> = repository.getMergedLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
