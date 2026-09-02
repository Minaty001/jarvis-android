package com.jarvis.backend

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketClient(
    private val baseUrl: String,
    private val onMessageReceived: ((String) -> Unit)? = null,
    private val onConnected: (() -> Unit)? = null,
    private val onDisconnected: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "WebSocketClient"
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isConnected = false

    fun connect(token: String, deviceId: String) {
        if (isConnected) return

        val cleanUrl = baseUrl.trim().trimEnd('/')
        val url = "$cleanUrl/ws?device=$deviceId&token=$token"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                isConnected = true
                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                onMessageReceived?.invoke(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $reason ($code)")
                webSocket.close(1000, null)
                isConnected = false
                onDisconnected?.invoke()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $reason ($code)")
                isConnected = false
                onDisconnected?.invoke()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                isConnected = false
                onDisconnected?.invoke()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
    }

    fun sendCommand(command: String) {
        if (!isConnected) {
            Log.w(TAG, "Cannot send command: WebSocket not connected")
            return
        }

        val message = """
            {
                "type": "command",
                "command": "$command"
            }
        """.trimIndent()

        webSocket?.send(message)
    }

    fun sendPing() {
        if (!isConnected) return

        val message = """
            {
                "type": "ping"
            }
        """.trimIndent()

        webSocket?.send(message)
    }

    fun isConnected(): Boolean = isConnected
}
