package com.jarvis.automation

import android.util.Log
import com.jarvis.core.PerformanceMonitor
import org.json.JSONObject

sealed interface ActionResult {
    data object Success : ActionResult
    data class Failed(val reason: String) : ActionResult
    data object Denied : ActionResult
    data object Cancelled : ActionResult
    data object Timeout : ActionResult
    data object NeedsConfirmation : ActionResult
    data class ScreenContent(val content: String) : ActionResult
    data class BatteryInfo(val summary: String) : ActionResult
    data class CalendarInfo(val summary: String) : ActionResult
    data class NeedsPermission(val permission: String) : ActionResult
    data class Unsupported(val reason: String) : ActionResult
    data object RequiresUserAction : ActionResult
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
        val plan = ActionValidator.parseActions(actionsJson)
        val validation = ActionValidator.validatePlan(plan)
        if (!validation.isValid) {
            Log.w(TAG, "ActionPlan validation failed: ${validation.reason}")
            return false
        }

        val planStart = PerformanceMonitor.startTimer("action_plan_total")
        for (action in plan.actions) {
            if (ActionValidator.requiresConfirmation(JSONObject().put("type", action.type.value))) {
                val paramMap = action.params.toMutableMap()
                val result = confirmationManager?.requestConfirmation(action.type.value, action.riskLevel, paramMap)
                if (result != ConfirmationResult.ALLOWED) {
                    Log.i(TAG, "Action confirmation $result: ${action.type.value}")
                    return false
                }
            }

            val actionStart = PerformanceMonitor.startTimer("action_${action.type.value}")
            val result = executeAction(action)
            PerformanceMonitor.endTimer("action_${action.type.value}", actionStart)
            when (result) {
                is ActionResult.Success -> { /* continue */ }
                is ActionResult.ScreenContent -> { lastScreenContent = result.content }
                is ActionResult.BatteryInfo -> { Log.d(TAG, result.summary) }
                is ActionResult.CalendarInfo -> { Log.d(TAG, result.summary) }
                is ActionResult.Failed -> {
                    Log.e(TAG, "Action failed: ${action.type.value} (reason: ${result.reason})")
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
                is ActionResult.Denied -> {
                    Log.w(TAG, "Action denied by user")
                    return false
                }
                is ActionResult.Cancelled -> {
                    Log.i(TAG, "Action cancelled")
                    return false
                }
                is ActionResult.Timeout -> {
                    Log.w(TAG, "Action timed out")
                    return false
                }
                is ActionResult.NeedsConfirmation -> {
                    Log.w(TAG, "Action needs confirmation but no manager")
                    return false
                }
                is ActionResult.RequiresUserAction -> {
                    Log.w(TAG, "Action requires user action (e.g. BT/WiFi toggle)")
                    return false
                }
            }

            kotlinx.coroutines.delay(300)
        }
        PerformanceMonitor.endTimer("action_plan_total", planStart)
        return true
    }

    private suspend fun executeAction(action: ActionItem): ActionResult {
        return when (action.type) {
            ActionType.OPEN_APP -> {
                val pkg = action.params["package"] ?: ""
                if (pkg.isBlank()) return ActionResult.Failed("Missing package")
                val launched = automationController.appController.launchApp(pkg)
                if (launched) ActionResult.Success else ActionResult.Failed("App not found: $pkg")
            }
            ActionType.TAP -> {
                val text = action.params["text"] ?: ""
                if (text.isBlank()) return ActionResult.Failed("Missing text")
                if (automationController.tapElement(text)) ActionResult.Success else ActionResult.Failed("Tap failed: $text")
            }
            ActionType.TYPE -> {
                val text = action.params["text"] ?: ""
                if (text.isBlank()) return ActionResult.Failed("Missing text")
                if (automationController.typeText(text)) ActionResult.Success else ActionResult.Failed("Type failed")
            }
            ActionType.SWIPE -> {
                val service = JarvisAccessibilityService.instance
                    ?: return ActionResult.NeedsPermission("Accessibility")
                val dir = action.params["direction"] ?: "up"
                val ok = when (dir) {
                    "up" -> service.swipeUp()
                    "down" -> service.swipeDown()
                    "left" -> service.swipeLeft()
                    "right" -> service.swipeRight()
                    else -> false
                }
                if (ok) ActionResult.Success else ActionResult.Failed("Swipe $dir failed")
            }
            ActionType.WAIT -> {
                val ms = action.params["durationMs"]?.toLongOrNull() ?: 1000
                kotlinx.coroutines.delay(ms.coerceIn(0, 30000))
                ActionResult.Success
            }
            ActionType.GO_BACK -> if (automationController.goBack()) ActionResult.Success else ActionResult.Failed("goBack failed")
            ActionType.GO_HOME -> if (automationController.goHome()) ActionResult.Success else ActionResult.Failed("goHome failed")
            ActionType.READ_SCREEN -> {
                val content = automationController.readScreen()
                if (content.isNotBlank()) ActionResult.ScreenContent(content) else ActionResult.Failed("No screen content")
            }
            ActionType.SEND_SMS -> {
                val phone = action.params["phone"] ?: ""
                val message = action.params["message"] ?: ""
                if (phone.isBlank() || message.isBlank()) return ActionResult.Failed("Missing phone or message")
                val sent = automationController.sendSms(phone, message)
                if (sent.success) ActionResult.Success else ActionResult.Failed("SMS failed")
            }
            ActionType.MAKE_CALL -> {
                val phone = action.params["phone"] ?: ""
                if (phone.isBlank()) return ActionResult.Failed("Missing phone")
                ActionResult.Failed("make_call not yet implemented")
            }
            ActionType.OPEN_URL -> {
                val url = action.params["url"] ?: ""
                if (url.isBlank()) return ActionResult.Failed("Missing url")
                ActionResult.Unsupported("open_url not yet implemented")
            }
            ActionType.MEDIA_CONTROL -> {
                ActionResult.Unsupported("media_control not yet implemented")
            }
            ActionType.BLUETOOTH -> {
                val btAction = action.params["action"] ?: "toggle"
                val result = when (btAction) {
                    "on" -> automationController.toggleBluetooth(true)
                    "off" -> automationController.toggleBluetooth(false)
                    "toggle" -> automationController.toggleBluetoothAuto()
                    else -> false
                }
                if (result) ActionResult.Success else ActionResult.RequiresUserAction
            }
            ActionType.WIFI -> {
                val wifiAction = action.params["action"] ?: "toggle"
                val result = when (wifiAction) {
                    "on" -> automationController.toggleWifi(true)
                    "off" -> automationController.toggleWifi(false)
                    "toggle" -> automationController.toggleWifiAuto()
                    else -> false
                }
                if (result) ActionResult.Success else ActionResult.RequiresUserAction
            }
            ActionType.BATTERY_STATUS -> ActionResult.BatteryInfo(automationController.getBatterySummary())
            ActionType.CALENDAR -> {
                val calAction = action.params["action"] ?: "today"
                when (calAction) {
                    "today" -> ActionResult.CalendarInfo(automationController.getCalendarSummary())
                    "search" -> {
                        val query = action.params["query"] ?: ""
                        if (query.isBlank()) return ActionResult.Failed("Missing query")
                        val events = automationController.searchCalendar(query)
                        ActionResult.CalendarInfo(events.joinToString { it.title })
                    }
                    else -> ActionResult.Failed("Unknown calendar action: $calAction")
                }
            }
            ActionType.SHARE -> {
                val text = action.params["text"] ?: ""
                if (text.isBlank()) return ActionResult.Failed("Missing text")
                if (automationController.shareText(text)) ActionResult.Success else ActionResult.Failed("Share failed")
            }
        }
    }
}
