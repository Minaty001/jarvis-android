package com.jarvis.backend

import android.util.Log

enum class ConnectionState {
    CONNECTED, DISCONNECTED, CONNECTING, RECONNECTING
}

class ConnectionManager {
    var onStateChanged: ((ConnectionState) -> Unit)? = null

    var state: ConnectionState = ConnectionState.DISCONNECTED
        private set

    val isConnected: Boolean
        get() = state == ConnectionState.CONNECTED

    fun setConnectionState(newState: ConnectionState) {
        state = newState
        Log.i("ConnectionManager", "Network state: $newState")
        onStateChanged?.invoke(newState)
    }

    fun onConnected() {
        state = ConnectionState.CONNECTED
        Log.i("ConnectionManager", "Network state: CONNECTED")
        onStateChanged?.invoke(ConnectionState.CONNECTED)
    }

    fun onDisconnected() {
        state = ConnectionState.DISCONNECTED
        Log.i("ConnectionManager", "Network state: DISCONNECTED")
        onStateChanged?.invoke(ConnectionState.DISCONNECTED)
    }
}
