package com.jarvis.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

enum class AudioFocusResult {
    GRANTED,
    DENIED,
    TRANSIENT,
    TRANSIENT_MAY_DUCK
}

enum class AudioSessionState {
    IDLE,
    RECORDING,
    PLAYING,
    DUCKED
}

class AudioSessionManager(context: Context) {
    companion object {
        private const val TAG = "AudioSessionManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { focusChange ->
            handleFocusChange(focusChange)
        }
        .build()

    private var currentState = AudioSessionState.IDLE
    private var onStateChanged: ((AudioSessionState) -> Unit)? = null

    fun requestFocus(): AudioFocusResult {
        val result = audioManager.requestAudioFocus(focusRequest)
        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                setState(AudioSessionState.IDLE)
                AudioFocusResult.GRANTED
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> AudioFocusResult.DENIED
            else -> AudioFocusResult.DENIED
        }
    }

    fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        setState(AudioSessionState.IDLE)
        Log.i(TAG, "Audio focus abandoned")
    }

    fun startRecording() {
        setState(AudioSessionState.RECORDING)
    }

    fun stopRecording() {
        setState(AudioSessionState.IDLE)
    }

    fun startPlayback() {
        setState(AudioSessionState.PLAYING)
    }

    fun stopPlayback() {
        setState(AudioSessionState.IDLE)
    }

    fun isRecording(): Boolean = currentState == AudioSessionState.RECORDING

    fun isPlaying(): Boolean = currentState == AudioSessionState.PLAYING

    fun getState(): AudioSessionState = currentState

    fun setOnStateChangedListener(listener: (AudioSessionState) -> Unit) {
        onStateChanged = listener
    }

    private fun setState(state: AudioSessionState) {
        if (currentState != state) {
            currentState = state
            onStateChanged?.invoke(state)
            Log.d(TAG, "Audio session state: $state")
        }
    }

    private fun handleFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus lost")
                setState(AudioSessionState.IDLE)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "Audio focus lost transient")
                setState(AudioSessionState.DUCKED)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus can duck")
                setState(AudioSessionState.DUCKED)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                setState(AudioSessionState.IDLE)
            }
        }
    }

    fun release() {
        abandonFocus()
        onStateChanged = null
    }
}
