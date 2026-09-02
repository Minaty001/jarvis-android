package com.jarvis.stt

data class SttResult(
    val text: String,
    val isPartial: Boolean = false,
    val confidence: Float = 1.0f
)

interface SttEngine {
    val isAvailable: Boolean
    val isListening: Boolean

    fun initialize(onReady: () -> Unit = {}, onError: (String) -> Unit = {})

    fun listen(
        onResult: (SttResult) -> Unit,
        onPartialResult: (SttResult) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean

    fun cancel()
    fun release()
}
