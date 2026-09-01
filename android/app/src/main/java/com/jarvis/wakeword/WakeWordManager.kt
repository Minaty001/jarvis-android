package com.jarvis.wakeword

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class WakeWordDetection(
    val modelName: String,
    val score: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Wake word detector using Android's built-in SpeechRecognizer in a continuous loop.
 * Listens for "hey jarvis" or "jarvis" in transcripts without any ML model file required.
 * Runs entirely on-device via the system speech recognition engine.
 */
class WakeWordManager(private val context: Context) {
    companion object {
        private const val TAG = "WakeWordManager"
        private val WAKE_KEYWORDS = listOf("hey jarvis", "jarvis", "hey davis", "hey travis")
        private const val RESTART_DELAY_MS = 500L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _detections = MutableSharedFlow<WakeWordDetection>(extraBufferCapacity = 10)
    val detections: SharedFlow<WakeWordDetection> = _detections.asSharedFlow()

    private var isActive = false
    private var onDetectedCallback: (() -> Unit)? = null

    fun start(): Boolean {
        if (isActive) return true
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer unavailable — wake word detection disabled")
            return false
        }
        isActive = true
        Log.d(TAG, "Wake word detection started (keyword: ${WAKE_KEYWORDS.first()})")
        mainHandler.post { startLoop() }
        return true
    }

    private fun startLoop() {
        if (!isActive) return

        destroyRecognizer()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onEndOfSpeech() {}

                override fun onPartialResults(partialResults: Bundle?) {
                    checkForWakeWord(partialResults)
                }

                override fun onResults(results: Bundle?) {
                    val triggered = checkForWakeWord(results)
                    if (!triggered && isActive) {
                        // Restart loop after a short delay
                        mainHandler.postDelayed({ startLoop() }, RESTART_DELAY_MS)
                    }
                }

                override fun onError(error: Int) {
                    if (!isActive) return
                    // On any error just restart the loop
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
                        SpeechRecognizer.ERROR_AUDIO -> 1000L
                        else -> RESTART_DELAY_MS
                    }
                    Log.d(TAG, "Wake word loop restarting after error code $error")
                    mainHandler.postDelayed({ startLoop() }, delay)
                }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting wake word loop", e)
            if (isActive) mainHandler.postDelayed({ startLoop() }, 1000L)
        }
    }

    /**
     * Returns true if a wake keyword was found and callback was fired.
     */
    private fun checkForWakeWord(bundle: Bundle?): Boolean {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        for (match in matches) {
            val lower = match.lowercase()
            if (WAKE_KEYWORDS.any { lower.contains(it) }) {
                Log.d(TAG, "Wake word detected in: \"$match\"")
                _detections.tryEmit(WakeWordDetection(modelName = "keyword_match", score = 1.0f))
                onDetectedCallback?.invoke()
                // Delay before restarting to avoid double-trigger
                if (isActive) mainHandler.postDelayed({ startLoop() }, 2000L)
                return true
            }
        }
        return false
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun stop() {
        isActive = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post { destroyRecognizer() }
        Log.d(TAG, "Wake word detection stopped")
    }

    fun release() {
        stop()
    }

    fun isRunning() = isActive
}
