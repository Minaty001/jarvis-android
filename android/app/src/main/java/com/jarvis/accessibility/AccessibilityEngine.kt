package com.jarvis.accessibility

import android.util.Log
import com.jarvis.automation.JarvisAccessibilityService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(ExperimentalCoroutinesApi::class)

enum class AccessibilityState {
    UNAVAILABLE,
    CONNECTED,
    ERROR
}

class AccessibilityEngine {
    companion object {
        private const val TAG = "AccessibilityEngine"
        private const val DEFAULT_TIMEOUT_MS = 10_000L
    }

    private val _state = MutableStateFlow(AccessibilityState.UNAVAILABLE)
    val state: StateFlow<AccessibilityState> = _state.asStateFlow()

    private var service: JarvisAccessibilityService? = null

    fun connect() {
        service = JarvisAccessibilityService.instance
        _state.value = if (service != null) AccessibilityState.CONNECTED else AccessibilityState.UNAVAILABLE
        Log.d(TAG, "Accessibility engine connected: ${_state.value}")
    }

    fun disconnect() {
        service = null
        _state.value = AccessibilityState.UNAVAILABLE
    }

    fun isConnected(): Boolean = service != null && _state.value == AccessibilityState.CONNECTED

    suspend fun tap(x: Float, y: Float): Boolean = withContext(Dispatchers.Main) {
        service?.tap(x, y) ?: false
    }

    suspend fun longPress(x: Float, y: Float, durationMs: Long = 1500): Boolean = withContext(Dispatchers.Main) {
        service?.longPress(x, y, durationMs) ?: false
    }

    suspend fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean = withContext(Dispatchers.Main) {
        service?.swipe(startX, startY, endX, endY, durationMs) ?: false
    }

    suspend fun pressBack(): Boolean = withContext(Dispatchers.Main) {
        service?.pressBack() ?: false
    }

    suspend fun pressHome(): Boolean = withContext(Dispatchers.Main) {
        service?.pressHome() ?: false
    }

    suspend fun clickByText(text: String): Boolean = withContext(Dispatchers.Main) {
        service?.clickByText(text) ?: false
    }

    suspend fun setTextByText(targetText: String, newText: String): Boolean = withContext(Dispatchers.Main) {
        service?.setTextByFind(targetText, newText) ?: false
    }

    suspend fun setTextByViewId(viewId: String, text: String): Boolean = withContext(Dispatchers.Main) {
        service?.setTextByViewId(viewId, text) ?: false
    }

    suspend fun getScreenContent(): String = withContext(Dispatchers.Main) {
        service?.getScreenContent() ?: ""
    }

    suspend fun waitForText(text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean = withContext(Dispatchers.Main) {
        service?.waitForText(text, timeoutMs) ?: false
    }

    suspend fun screenshot(): Any? = withContext(Dispatchers.Main) {
        val svc = service ?: return@withContext null
        suspendCancellableCoroutine { cont ->
            svc.takeScreenshotIfAvailable { result ->
                if (cont.isActive) cont.resume(result) {}
            }
        }
    }
}
