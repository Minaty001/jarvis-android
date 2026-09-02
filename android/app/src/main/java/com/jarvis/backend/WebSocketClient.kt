package com.jarvis.backend

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketClient(
    var wsUrl: String,
    val connectionManager: ConnectionManager = ConnectionManager(),
    var sessionId: String? = null,
    private val authTokenManager: AuthTokenManager? = null,
    private val onMessageReceived: ((String) -> Unit)? = null,
    private val onConnected: (() -> Unit)? = null,
    private val onDisconnected: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "WebSocketClient"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    private var client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    @Volatile private var disconnectRequested = false

    fun updateUrl(newWsUrl: String) {
        val changed = wsUrl != newWsUrl.trim()
        wsUrl = newWsUrl.trim()
        if (changed && webSocket != null) {
            disconnect()
            connect()
        }
    }

    fun reconnect() {
        disconnect()
        connect()
    }

    private fun scheduleReconnect() {
        if (disconnectRequested) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            connectionManager.setConnectionState(ConnectionState.RECONNECTING)
            reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(5)
            val delayMs = (1000L * (1L shl reconnectAttempts)).coerceIn(1500L, 8000L)
            delay(delayMs)
            connect()
        }
    }

    fun connect() {
        disconnectRequested = false
        reconnectJob?.cancel()

        val baseUrl = sessionId?.let { sid ->
            if (wsUrl.contains("session_id=")) wsUrl
            else if (wsUrl.contains("?")) "$wsUrl&session_id=$sid"
            else "$wsUrl?session_id=$sid"
        } ?: wsUrl

        val targetUrl = authTokenManager?.accessToken?.let { token ->
            if (!authTokenManager.isTokenExpired(token)) {
                val separator = if (baseUrl.contains("?")) "&" else "?"
                "$baseUrl${separator}token=$token"
            } else {
                baseUrl
            }
        } ?: baseUrl

        Log.i(TAG, "Connecting to ${targetUrl.substringBefore("?")}...")
        connectionManager.setConnectionState(ConnectionState.CONNECTING)
        val request = Request.Builder().url(targetUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                reconnectAttempts = 0
                connectionManager.onConnected()
                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                onMessageReceived?.invoke(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}. Scheduling reconnect...")
                connectionManager.onDisconnected()
                onDisconnected?.invoke()
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $reason ($code)")
                connectionManager.onDisconnected()
                onDisconnected?.invoke()
                if (!disconnectRequested) scheduleReconnect()
            }
        })
    }

    fun sendCommand(command: String, userId: String = "") {
        val msg = JSONObject().apply {
            put("type", "command")
            put("command", command)
            if (userId.isNotBlank()) put("userId", userId)
        }
        val sent = webSocket?.send(msg.toString()) ?: false
        if (!sent) Log.w(TAG, "Failed to send command (socket disconnected)")
    }

    fun sendPing() {
        val msg = JSONObject().apply { put("type", "ping") }
        webSocket?.send(msg.toString())
    }

    fun disconnect() {
        disconnectRequested = true
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        connectionManager.onDisconnected()
    }

    fun isConnected() = connectionManager.isConnected
}
