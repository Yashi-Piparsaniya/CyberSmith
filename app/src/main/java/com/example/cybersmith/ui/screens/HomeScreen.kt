package com.example.cybersmith.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cybersmith.ui.theme.*

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cybersmith.ui.viewmodel.DashboardViewModel
import com.example.cybersmith.data.model.CallLogRecord
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(viewModel: DashboardViewModel = viewModel()) {
    val recentActivity by viewModel.recentActivity.collectAsState()
    val totalCalls by viewModel.totalCalls.collectAsState()
    val threatsBlocked by viewModel.threatsBlocked.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // Protection Shield
        ProtectionShield(isProtected = viewModel.isProtectionEnabled)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Stats Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatsCard(
                title = "Total Analyzed",
                value = totalCalls.toString(),
                icon = Icons.Default.History,
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = "Threats Blocked",
                value = threatsBlocked.toString(),
                icon = Icons.Default.Warning,
                color = Danger,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Recent Activity Label
        Text(
            text = "Recent Activity",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Recent Activity List (Real Data)
        RecentActivityList(recentActivity)
    }
}

@Composable
fun ProtectionShield(isProtected: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isProtected) ShieldActive.copy(alpha = 0.2f) else ShieldInactive.copy(alpha = 0.2f),
                            androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isProtected) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = if (isProtected) ShieldActive else ShieldInactive
            )
            
            // Outer Ring
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .border(2.dp, if (isProtected) ShieldActive.copy(alpha = 0.5f) else ShieldInactive.copy(alpha = 0.5f), CircleShape)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (isProtected) "PROTECTED" else "UNPROTECTED",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (isProtected) ShieldActive else ShieldWarning
        )
        
        Text(
            text = if (isProtected) "Active since 2 hours" else "Tap to enable protection",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
fun StatsCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color = Primary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun RecentActivityList(activities: List<CallLogRecord>) {
    if (activities.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No recent activity", color = TextSecondary)
        }
        return
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(activities) { item ->
            val statusColor = when (item.status) {
                "FRAUD" -> Danger
                "SAFE" -> Success
                else -> TextSecondary
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = (item.callerName ?: item.phoneNumber).firstOrNull()?.toString() ?: "?"
                        Text(
                            text = initial,
                            color = PrimaryLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.callerName ?: item.phoneNumber, 
                            color = TextPrimary, 
                            fontWeight = FontWeight.Medium
                        )
                        if (item.callerName != null) {
                            Text(
                                text = item.phoneNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = timeFormat.format(Date(item.timestamp)), 
                            style = MaterialTheme.typography.bodySmall, 
                            color = TextMuted
                        )
                    }
                }
                
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = item.status,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

data class ActivityItem(
    val number: String,
    val status: String,
    val time: String,
    val statusColor: androidx.compose.ui.graphics.Color
)
