package com.jarvis.auth

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthManagerTest {

    @Test
    fun `AuthState starts as Unauthenticated`() {
        val state = AuthState.Unauthenticated
        assertTrue(state is AuthState.Unauthenticated)
    }

    @Test
    fun `AuthState Authenticated contains tokens`() {
        val state = AuthState.Authenticated(
            accessToken = "test_access",
            refreshToken = "test_refresh",
            expiresIn = 3600,
            deviceId = "device123"
        )
        assertTrue(state is AuthState.Authenticated)
        assertEquals("test_access", (state as AuthState.Authenticated).accessToken)
        assertEquals("device123", state.deviceId)
    }

    @Test
    fun `AuthState Error contains message`() {
        val state = AuthState.Error("Connection failed")
        assertTrue(state is AuthState.Error)
        assertEquals("Connection failed", (state as AuthState.Error).message)
    }

    @Test
    fun `AuthState Refreshing contains refresh token`() {
        val state = AuthState.Refreshing(refreshToken = "refresh_token_123")
        assertTrue(state is AuthState.Refreshing)
        assertEquals("refresh_token_123", (state as AuthState.Refreshing).refreshToken)
    }
}
