package com.jarvis.automation

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

enum class RiskLevel { AUTOMATIC, LOW, MEDIUM, HIGH, FORBIDDEN }

enum class ActionType(val value: String) {
    OPEN_APP("open_app"),
    TAP("tap"),
    TYPE("type"),
    SWIPE("swipe"),
    GO_BACK("press_back"),
    READ_SCREEN("read_screen"),
    SEND_SMS("send_sms"),
    MAKE_CALL("make_call"),
    OPEN_URL("open_url"),
    MEDIA_CONTROL("media_control"),
    WIFI("wifi"),
    BLUETOOTH("bluetooth"),
    CALENDAR("calendar"),
    SHARE("share"),
    GO_HOME("go_home"),
    WAIT("wait"),
    BATTERY_STATUS("battery_status");

    companion object {
        private val BY_NAME = entries.associateBy { it.value }
        fun fromString(value: String): ActionType? = BY_NAME[value]
    }
}

data class ActionPlan(
    val actions: List<ActionItem>,
    val requestId: String = "req_${System.currentTimeMillis()}"
)

data class ActionItem(
    val type: ActionType,
    val params: Map<String, String> = emptyMap(),
    val riskLevel: RiskLevel = RiskLevel.AUTOMATIC
)

data class ActionPolicy(
    val type: ActionType,
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
        ActionType.OPEN_APP to ActionPolicy(ActionType.OPEN_APP, RiskLevel.AUTOMATIC, requiredParams = listOf("package")),
        ActionType.READ_SCREEN to ActionPolicy(ActionType.READ_SCREEN, RiskLevel.AUTOMATIC),
        ActionType.SWIPE to ActionPolicy(ActionType.SWIPE, RiskLevel.AUTOMATIC, optionalParams = listOf("direction")),
        ActionType.WAIT to ActionPolicy(ActionType.WAIT, RiskLevel.AUTOMATIC, optionalParams = listOf("durationMs")),
        ActionType.GO_BACK to ActionPolicy(ActionType.GO_BACK, RiskLevel.AUTOMATIC),
        ActionType.GO_HOME to ActionPolicy(ActionType.GO_HOME, RiskLevel.AUTOMATIC),
        ActionType.TAP to ActionPolicy(ActionType.TAP, RiskLevel.LOW, requiredParams = listOf("text")),
        ActionType.TYPE to ActionPolicy(ActionType.TYPE, RiskLevel.LOW, requiredParams = listOf("text")),
        ActionType.BATTERY_STATUS to ActionPolicy(ActionType.BATTERY_STATUS, RiskLevel.AUTOMATIC),
        ActionType.CALENDAR to ActionPolicy(ActionType.CALENDAR, RiskLevel.LOW, requiredParams = listOf("action")),
        ActionType.BLUETOOTH to ActionPolicy(ActionType.BLUETOOTH, RiskLevel.MEDIUM, requiredParams = listOf("action")),
        ActionType.WIFI to ActionPolicy(ActionType.WIFI, RiskLevel.MEDIUM, requiredParams = listOf("action")),
        ActionType.SEND_SMS to ActionPolicy(ActionType.SEND_SMS, RiskLevel.HIGH, requiresConfirmation = true, requiredParams = listOf("phone", "message")),
        ActionType.MAKE_CALL to ActionPolicy(ActionType.MAKE_CALL, RiskLevel.HIGH, requiresConfirmation = true, requiredParams = listOf("phone")),
        ActionType.OPEN_URL to ActionPolicy(ActionType.OPEN_URL, RiskLevel.MEDIUM, requiredParams = listOf("url")),
        ActionType.MEDIA_CONTROL to ActionPolicy(ActionType.MEDIA_CONTROL, RiskLevel.LOW, requiredParams = listOf("action")),
        ActionType.SHARE to ActionPolicy(ActionType.SHARE, RiskLevel.MEDIUM, requiredParams = listOf("text")),
    )

    private val forbiddenTypes = setOf(
        "install_apk",
        "delete_system",
        "factory_reset",
        "wipe_data",
        "financial_transfer",
    )

    fun parseActions(actionsJson: JSONArray): ActionPlan {
        val actions = mutableListOf<ActionItem>()
        for (i in 0 until actionsJson.length()) {
            val action = actionsJson.getJSONObject(i)
            val typeStr = action.optString("type", "")
            val type = ActionType.fromString(typeStr) ?: continue
            val params = mutableMapOf<String, String>()
            val paramsJson = action.optJSONObject("params") ?: JSONObject()
            for (key in paramsJson.keys()) {
                params[key] = paramsJson.optString(key, "")
            }
            val policy = policies[type]
            actions.add(ActionItem(type, params, policy?.riskLevel ?: RiskLevel.MEDIUM))
        }
        return ActionPlan(actions)
    }

    fun validate(action: JSONObject): ValidationResult {
        val type = action.optString("type", "")
        if (type.isBlank()) {
            return ValidationResult(false, "Action type is empty")
        }
        if (type in forbiddenTypes) {
            Log.w(TAG, "Forbidden action type: $type")
            return ValidationResult(false, "Action type '$type' is not allowed")
        }
        val actionType = ActionType.fromString(type)
        if (actionType == null) {
            Log.w(TAG, "Unknown action type: $type — BLOCKED")
            return ValidationResult(false, "Unknown action type: '$type' is not allowed")
        }

        val policy = policies[actionType] ?: return ValidationResult(false, "No policy for $type")

        val params = action.optJSONObject("params") ?: JSONObject()
        val missing = policy.requiredParams.filter { params.optString(it, "").isBlank() }
        if (missing.isNotEmpty()) {
            return ValidationResult(false, "Action '$type' missing required params: ${missing.joinToString()}")
        }

        return ValidationResult(true)
    }

    fun validatePlan(plan: ActionPlan): ValidationResult {
        for (action in plan.actions) {
            val policy = policies[action.type] ?: continue
            val missing = policy.requiredParams.filter { action.params[it].isNullOrBlank() }
            if (missing.isNotEmpty()) {
                return ValidationResult(false, "Action '${action.type.value}' missing: ${missing.joinToString()}")
            }
        }
        return ValidationResult(true)
    }

    fun requiresConfirmation(action: JSONObject): Boolean {
        val type = action.optString("type", "")
        val actionType = ActionType.fromString(type) ?: return false
        val policy = policies[actionType] ?: return false
        return policy.requiresConfirmation
    }

    fun getRiskLevel(action: JSONObject): RiskLevel {
        val type = action.optString("type", "")
        val actionType = ActionType.fromString(type) ?: return RiskLevel.MEDIUM
        return policies[actionType]?.riskLevel ?: RiskLevel.MEDIUM
    }
}
