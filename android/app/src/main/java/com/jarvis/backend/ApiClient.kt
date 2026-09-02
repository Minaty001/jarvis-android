package com.jarvis.backend

import android.util.Log
import com.jarvis.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Route
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>
    data object NetworkError : ApiResult<Nothing>
    data object Unauthorized : ApiResult<Nothing>
}

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
    private val authManager: AuthManager
) {
    companion object {
        private const val TAG = "ApiClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val token = authManager.accessToken
        if (token == null || authManager.isTokenExpired()) {
            chain.proceed(original)
        } else {
            val newRequest = original.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("X-Device-ID", authManager.deviceId ?: "")
                .build()
            chain.proceed(newRequest)
        }
    }

    private val tokenAuthenticator = Authenticator { route, response ->
        if (response.code != 401) return@Authenticator null
        if (response.request().header("X-Refresh-Retry") != null) return@Authenticator null

        val refreshToken = authManager.refreshToken ?: return@Authenticator null
        Log.i(TAG, "Token expired, attempting refresh...")

        val refreshRequest = Request.Builder()
            .url("$baseUrl/api/v1/auth/refresh")
            .post(
                JSONObject().apply { put("refresh_token", refreshToken) }
                    .toString().toRequestBody(JSON_MEDIA_TYPE)
            )
            .header("X-Refresh-Retry", "true")
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val refreshResponse = client.newCall(refreshRequest).execute()
        if (!refreshResponse.isSuccessful) {
            authManager.setState(TokenState.UNAUTHENTICATED)
            return@Authenticator null
        }

        val body = refreshResponse.body?.string() ?: return@Authenticator null
        val json = JSONObject(body)
        val newAccess = json.getString("access_token")
        val newRefresh = json.getString("refresh_token")
        val deviceId = json.getString("device_id")
        val expiresIn = json.getInt("expires_in")
        val trusted = json.optBoolean("trusted", false)

        authManager.saveTokens(newAccess, newRefresh, deviceId, expiresIn, trusted)
        Log.i(TAG, "Token refresh successful")

        response.request().newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .header("X-Device-ID", deviceId)
            .build()
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(3000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // --- Bootstrap ---

    suspend fun bootstrap(): Boolean = withContext(Dispatchers.IO) {
        val deviceId = authManager.deviceId

        if (deviceId != null && authManager.isAuthenticated && !authManager.isTokenExpired()) {
            Log.i(TAG, "Bootstrap: already authenticated for $deviceId")
            return@withContext true
        }

        if (deviceId != null && authManager.refreshToken != null && authManager.isTokenExpired()) {
            Log.i(TAG, "Bootstrap: token expired, attempting refresh for $deviceId")
            authManager.setState(TokenState.REFRESHING)
            val refreshed = attemptRefresh()
            if (refreshed) return@withContext true
        }

        Log.i(TAG, "Bootstrap: registering new device")
        authManager.setState(TokenState.REGISTERING)
        val registered = attemptRegistration()
        return@withContext registered
    }

    private suspend fun attemptRefresh(): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = authManager.refreshToken ?: return@withContext false
        try {
            val bodyJson = JSONObject().apply { put("refresh_token", refreshToken) }.toString()
            val request = Request.Builder()
                .url("$baseUrl/api/v1/auth/refresh")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("X-Request-ID", "ref-${UUID.randomUUID()}")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    authManager.saveTokens(
                        access = json.getString("access_token"),
                        refresh = json.getString("refresh_token"),
                        deviceId = json.getString("device_id"),
                        expiresIn = json.getInt("expires_in"),
                        trusted = json.optBoolean("trusted", false)
                    )
                    Log.i(TAG, "Refresh successful")
                    true
                } else {
                    Log.w(TAG, "Refresh failed: ${response.code}")
                    authManager.setState(TokenState.UNAUTHENTICATED)
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Refresh error: ${e.message}")
            authManager.setState(TokenState.UNAUTHENTICATED)
            false
        }
    }

    private suspend fun attemptRegistration(): Boolean = withContext(Dispatchers.IO) {
        val deviceId = authManager.deviceId ?: return@withContext false
        try {
            val bodyJson = JSONObject().apply {
                put("device_id", deviceId)
                put("device_name", android.os.Build.MODEL)
                put("device_model", android.os.Build.MODEL)
                put("os_version", "Android ${android.os.Build.VERSION.RELEASE}")
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/api/v1/auth/token")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("X-Request-ID", "reg-${UUID.randomUUID()}")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    authManager.saveTokens(
                        access = json.getString("access_token"),
                        refresh = json.getString("refresh_token"),
                        deviceId = json.getString("device_id"),
                        expiresIn = json.getInt("expires_in"),
                        trusted = json.optBoolean("trusted", false)
                    )
                    Log.i(TAG, "Registration successful")
                    true
                } else {
                    Log.w(TAG, "Registration failed: ${response.code}")
                    authManager.setState(TokenState.UNAUTHENTICATED)
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Registration error: ${e.message}")
            authManager.setState(TokenState.UNAUTHENTICATED)
            false
        }
    }

    // --- Health ---

    fun pingBackend(urlToTest: String = baseUrl, onResult: (PingResult) -> Unit) {
        scope.launch {
            val start = System.currentTimeMillis()
            val cleanUrl = urlToTest.trim().trimEnd('/')
            val request = Request.Builder()
                .url("$cleanUrl/api/v1/health")
                .header("Accept", "application/json")
                .header("X-Request-ID", "ping-${UUID.randomUUID()}")
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
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

    // --- Memory ---

    suspend fun searchMemory(query: String, limit: Int = 5): ApiResult<List<MemoryResult>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val url = "$cleanUrl/memory/search?query=${query.encodeToURL()}&limit=$limit"
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                if (response.code == 401) return@withContext ApiResult.Unauthorized
                val body = response.body?.string() ?: return@withContext ApiResult.Error(response.code, "Empty body")
                val json = JSONObject(body)
                val results = json.getJSONArray("results")
                ApiResult.Success((0 until results.length()).map { i ->
                    val item = results.getJSONObject(i)
                    MemoryResult(
                        id = item.optString("id", ""),
                        content = item.optString("content", ""),
                        similarity = item.optDouble("similarity", 0.0)
                    )
                })
            } catch (e: Exception) {
                ApiResult.NetworkError
            }
        }

    suspend fun storeMemory(content: String, type: String = "conversation"): ApiResult<MemoryResult> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val body = JSONObject().apply {
                    put("content", content)
                    put("memoryType", type)
                }
                val request = Request.Builder()
                    .url("$cleanUrl/memory/store")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.code == 401) return@withContext ApiResult.Unauthorized
                val respBody = response.body?.string() ?: return@withContext ApiResult.Error(response.code, "Empty body")
                val json = JSONObject(respBody)
                val mem = json.getJSONObject("memory")
                ApiResult.Success(MemoryResult(id = mem.optString("id", ""), content = mem.optString("content", "")))
            } catch (e: Exception) {
                ApiResult.NetworkError
            }
        }

    suspend fun deleteMemory(memoryId: String): ApiResult<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/memory/$memoryId")
                    .delete()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.code == 401) return@withContext ApiResult.Unauthorized
                ApiResult.Success(response.isSuccessful)
            } catch (e: Exception) {
                ApiResult.NetworkError
            }
        }

    suspend fun getMemoryStats(): ApiResult<MemoryStats> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/memory/stats")
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.code == 401) return@withContext ApiResult.Unauthorized
                val body = response.body?.string() ?: return@withContext ApiResult.Error(response.code, "Empty body")
                val json = JSONObject(body)
                ApiResult.Success(MemoryStats(
                    totalMemories = json.optInt("totalMemories", 0),
                    totalSkills = json.optInt("totalSkills", 0),
                    lastSync = if (json.isNull("lastSync")) null else json.optString("lastSync")
                ))
            } catch (e: Exception) {
                ApiResult.NetworkError
            }
        }

    suspend fun getRecentMemories(limit: Int = 10): ApiResult<List<MemoryResult>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/memory/recent?limit=$limit")
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.code == 401) return@withContext ApiResult.Unauthorized
                val body = response.body?.string() ?: return@withContext ApiResult.Error(response.code, "Empty body")
                val json = JSONObject(body)
                val results = json.getJSONArray("results")
                ApiResult.Success((0 until results.length()).map { i ->
                    val item = results.getJSONObject(i)
                    MemoryResult(
                        id = item.optString("id", ""),
                        content = item.optString("content", ""),
                        memoryType = item.optString("memory_type", "")
                    )
                })
            } catch (e: Exception) {
                ApiResult.NetworkError
            }
        }

    // --- Skills ---

    suspend fun matchSkill(command: String): ApiResult<SkillResult?> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val url = "$cleanUrl/skill/match?command=${command.encodeToURL()}"
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                if (response.code == 401) return@withContext ApiResult.Unauthorized
                val body = response.body?.string() ?: return@withContext ApiResult.Error(response.code, "Empty body")
                val json = JSONObject(body)
                if (json.isNull("skill")) return@withContext ApiResult.Success(null)
                val skill = json.getJSONObject("skill")
                ApiResult.Success(SkillResult(
                    id = skill.optString("id", ""),
                    name = skill.optString("name", ""),
                    triggerPattern = skill.optString("trigger_pattern", ""),
                    actionSequence = skill.optJSONArray("action_sequence")?.toString() ?: "[]",
                    usageCount = skill.optInt("usage_count", 0),
                    matchType = skill.optString("matchType", "")
                ))
            } catch (e: Exception) {
                ApiResult.NetworkError
            }
        }

    suspend fun listSkills(): ApiResult<List<SkillResult>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/skill/list")
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.code == 401) return@withContext ApiResult.Unauthorized
                val body = response.body?.string() ?: return@withContext ApiResult.Error(response.code, "Empty body")
                val json = JSONObject(body)
                val skills = json.getJSONArray("skills")
                ApiResult.Success((0 until skills.length()).map { i ->
                    val skill = skills.getJSONObject(i)
                    SkillResult(
                        id = skill.optString("id", ""),
                        name = skill.optString("name", ""),
                        triggerPattern = skill.optString("trigger_pattern", ""),
                        actionSequence = skill.optJSONArray("action_sequence")?.toString() ?: "[]",
                        usageCount = skill.optInt("usage_count", 0)
                    )
                })
            } catch (e: Exception) {
                ApiResult.NetworkError
            }
        }

    suspend fun learnSkill(command: String, name: String, actions: JSONArray): ApiResult<SkillResult> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val body = JSONObject().apply {
                    put("command", command)
                    put("name", name)
                    put("actions", actions)
                }
                val request = Request.Builder()
                    .url("$cleanUrl/skill/learn")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.code == 401) return@withContext ApiResult.Unauthorized
                val respBody = response.body?.string() ?: return@withContext ApiResult.Error(response.code, "Empty body")
                val json = JSONObject(respBody)
                val skill = json.getJSONObject("skill")
                ApiResult.Success(SkillResult(
                    id = skill.optString("id", ""),
                    name = skill.optString("name", ""),
                    triggerPattern = skill.optString("trigger_pattern", ""),
                    actionSequence = skill.optJSONArray("action_sequence")?.toString() ?: "[]"
                ))
            } catch (e: Exception) {
                ApiResult.NetworkError
            }
        }

    suspend fun deleteSkill(skillId: String): ApiResult<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')
                val request = Request.Builder()
                    .url("$cleanUrl/skill/$skillId")
                    .delete()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.code == 401) return@withContext ApiResult.Unauthorized
                ApiResult.Success(response.isSuccessful)
            } catch (e: Exception) {
                ApiResult.NetworkError
            }
        }

    private fun String.encodeToURL(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
