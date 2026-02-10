package com.example.cybersmith.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.cybersmith.data.model.CallLogRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CallLogRecord>>

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC LIMIT 5")
    fun getRecentLogs(): Flow<List<CallLogRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(record: CallLogRecord): Long

    @Update
    suspend fun updateLog(record: CallLogRecord)

    @Query("SELECT COUNT(*) FROM call_logs")
    fun getTotalCallCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_logs WHERE status = 'FRAUD'")
    fun getThreatCount(): Flow<Int>

    @Query("SELECT * FROM call_logs WHERE id = :id")
    suspend fun getLogById(id: Int): CallLogRecord?
}
