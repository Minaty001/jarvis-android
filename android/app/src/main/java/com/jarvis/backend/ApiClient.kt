package com.jarvis.backend

import android.util.Log
import com.jarvis.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
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

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val deviceId: String,
    val trusted: Boolean
)

data class PingResult(val isSuccess: Boolean, val latencyMs: Long, val message: String)

class ApiClient(
    var baseUrl: String = Config.BACKEND_API_URL,
    private val authTokenManager: AuthTokenManager? = null
) {
    companion object {
        private const val TAG = "ApiClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                .connectTimeout(3000, TimeUnit.MILLISECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(3000, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun addAuthHeaders(builder: Request.Builder) {
        builder.header("Connection", "keep-alive")
        builder.header("Accept", "application/json")
        authTokenManager?.accessToken?.let { token ->
            if (!authTokenManager.isTokenExpired(token)) {
                builder.header("Authorization", "Bearer $token")
            }
        }
    }

    fun pingBackend(urlToTest: String = baseUrl, onResult: (PingResult) -> Unit) {
        scope.launch {
            val start = System.currentTimeMillis()
            val cleanUrl = urlToTest.trim().trimEnd('/')
            val request = Request.Builder()
                .url("$cleanUrl/api/v1/health")
                .header("Connection", "keep-alive")
                .header("Accept", "application/json")
                .header("X-Request-ID", "ping-${UUID.randomUUID()}")
                .build()

            try {
                sharedClient.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - start
                    if (response.isSuccessful) {
                        launch(Dispatchers.Main) { onResult(PingResult(true, latency, "Online (${latency}ms) — HTTP ${response.code}")) }
                    } else {
                        launch(Dispatchers.Main) { onResult(PingResult(false, latency, "HTTP ${response.code}: ${response.message}")) }
                    }
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - start
                Log.w(TAG, "Ping failed after ${latency}ms: ${e.message}")
                launch(Dispatchers.Main) { onResult(PingResult(false, latency, e.message ?: "Connection timeout")) }
            }
        }
    }

    fun registerDevice(
        deviceName: String,
        deviceModel: String,
        osVersion: String,
        onResult: (AuthTokens?) -> Unit
    ) {
        scope.launch {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("device_name", deviceName)
                put("device_model", deviceModel)
                put("os_version", osVersion)
                authTokenManager?.deviceId?.let { put("device_id", it) }
            }.toString()

            val request = Request.Builder()
                .url("$cleanUrl/api/v1/auth/token")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Connection", "keep-alive")
                .header("Accept", "application/json")
                .header("X-Request-ID", "reg-${UUID.randomUUID()}")
                .build()

            try {
                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val tokens = AuthTokens(
                            accessToken = json.getString("access_token"),
                            refreshToken = json.getString("refresh_token"),
                            expiresIn = json.getInt("expires_in"),
                            deviceId = json.getString("device_id"),
                            trusted = json.optBoolean("trusted", false)
                        )
                        authTokenManager?.saveTokens(
                            tokens.accessToken, tokens.refreshToken, tokens.deviceId, tokens.trusted
                        )
                        launch(Dispatchers.Main) { onResult(tokens) }
                    } else {
                        Log.w(TAG, "Device registration failed: ${response.code}")
                        launch(Dispatchers.Main) { onResult(null) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Device registration failed: ${e.message}")
                launch(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    fun refreshAccessToken(onResult: (String?) -> Unit) {
        scope.launch {
            val refresh = authTokenManager?.refreshToken
            if (refresh == null) {
                launch(Dispatchers.Main) { onResult(null) }
                return@launch
            }

            val cleanUrl = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("refresh_token", refresh)
            }.toString()

            val request = Request.Builder()
                .url("$cleanUrl/api/v1/auth/refresh")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Connection", "keep-alive")
                .header("X-Request-ID", "ref-${UUID.randomUUID()}")
                .build()

            try {
                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val newAccess = json.getString("access_token")
                        val newRefresh = json.getString("refresh_token")
                        val deviceId = json.getString("device_id")
                        val trusted = json.optBoolean("trusted", false)
                        authTokenManager?.saveTokens(newAccess, newRefresh, deviceId, trusted)
                        launch(Dispatchers.Main) { onResult(newAccess) }
                    } else {
                        Log.w(TAG, "Token refresh failed: ${response.code}")
                        launch(Dispatchers.Main) { onResult(null) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Token refresh failed: ${e.message}")
                launch(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    suspend fun searchMemory(userId: String, query: String, limit: Int = 5): List<MemoryResult> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val url = "$cleanUrl/memory/search?userId=$userId&query=${query.encodeToURL()}&limit=$limit"
                val request = Request.Builder().url(url).get().also { addAuthHeaders(it) }.build()
                val response = sharedClient.newCall(request).execute()
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
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val body = JSONObject().apply {
                    put("userId", userId)
                    put("content", content)
                    put("memoryType", type)
                }
                val request = Request.Builder()
                    .url("$cleanUrl/memory/store")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .also { addAuthHeaders(it) }
                    .build()
                val response = sharedClient.newCall(request).execute()
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
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/memory/$memoryId")
                    .delete()
                    .also { addAuthHeaders(it) }
                    .build()
                val response = sharedClient.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }

    suspend fun getMemoryStats(userId: String): MemoryStats =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/memory/stats?userId=$userId")
                    .get()
                    .also { addAuthHeaders(it) }
                    .build()
                val response = sharedClient.newCall(request).execute()
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
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val url = "$cleanUrl/skill/match?userId=$userId&command=${command.encodeToURL()}"
                val request = Request.Builder().url(url).get().also { addAuthHeaders(it) }.build()
                val response = sharedClient.newCall(request).execute()
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
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/skill/list?userId=$userId")
                    .get()
                    .also { addAuthHeaders(it) }
                    .build()
                val response = sharedClient.newCall(request).execute()
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
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val body = JSONObject().apply {
                    put("userId", userId)
                    put("command", command)
                    put("name", name)
                    put("actions", actions)
                }
                val request = Request.Builder()
                    .url("$cleanUrl/skill/learn")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .also { addAuthHeaders(it) }
                    .build()
                val response = sharedClient.newCall(request).execute()
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
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/skill/$skillId")
                    .delete()
                    .also { addAuthHeaders(it) }
                    .build()
                val response = sharedClient.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }

    suspend fun getRecentMemories(userId: String, limit: Int = 10): List<MemoryResult> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/memory/recent?userId=$userId&limit=$limit")
                    .get()
                    .also { addAuthHeaders(it) }
                    .build()
                val response = sharedClient.newCall(request).execute()
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
