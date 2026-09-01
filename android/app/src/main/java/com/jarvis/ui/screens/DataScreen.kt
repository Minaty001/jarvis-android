package com.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.backend.ApiClient
import com.jarvis.backend.MemoryStats
import com.jarvis.config.Config
import com.jarvis.ui.theme.*

data class DataMetric(
    val label: String,
    val value: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun DataScreen() {
    val apiClient = remember { ApiClient() }
    var stats by remember { mutableStateOf(MemoryStats(0, 0)) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        hasError = false
        try {
            stats = apiClient.getMemoryStats(Config.DEVICE_ID)
        } catch (e: Exception) {
            hasError = true
        } finally {
            isLoading = false
        }
    }

    val metrics = listOf(
        DataMetric("Memory Entries", "${stats.totalMemories}", Icons.Default.Memory),
        DataMetric("Skills Learned", "${stats.totalSkills}", Icons.Default.School),
        DataMetric("LLM Provider", "Groq / OpenRouter", Icons.Default.Speed),
        DataMetric("Sync Status", if (stats.lastSync != null) "Synced" else "Not synced", Icons.Default.CloudDone),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "JARVIS",
                    color = CyanGlow,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Text(text = "Data & Analytics", color = TextGray, fontSize = 14.sp)
            }
            IconButton(onClick = { refreshKey++ }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = CyanGlow
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CyanGlow)
                }
            }
            hasError -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Could not reach backend", color = TextGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { refreshKey++ },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanGlow)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(metrics) { metric ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CyanGlow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .background(DarkCardBg, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = metric.icon,
                                contentDescription = null,
                                tint = CyanGlow,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = metric.label, color = TextGray, fontSize = 13.sp)
                                Text(
                                    text = metric.value,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
