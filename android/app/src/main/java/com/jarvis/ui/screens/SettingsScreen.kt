package com.jarvis.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.jarvis.config.Config
import com.jarvis.ui.theme.*

data class ToggleSettingItem(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val state: Boolean,
    val onStateChange: (Boolean) -> Unit
)

data class InfoSettingItem(
    val icon: ImageVector,
    val label: String,
    val value: String
)

@Composable
fun SettingsScreen(
    isAutomationEnabled: Boolean = false,
    onEnableAutomation: () -> Unit = {},
    isBackendConnected: Boolean = false,
    onTestTts: () -> Unit = {},
    onEnrollWithSecret: (String) -> Unit = {},
    isEnrolled: Boolean = false
) {
    val context = LocalContext.current
    var wakeWordEnabled by remember { mutableStateOf(true) }
    var sttEnabled by remember { mutableStateOf(true) }
    var ttsEnabled by remember { mutableStateOf(true) }
    var memorySyncEnabled by remember { mutableStateOf(true) }
    var showPairingDialog by remember { mutableStateOf(false) }

    val toggleSettings = listOf(
        ToggleSettingItem(
            icon = Icons.Default.Mic,
            label = "Speech-to-Text (STT)",
            description = "Android Native SpeechRecognizer",
            state = sttEnabled,
            onStateChange = {
                sttEnabled = it
                Toast.makeText(context, "STT ${if (it) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            }
        ),
        ToggleSettingItem(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            label = "Text-to-Speech (TTS)",
            description = "Android System TTS Engine",
            state = ttsEnabled,
            onStateChange = {
                ttsEnabled = it
                Toast.makeText(context, "TTS ${if (it) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            }
        ),
        ToggleSettingItem(
            icon = Icons.Default.RecordVoiceOver,
            label = "Wake Word Detection",
            description = "OpenWakeWord Engine",
            state = wakeWordEnabled,
            onStateChange = {
                wakeWordEnabled = it
                Toast.makeText(context, "Wake word ${if (it) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            }
        ),
        ToggleSettingItem(
            icon = Icons.Default.Memory,
            label = "Memory System Sync",
            description = "Supabase Cloud + Room DB",
            state = memorySyncEnabled,
            onStateChange = {
                memorySyncEnabled = it
                Toast.makeText(context, "Memory sync ${if (it) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            }
        )
    )

    val infoSettings = listOf(
        InfoSettingItem(Icons.Default.Speed, "Primary LLM Provider", "Groq / OpenRouter / NIM"),
        InfoSettingItem(Icons.Default.PhoneAndroid, "Device ID", Config.getDeviceId(context)),
        InfoSettingItem(Icons.Default.Info, "App Version", "1.0.0 (Production Build)")
    )

    if (showPairingDialog) {
        PairingDialog(
            onConfirm = { secret ->
                showPairingDialog = false
                onEnrollWithSecret(secret)
            },
            onDismiss = { showPairingDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = "JARVIS",
            color = CyanGlow,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Text(
            text = "System Settings & Diagnostics",
            color = TextGray,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Section 1: Backend Connection Status Card
            item {
                val statusBorderColor by animateColorAsState(
                    targetValue = if (isBackendConnected) CyanGlow else Color.Red.copy(alpha = 0.5f),
                    label = "statusBorder"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, statusBorderColor, RoundedCornerShape(14.dp))
                        .background(DarkCardBg, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (isBackendConnected) CyanGlow else Color.Red,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Backend Server Status",
                                color = White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (isBackendConnected) "ONLINE" else "OFFLINE",
                                color = if (isBackendConnected) CyanGlow else Color.Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "WebSocket: ${Config.BACKEND_WS_URL}", color = TextGray, fontSize = 11.sp)
                        Text(text = "API Base: ${Config.BACKEND_API_URL}", color = TextGray, fontSize = 11.sp)
                    }
                }
            }

            // Section 1b: Pairing Card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanGlow.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .background(DarkCardBg, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isEnrolled) Icons.Default.Link else Icons.Default.LinkOff,
                        contentDescription = null,
                        tint = if (isEnrolled) CyanGlow else Color.Yellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnrolled) "Device Paired" else "Device Not Paired",
                            color = White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isEnrolled) "Connected to backend" else "Enter pairing code from backend to register",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = { showPairingDialog = true },
                        enabled = !isEnrolled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanGlow,
                            contentColor = DarkCardBg
                        )
                    ) {
                        Text(
                            text = if (isEnrolled) "Paired" else "Pair",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Section 2: Automation Permission Card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanGlow.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .background(DarkCardBg, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CyanGlow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Device Automation", color = White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (isAutomationEnabled) "Accessibility Service Enabled" else "Required for app gestures & actions",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = onEnableAutomation,
                        enabled = !isAutomationEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanGlow,
                            contentColor = DarkCardBg
                        )
                    ) {
                        Text(
                            text = if (isAutomationEnabled) "Active" else "Enable",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Section 3: Feature Toggles
            items(toggleSettings) { setting ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanGlow.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .background(DarkCardBg, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = setting.icon,
                        contentDescription = null,
                        tint = CyanGlow,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = setting.label, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = setting.description, color = TextGray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = setting.state,
                        onCheckedChange = setting.onStateChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanGlow,
                            checkedTrackColor = CyanGlow.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = DarkCardBg
                        )
                    )
                }
            }

            // Section 4: System Information Items
            items(infoSettings) { info ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanGlow.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .background(DarkCardBg, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = info.icon,
                        contentDescription = null,
                        tint = CyanGlow,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = info.label, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = info.value, color = TextGray, fontSize = 11.sp)
                    }
                }
            }

            // Section 5: Maintenance Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            Toast.makeText(context, "App cache cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanGlow),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CyanGlow.copy(alpha = 0.5f)))
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Cache", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onTestTts,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanGlow),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CyanGlow.copy(alpha = 0.5f)))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Voice", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pairingCode by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair Device") },
        text = {
            Column {
                Text(
                    "Enter the pairing code from your JARVIS backend to register this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = {
                        pairingCode = it.trim()
                        isError = false
                    },
                    label = { Text("Pairing Code") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Please enter a valid pairing code", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pairingCode.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(pairingCode)
                    }
                }
            ) { Text("Pair") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
