package com.example.cybersmith.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phoneNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String, // INCOMING, OUTGOING
    val status: String,    // SAFE, FRAUD, UNKNOWN
    val callerName: String? = null,
    val confidence: Float = 0f,
    val duration: Long = 0,
    val reason: String? = null
)
