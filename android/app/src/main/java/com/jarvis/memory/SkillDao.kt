package com.jarvis.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SkillDao {
    @Query("SELECT * FROM cached_skills ORDER BY usageCount DESC")
    suspend fun getAll(): List<CachedSkill>

    @Query("SELECT * FROM cached_skills WHERE id = :id")
    suspend fun getById(id: String): CachedSkill?

    @Query("SELECT * FROM cached_skills WHERE name LIKE '%' || :query || '%' OR triggerPattern LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<CachedSkill>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skill: CachedSkill)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(skills: List<CachedSkill>)

    @Query("DELETE FROM cached_skills WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM cached_skills")
    suspend fun count(): Int
}
