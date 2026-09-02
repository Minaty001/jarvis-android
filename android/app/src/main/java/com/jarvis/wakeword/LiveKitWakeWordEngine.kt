package com.jarvis.wakeword

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log

/**
 * Continuous, low-power offline wake-word engine — Phase 1, 2, 5, 6 rebuild.
 *
 * Phase 1 fix: STT text-matching fallback is PERMANENTLY REMOVED.
 *   If ONNX models are missing → error reported → engine stays stopped.
 *   SpeechRecognizer MUST NOT run during wake mode.
 *
 * Phase 5 fix: Single inference pipeline.
 *   feedPcm() is called once per audio frame.
 *   processAndDetect() no longer exists.
 *   The temporal gate and listener callback live inside feedPcm().
 *
 * Phase 2 fix: startMonitoring() does NOT auto-start on runtime init.
 *   VoiceRuntime.setWakeEnabled(true/false) is the single control point.
 */
class LiveKitWakeWordEngine(
    private val context: Context? = null,
    private val config: WakeWordConfig = WakeWordConfig(),
    private val detector: OnnxWakeWordDetector = OnnxWakeWordDetector(context, config)
) {
    companion object {
        private const val TAG = "LiveKitWakeWordEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL  = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SAMPLES = 320  // 20 ms @ 16 kHz
    }

    @Volatile private var isMonitoring     = false
    @Volatile private var pausedForCommand = false

    private var captureThread: Thread? = null
    private var audioRecord:   AudioRecord? = null
    private val stateLock = Any()

    private var onWakeCallback:  ((String?) -> Unit)? = null
    private var onErrorCallback: ((Throwable) -> Unit)? = null

    val isAvailable:      Boolean get() = detector.isAvailable()
    val isMonitoringNow:  Boolean get() = isMonitoring

    fun setSensitivity(sensitivity: Float) { detector.setSensitivity(sensitivity) }
    fun setOnWakeListener(onWake: (String?) -> Unit)  { onWakeCallback  = onWake }
    fun setOnErrorListener(onError: (Throwable) -> Unit) { onErrorCallback = onError }

    /**
     * Start wake-word monitoring.
     *
     * Phase 1: If ONNX unavailable → report error and return false.
     *          NEVER falls back to SpeechRecognizer.
     */
    fun startMonitoring(): Boolean {
        if (isMonitoring) return true

        // Phase 1: ONNX unavailable → hard stop, no STT fallback.
        if (!detector.isAvailable()) {
            val msg = "Wake-word ONNX models missing — offline detection unavailable; NOT falling back to STT"
            Log.w(TAG, msg)
            onErrorCallback?.invoke(IllegalStateException(msg))
            return false
        }

        detector.setListener(object : WakeWordListener {
            override fun onWakeWordDetected() {
                if (!isMonitoring || pausedForCommand) {
                    Log.d(TAG, "Wake event suppressed (isMonitoring=$isMonitoring, paused=$pausedForCommand)")
                    return
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (isMonitoring && !pausedForCommand) {
                        Log.i(TAG, "Wake word detected — firing callback")
                        onWakeCallback?.invoke(null)
                    }
                }
            }
            override fun onWakeWordError(error: Throwable) {
                Log.e(TAG, "Wake-word detector error", error)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onErrorCallback?.invoke(error)
                }
            }
        })

        detector.start()
        isMonitoring     = true
        pausedForCommand = false
        startCaptureThread()
        Log.i(TAG, "Wake-word monitoring started (ONNX pipeline, no STT fallback)")
        return true
    }

    private fun startCaptureThread() {
        synchronized(stateLock) {
            captureThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val minBuf  = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
                val bufSize = (FRAME_SAMPLES * 2 * 4).coerceAtLeast(minBuf)
                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE, CHANNEL, ENCODING, bufSize
                    )
                    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord init failed for wake-word capture")
                        handleCaptureError(IllegalStateException("AudioRecord init failed"))
                        return@Thread
                    }
                    audioRecord?.startRecording()
                    val buffer = ShortArray(FRAME_SAMPLES)
                    // Phase 5: single inference path — feedPcm handles detection internally.
                    while (isMonitoring && !pausedForCommand) {
                        val read = audioRecord?.read(buffer, 0, FRAME_SAMPLES) ?: -1
                        if (read > 0) {
                            val slice = if (read == FRAME_SAMPLES) buffer else buffer.copyOf(read)
                            // Single call — temporal gate + listener callback inside feedPcm.
                            detector.feedPcm(slice, 0, slice.size)
                        } else if (read < 0) {
                            Log.w(TAG, "Wake-word AudioRecord read error: $read")
                            Thread.sleep(10)
                        }
                    }
                } catch (e: InterruptedException) {
                    // Normal shutdown path.
                } catch (e: Exception) {
                    Log.e(TAG, "Wake-word capture loop error", e)
                    handleCaptureError(e)
                } finally {
                    releaseAudioRecord()
                }
            }, "Jarvis-WakeWordCapture").also {
                it.priority = Thread.MAX_PRIORITY
                it.start()
            }
        }
    }

    private fun handleCaptureError(error: Throwable) {
        isMonitoring = false
        releaseAudioRecord()
        onErrorCallback?.invoke(error)
    }

    private fun releaseAudioRecord() {
        synchronized(stateLock) {
            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.stop()
                }
            } catch (_: Exception) {}
            try { audioRecord?.release() } catch (_: Exception) {}
            audioRecord = null
        }
    }

    val isAudioRecordReleased: Boolean get() = synchronized(stateLock) { audioRecord == null }

    /**
     * Non-blocking pause — sets flag and releases AudioRecord.
     */
    fun pauseAsync() {
        if (!isMonitoring) return
        pausedForCommand = true
        releaseAudioRecord()
        detector.pause()
        Log.i(TAG, "Wake-word paused (async) — microphone released for command mode")
    }

    /**
     * Synchronous pause — guarantees capture thread is stopped and AudioRecord is released.
     */
    fun pause() {
        if (!isMonitoring) return
        pausedForCommand = true
        releaseAudioRecord()

        val threadToJoin = captureThread
        captureThread = null
        if (threadToJoin != null && threadToJoin.isAlive && Thread.currentThread() != threadToJoin) {
            try {
                threadToJoin.interrupt()
                threadToJoin.join(300)
            } catch (_: Exception) {}
        }
        detector.pause()
        Log.i(TAG, "Wake-word paused (sync) — microphone guaranteed released")
    }

    /**
     * Returns the mic to wake-word listening after command session completes.
     */
    fun resume() {
        if (!isMonitoring || !pausedForCommand) return
        pausedForCommand = false
        detector.resume()
        if (detector.isAvailable()) {
            startCaptureThread()
        } else {
            Log.w(TAG, "Cannot resume wake-word: ONNX unavailable.")
            isMonitoring = false
            onErrorCallback?.invoke(IllegalStateException("ONNX unavailable on resume"))
        }
        Log.i(TAG, "Wake-word resumed")
    }

    fun stopMonitoring() {
        isMonitoring     = false
        pausedForCommand = false
        val threadToJoin = captureThread
        captureThread = null
        if (threadToJoin != null && threadToJoin.isAlive && Thread.currentThread() != threadToJoin) {
            try {
                threadToJoin.interrupt()
                threadToJoin.join(1000)
            } catch (_: Exception) {}
        }
        releaseAudioRecord()
        detector.stop()
        Log.i(TAG, "Wake-word monitoring stopped")
    }

    fun stop() {
        stopMonitoring()
    }

    fun release() {
        stopMonitoring()
        detector.release()
        onWakeCallback  = null
        onErrorCallback = null
        Log.i(TAG, "Wake-word engine released")
    }
}
