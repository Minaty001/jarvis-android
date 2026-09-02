package com.jarvis.auth

import android.content.Context
import android.util.Log
import com.jarvis.backend.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthManager(private val context: Context) {
    companion object {
        private const val TAG = "AuthManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tokenStore = TokenStore(context)
    private val apiClient = ApiClient()

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val currentState: AuthState get() = _state.value
    val isAuthenticated: Boolean get() = _state.value is AuthState.Authenticated
    val deviceId: String? get() = tokenStore.getDeviceId()

    private val refreshMutex = Any()
    private var refreshJob: kotlinx.coroutines.Job? = null

    fun initialize() {
        scope.launch {
            val accessToken = tokenStore.getAccessToken()
            val refreshToken = tokenStore.getRefreshToken()
            val deviceId = tokenStore.getDeviceId()

            when {
                accessToken == null || refreshToken == null || deviceId == null -> {
                    _state.value = AuthState.LoggedOut
                }
                tokenStore.isAccessTokenExpired() -> {
                    if (tokenStore.isRefreshTokenExpired()) {
                        _state.value = AuthState.LoggedOut
                    } else {
                        refreshAccessToken()
                    }
                }
                else -> {
                    _state.value = AuthState.Authenticated
                }
            }
        }
    }

    fun registerDevice() {
        if (_state.value != AuthState.LoggedOut) return
        _state.value = AuthState.Registering
        scope.launch {
            val deviceId = java.util.UUID.randomUUID().toString()
            apiClient.registerDevice(deviceId, "Android", android.os.Build.MODEL, android.os.Build.VERSION.RELEASE) { result ->
                if (result != null) {
                    tokenStore.saveTokens(
                        result.accessToken,
                        result.refreshToken,
                        result.deviceId,
                        result.expiresIn,
                        result.expiresIn * 30
                    )
                    _state.value = AuthState.Authenticated
                    Log.i(TAG, "Device registered successfully")
                } else {
                    _state.value = AuthState.Error("Registration failed")
                    Log.e(TAG, "Device registration failed")
                }
            }
        }
    }

    fun refreshAccessToken() {
        synchronized(refreshMutex) {
            if (refreshJob?.isActive == true) return
            refreshJob = scope.launch {
                _state.value = AuthState.Refreshing
                val refreshToken = tokenStore.getRefreshToken() ?: run {
                    _state.value = AuthState.LoggedOut
                    return@launch
                }
                apiClient.refreshAccessToken(refreshToken) { result ->
                    if (result != null) {
                        tokenStore.saveTokens(
                            result.accessToken,
                            result.refreshToken,
                            result.deviceId,
                            result.expiresIn,
                            result.expiresIn * 30
                        )
                        _state.value = AuthState.Authenticated
                        Log.i(TAG, "Token refreshed successfully")
                    } else {
                        tokenStore.clearTokens()
                        _state.value = AuthState.LoggedOut
                        Log.e(TAG, "Token refresh failed, logged out")
                    }
                }
            }
        }
    }

    fun logout() {
        tokenStore.clearTokens()
        _state.value = AuthState.LoggedOut
        Log.i(TAG, "User logged out")
    }

    fun getAccessTokenForRequest(): String? {
        if (isAuthenticated && !tokenStore.isAccessTokenExpired()) {
            return tokenStore.getAccessToken()
        }
        return null
    }
}