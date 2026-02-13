package com.example.cybersmith
 
 import androidx.core.content.ContextCompat

import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cybersmith.ui.theme.CyberSmithTheme
import com.example.cybersmith.ui.theme.Primary
import com.example.cybersmith.ui.theme.SurfaceDark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight

class InCallActivity : ComponentActivity() {

    private var callState by mutableIntStateOf(Call.STATE_RINGING)
    private var isSpeakerOn by mutableStateOf(false)
    private var callerName by mutableStateOf<String?>(null)
    private var isFraudWarningVisible by mutableStateOf(false)
    private var fraudReason by mutableStateOf<String?>(null)

    private val fraudBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CallDetectionService.ACTION_FRAUD_DETECTED) {
                isFraudWarningVisible = true
                fraudReason = intent.getStringExtra(CallDetectionService.EXTRA_FRAUD_REASON)
            }
        }
    }
    
    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateCallState(state)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val call = AppInCallService.currentCall
        if (call == null) {
            finish()
            return
        }

        call.registerCallback(callCallback)
        updateCallState(call.state)
        
        val number = call.details.handle?.schemeSpecificPart
        callerName = ContactHelper.getContactName(this, number)

        // Register for fraud alerts
        val filter = IntentFilter(CallDetectionService.ACTION_FRAUD_DETECTED)
        ContextCompat.registerReceiver(
            this,
            fraudBroadcastReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            CyberSmithTheme {
                InCallScreen(
                    phoneNumber = number ?: "Unknown Caller",
                    contactName = callerName,
                    state = callState,
                    isSpeakerOn = isSpeakerOn,
                    isFraudVisible = isFraudWarningVisible,
                    fraudReason = fraudReason,
                    onAnswer = { 
                        call.answer(VideoProfile.STATE_AUDIO_ONLY) 
                    },
                    onReject = { 
                        if (call.state == Call.STATE_RINGING) {
                            call.reject(false, null)
                        } else {
                            call.disconnect()
                        }
                        finish()
                    },
                    onToggleSpeaker = {
                        isSpeakerOn = !isSpeakerOn
                        val route = if (isSpeakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
                        AppInCallService.instance?.setAudioRoute(route)
                    }
                )
            }
        }
    }

    private fun updateCallState(state: Int) {
        callState = state
        if (state == Call.STATE_DISCONNECTED) {
            finish()
        }
    }

    override fun onDestroy() {
        AppInCallService.currentCall?.unregisterCallback(callCallback)
        try {
            unregisterReceiver(fraudBroadcastReceiver)
        } catch (e: Exception) {}
        super.onDestroy()
    }
}

@Composable
fun InCallScreen(
    phoneNumber: String,
    contactName: String?,
    state: Int,
    isSpeakerOn: Boolean,
    isFraudVisible: Boolean = false,
    fraudReason: String? = null,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (contactName != null) {
                    Text(
                        text = contactName,
                        fontSize = 32.sp,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = phoneNumber,
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = phoneNumber,
                        fontSize = 32.sp,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                
                Text(
                    text = when (state) {
                        Call.STATE_RINGING -> "Incoming Call..."
                        Call.STATE_DIALING -> "Dialing..."
                        Call.STATE_ACTIVE -> "Active Call"
                        Call.STATE_CONNECTING -> "Connecting..."
                        Call.STATE_DISCONNECTING -> "Disconnecting..."
                        else -> "Call"
                    },
                    fontSize = 18.sp,
                    color = Primary,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (isFraudVisible) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "FRAUD DETECTED",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Text(
                                text = fraudReason ?: "Suspicious activity detected on this call.",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state == Call.STATE_ACTIVE) {
                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Speaker",
                            tint = if (isSpeakerOn) Primary else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "Speaker",
                        color = if (isSpeakerOn) Primary else Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (state == Call.STATE_RINGING) {
                        FilledIconButton(
                            onClick = onAnswer,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Answer", modifier = Modifier.size(40.dp))
                        }
                    }

                    FilledIconButton(
                        onClick = onReject,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFF44336)
                        )
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "Reject",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}
