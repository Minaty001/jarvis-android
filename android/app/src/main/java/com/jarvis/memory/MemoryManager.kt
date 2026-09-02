package com.jarvis.memory

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryManager(private val context: Context) {
    companion object {
        private const val TAG = "MemoryManager"
        private const val MAX_MEMORIES = 1000
    }

    private val db = JarvisDatabase.getInstance(context)
    private val memoryDao = db.memoryDao()

    suspend fun store(
        content: String,
        memoryType: String,
        importance: Float = 0.5f
    ): CachedMemory = withContext(Dispatchers.IO) {
        val id = "mem_${System.currentTimeMillis()}_${content.hashCode().toUInt()}"
        val memory = CachedMemory(
            id = id,
            content = content,
            memoryType = memoryType,
            importance = importance,
            timestamp = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_CREATE
        )

        val count = memoryDao.count()
        if (count >= MAX_MEMORIES) {
            val oldest = memoryDao.getAll().lastOrNull()
            if (oldest != null) {
                memoryDao.deleteById(oldest.id)
                Log.d(TAG, "Evicted oldest memory: ${oldest.id}")
            }
        }

        memoryDao.upsert(memory)
        Log.d(TAG, "Stored memory: $id (type=$memoryType, importance=$importance)")
        memory
    }

    suspend fun search(query: String, limit: Int = 5): List<CachedMemory> = withContext(Dispatchers.IO) {
        memoryDao.search(query, limit)
    }

    suspend fun getAll(): List<CachedMemory> = withContext(Dispatchers.IO) {
        memoryDao.getAll()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        memoryDao.deleteById(id)
        Log.d(TAG, "Deleted memory: $id")
    }

    suspend fun getPendingSync(): List<CachedMemory> = withContext(Dispatchers.IO) {
        memoryDao.getPendingSync()
    }

    suspend fun markSynced(id: String) = withContext(Dispatchers.IO) {
        memoryDao.updateSyncStatus(id, SyncStatus.SYNCED)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val all = memoryDao.getAll()
        all.forEach { memoryDao.deleteById(it.id) }
        Log.d(TAG, "Cleared all ${all.size} memories")
    }

    suspend fun getStats(): MemoryStats = withContext(Dispatchers.IO) {
        val total = memoryDao.count()
        val pending = memoryDao.getPendingSync().size
        MemoryStats(totalMemories = total, pendingSync = pending)
    }
}

data class MemoryStats(
    val totalMemories: Int,
    val pendingSync: Int
)
