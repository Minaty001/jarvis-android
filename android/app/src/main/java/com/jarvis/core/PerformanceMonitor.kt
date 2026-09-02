package com.jarvis.core

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

object PerformanceMonitor {
    private const val TAG = "PerfMonitor"
    private const val MAX_SAMPLES = 1000

    data class Timing(
        val name: String,
        val durationMs: Long,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class MemorySnapshot(
        val heapUsedMB: Float,
        val heapMaxMB: Float,
        val nativeUsedMB: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class TimingStats(
        val name: String,
        val count: Int,
        val avgMs: Double,
        val minMs: Long,
        val maxMs: Long,
        val p50Ms: Long,
        val p95Ms: Long,
        val p99Ms: Long
    )

    private val timings = CopyOnWriteArrayList<Timing>()
    private val sampleCount = AtomicInteger(0)

    fun startTimer(name: String): Long {
        return SystemClock.elapsedRealtime()
    }

    fun endTimer(name: String, startMs: Long): Timing {
        val duration = SystemClock.elapsedRealtime() - startMs
        val timing = Timing(name, duration)

        if (sampleCount.get() >= MAX_SAMPLES) {
            val oldestIndex = timings.indexOfFirst { it.name == name }
            if (oldestIndex >= 0) {
                timings.removeAt(oldestIndex)
            } else if (timings.isNotEmpty()) {
                timings.removeAt(0)
            }
        } else {
            sampleCount.incrementAndGet()
        }

        timings.add(timing)
        Log.d(TAG, "$name: ${duration}ms")
        return timing
    }

    fun getMemorySnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        val heapMax = runtime.maxMemory() / (1024f * 1024f)
        val nativeUsed = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)
        return MemorySnapshot(heapUsed, heapMax, nativeUsed)
    }

    fun logMemory(label: String = "Memory") {
        val snapshot = getMemorySnapshot()
        Log.d(TAG, "$label: heap=${snapshot.heapUsedMB}/${snapshot.heapMaxMB}MB native=${snapshot.nativeUsedMB}MB")
    }

    fun getTimings(): List<Timing> = timings.toList()

    fun getTimingsByName(name: String): List<Timing> = timings.filter { it.name == name }

    fun getTimingStats(name: String): TimingStats? {
        val filtered = timings.filter { it.name == name }
        if (filtered.isEmpty()) return null

        val durations = filtered.map { it.durationMs }.sorted()
        val count = durations.size
        val avg = durations.average()
        val min = durations.first()
        val max = durations.last()

        return TimingStats(
            name = name,
            count = count,
            avgMs = avg,
            minMs = min,
            maxMs = max,
            p50Ms = percentile(durations, 50),
            p95Ms = percentile(durations, 95),
            p99Ms = percentile(durations, 99)
        )
    }

    fun getAverageTiming(name: String): Double {
        return getTimingStats(name)?.avgMs ?: 0.0
    }

    fun logTimingStats(name: String) {
        val stats = getTimingStats(name) ?: run {
            Log.d(TAG, "$name: no data")
            return
        }
        Log.d(TAG, "$name: count=${stats.count} avg=${String.format("%.1f", stats.avgMs)}ms " +
                "p50=${stats.p50Ms}ms p95=${stats.p95Ms}ms p99=${stats.p99Ms}ms " +
                "min=${stats.minMs}ms max=${stats.maxMs}ms")
    }

    fun logAllStats() {
        val names = timings.map { it.name }.distinct()
        Log.d(TAG, "=== Performance Stats (${names.size} metrics, ${timings.size} samples) ===")
        names.forEach { logTimingStats(it) }
        logMemory("After stats")
    }

    fun clearTimings() {
        timings.clear()
        sampleCount.set(0)
    }

    private fun percentile(sorted: List<Long>, p: Int): Long {
        if (sorted.isEmpty()) return 0
        val index = ((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }
}
