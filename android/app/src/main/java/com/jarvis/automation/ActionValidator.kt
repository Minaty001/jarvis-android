package com.jarvis.automation

import android.util.Log
import org.json.JSONObject

enum class RiskLevel { AUTOMATIC, LOW, MEDIUM, HIGH, FORBIDDEN }

data class ActionPolicy(
    val type: String,
    val riskLevel: RiskLevel,
    val requiresConfirmation: Boolean = false,
    val requiredParams: List<String> = emptyList(),
    val optionalParams: List<String> = emptyList(),
)

data class ValidationResult(
    val isValid: Boolean,
    val reason: String? = null
)

object ActionValidator {
    private const val TAG = "ActionValidator"

    private val policies = mapOf(
        "open_app" to ActionPolicy("open_app", RiskLevel.AUTOMATIC, requiredParams = listOf("package")),
        "read_screen" to ActionPolicy("read_screen", RiskLevel.AUTOMATIC),
        "swipe" to ActionPolicy("swipe", RiskLevel.AUTOMATIC, optionalParams = listOf("direction")),
        "wait" to ActionPolicy("wait", RiskLevel.AUTOMATIC, optionalParams = listOf("durationMs")),
        "go_back" to ActionPolicy("go_back", RiskLevel.AUTOMATIC),
        "go_home" to ActionPolicy("go_home", RiskLevel.AUTOMATIC),
        "tap" to ActionPolicy("tap", RiskLevel.LOW, requiredParams = listOf("text")),
        "type" to ActionPolicy("type", RiskLevel.LOW, requiredParams = listOf("text")),
        "battery_status" to ActionPolicy("battery_status", RiskLevel.AUTOMATIC),
        "calendar_today" to ActionPolicy("calendar_today", RiskLevel.LOW),
        "calendar_search" to ActionPolicy("calendar_search", RiskLevel.LOW, requiredParams = listOf("query")),
        "bluetooth_on" to ActionPolicy("bluetooth_on", RiskLevel.MEDIUM),
        "bluetooth_off" to ActionPolicy("bluetooth_off", RiskLevel.MEDIUM),
        "bluetooth_toggle" to ActionPolicy("bluetooth_toggle", RiskLevel.MEDIUM),
        "wifi_on" to ActionPolicy("wifi_on", RiskLevel.MEDIUM),
        "wifi_off" to ActionPolicy("wifi_off", RiskLevel.MEDIUM),
        "wifi_toggle" to ActionPolicy("wifi_toggle", RiskLevel.MEDIUM),
        "send_sms" to ActionPolicy("send_sms", RiskLevel.HIGH, requiresConfirmation = true, requiredParams = listOf("phone", "message")),
        "share_text" to ActionPolicy("share_text", RiskLevel.MEDIUM, requiredParams = listOf("text")),
    )

    private val forbiddenTypes = setOf(
        "install_apk",
        "delete_system",
        "factory_reset",
        "wipe_data",
        "financial_transfer",
    )

    fun validate(action: JSONObject): ValidationResult {
        val type = action.optString("type", "")
        if (type.isBlank()) {
            return ValidationResult(false, "Action type is empty")
        }
        if (type in forbiddenTypes) {
            Log.w(TAG, "Forbidden action type: $type")
            return ValidationResult(false, "Action type '$type' is not allowed")
        }
        val policy = policies[type]
        if (policy == null) {
            Log.w(TAG, "Unknown action type: $type — BLOCKED")
            return ValidationResult(false, "Unknown action type: '$type' is not allowed")
        }

        val params = action.optJSONObject("params") ?: JSONObject()
        val missing = policy.requiredParams.filter { params.optString(it, "").isBlank() }
        if (missing.isNotEmpty()) {
            return ValidationResult(false, "Action '$type' missing required params: ${missing.joinToString()}")
        }

        return ValidationResult(true)
    }

    fun requiresConfirmation(action: JSONObject): Boolean {
        val type = action.optString("type", "")
        val policy = policies[type] ?: return false
        return policy.requiresConfirmation
    }

    fun getRiskLevel(action: JSONObject): RiskLevel {
        val type = action.optString("type", "")
        return policies[type]?.riskLevel ?: RiskLevel.MEDIUM
    }
}
