package com.jarvis.memory

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jarvis.backend.ApiClient

class MemorySyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "MemorySyncWorker"
        const val WORK_NAME = "jarvis_memory_sync"
    }

    private val db = JarvisDatabase.getInstance(context)
    private val apiClient = ApiClient()

    override suspend fun doWork(): Result {
        return try {
            val userId = inputData.getString("userId") ?: return Result.failure()
            val memoryDao = db.memoryDao()

            val pendingCreates = memoryDao.getPendingSync()
            for (memory in pendingCreates) {
                val result = apiClient.storeMemory(userId, memory.content, memory.memoryType)
                if (result != null) {
                    memoryDao.updateSyncStatus(memory.id, SyncStatus.SYNCED)
                    Log.d(TAG, "Synced memory: ${memory.id}")
                }
            }

            Log.d(TAG, "Memory sync complete. Synced ${pendingCreates.size} items.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
            Result.retry()
        }
    }
}
