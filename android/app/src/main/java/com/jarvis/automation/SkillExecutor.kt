package com.jarvis.automation

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface ConfirmationGate {
    suspend fun requestConfirmation(actionType: String, riskLevel: RiskLevel, params: Map<String, String>): Boolean
}

data class ActionResult(
    val success: Boolean,
    val screenContent: String? = null,
    val batterySummary: String? = null,
    val calendarSummary: String? = null,
    val reason: String? = null
)

class SkillExecutor(
    private val automationController: AutomationController,
    private val confirmationGate: ConfirmationGate? = null
) {
    companion object {
        private const val TAG = "SkillExecutor"
    }

    var lastScreenContent: String? = null
        private set

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

            val result = executeAction(type, params)
            if (!result.success) {
                Log.e(TAG, "Action failed: $type (reason: ${result.reason})")
                return false
            }

            lastScreenContent = result.screenContent

            kotlinx.coroutines.delay(300)
        }
        return true
    }

    private suspend fun executeAction(type: String, params: org.json.JSONObject): ActionResult {
        return when (type) {
            "open_app" -> {
                val pkg = params.optString("package", "")
                if (pkg.isNotBlank()) {
                    val launched = automationController.appController.launchApp(pkg)
                    ActionResult(launched, reason = if (!launched) "App not found: $pkg" else null)
                } else {
                    ActionResult(false, reason = "Missing package parameter")
                }
            }
            "tap" -> {
                val text = params.optString("text", "")
                ActionResult(automationController.tapElement(text), reason = if (text.isBlank()) "Missing text parameter" else null)
            }
            "type" -> {
                val text = params.optString("text", "")
                ActionResult(automationController.typeText(text), reason = if (text.isBlank()) "Missing text parameter" else null)
            }
            "swipe" -> {
                val service = JarvisAccessibilityService.instance
                if (service == null) {
                    ActionResult(false, reason = "Accessibility service not enabled")
                } else {
                    val dir = params.optString("direction", "up")
                    val ok = when (dir) {
                        "up" -> service.swipeUp()
                        "down" -> service.swipeDown()
                        "left" -> service.swipeLeft()
                        "right" -> service.swipeRight()
                        else -> false
                    }
                    ActionResult(ok, reason = if (!ok) "Swipe failed" else null)
                }
            }
            "wait" -> {
                val ms = params.optLong("durationMs", 1000).coerceIn(0, 30000)
                kotlinx.coroutines.delay(ms)
                ActionResult(true)
            }
            "go_back" -> ActionResult(automationController.goBack())
            "go_home" -> ActionResult(automationController.goHome())
            "read_screen" -> {
                val content = automationController.readScreen()
                ActionResult(content.isNotBlank(), screenContent = content, reason = if (content.isBlank()) "No screen content" else null)
            }
            "send_sms" -> {
                val phone = params.optString("phone", "")
                val message = params.optString("message", "")
                if (phone.isNotBlank() && message.isNotBlank()) {
                    val sent = automationController.sendSms(phone, message)
                    ActionResult(sent.success, reason = if (!sent.success) "SMS failed" else null)
                } else {
                    ActionResult(false, reason = "Missing phone or message")
                }
            }
            "bluetooth_on" -> ActionResult(automationController.toggleBluetooth(true))
            "bluetooth_off" -> ActionResult(automationController.toggleBluetooth(false))
            "bluetooth_toggle" -> ActionResult(automationController.toggleBluetoothAuto())
            "wifi_on" -> ActionResult(automationController.toggleWifi(true))
            "wifi_off" -> ActionResult(automationController.toggleWifi(false))
            "wifi_toggle" -> ActionResult(automationController.toggleWifiAuto())
            "battery_status" -> {
                val summary = automationController.getBatterySummary()
                ActionResult(true, batterySummary = summary)
            }
            "calendar_today" -> {
                val summary = automationController.getCalendarSummary()
                ActionResult(true, calendarSummary = summary)
            }
            "calendar_search" -> {
                val query = params.optString("query", "")
                val events = automationController.searchCalendar(query)
                val summary = events.joinToString { it.title }
                ActionResult(true, calendarSummary = summary)
            }
            "share_text" -> {
                val text = params.optString("text", "")
                ActionResult(automationController.shareText(text), reason = if (text.isBlank()) "Missing text" else null)
            }
            else -> ActionResult(false, reason = "Unknown action type: $type")
        }
    }
}
