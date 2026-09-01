package com.jarvis.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ui.components.*
import com.jarvis.ui.theme.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit = {},
    onMicClick: () -> Unit = {},
    isConnected: Boolean = false,
    isAutomationEnabled: Boolean = false
) {
    val statusText = if (isConnected) "READY TO HELP" else "CONNECTING"
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "JARVIS",
                    color = CyanGlow,
                    fontSize = 40.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Text(text = "Personal AI Assistant", color = TextGray, fontSize = 14.sp)
            }
            AssistChip(
                onClick = { onNavigate("settings") },
                label = { Text(if (isConnected) "ONLINE" else "OFFLINE") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = if (isConnected) CyanGlow else TextGray,
                    leadingIconContentColor = if (isConnected) CyanGlow else TextGray
                )
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = statusText,
            color = CyanGlow,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        GlowingMicOrb(onClick = onMicClick)

        Spacer(modifier = Modifier.height(40.dp))

        MessageBubble(
            message = if (isAutomationEnabled) {
                "Automation is ready.\nWhat should I do?"
            } else {
                "Tell me what you need. Enable automation in Settings for app control."
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionCard(
                    icon = Icons.Default.SettingsSuggest,
                    title = "Systems Check",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("settings") }
                )
                ActionCard(
                    icon = Icons.Default.Analytics,
                    title = "Analyze Data",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("data") }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionCard(
                    icon = Icons.Default.MicNone,
                    title = "Voice Command",
                    modifier = Modifier.weight(1f),
                    onClick = onMicClick
                )
                ActionCard(
                    icon = Icons.Default.AutoAwesome,
                    title = if (isAutomationEnabled) "Automation Ready" else "Enable Automation",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("settings") }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionCard(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = "Type Command",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("communication") }
                )
            }
        }
    }
}
