package com.jarvis

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.jarvis.automation.AutomationController
import com.jarvis.automation.SkillExecutor
import com.jarvis.audio.ClapDetector
import com.jarvis.backend.WebSocketClient
import com.jarvis.config.Config
import com.jarvis.memory.MemorySyncWorker
import com.jarvis.stt.NativeSttManager
import com.jarvis.tts.TtsManager
import com.jarvis.ui.components.*
import com.jarvis.ui.screens.*
import com.jarvis.ui.theme.*
import com.jarvis.wakeword.WakeWordManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var wakeWordManager: WakeWordManager
    private lateinit var sttManager: NativeSttManager
    private lateinit var ttsManager: TtsManager
    private lateinit var clapDetector: ClapDetector
    private var sendCommand: ((String) -> Unit)? = null
    private val automationController by lazy { AutomationController(this) }
    private val skillExecutor by lazy { SkillExecutor(automationController) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            initVoiceComponents()
        } else {
            Toast.makeText(this, "Audio permission required for voice features", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()
        scheduleMemorySync()

        setContent {
            JarvisTheme {
                JarvisApp(
                    onMicClick = { toggleListening() },
                    speakText = { if (::ttsManager.isInitialized) ttsManager.speak(it) },
                    onCommandReady = { sendCommand = it },
                    onAutomationPlan = { executeAutomationPlan(it) },
                    isAutomationEnabled = { automationController.isAccessibilityEnabled },
                    onEnableAutomation = automationController::openAccessibilitySettings,
                    isListening = { isListeningActive }
                )
            }
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.READ_CONTACTS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.CALL_PHONE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            initVoiceComponents()
        }
    }

    private fun scheduleMemorySync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<MemorySyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(workDataOf("userId" to Config.DEVICE_ID))
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MemorySyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun initVoiceComponents() {
        wakeWordManager = WakeWordManager(this)
        sttManager = NativeSttManager(this)
        ttsManager = TtsManager(this)
        clapDetector = ClapDetector(this)

        ttsManager.initialize()

        sttManager.initialize(
            onReady = {
                runOnUiThread {
                    Toast.makeText(this, "Voice system ready", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "STT error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val wakeWordStarted = wakeWordManager.start()
        clapDetector.start()

        if (wakeWordStarted) {
            lifecycleScope.launch {
                wakeWordManager.detections.collect {
                    runOnUiThread { activateListening() }
                }
            }
        }

        lifecycleScope.launch {
            clapDetector.doubleClaps.collect {
                runOnUiThread { activateListening() }
            }
        }
    }

    private var isListeningActive by mutableStateOf(false)

    private fun activateListening() {
        if (isListeningActive) return
        if (!sttManager.isAvailable) {
            Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        isListeningActive = true
        Toast.makeText(this, "Listening...", Toast.LENGTH_SHORT).show()

        val started = sttManager.startListening(
            onResult = { text ->
                isListeningActive = false
                sendCommandToBackend(text)
            },
            onPartialResult = { partial ->
                // Could update UI with partial text
            },
            onError = { errorMsg ->
                isListeningActive = false
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            }
        )
        if (!started) {
            isListeningActive = false
        }
    }

    private fun toggleListening() {
        if (isListeningActive) {
            sttManager.stopListening()
            isListeningActive = false
        } else {
            activateListening()
        }
    }

    private fun sendCommandToBackend(command: String) {
        sendCommand?.invoke(command) ?: ttsManager.speak("I'm still connecting. Please try again.")
    }

    private fun executeAutomationPlan(actions: JSONArray) {
        val requiresAccessibility = (0 until actions.length()).any { index ->
            actions.optJSONObject(index)?.optString("type") in setOf(
                "tap", "type", "swipe", "go_back", "go_home", "read_screen"
            )
        }
        if (requiresAccessibility && !automationController.isAccessibilityEnabled) {
            Toast.makeText(this, "Enable JARVIS Automation to continue", Toast.LENGTH_LONG).show()
            automationController.openAccessibilitySettings()
            return
        }

        lifecycleScope.launch {
            if (!skillExecutor.execute(actions)) {
                Toast.makeText(this@MainActivity, "Automation could not complete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wakeWordManager.isInitialized) wakeWordManager.release()
        if (::sttManager.isInitialized) sttManager.release()
        if (::ttsManager.isInitialized) ttsManager.shutdown()
        if (::clapDetector.isInitialized) clapDetector.stop()
    }
}

@Composable
fun JarvisApp(
    onMicClick: () -> Unit = {},
    speakText: (String) -> Unit = {},
    onCommandReady: ((String) -> Unit) -> Unit = {},
    onAutomationPlan: (JSONArray) -> Unit = {},
    isAutomationEnabled: () -> Boolean = { false },
    onEnableAutomation: () -> Unit = {},
    isListening: () -> Boolean = { false }
) {
    var currentScreen by remember { mutableStateOf("home") }
    var isConnected by remember { mutableStateOf(false) }
    var chatMessages by remember { mutableStateOf(listOf<Pair<String, Boolean>>()) }

    val wsClient = remember {
        WebSocketClient(
            url = Config.BACKEND_WS_URL,
            deviceId = Config.DEVICE_ID,
            onMessage = { msg ->
                val data = JSONObject(msg).optJSONObject("data")
                data?.optJSONArray("actions")
                    ?.takeIf { it.length() > 0 }
                    ?.let(onAutomationPlan)
                val response = data
                    ?.optString("response")
                    ?.takeIf { it.isNotBlank() }
                if (response != null) {
                    chatMessages = chatMessages + (response to false)
                    speakText(response)
                }
            },
            onConnected = { isConnected = true },
            onDisconnected = { isConnected = false }
        )
    }

    SideEffect { onCommandReady { command -> wsClient.sendCommand(command) } }

    DisposableEffect(Unit) {
        wsClient.connect()
        onDispose { wsClient.disconnect() }
    }

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
                    onMicClick = onMicClick,
                    isConnected = isConnected,
                    isAutomationEnabled = isAutomationEnabled(),
                    isListening = isListening()
                )
                "data" -> DataScreen()
                "map" -> MapScreen()
                "communication" -> CommunicationScreen(
                    messages = chatMessages,
                    onSendMessage = { msg ->
                        chatMessages = chatMessages + (msg to true)
                        wsClient.sendCommand(msg)
                    }
                )
                "settings" -> SettingsScreen(
                    isAutomationEnabled = isAutomationEnabled(),
                    onEnableAutomation = onEnableAutomation,
                    isBackendConnected = isConnected,
                    onTestTts = { speakText("JARVIS voice system operational.") }
                )
            }
        }
    }
}
