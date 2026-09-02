package com.jarvis.automation

import org.junit.Assert.*
import org.junit.Test

class ActionPolicyEngineTest {

    @Test
    fun `low risk actions require no confirmation`() {
        val action = ActionItem(
            type = ActionType.OPEN_APP,
            params = mapOf("package" to "com.test"),
            riskLevel = RiskLevel.LOW
        )
        assertFalse(ActionValidator.requiresConfirmation(
            org.json.JSONObject().put("type", "open_app")
        ))
    }

    @Test
    fun `high risk actions require confirmation`() {
        assertTrue(ActionValidator.requiresConfirmation(
            org.json.JSONObject().put("type", "send_sms")
        ))
    }

    @Test
    fun `ActionType enum values match backend schema`() {
        val expectedTypes = setOf(
            "open_app", "tap", "type", "swipe", "press_back", "read_screen",
            "send_sms", "make_call", "open_url", "media_control",
            "wifi", "bluetooth", "calendar", "share"
        )
        val actualTypes = ActionType.values().map { it.value }.toSet()
        assertEquals(expectedTypes, actualTypes)
    }

    @Test
    fun `RiskLevel enum values are correct`() {
        assertEquals(3, RiskLevel.values().size)
        assertNotNull(RiskLevel.LOW)
        assertNotNull(RiskLevel.MEDIUM)
        assertNotNull(RiskLevel.HIGH)
    }
}
