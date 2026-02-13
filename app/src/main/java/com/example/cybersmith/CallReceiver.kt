package com.example.cybersmith

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    Log.d("CallReceiver", "Phone Ringing: $phoneNumber")
                    startService(context, phoneNumber)
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    Log.d("CallReceiver", "Call Answered/Started: $phoneNumber")
                    startService(context, phoneNumber)
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.d("CallReceiver", "Call Ended")
                    // Use a slightly more robust way to stop if we were just ringing or offhook
                    stopService(context)
                }
            }
        }
    }

    private fun startService(context: Context, phoneNumber: String?) {
        val serviceIntent = Intent(context, CallDetectionService::class.java).apply {
            action = CallDetectionService.ACTION_START_RECORDING
            putExtra(CallDetectionService.EXTRA_PHONE_NUMBER, phoneNumber)
        }
        context.startForegroundService(serviceIntent)
    }

    private fun stopService(context: Context) {
        val serviceIntent = Intent(context, CallDetectionService::class.java).apply {
            action = CallDetectionService.ACTION_STOP_RECORDING
        }
        context.startService(serviceIntent)
    }
}
