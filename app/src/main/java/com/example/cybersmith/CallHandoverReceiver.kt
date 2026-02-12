package com.example.cybersmith

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Handles notification action for AI call handover.
 * Opens dialer with predefined AI number for user-assisted call merge.
 */
class CallHandoverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_HANDOVER_TO_AI -> {
                val aiNumber = intent.getStringExtra(EXTRA_AI_NUMBER) ?: DEFAULT_AI_NUMBER
                
                // Open dialer with AI number
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$aiNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                
                // Show guidance toast
                Toast.makeText(
                    context,
                    "Dial the AI number, then use your phone's merge/conference call option",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val ACTION_HANDOVER_TO_AI = "com.example.cybersmith.action.HANDOVER_TO_AI"
        const val EXTRA_AI_NUMBER = "extra_ai_number"
        
        // This is a placeholder; ensure the Vapi-attached phone number is entered in settings
        const val DEFAULT_AI_NUMBER = "+19793418014"
    }
}
