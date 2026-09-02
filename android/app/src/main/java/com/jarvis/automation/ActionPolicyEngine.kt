package com.jarvis.automation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PolicyDecision {
    ALLOW,
    DENY,
    CONFIRM,
    NEEDS_PERMISSION
}

data class PolicyResult(
    val decision: PolicyDecision,
    val reason: String? = null,
    val permission: String? = null
)

class ActionPolicyEngine(
    private val permissionChecker: PermissionChecker,
    private val confirmationManager: ConfirmationManager
) {
    companion object {
        private const val TAG = "ActionPolicyEngine"
    }

    suspend fun evaluatePlan(plan: ActionPlan): PolicyResult {
        for (action in plan.actions) {
            val result = evaluateAction(action)
            if (result.decision != PolicyDecision.ALLOW) {
                return result
            }
        }
        return PolicyResult(PolicyDecision.ALLOW)
    }

    suspend fun evaluateAction(action: ActionItem): PolicyResult {
        val validation = validate(action)
        if (validation.decision != PolicyDecision.ALLOW) {
            return validation
        }

        val permission = checkPermissions(action)
        if (permission.decision != PolicyDecision.ALLOW) {
            return permission
        }

        val confirmation = checkConfirmation(action)
        if (confirmation.decision != PolicyDecision.ALLOW) {
            return confirmation
        }

        return PolicyResult(PolicyDecision.ALLOW)
    }

    private fun validate(action: ActionItem): PolicyResult {
        val validation = ActionValidator.validate(
            org.json.JSONObject().apply {
                put("type", action.type.value)
                put("params", org.json.JSONObject(action.params))
            }
        )
        return if (validation.isValid) {
            PolicyResult(PolicyDecision.ALLOW)
        } else {
            PolicyResult(PolicyDecision.DENY, validation.reason)
        }
    }

    private fun checkPermissions(action: ActionItem): PolicyResult {
        val required = when (action.type) {
            ActionType.SEND_SMS -> PermissionChecker.Permission.SMS
            ActionType.READ_SCREEN -> PermissionChecker.Permission.ACCESSIBILITY
            ActionType.TAP, ActionType.TYPE, ActionType.SWIPE -> PermissionChecker.Permission.ACCESSIBILITY
            else -> null
        }

        if (required != null && !permissionChecker.hasPermission(required)) {
            return PolicyResult(
                PolicyDecision.NEEDS_PERMISSION,
                "Missing permission: $required",
                required.name
            )
        }

        return PolicyResult(PolicyDecision.ALLOW)
    }

    private suspend fun checkConfirmation(action: ActionItem): PolicyResult {
        if (action.riskLevel == RiskLevel.HIGH) {
            val result = confirmationManager.requestConfirmation(
                action.type.value,
                action.riskLevel,
                action.params
            )
            return when (result) {
                ConfirmationResult.ALLOWED -> PolicyResult(PolicyDecision.ALLOW)
                ConfirmationResult.DENIED -> PolicyResult(PolicyDecision.DENY, "User denied")
                ConfirmationResult.TIMEOUT -> PolicyResult(PolicyDecision.DENY, "Confirmation timeout")
                ConfirmationResult.CANCELLED -> PolicyResult(PolicyDecision.DENY, "Confirmation cancelled")
            }
        }

        if (action.riskLevel == RiskLevel.FORBIDDEN) {
            return PolicyResult(PolicyDecision.DENY, "Forbidden action type")
        }

        return PolicyResult(PolicyDecision.ALLOW)
    }

    suspend fun executePlan(plan: ActionPlan, executor: SkillExecutor): Boolean {
        val policyResult = evaluatePlan(plan)
        if (policyResult.decision != PolicyDecision.ALLOW) {
            Log.w(TAG, "Plan rejected: ${policyResult.reason}")
            return false
        }

        val json = org.json.JSONArray()
        for (action in plan.actions) {
            json.put(org.json.JSONObject().apply {
                put("type", action.type.value)
                put("params", org.json.JSONObject(action.params))
            })
        }

        return executor.execute(json)
    }
}

class PermissionChecker {
    enum class Permission {
        ACCESSIBILITY,
        SMS,
        CONTACTS,
        CALENDAR
    }

    fun hasPermission(permission: Permission): Boolean {
        return when (permission) {
            Permission.ACCESSIBILITY -> JarvisAccessibilityService.instance != null
            Permission.SMS -> true
            Permission.CONTACTS -> true
            Permission.CALENDAR -> true
        }
    }
}
