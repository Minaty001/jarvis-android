package com.jarvis

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.automation.ConfirmationRequest
import com.jarvis.automation.ConfirmationResult
import com.jarvis.automation.RiskLevel
import com.jarvis.permissions.PermissionManager
import com.jarvis.ui.screens.*
import com.jarvis.ui.theme.*
import com.jarvis.ui.viewmodel.MainViewModel
import com.jarvis.ui.components.BottomNavItem
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (!audioGranted) {
            Toast.makeText(this, "Audio permission required for voice features", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PermissionManager.isGranted(this, PermissionManager.AUDIO)) {
            permissionLauncher.launch(arrayOf(PermissionManager.AUDIO))
        }

        setContent {
            JarvisTheme {
                val viewModel: MainViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.initialize()
                }

                val confRequest = uiState.confirmationRequest
                if (confRequest != null) {
                    ConfirmationDialog(
                        actionType = confRequest.actionType,
                        riskLevel = confRequest.riskLevel,
                        params = confRequest.params,
                        onConfirm = { viewModel.confirmAction(true) },
                        onDeny = { viewModel.confirmAction(false) }
                    )
                }

                JarvisApp(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    actionType: String,
    riskLevel: RiskLevel,
    params: Map<String, String>,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text("Confirm Action") },
        text = {
            Column {
                Text("Action: $actionType", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Risk: $riskLevel", style = MaterialTheme.typography.bodyMedium,
                    color = when (riskLevel) {
                        RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                        RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    })
                Spacer(modifier = Modifier.height(8.dp))
                params.forEach { (key, value) ->
                    Text("$key: $value", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Allow") }
        },
        dismissButton = {
            TextButton(onClick = onDeny) { Text("Deny") }
        }
    )
}

@Composable
fun JarvisApp(
    uiState: com.jarvis.ui.viewmodel.MainUiState = com.jarvis.ui.viewmodel.MainUiState(),
    viewModel: MainViewModel
) {
    var currentScreen by remember { mutableStateOf("home") }

    val selectedIndex = when (currentScreen) {
        "home" -> 0
        "data" -> 1
        "map" -> 2
        "communication" -> 3
        "settings" -> 4
        else -> 0
    }

    Scaffold(
        containerColor = BgColor,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BottomBarBg)
                    .border(width = 1.dp, color = CyanGlow.copy(alpha = 0.3f))
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(icon = Icons.Default.Home, label = "Home", isSelected = selectedIndex == 0) { currentScreen = "home" }
                BottomNavItem(icon = Icons.Default.BarChart, label = "Data", isSelected = selectedIndex == 1) { currentScreen = "data" }
                BottomNavItem(icon = Icons.Default.Place, label = "Map", isSelected = selectedIndex == 2) { currentScreen = "map" }
                BottomNavItem(icon = Icons.Default.ChatBubbleOutline, label = "Communication", isSelected = selectedIndex == 3) { currentScreen = "communication" }
                BottomNavItem(icon = Icons.Default.Settings, label = "Settings", isSelected = selectedIndex == 4) { currentScreen = "settings" }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                "home" -> HomeScreen(
                    onNavigate = { currentScreen = it },
                    onMicClick = { viewModel.startListening() },
                    isConnected = uiState.isConnected,
                    isAutomationEnabled = uiState.isAutomationEnabled,
                    isListening = uiState.voiceState == com.jarvis.runtime.VoiceState.COMMAND_LISTENING
                )
                "data" -> DataScreen()
                "map" -> MapScreen()
                "communication" -> CommunicationScreen(
                    messages = uiState.chatMessages,
                    onSendMessage = { msg -> viewModel.sendCommand(msg) }
                )
                "settings" -> SettingsScreen(
                    isAutomationEnabled = uiState.isAutomationEnabled,
                    onEnableAutomation = { viewModel.openAccessibilitySettings() },
                    isBackendConnected = uiState.isConnected,
                    onTestTts = { viewModel.runtime.speakText("JARVIS voice system operational.") }
                )
            }
        }
    }
}
