package com.jarvis.core

import android.os.Debug
import android.os.SystemClock
import android.util.Log

object PerformanceMonitor {
    private const val TAG = "PerfMonitor"

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

    private val timings = mutableListOf<Timing>()

    fun startTimer(name: String): Long {
        return SystemClock.elapsedRealtime()
    }

    fun endTimer(name: String, startMs: Long): Timing {
        val duration = SystemClock.elapsedRealtime() - startMs
        val timing = Timing(name, duration)
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

    fun getAverageTiming(name: String): Double {
        val filtered = timings.filter { it.name == name }
        return if (filtered.isNotEmpty()) filtered.map { it.durationMs }.average() else 0.0
    }

    fun clearTimings() = timings.clear()
}
