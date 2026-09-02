package com.jarvis.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "jarvis_token_store",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ACCESS_EXPIRY = "access_expiry_ms"
        private const val KEY_REFRESH_EXPIRY = "refresh_expiry_ms"
    }

    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        deviceId: String,
        accessExpiresIn: Int,
        refreshExpiresIn: Int
    ) {
        val accessExpiry = System.currentTimeMillis() + (accessExpiresIn * 1000L)
        val refreshExpiry = System.currentTimeMillis() + (refreshExpiresIn * 1000L)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_DEVICE_ID, deviceId)
            .putLong(KEY_ACCESS_EXPIRY, accessExpiry)
            .putLong(KEY_REFRESH_EXPIRY, refreshExpiry)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun isAccessTokenExpired(): Boolean {
        val expiry = prefs.getLong(KEY_ACCESS_EXPIRY, 0)
        return System.currentTimeMillis() >= expiry
    }

    fun isRefreshTokenExpired(): Boolean {
        val expiry = prefs.getLong(KEY_REFRESH_EXPIRY, 0)
        return System.currentTimeMillis() >= expiry
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
    }
}