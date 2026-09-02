package com.jarvis.automation

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

data class ConfirmationRequest(
    val id: String,
    val actionType: String,
    val riskLevel: RiskLevel,
    val params: Map<String, String>,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ConfirmationResult {
    ALLOWED,
    DENIED,
    TIMEOUT,
    CANCELLED
}

interface ConfirmationUI {
    suspend fun showConfirmation(request: ConfirmationRequest): ConfirmationResult
}

class ConfirmationManager(
    private val ui: ConfirmationUI,
    private val timeoutMs: Long = 20_000
) {
    companion object {
        private const val TAG = "ConfirmationManager"
    }

    private val pending = ConcurrentHashMap<String, CompletableDeferred<ConfirmationResult>>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    suspend fun requestConfirmation(
        actionType: String,
        riskLevel: RiskLevel,
        params: Map<String, String>
    ): ConfirmationResult {
        val id = "conf_${System.currentTimeMillis()}_${actionType.hashCode()}"
        val request = ConfirmationRequest(id, actionType, riskLevel, params)
        val deferred = CompletableDeferred<ConfirmationResult>()
        pending[id] = deferred

        val job = scope.launch {
            try {
                withTimeout(timeoutMs) {
                    val result = ui.showConfirmation(request)
                    deferred.complete(result)
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Confirmation timed out for $actionType")
                deferred.complete(ConfirmationResult.TIMEOUT)
            } catch (e: Exception) {
                Log.e(TAG, "Confirmation error for $actionType", e)
                deferred.complete(ConfirmationResult.CANCELLED)
            } finally {
                pending.remove(id)
            }
        }

        val result = deferred.await()
        if (result == ConfirmationResult.TIMEOUT || result == ConfirmationResult.CANCELLED) {
            job.cancel()
        }
        return result
    }

    fun cancelAll() {
        pending.forEach { (id, deferred) ->
            deferred.complete(ConfirmationResult.CANCELLED)
        }
        pending.clear()
    }

    fun destroy() {
        cancelAll()
        scope.cancel()
    }
}
