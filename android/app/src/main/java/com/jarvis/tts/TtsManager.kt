package com.jarvis.tts

import android.content.Context
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
                    tts?.setLanguage(Locale.US)
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
                    }
                })

                _isReady.value = true
                Log.d(TAG, "TTS initialized")
                onReady()
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
            }
        }
    }

    fun speak(text: String) {
        if (!_isReady.value) {
            Log.w(TAG, "TTS not ready")
            return
        }

        if (text.length > MAX_CHUNK_SIZE) {
            val chunks = splitTextIntoChunks(text, MAX_CHUNK_SIZE)
            chunks.forEachIndexed { index, chunk ->
                tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, "chunk_$index")
            }
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
    }

    private fun splitTextIntoChunks(text: String, maxSize: Int): List<String> {
        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.length > maxSize) {
            val breakPoint = remaining.lastIndexOf(". ", maxSize)
                .coerceAtLeast(remaining.lastIndexOf("! ", maxSize))
                .coerceAtLeast(remaining.lastIndexOf("? ", maxSize))
                .coerceAtLeast(remaining.lastIndexOf(", ", maxSize))
                .coerceAtLeast(maxSize)

            chunks.add(remaining.substring(0, breakPoint + 1).trim())
            remaining = remaining.substring(breakPoint + 1).trim()
        }

        if (remaining.isNotBlank()) {
            chunks.add(remaining)
        }

        return chunks
    }
}
