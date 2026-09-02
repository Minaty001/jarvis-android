package com.jarvis.runtime

import android.util.Log
import com.jarvis.stt.NativeSttManager
import com.jarvis.tts.TtsManager
import com.jarvis.wakeword.LiveKitWakeWordEngine
import com.jarvis.wakeword.OnnxWakeWordDetector
import com.jarvis.wakeword.WakeWordConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceState {
    OFF,
    INITIALIZING,
    READY,
    WAKE_LISTENING,
    COMMAND_LISTENING,
    PROCESSING,
    CONFIRMING,
    SPEAKING,
    ERROR
}

enum class MicOwner {
    NONE,
    WAKE,
    COMMAND,
    TTS
}

class VoiceRuntime(private val scope: CoroutineScope) {
    companion object {
        private const val TAG = "VoiceRuntime"
    }

    private val _state = MutableStateFlow(VoiceState.OFF)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _micOwner = MutableStateFlow(MicOwner.NONE)
    val micOwner: StateFlow<MicOwner> = _micOwner.asStateFlow()

    private var ttsManager: TtsManager? = null
    private var sttManager: NativeSttManager? = null
    private var wakeEngine: LiveKitWakeWordEngine? = null

    private var onCommandDetected: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    fun initialize(
        tts: TtsManager,
        stt: NativeSttManager,
        wake: LiveKitWakeWordEngine?,
        onCommand: (String) -> Unit,
        onErr: (String) -> Unit
    ) {
        ttsManager = tts
        sttManager = stt
        wakeEngine = wake
        onCommandDetected = onCommand
        onError = onErr
        _state.value = VoiceState.READY
        Log.i(TAG, "VoiceRuntime initialized")
    }

    fun startWakeListening(): Boolean {
        if (_state.value != VoiceState.READY && _state.value != VoiceState.WAKE_LISTENING) {
            Log.w(TAG, "Cannot start wake: state=${_state.value}")
            return false
        }
        val wake = wakeEngine ?: return false
        if (!wake.isAvailable) {
            Log.w(TAG, "Wake engine unavailable")
            _state.value = VoiceState.ERROR
            onError?.invoke("Wake word engine unavailable")
            return false
        }

        if (_micOwner.value != MicOwner.NONE && _micOwner.value != MicOwner.WAKE) {
            Log.w(TAG, "Mic owned by ${_micOwner.value}, cannot start wake")
            return false
        }

        wake.setOnWakeListener {
            scope.launch(Dispatchers.Main) {
                handleWakeDetected()
            }
        }
        wake.setOnErrorListener { e ->
            scope.launch(Dispatchers.Main) {
                _state.value = VoiceState.ERROR
                onError?.invoke(e.message ?: "Wake error")
            }
        }

        val started = wake.startMonitoring()
        if (started) {
            _state.value = VoiceState.WAKE_LISTENING
            _micOwner.value = MicOwner.WAKE
            Log.i(TAG, "Wake listening started")
        }
        return started
    }

    fun stopWakeListening() {
        if (_micOwner.value == MicOwner.WAKE) {
            wakeEngine?.stop()
            _micOwner.value = MicOwner.NONE
            if (_state.value == VoiceState.WAKE_LISTENING) {
                _state.value = VoiceState.READY
            }
            Log.i(TAG, "Wake listening stopped")
        }
    }

    private suspend fun handleWakeDetected() {
        if (_state.value != VoiceState.WAKE_LISTENING) return
        Log.i(TAG, "Wake detected → switching to COMMAND_LISTENING")
        wakeEngine?.pause()
        _micOwner.value = MicOwner.COMMAND
        _state.value = VoiceState.COMMAND_LISTENING
        startSttListening()
    }

    fun startCommandListening(): Boolean {
        if (_state.value != VoiceState.READY && _state.value != VoiceState.WAKE_LISTENING) {
            Log.w(TAG, "Cannot start command: state=${_state.value}")
            return false
        }
        if (_micOwner.value != MicOwner.NONE && _micOwner.value != MicOwner.WAKE) {
            Log.w(TAG, "Mic owned by ${_micOwner.value}")
            return false
        }

        if (_micOwner.value == MicOwner.WAKE) {
            wakeEngine?.pause()
        }

        _micOwner.value = MicOwner.COMMAND
        _state.value = VoiceState.COMMAND_LISTENING
        startSttListening()
        return true
    }

    private fun startSttListening() {
        val stt = sttManager ?: return
        stt.startListening(
            onResult = { text ->
                scope.launch(Dispatchers.Main) {
                    _state.value = VoiceState.PROCESSING
                    onCommandDetected?.invoke(text)
                }
            },
            onPartialResult = { },
            onError = { error ->
                scope.launch(Dispatchers.Main) {
                    Log.w(TAG, "STT error: $error")
                    returnToIdle()
                    onError?.invoke(error)
                }
            }
        )
    }

    fun stopCommandListening() {
        sttManager?.stopListening()
        if (_micOwner.value == MicOwner.COMMAND) {
            returnToIdle()
        }
    }

    fun startSpeaking(text: String) {
        _state.value = VoiceState.SPEAKING
        _micOwner.value = MicOwner.TTS
        ttsManager?.speak(text)
        scope.launch {
            delay(500)
            returnToIdle()
        }
    }

    fun returnToIdle() {
        _micOwner.value = MicOwner.NONE
        if (wakeEngine?.isMonitoringNow == true) {
            wakeEngine?.resume()
            _state.value = VoiceState.WAKE_LISTENING
            _micOwner.value = MicOwner.WAKE
        } else {
            _state.value = VoiceState.READY
        }
    }

    fun release() {
        wakeEngine?.release()
        sttManager?.release()
        ttsManager?.shutdown()
        _state.value = VoiceState.OFF
        _micOwner.value = MicOwner.NONE
    }
}
