package com.jarvis.tts

import kotlinx.coroutines.flow.StateFlow

enum class TtsState {
    UNINITIALIZED,
    LOADING,
    READY,
    SPEAKING,
    ERROR
}

interface TtsEngine {
    val state: StateFlow<TtsState>
    val isReady: StateFlow<Boolean>
    val isSpeaking: StateFlow<Boolean>

    fun initialize(onReady: () -> Unit = {})
    fun speak(text: String)
    fun stop()
    fun shutdown()
}
