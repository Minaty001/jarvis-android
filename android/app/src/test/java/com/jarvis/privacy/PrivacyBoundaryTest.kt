package com.jarvis.privacy

import org.junit.Assert.*
import org.junit.Test

class PrivacyBoundaryTest {

    @Test
    fun `banking apps are blocked`() {
        assertEquals(
            PrivacyBoundary.PrivacyAction.BLOCK,
            PrivacyBoundary.evaluate("com.bank.mobile", null)
        )
    }

    @Test
    fun `password managers are blocked`() {
        assertEquals(
            PrivacyBoundary.PrivacyAction.BLOCK,
            PrivacyBoundary.evaluate("com.lastpass.android", null)
        )
    }

    @Test
    fun `credit card numbers are redacted`() {
        val result = PrivacyBoundary.redactScreenContent(
            "Card number: 4111 1111 1111 1111",
            "com.safe.app"
        )
        assertFalse(result.contains("4111"))
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun `OTP patterns are redacted`() {
        val result = PrivacyBoundary.redactScreenContent(
            "Your OTP is 123456",
            "com.safe.app"
        )
        assertFalse(result.contains("123456"))
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun `safe content passes through`() {
        assertEquals(
            PrivacyBoundary.PrivacyAction.ALLOW,
            PrivacyBoundary.evaluate("com.safe.app", "Hello world")
        )
    }

    @Test
    fun `sensitive data detection works`() {
        assertTrue(PrivacyBoundary.containsSensitiveData("Your OTP is 123456"))
        assertTrue(PrivacyBoundary.containsSensitiveData("Card: 4111111111111111"))
        assertFalse(PrivacyBoundary.containsSensitiveData("Hello world"))
    }

    @Test
    fun `isBankingApp detects banking packages`() {
        assertTrue(PrivacyBoundary.isBankingApp("com.bank.mobile"))
        assertTrue(PrivacyBoundary.isBankingApp("com.finance.wallet"))
        assertFalse(PrivacyBoundary.isBankingApp("com.chrome"))
    }

    @Test
    fun `isPasswordManager detects password packages`() {
        assertTrue(PrivacyBoundary.isPasswordManager("com.lastpass.android"))
        assertTrue(PrivacyBoundary.isPasswordManager("com.bitwarden"))
        assertFalse(PrivacyBoundary.isPasswordManager("com.chrome"))
    }
}
