package com.example.cybersmith

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.cybersmith.ui.screens.CallLogsScreen
import com.example.cybersmith.ui.screens.HomeScreen
import com.example.cybersmith.ui.screens.SettingsScreen
import com.example.cybersmith.ui.theme.CyberSmithTheme
import com.example.cybersmith.ui.theme.*
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CALL_PHONE,
    ).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            it + Manifest.permission.POST_NOTIFICATIONS
        } else it
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(this, "Permissions required for protection", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        tts = TextToSpeech(this, this)
        checkPermissions()
        
        if (intent.getBooleanExtra("EXTRA_FRAUD_ALERT", false)) {
            triggerAlert()
        }

        enableEdgeToEdge()
        setContent {
            CyberSmithTheme {
                CyberSmithApp()
            }
        }
    }

    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun triggerAlert() {
        // Vibration
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(1000)
        }

        // Voice
        tts?.speak("Fraud call detected. Exercise caution.", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun CyberSmithApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = TextPrimary,
                tonalElevation = 8.dp
            ) {
                AppDestinations.entries.forEach { destination ->
                    val selected = destination == currentDestination
                    NavigationBarItem(
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) },
                        selected = selected,
                        onClick = { currentDestination = destination },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary,
                            selectedTextColor = Primary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Primary.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        },
        containerColor = BackgroundDark,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = {
                fadeIn().togetherWith(fadeOut())
            },
            label = "screen_transition",
            modifier = Modifier.padding(innerPadding)
        ) { destination ->
            when (destination) {
                AppDestinations.HOME -> HomeScreen()
                AppLogDestinations.LOGS -> CallLogsScreen() // Using internal enum for better mapping if needed or just destinations
                AppDestinations.SETTINGS -> SettingsScreen()
                else -> HomeScreen()
            }
        }
    }
}

// Updated Destinations to match Truecaller style
enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    LOGS("Call Logs", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings),
}

// Alias for compatibility if I misnamed in planning
object AppLogDestinations {
    val LOGS = AppDestinations.LOGS
}