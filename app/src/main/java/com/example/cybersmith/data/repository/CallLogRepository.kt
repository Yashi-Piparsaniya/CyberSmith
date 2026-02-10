package com.example.cybersmith.data.repository

import android.content.Context
import android.provider.CallLog
import com.example.cybersmith.data.db.CallLogDao
import com.example.cybersmith.data.model.CallLogRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

class CallLogRepository(
    private val context: Context,
    private val callLogDao: CallLogDao
) {
    /**
     * Fetches and merges logs from both the internal Room DB and the system's CallLog provider.
     */
    fun getMergedLogs(): Flow<List<CallLogRecord>> = combine(
        callLogDao.getAllLogs(),
        getSystemCallLogsFlow()
    ) { roomLogs, systemLogs ->
        mergeAndDeduplicate(roomLogs, systemLogs)
    }

    /**
     * Fetches and merges recent logs for the dashboard.
     */
    fun getRecentMergedLogs(limit: Int = 5): Flow<List<CallLogRecord>> = combine(
        callLogDao.getRecentLogs(),
        getSystemCallLogsFlow()
    ) { roomLogs, systemLogs ->
        mergeAndDeduplicate(roomLogs, systemLogs).take(limit)
    }

    private fun getSystemCallLogsFlow(): Flow<List<CallLogRecord>> = flow {
        emit(fetchSystemCallLogs())
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchSystemCallLogs(): List<CallLogRecord> = withContext(Dispatchers.IO) {
        val logs = mutableListOf<CallLogRecord>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_NAME
        )

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                while (it.moveToNext()) {
                    val number = it.getString(numberIndex)
                    val timestamp = it.getLong(dateIndex)
                    val type = it.getInt(typeIndex)
                    val duration = it.getLong(durationIndex)
                    val name = it.getString(nameIndex)

                    val direction = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        else -> "UNKNOWN"
                    }

                    logs.add(
                        CallLogRecord(
                            phoneNumber = number,
                            timestamp = timestamp,
                            direction = direction,
                            status = "UNKNOWN",
                            callerName = name,
                            duration = duration
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        logs
    }

    private fun mergeAndDeduplicate(
        roomLogs: List<CallLogRecord>,
        systemLogs: List<CallLogRecord>
    ): List<CallLogRecord> {
        val finalLogs = mutableListOf<CallLogRecord>()
        val matchedRoomIds = mutableSetOf<Int>()

        // System logs are our primary list (more complete history)
        for (sysLog in systemLogs) {
            val matchingRoomLog = roomLogs.find { roomLog ->
                val numMatch = normalizePhoneNumber(roomLog.phoneNumber) == normalizePhoneNumber(sysLog.phoneNumber)
                val timeMatch = Math.abs(roomLog.timestamp - sysLog.timestamp) < 30000 // 30s window
                numMatch && timeMatch
            }

            if (matchingRoomLog != null) {
                // Use Room record because it contains our SAFE/FRAUD metadata
                // But keep system's duration/timestamp/name if they are more accurate
                finalLogs.add(matchingRoomLog.copy(
                    duration = if (matchingRoomLog.duration == 0L) sysLog.duration else matchingRoomLog.duration,
                    callerName = matchingRoomLog.callerName ?: sysLog.callerName
                ))
                matchedRoomIds.add(matchingRoomLog.id)
            } else {
                finalLogs.add(sysLog)
            }
        }

        // Add any Room logs that didn't match anything in system log (safety fallback)
        roomLogs.filter { it.id !in matchedRoomIds }.forEach {
            finalLogs.add(it)
        }

        return finalLogs.sortedByDescending { it.timestamp }
    }

    private fun normalizePhoneNumber(number: String?): String {
        if (number == null) return ""
        // Remove all non-digit characters and take the last 10 digits
        val digits = number.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }
}
