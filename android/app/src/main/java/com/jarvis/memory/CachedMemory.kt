package com.jarvis.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus { SYNCED, PENDING_CREATE, PENDING_DELETE }

@Entity(tableName = "cached_memories")
data class CachedMemory(
    @PrimaryKey val id: String,
    val content: String,
    val memoryType: String,
    val importance: Float,
    val timestamp: Long,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
