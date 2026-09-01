package com.jarvis.backend

import com.jarvis.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MemoryResult(
    val id: String,
    val content: String,
    val memoryType: String = "",
    val similarity: Double = 0.0
)

data class MemoryStats(
    val totalMemories: Int,
    val totalSkills: Int,
    val lastSync: String? = null
)

data class SkillResult(
    val id: String,
    val name: String,
    val triggerPattern: String,
    val actionSequence: String,
    val usageCount: Int = 0,
    val matchType: String = ""
)

class ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl get() = Config.BACKEND_API_URL

    suspend fun searchMemory(userId: String, query: String, limit: Int = 5): List<MemoryResult> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/memory/search?userId=$userId&query=${query.encodeToURL()}&limit=$limit"
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val results = json.getJSONArray("results")
                (0 until results.length()).map { i ->
                    val item = results.getJSONObject(i)
                    MemoryResult(
                        id = item.optString("id", ""),
                        content = item.optString("content", ""),
                        similarity = item.optDouble("similarity", 0.0)
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun storeMemory(userId: String, content: String, type: String = "conversation"): MemoryResult? =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("userId", userId)
                    put("content", content)
                    put("memoryType", type)
                }
                val request = Request.Builder()
                    .url("$baseUrl/memory/store")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()
                val response = client.newCall(request).execute()
                val respBody = response.body?.string() ?: return@withContext null
                val json = JSONObject(respBody)
                val mem = json.getJSONObject("memory")
                MemoryResult(id = mem.optString("id", ""), content = mem.optString("content", ""))
            } catch (e: Exception) {
                null
            }
        }

    suspend fun deleteMemory(memoryId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/memory/$memoryId")
                    .delete()
                    .build()
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }

    suspend fun getMemoryStats(userId: String): MemoryStats =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/memory/stats?userId=$userId")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext MemoryStats(0, 0)
                val json = JSONObject(body)
                MemoryStats(
                    totalMemories = json.optInt("totalMemories", 0),
                    totalSkills = json.optInt("totalSkills", 0),
                    lastSync = if (json.isNull("lastSync")) null else json.optString("lastSync")
                )
            } catch (e: Exception) {
                MemoryStats(0, 0)
            }
        }

    suspend fun matchSkill(userId: String, command: String): SkillResult? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/skill/match?userId=$userId&command=${command.encodeToURL()}"
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (json.isNull("skill")) return@withContext null
                val skill = json.getJSONObject("skill")
                SkillResult(
                    id = skill.optString("id", ""),
                    name = skill.optString("name", ""),
                    triggerPattern = skill.optString("trigger_pattern", ""),
                    actionSequence = skill.optJSONArray("action_sequence")?.toString() ?: "[]",
                    usageCount = skill.optInt("usage_count", 0),
                    matchType = skill.optString("matchType", "")
                )
            } catch (e: Exception) {
                null
            }
        }

    suspend fun listSkills(userId: String): List<SkillResult> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/skill/list?userId=$userId")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val skills = json.getJSONArray("skills")
                (0 until skills.length()).map { i ->
                    val skill = skills.getJSONObject(i)
                    SkillResult(
                        id = skill.optString("id", ""),
                        name = skill.optString("name", ""),
                        triggerPattern = skill.optString("trigger_pattern", ""),
                        actionSequence = skill.optJSONArray("action_sequence")?.toString() ?: "[]",
                        usageCount = skill.optInt("usage_count", 0)
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun learnSkill(userId: String, command: String, name: String, actions: JSONArray): SkillResult? =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("userId", userId)
                    put("command", command)
                    put("name", name)
                    put("actions", actions)
                }
                val request = Request.Builder()
                    .url("$baseUrl/skill/learn")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()
                val response = client.newCall(request).execute()
                val respBody = response.body?.string() ?: return@withContext null
                val json = JSONObject(respBody)
                val skill = json.getJSONObject("skill")
                SkillResult(
                    id = skill.optString("id", ""),
                    name = skill.optString("name", ""),
                    triggerPattern = skill.optString("trigger_pattern", ""),
                    actionSequence = skill.optJSONArray("action_sequence")?.toString() ?: "[]"
                )
            } catch (e: Exception) {
                null
            }
        }

    suspend fun deleteSkill(skillId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/skill/$skillId")
                    .delete()
                    .build()
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }


    suspend fun getRecentMemories(userId: String, limit: Int = 10): List<MemoryResult> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/memory/recent?userId=$userId&limit=$limit")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val results = json.getJSONArray("results")
                (0 until results.length()).map { i ->
                    val item = results.getJSONObject(i)
                    MemoryResult(
                        id = item.optString("id", ""),
                        content = item.optString("content", ""),
                        memoryType = item.optString("memory_type", "")
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    private fun String.encodeToURL(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
