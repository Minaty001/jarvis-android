package com.jarvis.backend

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthTokenManager(context: Context) {

    companion object {
        private const val TAG = "AuthTokenManager"
        private const val PREFS_NAME = "jarvis_auth_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TRUSTED = "trusted"
        private const val KEY_ACCESS_EXPIRY = "access_expiry_ms"
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

    fun saveTokens(access: String, refresh: String, deviceId: String, expiresIn: Int = 86400, trusted: Boolean = false) {
        val expiryMs = System.currentTimeMillis() + (expiresIn * 1000L)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, access)
            .putString(KEY_REFRESH_TOKEN, refresh)
            .putString(KEY_DEVICE_ID, deviceId)
            .putBoolean(KEY_TRUSTED, trusted)
            .putLong(KEY_ACCESS_EXPIRY, expiryMs)
            .apply()
        Log.i(TAG, "Tokens saved for device: $deviceId (expires in ${expiresIn}s)")
    }

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    fun isTokenExpired(token: String): Boolean {
        if (token.isBlank()) return true
        val storedToken = accessToken
        if (token != storedToken) return true
        val expiryMs = prefs.getLong(KEY_ACCESS_EXPIRY, 0)
        if (expiryMs == 0L) return true
        return System.currentTimeMillis() >= expiryMs
    }

    fun getTokenExpiryMs(token: String): Long {
        val expiryMs = prefs.getLong(KEY_ACCESS_EXPIRY, 0)
        return (expiryMs - System.currentTimeMillis()).coerceAtLeast(0)
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
        Log.i(TAG, "Tokens cleared")
    }
}
