package com.example.cybersmith

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class AppInCallService : InCallService() {
    companion object {
        var currentCall: Call? = null
        var instance: AppInCallService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d("AppInCallService", "Call added: ${call.details.handle}")
        currentCall = call
        
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d("AppInCallService", "Call removed")
        if (currentCall == call) {
            currentCall = null
        }
    }
}
