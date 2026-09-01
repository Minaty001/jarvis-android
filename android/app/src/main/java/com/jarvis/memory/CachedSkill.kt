package com.jarvis.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_skills")
data class CachedSkill(
    @PrimaryKey val id: String,
    val name: String,
    val triggerPattern: String,
    val actionSequence: String,
    val examples: String,
    val usageCount: Int,
    val lastUsed: Long?,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
