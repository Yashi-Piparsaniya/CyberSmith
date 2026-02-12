package com.example.cybersmith

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.cybersmith.data.SettingsManager
import com.example.cybersmith.data.db.CallLogDatabase
import com.example.cybersmith.data.model.CallLogRecord
import com.example.cybersmith.model.FraudDetectionResult
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import okio.ByteString.Companion.toByteString
import java.util.Locale

class CallDetectionService : Service(), TextToSpeech.OnInitListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private var webSocket: WebSocket? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    // Persistence
    private lateinit var settingsManager: SettingsManager
    private lateinit var callLogDao: com.example.cybersmith.data.db.CallLogDao
    private var currentCallLogId: Int = -1
    private var currentPhoneNumber: String = "Unknown"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        // Initialize persistence
        settingsManager = SettingsManager(this)
        callLogDao = CallLogDatabase.getDatabase(this).callLogDao()
        
        // Initialize TTS
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
            Log.d(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown"
        
        if (action == ACTION_START_RECORDING) {
            currentPhoneNumber = phoneNumber
            startRecordingAndStreaming()
        } else if (action == ACTION_STOP_RECORDING) {
            stopRecordingAndStreaming()
            stopSelf()
        }
        return START_STICKY
    }

    private fun startRecordingAndStreaming() {
        if (isRecording) return
        isRecording = true
        
        // Log start of call in database
        serviceScope.launch {
            val record = CallLogRecord(
                phoneNumber = currentPhoneNumber,
                direction = "INCOMING",
                status = "UNKNOWN"
            )
            val id = callLogDao.insertLog(record)
            currentCallLogId = id.toInt()
            Log.d(TAG, "Logged call start in DB with ID: $currentCallLogId")
        }
        
        connectWebSocket()
        
        serviceScope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed")
                    return@launch
                }

                Log.d(TAG, "AudioRecord started. Buffer size: $bufferSize")
                val buffer = ByteArray(bufferSize)
                audioRecord?.startRecording()
                
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        // Vapi supports binary audio streaming directly.
                        if (webSocket != null) {
                            webSocket?.send(buffer.toByteString(0, read))
                            // Log periodically (every ~1 second of audio at 16khz/16bit/mono)
                            // 16000 * 2 = 32000 bytes per sec. 
                        }
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord read error: $read")
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied for recording: ${e.message}")
            } finally {
                stopRecordingInternal()
            }
        }
    }

    private fun connectWebSocket() {
        val request = Request.Builder()
            .url(settingsManager.webSocketUrl)
            .addHeader("Authorization", "Bearer ${settingsManager.vapiApiKey}")
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Opened to Vapi. Response: ${response.message}")
                updateStatusNotification("Vapi Connected")
                
                // Start session with Setup message (Client SDK style)
                // We add explicit audio config to ensure Vapi knows the format
                val setupMessage = """
                    {
                        "type": "setup",
                        "assistantId": "${settingsManager.vapiScreeningAgentId}",
                        "publicKey": "${settingsManager.vapiApiKey}",
                        "inputFormat": {
                            "sampleRate": 16000,
                            "encoding": "linear16",
                            "container": "raw",
                            "channels": 1
                        }
                    }
                """.trimIndent()
                Log.d(TAG, "Sending Setup: $setupMessage")
                webSocket.send(setupMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "VAPI MSG: $text") // Log every message for debugging
                handleVapiMessage(text)
            }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket Failure: ${t.message}", t)
            response?.let {
                Log.e(TAG, "Failure Response: ${it.code} ${it.message}")
                try {
                    Log.e(TAG, "Failure Body: ${it.body?.string()}")
                } catch (e: Exception) {}
            }
            updateStatusNotification("Vapi Connection Failed")
        }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket Closing: $code / $reason")
                updateStatusNotification("Vapi Disconnecting")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket Closed: $code / $reason")
                updateStatusNotification("Vapi Disconnected")
            }
        })
    }
    
    private fun handleVapiMessage(text: String) {
        try {
            val messageJson = json.parseToJsonElement(text)
            val type = messageJson.jsonObject["type"]?.jsonPrimitive?.content
            
            if (type == "transcript") {
                val transcriptObj = messageJson.jsonObject["transcript"]?.jsonObject
                val transcript = if (transcriptObj != null) {
                    transcriptObj["transcript"]?.jsonPrimitive?.content ?: ""
                } else {
                    messageJson.jsonObject["transcript"]?.jsonPrimitive?.content ?: ""
                }
                
                if (transcript.isNotEmpty()) {
                    Log.d(TAG, "Captured Transcript: $transcript")
                    
                    // Simple keyword detection for fraud alerts in transcript
                    // Using case-insensitive check and also searching for common scam keywords
                    val scamKeywords = listOf("Warning", "चेतावनी", "OTAC", "OTP", "Bank", "Police", "Account", "Verify")
                    if (scamKeywords.any { transcript.contains(it, ignoreCase = true) }) {
                        Log.w(TAG, "Scam keyword detected in transcript!")
                        updateLogAndTriggerAlert(FraudDetectionResult(
                            isFraud = true, 
                            confidence = 0.9f, 
                            reason = "Scam pattern detected: $transcript"
                        ))
                    }
                }
            } else if (type == "speech-update") {
                // Speech updates can tell us if Vapi is hearing something
                Log.d(TAG, "Vapi Speech Update: $text")
            } else if (type == "error") {
                Log.e(TAG, "Vapi Reported Error: $text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Vapi message: ${e.message}")
        }
        
        // Also check for error messages from Vapi
        try {
            val messageJson = json.parseToJsonElement(text)
            val type = messageJson.jsonObject["type"]?.jsonPrimitive?.content
            if (type == "error") {
                val error = messageJson.jsonObject["error"]?.jsonPrimitive?.content ?: "Unknown error"
                Log.e(TAG, "Vapi Error: $error")
            }
        } catch (e: Exception) {}
    }

    private fun updateStatusNotification(status: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(status))
    }

    private fun updateLogStatus(status: String) {
        serviceScope.launch {
            if (currentCallLogId != -1) {
                val currentRecord = callLogDao.getLogById(currentCallLogId)
                currentRecord?.let {
                    callLogDao.updateLog(it.copy(status = status))
                    Log.d(TAG, "Updated log record to $status")
                }
            }
        }
    }

    private fun updateLogAndTriggerAlert(result: FraudDetectionResult) {
        // Update database
        serviceScope.launch {
            if (currentCallLogId != -1) {
                val currentRecord = callLogDao.getLogById(currentCallLogId)
                currentRecord?.let {
                    val updated = it.copy(
                        status = "FRAUD",
                        confidence = result.confidence,
                        reason = result.reason
                    )
                    callLogDao.updateLog(updated)
                    Log.d(TAG, "Updated log record to FRAUD")
                }
            }
        }
        
        triggerFraudAlert(result)
    }

    private fun triggerFraudAlert(result: FraudDetectionResult) {
        // 1. Vibrate
        triggerVibration()
        
        // 2. Voice alert
        speakFraudWarning(result)
        
        // 3. Show actionable notification
        showFraudNotification(result)
    }

    private fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Pattern: wait 0ms, vibrate 500ms, wait 200ms, vibrate 500ms, wait 200ms, vibrate 500ms
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun speakFraudWarning(result: FraudDetectionResult) {
        if (isTtsReady) {
            val message = buildString {
                append("Warning! Potential fraud call detected. ")
                if (result.confidence > 0.8f) {
                    append("High confidence. ")
                }
                result.reason?.let { append(it) }
                append(" Consider ending this call or handing over to AI assistant.")
            }
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "fraud_alert")
        }
    }

    private fun showFraudNotification(result: FraudDetectionResult) {
        // Intent for handover action
        val handoverIntent = Intent(this, CallHandoverReceiver::class.java).apply {
            action = CallHandoverReceiver.ACTION_HANDOVER_TO_AI
            putExtra(CallHandoverReceiver.EXTRA_AI_NUMBER, settingsManager.aiNumber)
        }
        val handoverPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            handoverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to open app
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("EXTRA_FRAUD_ALERT", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confidencePercent = (result.confidence * 100).toInt()
        
        val notification = NotificationCompat.Builder(this, FRAUD_CHANNEL_ID)
            .setContentTitle("⚠️ Fraud Call Detected!")
            .setContentText("Confidence: $confidencePercent%. Tap for options.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("This call appears to be fraudulent (${confidencePercent}% confidence).\n${result.reason ?: "Exercise caution and consider ending the call."}"))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_call,
                "🤖 Handover to AI",
                handoverPendingIntent
            )
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(FRAUD_NOTIFICATION_ID, notification)
    }

    private fun stopRecordingAndStreaming() {
        isRecording = false
    }

    private fun stopRecordingInternal() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        webSocket?.close(1000, "Recording stopped")
        webSocket = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecordingAndStreaming()
        serviceScope.cancel()
        
        // Cleanup TTS
        tts?.stop()
        tts?.shutdown()
        tts = null
        
        // Clear fraud notification
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(FRAUD_NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Service channel
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Call Detection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for call monitoring"
            }
            
            // Fraud alert channel (high priority)
            val fraudChannel = NotificationChannel(
                FRAUD_CHANNEL_ID,
                "Fraud Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alerts for detected fraud calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(fraudChannel)
        }
    }

    private fun createNotification(status: String = "Monitoring for fraud..."): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CyberSmith Call Protection")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "CallDetectionService"
        private const val CHANNEL_ID = "CallDetectionChannel"
        private const val FRAUD_CHANNEL_ID = "FraudAlertChannel"
        private const val NOTIFICATION_ID = 1
        private const val FRAUD_NOTIFICATION_ID = 2
        private const val SAMPLE_RATE = 16000
        
        const val ACTION_START_RECORDING = "com.example.cybersmith.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.cybersmith.action.STOP_RECORDING"
        const val EXTRA_PHONE_NUMBER = "com.example.cybersmith.extra.PHONE_NUMBER"
    }
}
