package com.jarvis.wakeword

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.jarvis.core.PerformanceMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.FloatBuffer
import kotlin.math.sqrt
import kotlin.math.min

enum class OnnxLifecycleState {
    UNLOADED,
    LOADING,
    READY,
    ERROR
}

/**
 * Offline wake-word detector — Phase 4 rebuild.
 *
 * Adds a TEMPORAL GATE: instead of firing on a single score above threshold,
 * the detector requires [WakeWordConfig.temporalPositiveCount] positives out of
 * the last [WakeWordConfig.temporalWindowSize] inference windows, each scoring
 * at least [WakeWordConfig.minConfidenceForPositive].
 *
 * Single inference pipeline:
 *   feedPcm()  → ring buffer append
 *               → mel spectrogram
 *               → embeddings
 *               → classifier score
 *               → temporal gate
 *               → cooldown
 *               → WakeWordListener.onWakeWordDetected()
 *
 * Phase 5 fix: processAndDetect() is REMOVED.
 * LiveKitWakeWordEngine calls feedPcm() once per audio frame and reads the
 * returned score directly — no second inference.
 */
class OnnxWakeWordDetector(
    private val context: Context? = null,
    private val config: WakeWordConfig = WakeWordConfig()
) : WakeWordDetector {

    companion object {
        private const val TAG = "OnnxWakeWordDetector"

        // Front-end contract (verified against upstream models).
        const val SAMPLE_RATE = 16000
        const val N_MELS = 32
        const val EMBEDDING_WINDOW = 76
        const val EMBEDDING_STRIDE = 8
        const val MIN_EMBEDDINGS = 16
        const val EMBEDDING_DIM = 96

        const val ASSET_DIR = "wakeword"
        const val MEL_MODEL = "melspectrogram.onnx"
        const val EMB_MODEL = "embedding_model.onnx"
        const val CLS_MODEL = "hey_jarvis.onnx"

        // Rolling PCM ring buffer: 2.5 s @ 16 kHz covers the 2 s window + slack.
        const val PCM_BUFFER_SAMPLES = (SAMPLE_RATE * 2.5).toInt()
        // Classifier is evaluated on a 2.0 s slice.
        const val CLASSIFY_WINDOW_SAMPLES = SAMPLE_RATE * 2

        // Phase 10: adaptive noise gate — fixed floor replaced by calibration.
        // Base minimum RMS; the adaptive gate raises this dynamically.
        private const val BASE_AUDIO_RMS = 0.005f

        const val MEL_INPUT  = "input"
        const val EMB_INPUT  = "input_1"
        const val CLS_INPUT  = "input"
        const val CLS_OUTPUT = "score"

        // Stride: evaluate ONNX inference every 80ms (1280 samples) instead of every 20ms
        const val INFERENCE_STRIDE_SAMPLES = 1280
    }

    private val ortEnv: OrtEnvironment? by lazy {
        try {
            OrtEnvironment.getEnvironment()
        } catch (e: Throwable) {
            Log.w(TAG, "ONNX Runtime environment unavailable: ${e.message}")
            null
        }
    }
    private var melSession: OrtSession? = null
    private var embSession: OrtSession? = null
    private var clsSession: OrtSession? = null

    @Volatile private var available = false

    // Rolling int16 PCM ring buffer (single writer from the capture thread).
    private val pcmRing = ShortArray(PCM_BUFFER_SAMPLES)
    @Volatile private var pcmWritePos = 0
    @Volatile private var pcmFilled  = 0
    private var samplesSinceLastInference = 0

    private var listener: WakeWordListener? = null
    private val cooldown = WakeCooldown(config.cooldownMs)

    // ── Phase 4: Temporal gate ───────────────────────────────────────────────
    // Circular buffer of recent scores for temporal majority vote.
    private val scoreWindow = FloatArray(config.temporalWindowSize) { 0f }
    private var scoreWindowIdx = 0

    // ── Phase 10: Adaptive noise gate ───────────────────────────────────────
    // Calibrated noise floor updated on startup and during silence windows.
    @Volatile private var noiseFloor = BASE_AUDIO_RMS
    private var calibrationSamples = 0
    private var calibrationRmsSum = 0.0
    private val calibrationTarget = 30  // number of silent windows to calibrate
    @Volatile private var calibrated = false

    // Reusable buffers to eliminate per-frame GC allocations
    private val classifyWindow = FloatArray(CLASSIFY_WINDOW_SAMPLES)
    private val last16Buffer = FloatArray(MIN_EMBEDDINGS * EMBEDDING_DIM)

    private val _lifecycleState = MutableStateFlow(OnnxLifecycleState.UNLOADED)
    val lifecycleState: StateFlow<OnnxLifecycleState> = _lifecycleState.asStateFlow()

    private var customThreshold: Float? = null

    val threshold: Float
        get() = customThreshold ?: WakeWordConfig.thresholdForSensitivity(config.sensitivity)

    fun setThreshold(value: Float) {
        customThreshold = value.coerceIn(0f, 1f)
    }

    fun setSensitivity(sensitivity: Float) {
        config.sensitivity = sensitivity.coerceIn(0f, 1f)
        customThreshold = null
    }

    fun setSensitivity(label: String) {
        val mappedThreshold = when (label.lowercase().trim()) {
            "low" -> 0.7f
            "high" -> 0.3f
            "medium" -> 0.5f
            else -> 0.5f
        }
        setThreshold(mappedThreshold)
    }

    override fun isAvailable(): Boolean = available

    private val loadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loadJob: Job? = null

    init {
        loadJob = loadScope.launch { loadModels() }
    }

    private fun loadModels() {
        val ctx = context ?: run {
            Log.w(TAG, "No context — cannot load ONNX assets")
            _lifecycleState.value = OnnxLifecycleState.ERROR
            return
        }
        _lifecycleState.value = OnnxLifecycleState.LOADING
        try {
            val am: AssetManager = ctx.assets
            melSession = newSession(am, "$ASSET_DIR/$MEL_MODEL")
            embSession = newSession(am, "$ASSET_DIR/$EMB_MODEL")
            clsSession = newSession(am, "$ASSET_DIR/$CLS_MODEL")
            available = melSession != null && embSession != null && clsSession != null
            _lifecycleState.value = if (available) OnnxLifecycleState.READY else OnnxLifecycleState.ERROR
            Log.i(TAG, "ONNX wake-word models loaded (available=$available)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ONNX wake-word models — offline detection disabled", e)
            available = false
            _lifecycleState.value = OnnxLifecycleState.ERROR
        }
    }

    private fun newSession(am: AssetManager, assetPath: String): OrtSession? {
        val env = ortEnv ?: return null
        am.open(assetPath).use { stream ->
            val bytes = stream.readBytes()
            if (bytes.isEmpty()) return null
            return env.createSession(bytes)
        }
    }

    /**
     * Feed a chunk of raw 16 kHz mono int16 PCM.
     *
     * Phase 5: This is the SINGLE inference call per audio frame.
     * Returns the classifier score (0..1), or null when the window is too short,
     * the RMS gate rejects the frame, or the models are unavailable.
     *
     * Also applies the temporal gate and fires [WakeWordListener] when accepted.
     *
     * Thread-safe: called exclusively from the capture thread.
     */
    @Synchronized
    fun feedPcm(samples: ShortArray, offset: Int, length: Int): Float? {
        if (!available) return null

        // 1. Append to rolling ring buffer.
        var o = offset
        var n = length
        while (n > 0) {
            val space = PCM_BUFFER_SAMPLES - pcmWritePos
            val take  = min(space, n)
            System.arraycopy(samples, o, pcmRing, pcmWritePos, take)
            pcmWritePos = (pcmWritePos + take) % PCM_BUFFER_SAMPLES
            pcmFilled   = min(pcmFilled + take, PCM_BUFFER_SAMPLES)
            o += take
            n -= take
        }
        if (pcmFilled < CLASSIFY_WINDOW_SAMPLES) return null

        samplesSinceLastInference += length
        if (samplesSinceLastInference < INFERENCE_STRIDE_SAMPLES) {
            return null
        }
        samplesSinceLastInference = 0

        // 2. Extract last 2.0 s as float32 into preallocated classifyWindow.
        val startIdx = (pcmWritePos - CLASSIFY_WINDOW_SAMPLES + PCM_BUFFER_SAMPLES) % PCM_BUFFER_SAMPLES
        var sumSquares = 0.0
        for (i in 0 until CLASSIFY_WINDOW_SAMPLES) {
            val sample = pcmRing[(startIdx + i) % PCM_BUFFER_SAMPLES] / 32768.0f
            classifyWindow[i] = sample
            sumSquares += sample * sample
        }

        // 3. Phase 10: Adaptive noise gate.
        val rms = sqrt(sumSquares / CLASSIFY_WINDOW_SAMPLES).toFloat()
        val dynamicThreshold = noiseFloor * 2.5f  // signal must be 2.5× noise floor
        if (!calibrated) {
            // Calibration phase: sample quiet-ish windows to estimate noise floor.
            if (rms < BASE_AUDIO_RMS * 10f) {
                calibrationRmsSum += rms
                calibrationSamples++
                if (calibrationSamples >= calibrationTarget) {
                    noiseFloor = (calibrationRmsSum / calibrationSamples).toFloat().coerceAtLeast(BASE_AUDIO_RMS)
                    calibrated = true
                    Log.i(TAG, "Noise floor calibrated: %.4f".format(noiseFloor))
                }
            }
            // During calibration use fixed base floor.
            if (rms < BASE_AUDIO_RMS) return null
        } else {
            // Only adapt noise floor when the window looks like background noise.
            // Never learn from strong speech/wake candidates.
            if (rms < noiseFloor * 1.8f) {
                noiseFloor = (
                    noiseFloor * 0.995f +
                        rms * 0.005f
                    ).coerceIn(
                        BASE_AUDIO_RMS,
                        0.08f
                    )
            }
            if (rms < dynamicThreshold.coerceAtLeast(BASE_AUDIO_RMS)) {
                // Below noise floor — skip expensive inference
                return null
            }
        }

        // 4. Mel spectrogram.
        val mel = runMel(classifyWindow) ?: return null

        // 5. Embeddings.
        val embeddings = runEmbeddings(mel) ?: return null
        if (embeddings.size < MIN_EMBEDDINGS * EMBEDDING_DIM) return null
        System.arraycopy(
            embeddings,
            embeddings.size - MIN_EMBEDDINGS * EMBEDDING_DIM,
            last16Buffer,
            0,
            MIN_EMBEDDINGS * EMBEDDING_DIM
        )

        // 6. Classifier score.
        val score = runClassifier(last16Buffer)

        // 7. Phase 4: Temporal gate.
        scoreWindow[scoreWindowIdx] = score
        scoreWindowIdx = (scoreWindowIdx + 1) % config.temporalWindowSize

        val positiveCount = scoreWindow.count { it >= config.minConfidenceForPositive }
        val temporalAccept = positiveCount >= config.temporalPositiveCount
        val confidenceAccept = score >= threshold
        val strongAccept = temporalAccept && confidenceAccept

        val decision = if (strongAccept && cooldown.allow()) "ACCEPT" else "REJECT"
        val rejectReason = when {
            score < config.minConfidenceForPositive -> "LOW_SCORE(${"%.3f".format(score)})"
            !temporalAccept -> "TEMPORAL_GATE(hits=$positiveCount/${config.temporalPositiveCount})"
            score < threshold -> "BELOW_THRESHOLD(${"%.3f".format(score)}<${"%.3f".format(threshold)})"
            else -> "COOLDOWN"
        }
        Log.d(
            TAG,
            "wakeCandidate: score=${"%.3f".format(score)} threshold=${"%.3f".format(threshold)} " +
                "hits=$positiveCount/${config.temporalWindowSize} rms=${"%.4f".format(rms)} " +
                "noiseFloor=${"%.4f".format(noiseFloor)} decision=$decision" +
                if (decision == "REJECT") " rejectReason=$rejectReason" else ""
        )

        if (decision == "ACCEPT") {
            Log.i(
                TAG,
                "WAKE CONFIRMED score=${
                    "%.3f".format(score)
                } hits=$positiveCount/${config.temporalWindowSize}"
            )
            // Immediately flush rolling ring buffer and temporal gate so stale audio cannot trigger again
            flushBuffers()
            listener?.onWakeWordDetected()
        }

        return score
    }

    private fun runMel(audio: FloatArray): FloatArray? {
        val session = melSession ?: return null
        val shape = longArrayOf(1, audio.size.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(audio), shape)
        val start = PerformanceMonitor.startTimer("onnx_mel")
        return try {
            session.run(mapOf(MEL_INPUT to tensor)).use { result ->
                val flat = resultToFloatArray(result) ?: return null
                if (flat.size % N_MELS != 0) return null
                FloatArray(flat.size) { flat[it] / 10.0f + 2.0f }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Mel spectrogram inference failed", e)
            null
        } finally {
            PerformanceMonitor.endTimer("onnx_mel", start)
            tensor.close()
        }
    }

    private fun resultToFloatArray(result: OrtSession.Result): FloatArray? {
        val tensor = result[0] as? OnnxTensor ?: return null
        val buf = tensor.floatBuffer ?: return null
        val n = buf.remaining()
        if (n <= 0) return null
        val arr = FloatArray(n)
        buf.get(arr)
        return arr
    }

    private fun runEmbeddings(mel: FloatArray): FloatArray? {
        val session = embSession ?: return null
        val frames = mel.size / N_MELS
        if (frames < EMBEDDING_WINDOW) return null
        val nWindows = (frames - EMBEDDING_WINDOW) / EMBEDDING_STRIDE + 1
        if (nWindows <= 0) return null

        val input = FloatArray(nWindows * EMBEDDING_WINDOW * N_MELS)
        var p = 0
        for (w in 0 until nWindows) {
            val frameStart = w * EMBEDDING_STRIDE
            for (r in 0 until EMBEDDING_WINDOW) {
                val melBase = (frameStart + r) * N_MELS
                for (c in 0 until N_MELS) {
                    input[p++] = mel[melBase + c]
                }
            }
        }
        val shape = longArrayOf(nWindows.toLong(), EMBEDDING_WINDOW.toLong(), N_MELS.toLong(), 1)
        val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(input), shape)
        val start = PerformanceMonitor.startTimer("onnx_embedding")
        return try {
            session.run(mapOf(EMB_INPUT to tensor)).use { result ->
                val flat = resultToFloatArray(result) ?: return null
                if (flat.size != nWindows * EMBEDDING_DIM) {
                    Log.w(TAG, "Embedding size ${flat.size} != expected ${nWindows * EMBEDDING_DIM}")
                    return@use null
                }
                flat
            }
        } catch (e: Exception) {
            Log.e(TAG, "Embedding inference failed", e)
            null
        } finally {
            PerformanceMonitor.endTimer("onnx_embedding", start)
            tensor.close()
        }
    }

    private fun runClassifier(last16: FloatArray): Float {
        val session = clsSession ?: return 0f
        val shape = longArrayOf(1, MIN_EMBEDDINGS.toLong(), EMBEDDING_DIM.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(last16), shape)
        val start = PerformanceMonitor.startTimer("onnx_classifier")
        return try {
            session.run(mapOf(CLS_INPUT to tensor)).use { result ->
                resultToFloatArray(result)?.firstOrNull() ?: 0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classifier inference failed", e)
            0f
        } finally {
            PerformanceMonitor.endTimer("onnx_classifier", start)
            tensor.close()
        }
    }

    /**
     * Flushes the rolling audio PCM ring buffer, temporal score window, and cooldown.
     * Prevents stale audio from causing immediate false re-triggers on pause/resume/accept.
     */
    @Synchronized
    fun flushBuffers() {
        pcmRing.fill(0)
        pcmWritePos = 0
        pcmFilled = 0
        scoreWindow.fill(0f)
        scoreWindowIdx = 0
        cooldown.triggerCooldown()
    }

    override fun setListener(listener: WakeWordListener) { this.listener = listener }

    override fun start() {
        loadScope.launch {
            loadJob?.join()
            if (available) {
                withContext(Dispatchers.Main) { flushBuffers() }
            }
        }
    }

    override fun stop() {
        flushBuffers()
    }

    override fun pause() {
        flushBuffers()
    }

    override fun resume() {
        flushBuffers()
    }

    override fun release() {
        listener = null
        flushBuffers()
        loadScope.cancel()
        try { melSession?.close() } catch (_: Exception) {}
        try { embSession?.close() } catch (_: Exception) {}
        try { clsSession?.close() } catch (_: Exception) {}
        melSession = null
        embSession = null
        clsSession = null
        available = false
        _lifecycleState.value = OnnxLifecycleState.UNLOADED
    }
}
