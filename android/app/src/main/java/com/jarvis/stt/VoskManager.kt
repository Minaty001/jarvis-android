package com.jarvis.stt

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService

class VoskManager(private val context: Context) {
    companion object {
        private const val TAG = "VoskManager"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_ASSET = "vosk-model-small-en-us-0.15"
        private const val MODEL_DIR = "model"
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var voskThread: HandlerThread? = null
    private var voskHandler: Handler? = null

    @Volatile
    private var isRecording = false

    private var onResult: ((String) -> Unit)? = null
    private var onPartialResult: ((String) -> Unit)? = null

    fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        StorageService.unpack(
            context.applicationContext,
            MODEL_ASSET,
            MODEL_DIR,
            { model ->
                this.model = model
                Log.d(TAG, "Vosk model loaded")
                onReady()
            },
            { exception ->
                Log.e(TAG, "Failed to load Vosk model", exception)
                onError(exception.message ?: "Unknown error")
            }
        )
    }

    fun startListening(
        onResult: (String) -> Unit,
        onPartialResult: (String) -> Unit
    ) {
        if (isRecording) return
        val currentModel = model ?: run {
            Log.e(TAG, "Model not initialized")
            return
        }

        this.onResult = onResult
        this.onPartialResult = onPartialResult

        recognizer = Recognizer(currentModel, SAMPLE_RATE.toFloat()).apply {
            setWords(false)
            setPartialWords(false)
        }

        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferBytes, SAMPLE_RATE * 200 / 1000 * 2)
        )

        voskThread = HandlerThread("VoskWorker").also { it.start() }
        voskHandler = Handler(voskThread!!.looper)

        isRecording = true
        audioRecord?.startRecording()

        val buffer = ShortArray(SAMPLE_RATE * 50 / 1000)

        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            while (isRecording) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readCount > 0) {
                    voskHandler?.post {
                        try {
                            if (recognizer?.acceptWaveForm(buffer, readCount) == true) {
                                val result = JSONObject(recognizer?.result ?: "{}")
                                val text = result.optString("text", "")
                                if (text.isNotBlank()) {
                                    onResult(text)
                                }
                            } else {
                                val partial = JSONObject(recognizer?.partialResult ?: "{}")
                                val partialText = partial.optString("partial", "")
                                if (partialText.isNotBlank()) {
                                    onPartialResult(partialText)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Vosk processing error", e)
                        }
                    }
                }
            }
            voskHandler?.post {
                try {
                    val finalResult = JSONObject(recognizer?.finalResult ?: "{}")
                    val text = finalResult.optString("text", "")
                    if (text.isNotBlank()) {
                        onResult(text)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Vosk final result error", e)
                }
            }
        }.start()

        Log.d(TAG, "Started listening")
    }

    fun stopListening() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recognizer?.reset()
        voskThread?.quitSafely()
        voskThread = null
        voskHandler = null
        Log.d(TAG, "Stopped listening")
    }

    fun release() {
        stopListening()
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
    }
}
