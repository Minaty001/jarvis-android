package com.jarvis.backend

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TokenState {
    NO_TOKEN,
    REGISTERING,
    AUTHENTICATED,
    REFRESHING,
    UNAUTHENTICATED
}

data class AuthInfo(
    val deviceId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val isTrusted: Boolean = false
)

class AuthManager(context: Context) {

    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_NAME = "jarvis_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ACCESS_EXPIRY = "access_expiry_ms"
        private const val KEY_TRUSTED = "trusted"
    }

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<TokenState> = _state.asStateFlow()

    val currentState: TokenState get() = _state.value

    val isAuthenticated: Boolean get() = _state.value == TokenState.AUTHENTICATED

    val deviceId: String? get() = prefs.getString(KEY_DEVICE_ID, null)

    val accessToken: String? get() = prefs.getString(KEY_ACCESS_TOKEN, null)

    val refreshToken: String? get() = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveTokens(access: String, refresh: String, deviceId: String, expiresIn: Int = 86400, trusted: Boolean = false) {
        val expiryMs = System.currentTimeMillis() + (expiresIn * 1000L)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, access)
            .putString(KEY_REFRESH_TOKEN, refresh)
            .putString(KEY_DEVICE_ID, deviceId)
            .putLong(KEY_ACCESS_EXPIRY, expiryMs)
            .putBoolean(KEY_TRUSTED, trusted)
            .apply()
        _state.value = TokenState.AUTHENTICATED
        Log.i(TAG, "Tokens saved for device: $deviceId (state=AUTHENTICATED)")
    }

    fun isTokenExpired(): Boolean {
        val expiryMs = prefs.getLong(KEY_ACCESS_EXPIRY, 0)
        if (expiryMs == 0L) return true
        return System.currentTimeMillis() >= expiryMs
    }

    fun isTokenExpiringSoon(bufferMs: Long = 300_000): Boolean {
        val expiryMs = prefs.getLong(KEY_ACCESS_EXPIRY, 0)
        if (expiryMs == 0L) return true
        return System.currentTimeMillis() >= (expiryMs - bufferMs)
    }

    fun setState(newState: TokenState) {
        val old = _state.value
        _state.value = newState
        Log.i(TAG, "State: $old → $newState")
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
        _state.value = TokenState.NO_TOKEN
        Log.i(TAG, "Tokens cleared (state=NO_TOKEN)")
    }

    fun getAuthInfo(): AuthInfo? {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val device = prefs.getString(KEY_DEVICE_ID, null) ?: return null
        val expiry = prefs.getLong(KEY_ACCESS_EXPIRY, 0)
        val trusted = prefs.getBoolean(KEY_TRUSTED, false)
        return AuthInfo(
            deviceId = device,
            accessToken = access,
            refreshToken = refresh,
            expiresAt = expiry,
            isTrusted = trusted
        )
    }

    private fun loadInitialState(): TokenState {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null)
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)
        return when {
            access == null && refresh == null -> TokenState.NO_TOKEN
            isTokenExpired() -> TokenState.UNAUTHENTICATED
            else -> TokenState.AUTHENTICATED
        }
    }
}
