package com.jarvis.automation

import android.util.Log
import kotlinx.coroutines.delay

data class AwaitConfig(
    val maxAttempts: Int = 15,
    val delayMs: Long = 300,
    val timeoutMs: Long = 15_000
)

class UiAwaiter(private val service: JarvisAccessibilityService) {
    companion object {
        private const val TAG = "UiAwaiter"
    }

    suspend fun awaitText(
        text: String,
        config: AwaitConfig = AwaitConfig()
    ): Boolean {
        val startTime = System.currentTimeMillis()
        repeat(config.maxAttempts) { attempt ->
            if (System.currentTimeMillis() - startTime > config.timeoutMs) {
                Log.w(TAG, "awaitText '$text' timed out after ${config.timeoutMs}ms")
                return false
            }
            delay(config.delayMs)
            val nodes = service.findByText(text)
            if (nodes.isNotEmpty()) {
                nodes.forEach { it.recycle() }
                Log.d(TAG, "awaitText '$text' found after ${attempt + 1} attempts")
                return true
            }
        }
        Log.w(TAG, "awaitText '$text' not found after ${config.maxAttempts} attempts")
        return false
    }

    suspend fun awaitViewId(
        viewId: String,
        config: AwaitConfig = AwaitConfig()
    ): Boolean {
        val startTime = System.currentTimeMillis()
        repeat(config.maxAttempts) { attempt ->
            if (System.currentTimeMillis() - startTime > config.timeoutMs) {
                Log.w(TAG, "awaitViewId '$viewId' timed out after ${config.timeoutMs}ms")
                return false
            }
            delay(config.delayMs)
            if (service.findByViewId(viewId).isNotEmpty()) {
                Log.d(TAG, "awaitViewId '$viewId' found after ${attempt + 1} attempts")
                return true
            }
        }
        Log.w(TAG, "awaitViewId '$viewId' not found after ${config.maxAttempts} attempts")
        return false
    }

    suspend fun awaitTextGone(
        text: String,
        config: AwaitConfig = AwaitConfig()
    ): Boolean {
        val startTime = System.currentTimeMillis()
        repeat(config.maxAttempts) { attempt ->
            if (System.currentTimeMillis() - startTime > config.timeoutMs) {
                Log.w(TAG, "awaitTextGone '$text' timed out after ${config.timeoutMs}ms")
                return false
            }
            delay(config.delayMs)
            val nodes = service.findByText(text)
            if (nodes.isEmpty()) {
                Log.d(TAG, "awaitTextGone '$text' gone after ${attempt + 1} attempts")
                return true
            }
            nodes.forEach { it.recycle() }
        }
        Log.w(TAG, "awaitTextGone '$text' still present after ${config.maxAttempts} attempts")
        return false
    }

    suspend fun awaitClickable(
        text: String,
        config: AwaitConfig = AwaitConfig()
    ): Boolean {
        val startTime = System.currentTimeMillis()
        repeat(config.maxAttempts) { attempt ->
            if (System.currentTimeMillis() - startTime > config.timeoutMs) {
                Log.w(TAG, "awaitClickable '$text' timed out after ${config.timeoutMs}ms")
                return false
            }
            delay(config.delayMs)
            val nodes = service.findByText(text)
            val clickable = nodes.filter { it.isClickable || it.parent?.isClickable == true }
            nodes.forEach { it.recycle() }
            if (clickable.isNotEmpty()) {
                Log.d(TAG, "awaitClickable '$text' found after ${attempt + 1} attempts")
                return true
            }
        }
        Log.w(TAG, "awaitClickable '$text' not found after ${config.maxAttempts} attempts")
        return false
    }
}
