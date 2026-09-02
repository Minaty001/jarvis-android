package com.jarvis

import android.Manifest
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.jarvis.automation.*
import com.jarvis.audio.ClapDetector
import com.jarvis.backend.ApiClient
import com.jarvis.backend.AuthManager
import com.jarvis.backend.ConnectionManager
import com.jarvis.backend.TokenState
import com.jarvis.backend.WebSocketClient
import com.jarvis.config.Config
import com.jarvis.memory.MemorySyncWorker
import com.jarvis.permissions.PermissionManager
import com.jarvis.stt.NativeSttManager
import com.jarvis.tts.TtsManager
import com.jarvis.ui.components.*
import com.jarvis.ui.screens.*
import com.jarvis.ui.theme.*
import com.jarvis.wakeword.LiveKitWakeWordEngine
import com.jarvis.wakeword.OnnxWakeWordDetector
import com.jarvis.wakeword.WakeWordConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

enum class VoiceInputMode { OFF, WAKE_WORD, COMMAND }

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private var wakeEngine: LiveKitWakeWordEngine? = null
    private var sttManager: NativeSttManager? = null
    private var ttsManager: TtsManager? = null
    private lateinit var authManager: AuthManager
    private lateinit var apiClient: ApiClient
    private var sendCommand: ((String) -> Unit)? = null
    private var wakeWordEnabled = false
    private var voiceMode = VoiceInputMode.OFF

    private val automationController by lazy { AutomationController(this) }

    private val confirmationManager by lazy {
        ConfirmationManager(
            ui = object : ConfirmationUI {
                override suspend fun showConfirmation(request: ConfirmationRequest): ConfirmationResult {
                    return suspendCancellableCoroutine { cont ->
                        runOnUiThread {
                            confirmationRequest = ConfirmationRequestWithResult(request, cont)
                        }
                    }
                }
            }
        )
    }

    private val skillExecutor by lazy { SkillExecutor(automationController, confirmationManager) }

    private var confirmationRequest by mutableStateOf<ConfirmationRequestWithResult?>(null)

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

        authManager = AuthManager(this)
        apiClient = ApiClient(authManager = authManager)

        requestAudioPermission()
        scheduleMemorySync()

        lifecycleScope.launch {
            val success = apiClient.bootstrap()
            if (success) {
                Log.i(TAG, "Auth bootstrap succeeded (state=${authManager.currentState})")
            } else {
                Log.e(TAG, "Auth bootstrap failed (state=${authManager.currentState})")
            }
        }

        setContent {
            JarvisTheme {
                val request = confirmationRequest
                if (request != null) {
                    ConfirmationDialog(
                        actionType = request.request.actionType,
                        riskLevel = request.request.riskLevel,
                        params = request.request.params,
                        onConfirm = {
                            confirmationRequest = null
                            request.continuation.resume(ConfirmationResult.ALLOWED)
                        },
                        onDeny = {
                            confirmationRequest = null
                            request.continuation.resume(ConfirmationResult.DENIED)
                        }
                    )
                }

                val authState by authManager.state.collectAsState()

                JarvisApp(
                    authState = authState,
                    onMicClick = { toggleListening() },
                    speakText = { speakText(it) },
                    onCommandReady = { sendCommand = it },
                    onAutomationPlan = { executeAutomationPlan(it) },
                    isAutomationEnabled = { automationController.isAccessibilityEnabled },
                    onEnableAutomation = automationController::openAccessibilitySettings,
                    isListening = { isListeningActive },
                    onToggleWakeWord = { toggleWakeWord() },
                    isWakeWordEnabled = { wakeWordEnabled },
                    authManager = authManager,
                    apiClient = apiClient
                )
            }
        }
    }

    private fun requestAudioPermission() {
        if (!PermissionManager.isGranted(this, PermissionManager.AUDIO)) {
            permissionLauncher.launch(arrayOf(PermissionManager.AUDIO))
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
            .setInputData(workDataOf("userId" to Config.getDeviceId(this)))
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MemorySyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun initVoiceComponents() {
        ttsManager = TtsManager(this).also { it.initialize() }
        sttManager = NativeSttManager(this).also {
            it.initialize(
                onReady = { Log.d(TAG, "STT ready") },
                onError = { error -> Log.w(TAG, "STT error: $error") }
            )
        }

        initWakeWordEngine()
    }

    private fun initWakeWordEngine() {
        try {
            val config = WakeWordConfig()
            val detector = OnnxWakeWordDetector(context = this, config = config)
            wakeEngine = LiveKitWakeWordEngine(context = this, config = config, detector = detector)
            wakeEngine?.setOnWakeListener {
                Log.i(TAG, "Wake word detected")
                runOnUiThread { activateListening() }
            }
            wakeEngine?.setOnErrorListener { e ->
                Log.e(TAG, "Wake word engine error", e)
            }
            Log.i(TAG, "Wake word engine initialized (not started — user must enable)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize wake word engine", e)
        }
    }

    private fun toggleWakeWord() {
        if (wakeWordEnabled) {
            wakeEngine?.stop()
            wakeWordEnabled = false
            voiceMode = VoiceInputMode.OFF
            Log.i(TAG, "Wake word disabled")
        } else {
            wakeEngine?.startMonitoring()
            wakeWordEnabled = true
            voiceMode = VoiceInputMode.WAKE_WORD
            Log.i(TAG, "Wake word enabled")
        }
    }

    private var isListeningActive by mutableStateOf(false)

    private fun activateListening() {
        if (isListeningActive) return
        val stt = sttManager
        if (stt == null || !stt.isAvailable) {
            Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        isListeningActive = true
        voiceMode = VoiceInputMode.COMMAND
        Toast.makeText(this, "Listening...", Toast.LENGTH_SHORT).show()

        wakeEngine?.pause()

        val started = stt.startListening(
            onResult = { text ->
                isListeningActive = false
                voiceMode = if (wakeWordEnabled) VoiceInputMode.WAKE_WORD else VoiceInputMode.OFF
                if (wakeWordEnabled) wakeEngine?.resume()
                sendCommandToBackend(text)
            },
            onPartialResult = { },
            onError = { errorMsg ->
                isListeningActive = false
                voiceMode = if (wakeWordEnabled) VoiceInputMode.WAKE_WORD else VoiceInputMode.OFF
                if (wakeWordEnabled) wakeEngine?.resume()
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            }
        )
        if (!started) {
            isListeningActive = false
            voiceMode = if (wakeWordEnabled) VoiceInputMode.WAKE_WORD else VoiceInputMode.OFF
            if (wakeWordEnabled) wakeEngine?.resume()
        }
    }

    private fun toggleListening() {
        if (isListeningActive) {
            sttManager?.stopListening()
            isListeningActive = false
            voiceMode = if (wakeWordEnabled) VoiceInputMode.WAKE_WORD else VoiceInputMode.OFF
            if (wakeWordEnabled) wakeEngine?.resume()
        } else {
            activateListening()
        }
    }

    private fun sendCommandToBackend(command: String) {
        sendCommand?.invoke(command) ?: ttsManager?.speak("I'm still connecting. Please try again.")
    }

    private fun speakText(text: String) {
        ttsManager?.speak(text)
    }

    private fun executeAutomationPlan(actions: org.json.JSONArray) {
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
        confirmationManager.destroy()
        wakeEngine?.release()
        sttManager?.release()
        ttsManager?.shutdown()
    }
}

data class ConfirmationRequestWithResult(
    val request: com.jarvis.automation.ConfirmationRequest,
    val continuation: kotlinx.coroutines.CancellableContinuation<ConfirmationResult>
)

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
    authState: TokenState = TokenState.NO_TOKEN,
    onMicClick: () -> Unit = {},
    speakText: (String) -> Unit = {},
    onCommandReady: ((String) -> Unit) -> Unit = {},
    onAutomationPlan: (org.json.JSONArray) -> Unit = {},
    isAutomationEnabled: () -> Boolean = { false },
    onEnableAutomation: () -> Unit = {},
    isListening: () -> Boolean = { false },
    onToggleWakeWord: () -> Unit = {},
    isWakeWordEnabled: () -> Boolean = { false },
    authManager: AuthManager,
    apiClient: ApiClient
) {
    var currentScreen by remember { mutableStateOf("home") }
    var isConnected by remember { mutableStateOf(false) }
    var chatMessages by remember { mutableStateOf(listOf<Pair<String, Boolean>>()) }
    val scope = rememberCoroutineScope()

    val isAuthReady = authState == TokenState.AUTHENTICATED

    val wsClient = remember(isAuthReady) {
        WebSocketClient(
            wsUrl = Config.BACKEND_WS_URL,
            authManager = authManager,
            onMessageReceived = { msg ->
                try {
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
                } catch (e: Exception) {
                    Log.e("JarvisApp", "Error parsing WS message", e)
                }
            },
            onConnected = { isConnected = true },
            onDisconnected = { isConnected = false }
        )
    }

    SideEffect { onCommandReady { command -> wsClient.sendCommand(command) } }

    LaunchedEffect(isAuthReady) {
        if (isAuthReady) {
            wsClient.connect()
        } else {
            wsClient.disconnect()
        }
    }

    DisposableEffect(Unit) {
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
