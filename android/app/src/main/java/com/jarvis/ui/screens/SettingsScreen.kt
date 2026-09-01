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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ui.theme.*

data class SettingItem(val icon: ImageVector, val label: String, val value: String, val enabled: Boolean = true)

@Composable
fun SettingsScreen(
    isAutomationEnabled: Boolean = false,
    onEnableAutomation: () -> Unit = {},
    isBackendConnected: Boolean = false
) {
    var wakeWordEnabled by remember { mutableStateOf(true) }
    var sttEnabled by remember { mutableStateOf(true) }
    var ttsEnabled by remember { mutableStateOf(true) }
    var memoryEnabled by remember { mutableStateOf(true) }

    val settings = listOf(
        SettingItem(Icons.Default.RecordVoiceOver, "Wake Word Detection", "ON"),
        SettingItem(Icons.Default.Mic, "Speech-to-Text", "Vosk"),
        SettingItem(Icons.Default.VolumeUp, "Text-to-Speech", "Android TTS"),
        SettingItem(Icons.Default.Memory, "Memory System", "Supabase"),
        SettingItem(Icons.Default.Speed, "Primary LLM", "Groq"),
        SettingItem(Icons.Default.PhoneAndroid, "Device ID", "android-local"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(text = "JARVIS", color = CyanGlow, fontSize = 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 4.sp)
        Text(text = "Settings", color = TextGray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 24.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(settings) { setting ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanGlow, RoundedCornerShape(12.dp))
                        .background(DarkCardBg, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = setting.icon, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = setting.label, color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp)
                        Text(text = setting.value, color = TextGray, fontSize = 12.sp)
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextGray)
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanGlow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .background(DarkCardBg, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Device Automation", color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp)
                        Text(
                            if (isAutomationEnabled) "Accessibility service enabled" else "Enable to run app actions",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }
                    Button(onClick = onEnableAutomation, enabled = !isAutomationEnabled) {
                        Text(if (isAutomationEnabled) "Enabled" else "Enable")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanGlow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .background(DarkCardBg, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(text = "Backend Status", color = TextGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isBackendConnected) "Connected" else "Disconnected",
                            color = if (isBackendConnected) CyanGlow else TextGray,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "ws://jarvis-backend.onrender.com/ws", color = TextGray, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
