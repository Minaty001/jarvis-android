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

class NativeSttManager(private val context: Context) {
    companion object {
        private const val TAG = "NativeSttManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isListening = false

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun initialize(onReady: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!isAvailable) {
            Log.w(TAG, "Speech recognition unavailable on this device")
            onError("Speech recognition unavailable")
            return
        }
        Log.d(TAG, "Native SpeechRecognizer initialized")
        onReady()
    }

    fun startListening(
        onResult: (String) -> Unit,
        onPartialResult: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        if (!isAvailable) {
            Log.w(TAG, "Speech recognition unavailable")
            onError("Speech recognition unavailable")
            return false
        }
        if (isListening) return true

        mainHandler.post {
            try {
                stopListeningInternal()

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListening = true
                            Log.d(TAG, "Ready for speech")
                        }

                        override fun onBeginningOfSpeech() {
                            Log.d(TAG, "Speech started")
                        }

                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            isListening = false
                            Log.d(TAG, "Speech ended")
                        }

                        override fun onError(error: Int) {
                            isListening = false
                            val message = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out"
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission required"
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection error"
                                else -> "Speech recognition error ($error)"
                            }
                            Log.e(TAG, "SpeechRecognizer error: $message (code $error)")
                            onError(message)
                        }

                        override fun onResults(results: Bundle?) {
                            isListening = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val recognizedText = matches[0]
                                Log.d(TAG, "Recognized text: $recognizedText")
                                onResult(recognizedText)
                            } else {
                                onError("No speech detected")
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                onPartialResult(matches[0])
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Log.e(TAG, "Error starting native SpeechRecognizer", e)
                isListening = false
                onError(e.message ?: "Failed to start speech recognition")
            }
        }
        return true
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping SpeechRecognizer", e)
        } finally {
            speechRecognizer = null
            isListening = false
        }
    }

    fun stopListening() {
        mainHandler.post { stopListeningInternal() }
    }

    fun release() {
        stopListening()
    }
}
