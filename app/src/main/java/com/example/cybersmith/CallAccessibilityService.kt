package com.example.cybersmith

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class CallAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to process specific events for now, just simply being active is enough
        // to often bypass the microphone restriction on some devices.
        // We can add logic here later if we want to detect dialer UI.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
             Log.v(TAG, "Window state changed: ${event.packageName}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "CyberSmith Accessibility Service Connected")
    }

    companion object {
        private const val TAG = "CallAccessibilityService"
    }
}
