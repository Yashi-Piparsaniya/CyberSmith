package com.example.cybersmith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cybersmith.ui.theme.*

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cybersmith.ui.viewmodel.LogsViewModel
import com.example.cybersmith.data.model.CallLogRecord
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CallLogsScreen(viewModel: LogsViewModel = viewModel()) {
    val logs by viewModel.allLogs.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        Text(
            text = "Call Logs",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Search Bar (Static for now)
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search numbers...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = SurfaceVariant,
                focusedBorderColor = Primary,
                unfocusedContainerColor = SurfaceDark,
                focusedContainerColor = SurfaceDark
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No call history", color = TextSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    CallLogItem(log)
                }
            }
        }
    }
}

@Composable
fun CallLogItem(log: CallLogRecord) {
    val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    val isFraud = log.status == "FRAUD"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isFraud) Icons.Default.Block else Icons.Default.Call,
                    contentDescription = null,
                    tint = if (isFraud) Danger else Success,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = log.callerName ?: log.phoneNumber,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (log.callerName != null) {
                        Text(
                            text = log.phoneNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = dateFormat.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            
            if (isFraud) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "BLOCKED",
                        color = Danger,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    log.reason?.let {
                        Text(
                            text = it.take(20) + if (it.length > 20) "..." else "",
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
