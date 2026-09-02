package com.jarvis.runtime

import android.content.Context
import android.util.Log
import com.jarvis.automation.*
import com.jarvis.backend.*
import com.jarvis.config.Config
import com.jarvis.stt.NativeSttManager
import com.jarvis.tts.TtsManager
import com.jarvis.wakeword.LiveKitWakeWordEngine
import com.jarvis.wakeword.OnnxWakeWordDetector
import com.jarvis.wakeword.WakeWordConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

enum class RuntimeState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    ERROR
}

class AssistantRuntime(private val context: Context) {
    companion object {
        private const val TAG = "AssistantRuntime"
        private var instance: AssistantRuntime? = null

        fun getInstance(context: Context): AssistantRuntime {
            return instance ?: synchronized(this) {
                instance ?: AssistantRuntime(context.applicationContext).also { instance = it }
            }
        }
    }

    val authManager = AuthManager(context)
    val apiClient = ApiClient(authManager = authManager)

    val connectionManager = ConnectionManager()

    val confirmationManager = ConfirmationManager(
        ui = object : ConfirmationUI {
            override suspend fun showConfirmation(request: ConfirmationRequest): ConfirmationResult {
                return withContext(Dispatchers.Main) {
                    _confirmationRequest.emit(request)
                    val result = CompletableDeferred<ConfirmationResult>()
                    _confirmationResult = result
                    result.await()
                }
            }
        }
    )

    private val permissionChecker = PermissionChecker()
    val policyEngine = ActionPolicyEngine(permissionChecker, confirmationManager)
    val automationController = AutomationController(context)
    val skillExecutor = SkillExecutor(automationController, confirmationManager)

    private var _confirmationRequest = MutableStateFlow<ConfirmationRequest?>(null)
    val confirmationRequest: StateFlow<ConfirmationRequest?> = _confirmationRequest.asStateFlow()
    private var _confirmationResult: CompletableDeferred<ConfirmationResult>? = null

    private val _runtimeState = MutableStateFlow(RuntimeState.UNINITIALIZED)
    val runtimeState: StateFlow<RuntimeState> = _runtimeState.asStateFlow()

    private val _lastScreenContent = MutableStateFlow<String?>(null)
    val lastScreenContent: StateFlow<String?> = _lastScreenContent.asStateFlow()

    private var ttsManager: TtsManager? = null
    private var sttManager: NativeSttManager? = null
    private var wakeEngine: LiveKitWakeWordEngine? = null
    private var wsClient: WebSocketClient? = null

    private val initialized = AtomicBoolean(false)

    suspend fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        _runtimeState.value = RuntimeState.INITIALIZING

        try {
            ttsManager = TtsManager(context).also { it.initialize() }
            sttManager = NativeSttManager(context).also {
                it.initialize(
                    onReady = { Log.d(TAG, "STT ready") },
                    onError = { e -> Log.w(TAG, "STT error: $e") }
                )
            }
            initWakeWord()
            _runtimeState.value = RuntimeState.READY
            Log.i(TAG, "Runtime initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Runtime init failed", e)
            _runtimeState.value = RuntimeState.ERROR
        }
    }

    private fun initWakeWord() {
        try {
            val config = WakeWordConfig()
            val detector = OnnxWakeWordDetector(context, config)
            wakeEngine = LiveKitWakeWordEngine(context, config, detector)
            wakeEngine?.setOnWakeListener {
                Log.i(TAG, "Wake word detected")
            }
            wakeEngine?.setOnErrorListener { e ->
                Log.e(TAG, "Wake word error", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wake word init failed", e)
        }
    }

    suspend fun bootstrapAndConnect(): Boolean {
        val success = apiClient.bootstrap()
        if (success) {
            connectWebSocket()
        }
        return success
    }

    fun connectWebSocket() {
        if (!authManager.isAuthenticated) {
            Log.w(TAG, "Cannot connect WS: not authenticated")
            return
        }

        wsClient?.disconnect()
        wsClient = WebSocketClient(
            wsUrl = Config.BACKEND_WS_URL,
            authManager = authManager,
            onMessageReceived = { msg -> handleWsMessage(msg) },
            onConnected = { connectionManager.onConnected() },
            onDisconnected = { connectionManager.onDisconnected() }
        )
        wsClient?.connect()
    }

    private fun handleWsMessage(msg: String) {
        try {
            val json = JSONObject(msg)
            val type = json.optString("type", "")
            when (type) {
                "response" -> {
                    val data = json.optJSONObject("data")
                    val actions = data?.optJSONArray("actions")
                    if (actions != null && actions.length() > 0) {
                        executeAutomationPlan(actions)
                    }
                    val response = data?.optString("response")
                    if (!response.isNullOrBlank()) {
                        speakText(response)
                    }
                }
                "pong" -> { /* heartbeat ack */ }
                "error" -> Log.w(TAG, "WS error: ${json.optString("message")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "WS message parse error", e)
        }
    }

    fun sendCommand(command: String) {
        wsClient?.sendCommand(command)
    }

    fun executeAutomationPlan(actions: org.json.JSONArray) {
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch {
            val plan = ActionValidator.parseActions(actions)
            val result = policyEngine.executePlan(plan, skillExecutor)
            if (!result) {
                Log.w(TAG, "Automation plan failed")
            }
            val screen = skillExecutor.lastScreenContent
            if (screen != null) {
                _lastScreenContent.value = screen
            }
        }
    }

    fun confirmAction(allowed: Boolean) {
        val result = if (allowed) ConfirmationResult.ALLOWED else ConfirmationResult.DENIED
        _confirmationResult?.complete(result)
        _confirmationResult = null
    }

    fun speakText(text: String) {
        ttsManager?.speak(text)
    }

    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val stt = sttManager ?: return
        wakeEngine?.pause()

        stt.startListening(
            onResult = { text ->
                if (authManager.isAuthenticated) {
                    sendCommand(text)
                }
                onResult(text)
            },
            onPartialResult = { },
            onError = { error ->
                onError(error)
            }
        )
    }

    fun stopListening() {
        sttManager?.stopListening()
    }

    fun toggleWakeWord() {
        if (wakeEngine == null) return
        if (isWakeWordActive()) {
            wakeEngine?.stop()
        } else {
            wakeEngine?.startMonitoring()
        }
    }

    fun isWakeWordActive(): Boolean = wakeEngine?.isMonitoringNow == true

    fun isConnected(): Boolean = connectionManager.isConnected

    fun destroy() {
        wakeEngine?.release()
        sttManager?.release()
        ttsManager?.shutdown()
        wsClient?.disconnect()
        confirmationManager.destroy()
        initialized.set(false)
        instance = null
    }
}
