package com.jarvis.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class ClapDetector(private val context: Context) {
    companion object {
        private const val TAG = "ClapDetector"
        private const val SAMPLE_RATE = 16000
        private const val SPIKE_RATIO = 3.0
        private const val MIN_RMS = 0.005
        private const val DOUBLE_GAP_MS = 500L
        private const val COOLDOWN_MS = 600L
        private const val BUFFER_SIZE_MS = 50
    }

    private var audioRecord: AudioRecord? = null
    private var noiseFloor = 0.001
    private var lastClapTime = 0L
    private var firstClapTime = 0L

    @Volatile
    private var isListening = false

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _doubleClaps = MutableSharedFlow<Unit>(extraBufferCapacity = 5)
    val doubleClaps: SharedFlow<Unit> = _doubleClaps.asSharedFlow()

    fun start() {
        if (isListening) return

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
            maxOf(minBufferBytes, SAMPLE_RATE * BUFFER_SIZE_MS / 1000 * 2)
        )

        isListening = true
        audioRecord?.startRecording()

        val buffer = ShortArray(SAMPLE_RATE * BUFFER_SIZE_MS / 1000)

        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (isListening) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readCount > 0) {
                    processAudioBuffer(buffer, readCount)
                }
            }
        }.start()

        Log.d(TAG, "Clap detection started")
    }

    private fun processAudioBuffer(buffer: ShortArray, count: Int) {
        val floatSamples = FloatArray(count) { buffer[it] / 32768f }
        val rms = sqrt(floatSamples.map { it * it }.average()).toFloat()

        noiseFloor = noiseFloor * 0.99 + rms.toDouble() * 0.01

        val threshold = maxOf(noiseFloor * SPIKE_RATIO, MIN_RMS)
        val now = System.currentTimeMillis()

        if (rms > threshold && (now - lastClapTime) > COOLDOWN_MS) {
            if (firstClapTime == 0L) {
                firstClapTime = now
                lastClapTime = now
            } else if (now - firstClapTime < DOUBLE_GAP_MS) {
                firstClapTime = 0L
                lastClapTime = now
                scope.launch {
                    Log.d(TAG, "Double clap detected!")
                    _doubleClaps.emit(Unit)
                }
            } else {
                firstClapTime = now
                lastClapTime = now
            }
        }
    }

    fun stop() {
        isListening = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord?.release()
        audioRecord = null
        firstClapTime = 0L
        lastClapTime = 0L
        noiseFloor = 0.001
        Log.d(TAG, "Clap detection stopped")
    }

    fun isRunning() = isListening
}
