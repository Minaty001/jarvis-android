package com.jarvis.skills

import android.content.Context
import android.util.Log
import com.jarvis.memory.CachedSkill
import com.jarvis.memory.JarvisDatabase
import com.jarvis.memory.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class SkillManager(private val context: Context) {
    companion object {
        private const val TAG = "SkillManager"
        private const val MAX_SKILLS = 200
    }

    private val db = JarvisDatabase.getInstance(context)
    private val skillDao = db.skillDao()

    suspend fun store(
        name: String,
        triggerPattern: String,
        actionSequence: JSONArray,
        examples: List<String> = emptyList()
    ): CachedSkill = withContext(Dispatchers.IO) {
        val id = "skill_${System.currentTimeMillis()}_${name.hashCode().toUInt()}"
        val skill = CachedSkill(
            id = id,
            name = name,
            triggerPattern = triggerPattern,
            actionSequence = actionSequence.toString(),
            examples = examples.joinToString("|||"),
            usageCount = 0,
            lastUsed = null,
            syncStatus = SyncStatus.PENDING_CREATE
        )

        val count = skillDao.count()
        if (count >= MAX_SKILLS) {
            val all = skillDao.getAll()
            val leastUsed = all.minByOrNull { it.usageCount }
            if (leastUsed != null) {
                skillDao.deleteById(leastUsed.id)
                Log.d(TAG, "Evicted least-used skill: ${leastUsed.name}")
            }
        }

        skillDao.upsert(skill)
        Log.d(TAG, "Stored skill: $name (id=$id)")
        skill
    }

    suspend fun match(query: String): CachedSkill? = withContext(Dispatchers.IO) {
        val candidates = skillDao.search(query)
        candidates.maxByOrNull { it.usageCount }
    }

    suspend fun incrementUsage(skillId: String) = withContext(Dispatchers.IO) {
        val skill = skillDao.getById(skillId) ?: return@withContext
        skillDao.upsert(skill.copy(
            usageCount = skill.usageCount + 1,
            lastUsed = System.currentTimeMillis()
        ))
    }

    suspend fun getAll(): List<CachedSkill> = withContext(Dispatchers.IO) {
        skillDao.getAll()
    }

    suspend fun delete(skillId: String) = withContext(Dispatchers.IO) {
        skillDao.deleteById(skillId)
        Log.d(TAG, "Deleted skill: $skillId")
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val all = skillDao.getAll()
        all.forEach { skillDao.deleteById(it.id) }
        Log.d(TAG, "Cleared all ${all.size} skills")
    }

    suspend fun getStats(): SkillStats = withContext(Dispatchers.IO) {
        val total = skillDao.count()
        val all = skillDao.getAll()
        val totalUsage = all.sumOf { it.usageCount }
        SkillStats(totalSkills = total, totalUsage = totalUsage)
    }
}

data class SkillStats(
    val totalSkills: Int,
    val totalUsage: Int
)
