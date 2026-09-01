package com.jarvis.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MemoryDao {
    @Query("SELECT * FROM cached_memories ORDER BY timestamp DESC")
    suspend fun getAll(): List<CachedMemory>

    @Query("SELECT * FROM cached_memories WHERE id = :id")
    suspend fun getById(id: String): CachedMemory?

    @Query("SELECT * FROM cached_memories WHERE content LIKE '%' || :query || '%' ORDER BY importance DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 5): List<CachedMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: CachedMemory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(memories: List<CachedMemory>)

    @Query("DELETE FROM cached_memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM cached_memories WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<CachedMemory>

    @Query("UPDATE cached_memories SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("SELECT COUNT(*) FROM cached_memories")
    suspend fun count(): Int
}
