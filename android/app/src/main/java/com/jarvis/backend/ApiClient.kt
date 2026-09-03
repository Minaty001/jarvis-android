package com.jarvis.backend

import android.util.Log
import com.jarvis.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val deviceId: String,
    val trusted: Boolean
)

data class PingResult(val isSuccess: Boolean, val latencyMs: Long, val message: String)

data class WsTicket(val ticket: String, val expiresAt: Long)

data class SkillResult(
    val id: String,
    val name: String,
    val triggerPattern: String,
    val usageCount: Int = 0
)

data class MemoryResult(
    val id: String,
    val content: String,
    val memoryType: String = "conversation",
    val importance: Float = 0.5f
)

data class MemoryStats(
    val totalMemories: Int = 0,
    val totalSkills: Int = 0,
    val lastSync: String = "Never"
)

class ApiClient(
    var baseUrl: String = Config.BACKEND_API_URL
) {
    var authToken: String? = null
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
        deviceId: String,
        deviceName: String,
        deviceModel: String,
        osVersion: String,
        enrollmentSecret: String? = null,
        onResult: (AuthTokens?) -> Unit
    ) {
        scope.launch {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("device_id", deviceId)
                put("device_name", deviceName)
                put("device_model", deviceModel)
                put("os_version", osVersion)
                if (enrollmentSecret != null) {
                    put("enrollment_secret", enrollmentSecret)
                }
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

    fun refreshAccessToken(refreshToken: String, onResult: (AuthTokens?) -> Unit) {
        scope.launch {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("refresh_token", refreshToken)
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
                        val tokens = AuthTokens(
                            accessToken = json.getString("access_token"),
                            refreshToken = json.getString("refresh_token"),
                            expiresIn = json.getInt("expires_in"),
                            deviceId = json.getString("device_id"),
                            trusted = json.optBoolean("trusted", false)
                        )
                        launch(Dispatchers.Main) { onResult(tokens) }
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

    fun getWsTicket(token: String, deviceId: String, onResult: (WsTicket?) -> Unit) {
        scope.launch {
            val cleanUrl = baseUrl.trim().trimEnd('/')
            val request = Request.Builder()
                .url("$cleanUrl/api/v1/auth/ws-ticket")
                .post("".toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $token")
                .header("X-Device-ID", deviceId)
                .header("Connection", "keep-alive")
                .header("Accept", "application/json")
                .header("X-Request-ID", "wst-${UUID.randomUUID()}")
                .build()

            try {
                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val wsTicket = WsTicket(
                            ticket = json.getString("ticket"),
                            expiresAt = json.getLong("expires_at")
                        )
                        launch(Dispatchers.Main) { onResult(wsTicket) }
                    } else {
                        Log.w(TAG, "WS ticket request failed: ${response.code}")
                        launch(Dispatchers.Main) { onResult(null) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "WS ticket request failed: ${e.message}")
                launch(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    suspend fun listSkills(deviceId: String): List<SkillResult> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/api/v1/skills?device_id=$deviceId")
                    .header("Accept", "application/json")
                    .build()

                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("skills") ?: return@withContext emptyList()
                        val skills = mutableListOf<SkillResult>()
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            skills.add(
                                SkillResult(
                                    id = item.optString("id", ""),
                                    name = item.optString("name", ""),
                                    triggerPattern = item.optString("trigger_pattern", ""),
                                    usageCount = item.optInt("usage_count", 0)
                                )
                            )
                        }
                        skills
                    } else {
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "listSkills failed: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun deleteSkill(skillId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/api/v1/skills/$skillId")
                    .delete()
                    .build()

                sharedClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.w(TAG, "deleteSkill failed: ${e.message}")
                false
            }
        }
    }

    suspend fun listMemories(deviceId: String): List<MemoryResult> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/api/v1/memories?device_id=$deviceId")
                    .header("Accept", "application/json")
                    .build()

                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("memories") ?: return@withContext emptyList()
                        val memories = mutableListOf<MemoryResult>()
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            memories.add(
                                MemoryResult(
                                    id = item.optString("id", ""),
                                    content = item.optString("content", ""),
                                    memoryType = item.optString("memory_type", "conversation"),
                                    importance = item.optDouble("importance", 0.5).toFloat()
                                )
                            )
                        }
                        memories
                    } else {
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "listMemories failed: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun deleteMemory(memoryId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/api/v1/memories/$memoryId")
                    .delete()
                    .build()

                sharedClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.w(TAG, "deleteMemory failed: ${e.message}")
                false
            }
        }
    }

    suspend fun getMemoryStats(deviceId: String): MemoryStats {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/api/v1/memory/stats?device_id=$deviceId")
                    .header("Accept", "application/json")
                    .build()

                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        MemoryStats(
                            totalMemories = json.optInt("total_memories", 0),
                            totalSkills = json.optInt("total_skills", 0),
                            lastSync = json.optString("last_sync", "Never")
                        )
                    } else {
                        MemoryStats()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "getMemoryStats failed: ${e.message}")
                MemoryStats()
            }
        }
    }

    suspend fun getRecentMemories(deviceId: String, limit: Int = 20): List<MemoryResult> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/api/v1/memories?device_id=$deviceId&limit=$limit")
                    .header("Accept", "application/json")
                    .build()

                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("memories") ?: return@withContext emptyList()
                        val memories = mutableListOf<MemoryResult>()
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            memories.add(
                                MemoryResult(
                                    id = item.optString("id", ""),
                                    content = item.optString("content", ""),
                                    memoryType = item.optString("memory_type", "conversation"),
                                    importance = item.optDouble("importance", 0.5).toFloat()
                                )
                            )
                        }
                        memories
                    } else {
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "getRecentMemories failed: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun storeMemory(userId: String, content: String, memoryType: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val bodyJson = JSONObject().apply {
                    put("user_id", userId)
                    put("content", content)
                    put("memory_type", memoryType)
                }.toString()

                val request = Request.Builder()
                    .url("$cleanUrl/api/v1/memories")
                    .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .header("Accept", "application/json")
                    .build()

                sharedClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.w(TAG, "storeMemory failed: ${e.message}")
                false
            }
        }
    }

    suspend fun searchMemory(deviceId: String, query: String): List<MemoryResult> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val bodyJson = JSONObject().apply {
                    put("device_id", deviceId)
                    put("query", query)
                }.toString()

                val request = Request.Builder()
                    .url("$cleanUrl/api/v1/memory/search")
                    .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .header("Accept", "application/json")
                    .build()

                sharedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("memories") ?: return@withContext emptyList()
                        val memories = mutableListOf<MemoryResult>()
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            memories.add(
                                MemoryResult(
                                    id = item.optString("id", ""),
                                    content = item.optString("content", ""),
                                    memoryType = item.optString("memory_type", "conversation"),
                                    importance = item.optDouble("importance", 0.5).toFloat()
                                )
                            )
                        }
                        memories
                    } else {
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "searchMemory failed: ${e.message}")
                emptyList()
            }
        }
    }

    fun sendCommand(
        command: String,
        token: String? = authToken,
        deviceId: String = "android-device",
        context: Map<String, Any> = emptyMap(),
        onResult: (WsMessage.CommandResponse?) -> Unit
    ) {
        scope.launch {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val bodyJson = JSONObject().apply {
                    put("command", command)
                    val ctxObj = JSONObject()
                    context.forEach { (k, v) -> ctxObj.put(k, v) }
                    put("context", ctxObj)
                }.toString()

                val builder = Request.Builder()
                    .url("$cleanUrl/api/v1/command")
                    .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .header("Accept", "application/json")
                    .header("X-Device-ID", deviceId)

                val effectiveToken = token ?: authToken
                if (!effectiveToken.isNullOrBlank()) {
                    builder.header("Authorization", "Bearer $effectiveToken")
                }

                sharedClient.newCall(builder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val parsed = WsMessage.parse(body)
                        if (parsed is WsMessage.CommandResponse) {
                            launch(Dispatchers.Main) { onResult(parsed) }
                        } else {
                            val json = JSONObject(body)
                            val resObj = json.optJSONObject("result") ?: json
                            val actionsArr = resObj.optJSONArray("actions")
                            val actions = mutableListOf<WsMessage.WsAction>()
                            if (actionsArr != null) {
                                for (i in 0 until actionsArr.length()) {
                                    val item = actionsArr.getJSONObject(i)
                                    val params = mutableMapOf<String, String>()
                                    val pJson = item.optJSONObject("params")
                                    if (pJson != null) {
                                        for (k in pJson.keys()) {
                                            params[k] = pJson.optString(k, "")
                                        }
                                    }
                                    actions.add(WsMessage.WsAction(item.optString("type", ""), params))
                                }
                            }
                            val cmdResp = WsMessage.CommandResponse(
                                intent = resObj.optString("intent", "unknown"),
                                response = resObj.optString("response", ""),
                                actions = actions,
                                provider = resObj.optString("provider", null)
                            )
                            launch(Dispatchers.Main) { onResult(cmdResp) }
                        }
                    } else {
                        Log.w(TAG, "sendCommand failed HTTP ${response.code}")
                        launch(Dispatchers.Main) { onResult(null) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "sendCommand failed: ${e.message}")
                launch(Dispatchers.Main) { onResult(null) }
            }
        }
    }
}