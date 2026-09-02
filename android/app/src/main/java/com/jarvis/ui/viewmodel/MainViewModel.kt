package com.jarvis.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.auth.AuthState
import com.jarvis.automation.ConfirmationRequest
import com.jarvis.automation.ConfirmationResult
import com.jarvis.backend.ConnectionState
import com.jarvis.assistant.AssistantRuntime
import com.jarvis.assistant.RuntimeState
import com.jarvis.voice.VoiceState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val runtimeState: RuntimeState = RuntimeState.UNINITIALIZED,
    val authState: AuthState = AuthState.Loading,
    val voiceState: VoiceState = VoiceState.OFF,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isConnected: Boolean = false,
    val chatMessages: List<Pair<String, Boolean>> = emptyList(),
    val lastScreenContent: String? = null,
    val confirmationRequest: ConfirmationRequest? = null,
    val isAutomationEnabled: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    val runtime = AssistantRuntime.getInstance(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                runtime.runtimeState,
                runtime.authManager.state,
                runtime.connectionManager.connectionState
            ) { runtimeState, authState, connState ->
                _uiState.value.copy(
                    runtimeState = runtimeState,
                    authState = authState,
                    connectionState = connState,
                    isConnected = connState == ConnectionState.CONNECTED
                )
            }.collect { newState -> _uiState.value = newState }
        }

        viewModelScope.launch {
            runtime.confirmationRequest.collect { request ->
                _uiState.value = _uiState.value.copy(confirmationRequest = request)
            }
        }

        viewModelScope.launch {
            runtime.lastScreenContent.collect { content ->
                _uiState.value = _uiState.value.copy(lastScreenContent = content)
            }
        }
    }

    fun initialize() {
        viewModelScope.launch {
            runtime.initialize()
            runtime.bootstrapAndConnect()
            _uiState.value = _uiState.value.copy(
                isAutomationEnabled = runtime.automationController.isAccessibilityEnabled
            )
        }
    }

    fun sendCommand(command: String) {
        _uiState.value = _uiState.value.copy(
            chatMessages = _uiState.value.chatMessages + (command to true)
        )
        runtime.sendCommand(command)
    }

    fun onWsResponse(response: String) {
        _uiState.value = _uiState.value.copy(
            chatMessages = _uiState.value.chatMessages + (response to false)
        )
        runtime.speakText(response)
    }

    fun toggleWakeWord() {
        runtime.toggleWakeWord()
    }

    fun startListening() {
        runtime.startListening(
            onResult = { /* command sent via runtime */ },
            onError = { error ->
                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + ("Error: $error" to false)
                )
            }
        )
    }

    fun stopListening() {
        runtime.stopListening()
    }

    fun confirmAction(allowed: Boolean) {
        runtime.confirmAction(allowed)
        _uiState.value = _uiState.value.copy(confirmationRequest = null)
    }

    fun openAccessibilitySettings() {
        runtime.automationController.openAccessibilitySettings()
    }

    fun refreshAutomationState() {
        _uiState.value = _uiState.value.copy(
            isAutomationEnabled = runtime.automationController.isAccessibilityEnabled
        )
    }

    override fun onCleared() {
        super.onCleared()
        runtime.destroy()
    }
}
