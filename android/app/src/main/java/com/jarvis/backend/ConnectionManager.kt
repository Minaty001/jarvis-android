package com.jarvis.backend

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    AUTH_FAILED,
    STOPPED
}

class ConnectionManager(
    private val maxReconnectAttempts: Int = 5,
    private val baseReconnectDelay: Long = 1000L
) {
    companion object {
        private const val TAG = "ConnectionManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()
    val isConnected: Boolean get() = _state.value == ConnectionState.CONNECTED

    private var onStateChange: ((ConnectionState) -> Unit)? = null
    private var onReconnect: (() -> Unit)? = null

    fun setOnStateChangeListener(listener: (ConnectionState) -> Unit) {
        onStateChange = listener
    }

    fun setOnReconnectListener(listener: () -> Unit) {
        onReconnect = listener
    }

    fun onConnected() {
        _state.value = ConnectionState.CONNECTED
        reconnectAttempts = 0
        onStateChange?.invoke(ConnectionState.CONNECTED)
        Log.i(TAG, "Connection state: CONNECTED")
    }

    fun onDisconnected() {
        _state.value = ConnectionState.DISCONNECTED
        onStateChange?.invoke(ConnectionState.DISCONNECTED)
        Log.i(TAG, "Connection state: DISCONNECTED")
    }

    fun onAuthFailed() {
        _state.value = ConnectionState.AUTH_FAILED
        onStateChange?.invoke(ConnectionState.AUTH_FAILED)
        Log.w(TAG, "Connection state: AUTH_FAILED")
    }

    fun startReconnect() {
        if (reconnectJob?.isActive == true) return
        if (_state.value == ConnectionState.STOPPED) return

        _state.value = ConnectionState.RECONNECTING
        onStateChange?.invoke(ConnectionState.RECONNECTING)

        reconnectJob = scope.launch {
            val delayMs = baseReconnectDelay * (1L shl reconnectAttempts.coerceAtMost(5))
            delay(delayMs)
            reconnectAttempts++
            onReconnect?.invoke()
        }
    }

    fun stopReconnect() {
        reconnectJob?.cancel()
        _state.value = ConnectionState.STOPPED
        onStateChange?.invoke(ConnectionState.STOPPED)
        Log.i(TAG, "Connection state: STOPPED")
    }

    fun reset() {
        reconnectJob?.cancel()
        reconnectAttempts = 0
        _state.value = ConnectionState.DISCONNECTED
    }
}
