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
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    
    private val client = OkHttpClient.Builder()
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS) // Disable read timeout for WebSockets
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS) // Keep connection alive
        .retryOnConnectionFailure(true)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    
    // Persistence
    private lateinit var settingsManager: SettingsManager
    private lateinit var callLogDao: com.example.cybersmith.data.db.CallLogDao
    private var currentCallLogId: Int = -1
    private var currentPhoneNumber: String = "Unknown"
    private var fraudPopupShown = false

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
            // Update phone number if it's currently Unknown and we just got a real one
            if (phoneNumber != "Unknown" && (currentPhoneNumber == "Unknown" || currentPhoneNumber.isEmpty())) {
                currentPhoneNumber = phoneNumber
                updateLogWithPhoneNumber(phoneNumber)
            } else if (currentPhoneNumber == "Unknown" || currentPhoneNumber.isEmpty()) {
                currentPhoneNumber = phoneNumber
            }
            
            // Reset fraud popup flag for new call
            fraudPopupShown = false
            
            // Initialize TTS only when we actually start recording
            if (tts == null) {
                tts = TextToSpeech(this, this)
            }
            
            startRecordingAndStreaming()
        } else if (action == ACTION_STOP_RECORDING) {
            stopRecordingAndStreaming()
            stopSelf()
        }
        return START_STICKY
    }

    private fun startRecordingAndStreaming() {
        if (isRecording) {
            Log.d(TAG, "Already recording, ignoring start request")
            return
        }
        isRecording = true
        Log.d(TAG, "--- Starting Recording and Streaming ---")

        // Acquire WakeLock
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "CyberSmith::CallDetectionWakeLock")
            wakeLock?.acquire(10*60*1000L /*10 minutes*/)
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock: ${e.message}")
        }
        
        // Log start of call in database ONLY if we don't have an active log ID
        if (currentCallLogId == -1) {
            serviceScope.launch {
                try {
                    val record = CallLogRecord(
                        phoneNumber = currentPhoneNumber,
                        direction = "INCOMING",
                        status = "UNKNOWN"
                    )
                    val id = callLogDao.insertLog(record)
                    currentCallLogId = id.toInt()
                    Log.d(TAG, "Logged call start in DB with ID: $currentCallLogId for $currentPhoneNumber")
                } catch (e: Exception) {
                    Log.e(TAG, "Error logging call start: ${e.message}")
                }
            }
        }
        
        connectWebSocket()
        
        serviceScope.launch {
            Log.d(TAG, "Recording coroutine launched")
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                Log.d(TAG, "Min buffer size: $minBufferSize")
                if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Invalid min buffer size: $minBufferSize")
                    return@launch
                }
                
                // Use a larger buffer for stability
                val bufferSize = minBufferSize * 2
                Log.d(TAG, "Using buffer size: $bufferSize")

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Changed to VOICE_COMMUNICATION (7) for better echo cancellation and in-call access
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed. State: ${audioRecord?.state}")
                    return@launch
                }
                
                // Initialize Whisper Engine
                val whisperEngine = com.example.cybersmith.ml.WhisperEngine(this@CallDetectionService)
                val whisperReady = whisperEngine.initialize()
                if (!whisperReady) {
                    Log.w(TAG, "Whisper Engine failed to initialize. Falling back to Vapi/Cloud streaming or no-op.")
                    updateStatusNotification("Local AI Model Missing (Check Assets)")
                } else {
                    updateStatusNotification("Local AI Listening...")
                }

                Log.d(TAG, "AudioRecord initialized. Starting recording...")
                audioRecord?.startRecording()
                
                if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "AudioRecord failed to start recording. State: ${audioRecord?.recordingState}")
                    return@launch
                }
                
                Log.d(TAG, "AudioRecord is now recording. Entering read loop...")
                val buffer = ByteArray(bufferSize)
                var totalBytesSent = 0L
                var lastLogTime = System.currentTimeMillis()
                
                // Accumulation buffer for Whisper (approx 4 seconds of audio at 16kHz)
                // 4 * 16000 = 64000 samples
                val WHISPER_WINDOW_SECONDS = 4
                val WHISPER_SAMPLE_COUNT = WHISPER_WINDOW_SECONDS * SAMPLE_RATE
                val accumulationBuffer = FloatArray(WHISPER_SAMPLE_COUNT)
                var accumulationIndex = 0
                
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        // Calculate RMS to check for silence
                        var sum = 0.0
                        for (i in 0 until read step 2) {
                            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                            val normalized = sample.toShort() / 32768.0
                            sum += normalized * normalized
                        }
                        val rms = Math.sqrt(sum / (read / 2))
                        val db = if (rms > 0) 20 * Math.log10(rms) else -100.0
                        
                        if (webSocket != null && whisperReady) {
                             // Convert current chunk to Float and append to accumulation buffer
                             val samplesRead = read / 2
                             for (i in 0 until samplesRead) {
                                 if (accumulationIndex < WHISPER_SAMPLE_COUNT) {
                                     val sample = (buffer[i*2].toInt() and 0xFF) or (buffer[i*2 + 1].toInt() shl 8)
                                     accumulationBuffer[accumulationIndex++] = sample.toShort() / 32768.0f
                                 }
                             }
                             
                             // If buffer is full, run inference triggers
                             if (accumulationIndex >= WHISPER_SAMPLE_COUNT) {
                                 Log.d(TAG, "Whisper Buffer Full ($accumulationIndex samples). Running Inference...")
                                 
                                 // Run Inference on the packet
                                 val text = whisperEngine.transcribe(accumulationBuffer)
                                 
                                 if (text.isNotEmpty() && !text.contains("not fully implemented")) {
                                     val jsonMessage = """
                                         {
                                             "type": "transcript_update",
                                             "text": "$text"
                                         }
                                     """.trimIndent()
                                     webSocket?.send(jsonMessage)
                                     Log.d(TAG, "Sent Local Transcript Packet: $text")
                                 }
                                 
                                 // Reset buffer (Simple non-overlapping window for now)
                                 accumulationIndex = 0
                                 // Optional: Arrays.fill(accumulationBuffer, 0f) - not strictly needed as we overwrite
                             }
                        }

                        // Log every 2 seconds
                        val now = System.currentTimeMillis()
                        if (now - lastLogTime > 2000) {
                             Log.d(TAG, "Audio stats: RMS: %.2f (%.1f dB). Read: $read. Buffer: $accumulationIndex/$WHISPER_SAMPLE_COUNT".format(rms, db))
                             lastLogTime = now
                        }

                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord read error code: $read")
                        break
                    }
                }
                whisperEngine.close()
                Log.d(TAG, "Exited recording loop.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in recording coroutine: ${e.message}", e)
            } finally {
                Log.d(TAG, "Cleaning up recording internal...")
                stopRecordingInternal()
            }
        }
    }

    private fun connectWebSocket() {
        // Vapi Client SDK connects with api_key in query param
        val url = "${settingsManager.webSocketUrl}?api_key=${settingsManager.vapiApiKey}"
        
        val request = Request.Builder()
            .url(url)
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
                Log.d(TAG, "VAPI MSG: $text") // Changed to Debug level to ensure we see it
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
            
            if (isRecording) {
                Log.w(TAG, "Connection lost while recording. Reconnecting in 3s...")
                serviceScope.launch {
                    delay(3000)
                    if (isRecording) {
                        Log.d(TAG, "Reconnecting WebSocket now...")
                        connectWebSocket()
                    }
                }
            }
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
            
            Log.i(TAG, "Received Vapi Message Type: $type, Content: $text")
            
            if (type == "transcript" || type == "live-caption" || type == "LIVE_CAPTION") {
                // Handle various transcript formats
                val transcript = when {
                    messageJson.jsonObject.containsKey("transcript") -> {
                         val tObj = messageJson.jsonObject["transcript"]
                         if (tObj is kotlinx.serialization.json.JsonObject) {
                             tObj["transcript"]?.jsonPrimitive?.content ?: ""
                         } else {
                             tObj?.jsonPrimitive?.content ?: ""
                         }
                    }
                    messageJson.jsonObject.containsKey("text") -> {
                        messageJson.jsonObject["text"]?.jsonPrimitive?.content ?: ""
                    }
                    else -> ""
                }
                
                if (transcript.isNotEmpty()) {
                    Log.d(TAG, "Captured Audio Text: $transcript")
                    updateStatusNotification("Heard: \"$transcript\"")
                    
                    // Simple keyword detection for fraud alerts in transcript
                    // Using case-insensitive check and also searching for common scam keywords
                    // Added more keywords based on common scams
                    val scamKeywords = listOf(
                        "Warning", "Alert", "Fraud", "Scam",
                        "chetaavani", "dhokha", // Hindi transliteration
                        "OTAC", "OTP", "Pin", "Password", "CVV", 
                        "Bank", "Police", "CBI", "RBI", "Customs", "Narcotics",
                        "Account", "Verify", "Block", "Expiry", "Upgrade",
                        "Hello", "Test" // For debugging purposes
                    )
                    
                    if (scamKeywords.any { transcript.contains(it, ignoreCase = true) }) {
                        val detectedWord = scamKeywords.first { transcript.contains(it, ignoreCase = true) }
                        Log.w(TAG, "Scam keyword detected locally: $detectedWord")
                        
                        updateLogAndTriggerAlert(FraudDetectionResult(
                            isFraud = true, 
                            confidence = 0.85f, // Slightly lower confidence for local keyword match
                            reason = "Suspicious keyword detected: $detectedWord"
                        ))
                    }
                    
                    // Broadcast transcript to UI
                    val intent = Intent(ACTION_TRANSCRIPT_UPDATE).apply {
                        putExtra(EXTRA_TRANSCRIPT, transcript)
                    }
                    sendBroadcast(intent)
                }
            } else if (type == "FRAUD_ALERT") {
                val keywords = messageJson.jsonObject["keywords"]?.let {
                    if (it is kotlinx.serialization.json.JsonArray) {
                        it.map { k -> k.jsonPrimitive.content }.joinToString(", ")
                    } else {
                        it.jsonPrimitive.content
                    }
                } ?: "Unknown"
                val severity = messageJson.jsonObject["severity"]?.jsonPrimitive?.content ?: "UNKNOWN"
                val transcript = messageJson.jsonObject["transcript"]?.jsonPrimitive?.content ?: ""
                
                Log.w(TAG, "FRAUD_ALERT received: Severity=$severity, Keywords=$keywords")
                
                updateLogAndTriggerAlert(FraudDetectionResult(
                    isFraud = true,
                    confidence = if (severity == "HIGH") 0.95f else 0.8f,
                    reason = "Server Alert ($severity): Detected $keywords in '$transcript'"
                ))
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

    private fun updateLogWithPhoneNumber(number: String) {
        serviceScope.launch {
            if (currentCallLogId != -1) {
                val currentRecord = callLogDao.getLogById(currentCallLogId)
                currentRecord?.let {
                    callLogDao.updateLog(it.copy(phoneNumber = number))
                    Log.d(TAG, "Updated log record with real phone number: $number")
                }
            }
        }
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
        // 0. Broadcast for active UI components
        val intent = Intent(ACTION_FRAUD_DETECTED).apply {
            putExtra(EXTRA_FRAUD_REASON, result.reason)
            putExtra(EXTRA_FRAUD_CONFIDENCE, result.confidence)
        }
        sendBroadcast(intent)

        // 1. Vibrate
        triggerVibration()
        
        // 2. Voice alert
        speakFraudWarning(result)
        
        // 3. Show actionable notification
        showFraudNotification(result)
        
        // 4. Show Overlay Popup (Instant & High Priority)
        // Deduplicate: Only show if not already shown for this call
        if (!fraudPopupShown) {
            fraudPopupShown = true
            showFraudPopup(result)
        }
    }
    
    private fun showFraudPopup(result: FraudDetectionResult) {
        try {
            val intent = Intent(this, FraudAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Add these flags to ensure it shows over lockscreen/other apps
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                
                putExtra(EXTRA_FRAUD_REASON, result.reason)
                putExtra(EXTRA_FRAUD_CONFIDENCE, result.confidence)
            }
            startActivity(intent)
            Log.d(TAG, "Launched FraudAlertActivity overlay")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch FraudAlertActivity: ${e.message}")
        }
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
             val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), audioAttributes)
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
        
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock: ${e.message}")
        }
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(status)) // Allow longer text for transcripts
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
        const val ACTION_FRAUD_DETECTED = "com.example.cybersmith.action.FRAUD_DETECTED"
        const val ACTION_TRANSCRIPT_UPDATE = "com.example.cybersmith.action.TRANSCRIPT_UPDATE"
        const val EXTRA_PHONE_NUMBER = "com.example.cybersmith.extra.PHONE_NUMBER"
        const val EXTRA_FRAUD_REASON = "com.example.cybersmith.extra.FRAUD_REASON"
        const val EXTRA_FRAUD_CONFIDENCE = "com.example.cybersmith.extra.FRAUD_CONFIDENCE"
        const val EXTRA_TRANSCRIPT = "com.example.cybersmith.extra.TRANSCRIPT"
    }
}
