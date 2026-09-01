package com.jarvis.wakeword

import android.content.Context
import android.util.Log
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.WakeWordModel
import com.rementia.openwakeword.lib.model.DetectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class WakeWordDetection(
    val modelName: String,
    val score: Float,
    val timestamp: Long = System.currentTimeMillis()
)

class WakeWordManager(private val context: Context) {
    companion object {
        private const val TAG = "WakeWordManager"
        private const val DETECTION_COOLDOWN_MS = 3000L
    }

    private var engine: WakeWordEngine? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _detections = MutableSharedFlow<WakeWordDetection>(extraBufferCapacity = 10)
    val detections: SharedFlow<WakeWordDetection> = _detections.asSharedFlow()

    private var isActive = false

    fun start() {
        if (isActive) return

        val models = listOf(
            WakeWordModel(
                name = "hey_jarvis",
                modelPath = "jarvis.onnx"
            )
        )

        engine = WakeWordEngine(
            context = context,
            models = models,
            detectionMode = DetectionMode.SINGLE_BEST,
            detectionCooldownMs = DETECTION_COOLDOWN_MS,
            scope = scope
        )

        scope.launch {
            engine?.detections?.collect { detection ->
                Log.d(TAG, "Wake word detected: ${detection.model.name} (score: ${detection.score})")
                _detections.emit(
                    WakeWordDetection(
                        modelName = detection.model.name,
                        score = detection.score
                    )
                )
            }
        }

        engine?.start()
        isActive = true
        Log.d(TAG, "Wake word detection started")
    }

    fun stop() {
        engine?.stop()
        isActive = false
        Log.d(TAG, "Wake word detection stopped")
    }

    fun release() {
        stop()
        engine?.release()
        engine = null
    }

    fun isRunning() = isActive
}
