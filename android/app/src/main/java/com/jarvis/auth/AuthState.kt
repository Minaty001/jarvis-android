package com.jarvis.auth

sealed interface AuthState {
    data object Loading : AuthState
    data object Registering : AuthState
    data object Authenticated : AuthState
    data object Refreshing : AuthState
    data object LoggedOut : AuthState
    data class Error(val reason: String) : AuthState
}