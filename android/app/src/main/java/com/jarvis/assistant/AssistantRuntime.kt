package com.jarvis.assistant

import android.content.Context
import android.util.Log
import com.jarvis.auth.AuthManager
import com.jarvis.auth.AuthState
import com.jarvis.audio.AudioSessionManager
import com.jarvis.automation.*
import com.jarvis.backend.ApiClient
import com.jarvis.backend.ConnectionManager
import com.jarvis.backend.WebSocketClient
import com.jarvis.config.Config
import com.jarvis.stt.NativeSttManager
import com.jarvis.tts.TtsManager
import com.jarvis.voice.VoiceRuntime
import com.jarvis.wakeword.LiveKitWakeWordEngine
import com.jarvis.wakeword.OnnxWakeWordDetector
import com.jarvis.wakeword.WakeWordConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

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
    val apiClient = ApiClient()

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

    private val permissionChecker = PermissionChecker(context)
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
    private var audioSessionManager: AudioSessionManager? = null
    var voiceRuntime: VoiceRuntime? = null
        private set

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
            audioSessionManager = AudioSessionManager(context)
            initWakeWord()

            val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            voiceRuntime = VoiceRuntime(scope, audioSessionManager).also {
                it.initialize(
                    tts = ttsManager!!,
                    stt = sttManager!!,
                    wake = wakeEngine,
                    onCommand = { text ->
                        if (authManager.isAuthenticated) {
                            sendCommand(text)
                        }
                    },
                    onErr = { error -> Log.w(TAG, "Voice error: $error") }
                )
            }

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
        authManager.initialize()
        when (authManager.currentState) {
            is AuthState.LoggedOut, is AuthState.NeedsEnrollment -> {
                Log.i(TAG, "Auth state: ${authManager.currentState}")
                return false
            }
            is AuthState.Error -> {
                Log.w(TAG, "Auth error: ${authManager.currentState}")
                return false
            }
            else -> {}
        }
        val authState = waitForAuthState()
        if (authState !is AuthState.Authenticated) {
            Log.w(TAG, "Auth not ready after bootstrap: $authState")
            return false
        }
        connectWebSocket()
        return true
    }

    private suspend fun waitForAuthState(): AuthState {
        var attempts = 0
        while (attempts < 30) {
            val state = authManager.currentState
            if (state is AuthState.Authenticated || state is AuthState.LoggedOut
                || state is AuthState.Error || state is AuthState.NeedsEnrollment) {
                return state
            }
            delay(200)
            attempts++
        }
        return authManager.currentState
    }

    fun connectWebSocket() {
        val token = authManager.getAccessTokenForRequest()
        val deviceId = authManager.deviceId
        if (token == null || deviceId == null) {
            Log.w(TAG, "Cannot connect WS: no token or deviceId")
            return
        }
        apiClient.authToken = token

        wsClient?.disconnect()
        wsClient = WebSocketClient(
            baseUrl = Config.BACKEND_WS_URL,
            onMessageReceived = { msg -> handleWsMessage(msg) },
            onConnected = { connectionManager.onConnected() },
            onDisconnected = { code -> handleWsDisconnected(code) },
            onAuthRejected = { handleWsAuthRejected() }
        )
        connectionManager.onConnecting()

        apiClient.getWsTicket(token, deviceId) { ticketResult ->
            if (ticketResult != null) {
                wsClient!!.connectWithTicket(ticketResult.ticket, deviceId)
            } else {
                Log.w(TAG, "Failed to get WS ticket, falling back to token")
                wsClient!!.connect(token, deviceId)
            }
        }
    }

    private fun handleWsDisconnected(code: Int) {
        connectionManager.onDisconnected()
        if (code == WebSocketClient.CLOSE_AUTH_REJECTED) return
        connectionManager.startReconnect { connectWebSocket() }
    }

    private fun handleWsAuthRejected() {
        Log.w(TAG, "WS auth rejected — attempting one refresh")
        connectionManager.onAuthFailed()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            authManager.refreshAccessToken()
            val state = authManager.currentState
            if (state is AuthState.Authenticated) {
                Log.i(TAG, "Refresh succeeded, reconnecting WS")
                delay(500)
                connectWebSocket()
            } else {
                Log.e(TAG, "Refresh failed after auth reject, logout")
                authManager.logout()
            }
        }
    }

    private fun handleWsMessage(msg: String) {
        try {
            val json = JSONObject(msg)
            val type = json.optString("type", "")
            when (type) {
                "command_response", "response" -> {
                    val data = json.optJSONObject("data")
                    val actions = data?.optJSONArray("actions") ?: json.optJSONArray("actions")
                    if (actions != null && actions.length() > 0) {
                        executeAutomationPlan(actions)
                    }
                    val response = if (data != null && data.has("response")) {
                        data.optString("response", "")
                    } else {
                        json.optString("response", "")
                    }
                    if (response.isNotBlank()) {
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
        if (wsClient?.isConnected() == true) {
            wsClient?.sendCommand(command)
        } else {
            val token = authManager.getAccessTokenForRequest()
            val deviceId = authManager.deviceId ?: "android-device"
            apiClient.sendCommand(command, token, deviceId) { result ->
                if (result != null) {
                    if (result.actions.isNotEmpty()) {
                        val actionsArr = org.json.JSONArray()
                        result.actions.forEach { act ->
                            val obj = JSONObject()
                            obj.put("type", act.type)
                            val pObj = JSONObject()
                            act.params.forEach { (k, v) -> pObj.put(k, v) }
                            obj.put("params", pObj)
                            actionsArr.put(obj)
                        }
                        executeAutomationPlan(actionsArr)
                    }
                    if (result.response.isNotBlank()) {
                        speakText(result.response)
                    }
                }
            }
        }
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
        voiceRuntime?.release()
        wakeEngine?.release()
        sttManager?.release()
        ttsManager?.shutdown()
        audioSessionManager?.release()
        wsClient?.disconnect()
        confirmationManager.destroy()
        initialized.set(false)
        instance = null
    }
}
