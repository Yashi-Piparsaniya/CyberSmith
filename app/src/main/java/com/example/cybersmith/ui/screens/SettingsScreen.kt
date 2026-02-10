package com.example.cybersmith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cybersmith.ui.theme.*

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cybersmith.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsSection(title = "Detection Engine") {
            SettingsTextField(
                label = "WebSocket URL",
                value = viewModel.webSocketUrl,
                onValueChange = { viewModel.updateWebSocketUrl(it) },
                icon = Icons.Default.Dns
            )
            SettingsTextField(
                label = "AI Handover Number",
                value = viewModel.aiNumber,
                onValueChange = { viewModel.updateAiNumber(it) },
                icon = Icons.Default.SmartToy
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsSection(title = "Alerts & Feedback") {
            SettingsToggle(
                title = "Voice Alerts",
                description = "Speak fraud warnings during calls",
                checked = viewModel.voiceAlertsEnabled,
                onCheckedChange = { viewModel.toggleVoiceAlerts(it) },
                icon = Icons.Default.RecordVoiceOver
            )
            SettingsToggle(
                title = "Haptic Feedback",
                description = "Vibrate when fraud is detected",
                checked = viewModel.hapticFeedbackEnabled,
                onCheckedChange = { viewModel.toggleHapticFeedback(it) },
                icon = Icons.Default.Vibration
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsSection(title = "Advanced") {
            SettingsItem(
                title = "Export Logs",
                subtitle = "Download call history as CSV",
                icon = Icons.Default.Download
            )
            SettingsItem(
                title = "Clear Database",
                subtitle = "Delete all stored call records",
                icon = Icons.Default.Delete,
                tint = Danger
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = PrimaryLight,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String, 
    subtitle: String, 
    icon: ImageVector,
    tint: Color = TextSecondary,
    onClick: () -> Unit = {}
) {
    ListItem(
        headlineContent = { Text(title, color = TextPrimary) },
        supportingContent = { Text(subtitle, color = TextSecondary) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = SurfaceVariant,
                focusedBorderColor = Primary,
                unfocusedContainerColor = SurfaceDark,
                focusedContainerColor = SurfaceDark
            )
        )
    }
}

@Composable
fun SettingsToggle(
    title: String, 
    description: String, 
    checked: Boolean, 
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector
) {
    ListItem(
        headlineContent = { Text(title, color = TextPrimary) },
        supportingContent = { Text(description, color = TextSecondary) },
        leadingContent = { Icon(icon, contentDescription = null, tint = TextSecondary) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Primary,
                    checkedTrackColor = Primary.copy(alpha = 0.5f)
                )
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
