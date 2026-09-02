package com.jarvis.automation

import android.util.Log
import org.json.JSONObject

sealed interface ActionResult {
    data object Success : ActionResult
    data class Failed(val reason: String) : ActionResult
    data class ScreenContent(val content: String) : ActionResult
    data class BatteryInfo(val summary: String) : ActionResult
    data class CalendarInfo(val summary: String) : ActionResult
    data class NeedsPermission(val permission: String) : ActionResult
    data class Unsupported(val reason: String) : ActionResult
}

class SkillExecutor(
    private val automationController: AutomationController,
    private val confirmationManager: ConfirmationManager? = null
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
            val params = action.optJSONObject("params") ?: JSONObject()

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
                val result = confirmationManager?.requestConfirmation(type, riskLevel, paramMap)
                if (result != ConfirmationResult.ALLOWED) {
                    Log.i(TAG, "Action confirmation $result: $type")
                    return false
                }
            }

            val result = executeAction(type, params)
            when (result) {
                is ActionResult.Success -> { /* continue */ }
                is ActionResult.ScreenContent -> { lastScreenContent = result.content }
                is ActionResult.BatteryInfo -> { Log.d(TAG, result.summary) }
                is ActionResult.CalendarInfo -> { Log.d(TAG, result.summary) }
                is ActionResult.Failed -> {
                    Log.e(TAG, "Action failed: $type (reason: ${result.reason})")
                    return false
                }
                is ActionResult.NeedsPermission -> {
                    Log.w(TAG, "Action needs permission: ${result.permission}")
                    return false
                }
                is ActionResult.Unsupported -> {
                    Log.w(TAG, "Action unsupported: ${result.reason}")
                    return false
                }
            }

            kotlinx.coroutines.delay(300)
        }
        return true
    }

    private suspend fun executeAction(type: String, params: JSONObject): ActionResult {
        return when (type) {
            "open_app" -> {
                val pkg = params.optString("package", "")
                if (pkg.isBlank()) return ActionResult.Failed("Missing package")
                val launched = automationController.appController.launchApp(pkg)
                if (launched) ActionResult.Success else ActionResult.Failed("App not found: $pkg")
            }
            "tap" -> {
                val text = params.optString("text", "")
                if (text.isBlank()) return ActionResult.Failed("Missing text")
                if (automationController.tapElement(text)) ActionResult.Success else ActionResult.Failed("Tap failed: $text")
            }
            "type" -> {
                val text = params.optString("text", "")
                if (text.isBlank()) return ActionResult.Failed("Missing text")
                if (automationController.typeText(text)) ActionResult.Success else ActionResult.Failed("Type failed")
            }
            "swipe" -> {
                val service = JarvisAccessibilityService.instance
                    ?: return ActionResult.NeedsPermission("Accessibility")
                val dir = params.optString("direction", "up")
                val ok = when (dir) {
                    "up" -> service.swipeUp()
                    "down" -> service.swipeDown()
                    "left" -> service.swipeLeft()
                    "right" -> service.swipeRight()
                    else -> false
                }
                if (ok) ActionResult.Success else ActionResult.Failed("Swipe $dir failed")
            }
            "wait" -> {
                val ms = params.optLong("durationMs", 1000).coerceIn(0, 30000)
                kotlinx.coroutines.delay(ms)
                ActionResult.Success
            }
            "go_back" -> if (automationController.goBack()) ActionResult.Success else ActionResult.Failed("goBack failed")
            "go_home" -> if (automationController.goHome()) ActionResult.Success else ActionResult.Failed("goHome failed")
            "read_screen" -> {
                val content = automationController.readScreen()
                if (content.isNotBlank()) ActionResult.ScreenContent(content) else ActionResult.Failed("No screen content")
            }
            "send_sms" -> {
                val phone = params.optString("phone", "")
                val message = params.optString("message", "")
                if (phone.isBlank() || message.isBlank()) return ActionResult.Failed("Missing phone or message")
                val sent = automationController.sendSms(phone, message)
                if (sent.success) ActionResult.Success else ActionResult.Failed("SMS failed")
            }
            "bluetooth_on" -> if (automationController.toggleBluetooth(true)) ActionResult.Success else ActionResult.Failed("BT on failed")
            "bluetooth_off" -> if (automationController.toggleBluetooth(false)) ActionResult.Success else ActionResult.Failed("BT off failed")
            "bluetooth_toggle" -> if (automationController.toggleBluetoothAuto()) ActionResult.Success else ActionResult.Failed("BT toggle failed")
            "wifi_on" -> if (automationController.toggleWifi(true)) ActionResult.Success else ActionResult.Failed("WiFi on failed")
            "wifi_off" -> if (automationController.toggleWifi(false)) ActionResult.Success else ActionResult.Failed("WiFi off failed")
            "wifi_toggle" -> if (automationController.toggleWifiAuto()) ActionResult.Success else ActionResult.Failed("WiFi toggle failed")
            "battery_status" -> ActionResult.BatteryInfo(automationController.getBatterySummary())
            "calendar_today" -> ActionResult.CalendarInfo(automationController.getCalendarSummary())
            "calendar_search" -> {
                val query = params.optString("query", "")
                if (query.isBlank()) return ActionResult.Failed("Missing query")
                val events = automationController.searchCalendar(query)
                ActionResult.CalendarInfo(events.joinToString { it.title })
            }
            "share_text" -> {
                val text = params.optString("text", "")
                if (text.isBlank()) return ActionResult.Failed("Missing text")
                if (automationController.shareText(text)) ActionResult.Success else ActionResult.Failed("Share failed")
            }
            else -> ActionResult.Unsupported("Unknown action: $type")
        }
    }
}
