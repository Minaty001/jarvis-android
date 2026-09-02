package com.jarvis.backend

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

class AuthTokenManager(context: Context) {

    companion object {
        private const val TAG = "AuthTokenManager"
        private const val PREFS_NAME = "jarvis_auth_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TRUSTED = "trusted"
    }

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val accessToken: String? get() = prefs.getString(KEY_ACCESS_TOKEN, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH_TOKEN, null)
    val deviceId: String? get() = prefs.getString(KEY_DEVICE_ID, null)
    val isTrusted: Boolean get() = prefs.getBoolean(KEY_TRUSTED, false)
    val isAuthenticated: Boolean get() = accessToken != null

    fun saveTokens(access: String, refresh: String, deviceId: String, trusted: Boolean = false) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, access)
            .putString(KEY_REFRESH_TOKEN, refresh)
            .putString(KEY_DEVICE_ID, deviceId)
            .putBoolean(KEY_TRUSTED, trusted)
            .apply()
        Log.i(TAG, "Tokens saved for device: $deviceId")
    }

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    fun isTokenExpired(token: String): Boolean {
        return try {
            val payload = decodeJwtPayload(token)
            val exp = payload.optLong("exp", 0)
            val now = System.currentTimeMillis() / 1000
            exp < now
        } catch (e: Exception) {
            true
        }
    }

    fun getTokenExpiryMs(token: String): Long {
        return try {
            val payload = decodeJwtPayload(token)
            val exp = payload.optLong("exp", 0)
            val now = System.currentTimeMillis() / 1000
            ((exp - now) * 1000).coerceAtLeast(0)
        } catch (e: Exception) {
            0
        }
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
        Log.i(TAG, "Tokens cleared")
    }

    private fun decodeJwtPayload(token: String): JSONObject {
        val parts = token.split(".")
        if (parts.size < 2) throw IllegalArgumentException("Invalid JWT")
        val payload = parts[1]
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return JSONObject(String(decoded))
    }
}
