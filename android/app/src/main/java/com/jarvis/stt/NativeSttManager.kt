package com.jarvis.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.jarvis.core.PerformanceMonitor

class NativeSttManager(private val context: Context) : SttEngine {
    companion object {
        private const val TAG = "NativeSttManager"
        private val RETRYABLE_ERRORS = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        )
        private var sttListenStart = 0L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var _isListening = false
    override val isListening: Boolean get() = _isListening

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    private var currentOnResult: ((String) -> Unit)? = null
    private var currentOnPartial: ((String) -> Unit)? = null
    private var currentOnError: ((String) -> Unit)? = null

    override fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        if (!isAvailable) {
            Log.w(TAG, "Speech recognition unavailable on this device")
            onError("Speech recognition unavailable on this device")
            return
        }
        Log.d(TAG, "Native SpeechRecognizer ready")
        onReady()
    }

    fun startListening(
        onResult: (String) -> Unit,
        onPartialResult: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        if (!isAvailable) {
            onError("Speech recognition unavailable")
            return false
        }

        mainHandler.post {
            if (_isListening) return@post

            currentOnResult = onResult
            currentOnPartial = onPartialResult
            currentOnError = onError

            destroyRecognizer()
            createAndStartRecognizer()
        }
        return true
    }

    override fun listen(
        onResult: (SttResult) -> Unit,
        onPartialResult: (SttResult) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        return startListening(
            onResult = { text -> onResult(SttResult(text)) },
            onPartialResult = { text -> onPartialResult(SttResult(text, isPartial = true)) },
            onError = onError
        )
    }

    override fun cancel() {
        stopListening()
    }

    private fun createAndStartRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(buildListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
            _isListening = true
            sttListenStart = PerformanceMonitor.startTimer("stt_listen")
            Log.d(TAG, "SpeechRecognizer started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            _isListening = false
            currentOnError?.invoke(e.message ?: "Failed to start speech recognition")
        }
    }

    private fun buildListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech input begun")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _isListening = false
            Log.d(TAG, "End of speech")
        }

        override fun onError(error: Int) {
            _isListening = false
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH         -> "No speech detected — try again"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT   -> "Listening timed out"
                SpeechRecognizer.ERROR_AUDIO            -> "Audio recording error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK          -> "Network error during recognition"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT  -> "Network timeout"
                SpeechRecognizer.ERROR_SERVER           -> "Recognition server error"
                SpeechRecognizer.ERROR_CLIENT           -> "Client error — please try again"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY  -> "Recognizer busy"
                else -> "Speech recognition error (code $error)"
            }
            Log.e(TAG, "SpeechRecognizer error: $message")
            currentOnError?.invoke(message)
        }

        override fun onResults(results: Bundle?) {
            _isListening = false
            PerformanceMonitor.endTimer("stt_listen", sttListenStart)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Log.d(TAG, "Recognized: $text")
                currentOnResult?.invoke(text)
            } else {
                currentOnError?.invoke("No speech detected")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                currentOnPartial?.invoke(matches[0])
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun stopListening() {
        mainHandler.post {
            _isListening = false
            speechRecognizer?.stopListening()
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying SpeechRecognizer: ${e.message}")
        } finally {
            speechRecognizer = null
            _isListening = false
        }
    }

    override fun release() {
        mainHandler.post {
            destroyRecognizer()
            currentOnResult = null
            currentOnPartial = null
            currentOnError = null
        }
    }
}
