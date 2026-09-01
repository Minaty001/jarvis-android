package com.jarvis.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class TtsManager(private val context: Context) {
    companion object {
        private const val TAG = "TtsManager"
        private const val MAX_CHUNK_SIZE = 4000
    }

    private var tts: TextToSpeech? = null
    private val pendingQueue = mutableListOf<String>()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    fun initialize(onReady: () -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = tts?.setLanguage(Locale.US)
                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "US English not supported, trying default locale")
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        Log.e(TAG, "TTS error for utterance: $utteranceId")
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                        Log.e(TAG, "TTS error code $errorCode for utterance: $utteranceId")
                    }
                })

                _isReady.value = true
                Log.d(TAG, "TTS initialized successfully")
                onReady()

                // Flush any queued speech
                if (pendingQueue.isNotEmpty()) {
                    pendingQueue.toList().forEach { speakInternal(it) }
                    pendingQueue.clear()
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!_isReady.value) {
            Log.w(TAG, "TTS not ready, queuing: $text")
            pendingQueue.add(text)
            return
        }
        speakInternal(text)
    }

    private fun speakInternal(text: String) {
        if (text.length > MAX_CHUNK_SIZE) {
            val chunks = splitTextIntoChunks(text, MAX_CHUNK_SIZE)
            chunks.forEachIndexed { index, chunk ->
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val params = Bundle()
                tts?.speak(chunk, queueMode, params, "chunk_${index}_${UUID.randomUUID()}")
            }
        } else {
            val params = Bundle()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
        }
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        stop()
        pendingQueue.clear()
        tts?.shutdown()
        tts = null
        _isReady.value = false
    }

    /**
     * Splits text into chunks at sentence boundaries to avoid cutting words mid-speech.
     * Falls back to last space if no sentence boundary found within maxSize.
     */
    private fun splitTextIntoChunks(text: String, maxSize: Int): List<String> {
        val chunks = mutableListOf<String>()
        var remaining = text.trim()

        while (remaining.length > maxSize) {
            val searchRange = remaining.substring(0, maxSize)
            // Prefer splitting at sentence boundaries
            val breakPoint = searchRange.lastIndexOf(". ")
                .coerceAtLeast(searchRange.lastIndexOf("! "))
                .coerceAtLeast(searchRange.lastIndexOf("? "))
                .coerceAtLeast(searchRange.lastIndexOf(", "))
                .coerceAtLeast(searchRange.lastIndexOf(" "))
                .let { if (it <= 0) maxSize else it + 1 }

            chunks.add(remaining.substring(0, breakPoint).trim())
            remaining = remaining.substring(breakPoint).trim()
        }

        if (remaining.isNotBlank()) {
            chunks.add(remaining)
        }

        return chunks
    }
}
