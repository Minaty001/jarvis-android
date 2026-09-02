package com.jarvis.automation

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface ConfirmationGate {
    suspend fun requestConfirmation(actionType: String, riskLevel: RiskLevel, params: Map<String, String>): Boolean
}

class SkillExecutor(
    private val automationController: AutomationController,
    private val confirmationGate: ConfirmationGate? = null
) {
    companion object {
        private const val TAG = "SkillExecutor"
    }

    suspend fun execute(actionsJson: org.json.JSONArray): Boolean {
        for (i in 0 until actionsJson.length()) {
            val action = actionsJson.getJSONObject(i)
            val type = action.getString("type")
            val params = action.optJSONObject("params") ?: org.json.JSONObject()

            val validation = ActionValidator.validate(action)
            if (!validation.isValid) {
                Log.w(TAG, "Action validation failed: ${validation.reason}")
                return false
            }

            if (ActionValidator.requiresConfirmation(action)) {
                val riskLevel = ActionValidator.getRiskLevel(action)
                val paramMap = mutableMapOf<String, String>()
                for (key in params.keys()) {
                    paramMap[key] = params.optString(key, "")
                }
                val confirmed = confirmationGate?.requestConfirmation(type, riskLevel, paramMap) ?: false
                if (!confirmed) {
                    Log.i(TAG, "Action confirmation denied: $type")
                    return false
                }
            }

            val success = when (type) {
                "open_app" -> {
                    val pkg = params.optString("package", "")
                    if (pkg.isNotBlank()) automationController.appController.launchApp(pkg) else false
                }
                "tap" -> automationController.tapElement(params.optString("text", ""))
                "type" -> automationController.typeText(params.optString("text", ""))
                "swipe" -> {
                    val service = JarvisAccessibilityService.instance
                    when (params.optString("direction", "up")) {
                        "up" -> service?.swipeUp() ?: false
                        "down" -> service?.swipeDown() ?: false
                        "left" -> service?.swipeLeft() ?: false
                        "right" -> service?.swipeRight() ?: false
                        else -> false
                    }
                }
                "wait" -> { kotlinx.coroutines.delay(params.optLong("durationMs", 1000)); true }
                "go_back" -> automationController.goBack()
                "go_home" -> automationController.goHome()
                "read_screen" -> { automationController.readScreen(); true }
                "send_sms" -> {
                    val phone = params.optString("phone", "")
                    val message = params.optString("message", "")
                    if (phone.isNotBlank() && message.isNotBlank()) automationController.sendSms(phone, message).success else false
                }
                "bluetooth_on" -> automationController.toggleBluetooth(true)
                "bluetooth_off" -> automationController.toggleBluetooth(false)
                "bluetooth_toggle" -> automationController.toggleBluetoothAuto()
                "wifi_on" -> automationController.toggleWifi(true)
                "wifi_off" -> automationController.toggleWifi(false)
                "wifi_toggle" -> automationController.toggleWifiAuto()
                "battery_status" -> { Log.d(TAG, automationController.getBatterySummary()); true }
                "calendar_today" -> { Log.d(TAG, automationController.getCalendarSummary()); true }
                "calendar_search" -> {
                    val query = params.optString("query", "")
                    Log.d(TAG, automationController.searchCalendar(query).joinToString { it.title })
                    true
                }
                "share_text" -> automationController.shareText(params.optString("text", ""))
                else -> { Log.w(TAG, "Unknown action type: $type"); false }
            }

            if (!success) { Log.e(TAG, "Action failed: $type"); return false }
            kotlinx.coroutines.delay(500)
        }
        return true
    }
}
