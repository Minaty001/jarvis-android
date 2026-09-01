package com.jarvis.backend

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketClient(
    private val url: String,
    private val deviceId: String,
    private val onMessage: (String) -> Unit,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {}
) {
    @Volatile private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    @Volatile private var isConnected = false
    private var reconnectJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect() {
        val request = Request.Builder()
            .url("$url?device=$deviceId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                mainHandler.post { onConnected() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                mainHandler.post { onMessage(text) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                isConnected = false
                mainHandler.post { onDisconnected() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                mainHandler.post { onDisconnected() }
                scheduleReconnect()
            }
        })
    }

    fun sendCommand(command: String, userId: String = deviceId) {
        val msg = JSONObject().apply {
            put("type", "command")
            put("command", command)
            put("userId", userId)
        }
        webSocket?.send(msg.toString())
    }

    fun sendPing() {
        val msg = JSONObject().apply {
            put("type", "ping")
        }
        webSocket?.send(msg.toString())
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnect")
        isConnected = false
    }

    fun isConnected() = isConnected

    private fun scheduleReconnect() {
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            delay(5000)
            if (!isConnected) connect()
        }
    }
}
