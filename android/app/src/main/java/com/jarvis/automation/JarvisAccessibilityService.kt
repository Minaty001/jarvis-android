package com.jarvis.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "JarvisAutoService"
        var instance: JarvisAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    var actionRecorder: ActionRecorder? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = serviceInfo
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        info.notificationTimeout = 100
        serviceInfo = info

        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { actionRecorder?.recordAccessibilityEvent(it) }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.d(TAG, "Accessibility service unbound")
        return super.onUnbind(intent)
    }

    // ==================== GLOBAL ACTIONS ====================

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    // ==================== GESTURE DISPATCH ====================

    fun tap(x: Float, y: Float, callback: GestureResultCallback? = null): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(
            path, 0, ViewConfiguration.getTapTimeout().toLong()
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, callback, null)
    }

    fun longPress(x: Float, y: Float, durationMs: Long = 1500): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 300
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipeUp() = swipe(540f, 1500f, 540f, 500f)
    fun swipeDown() = swipe(540f, 500f, 540f, 1500f)
    fun swipeLeft() = swipe(900f, 1200f, 200f, 1200f)
    fun swipeRight() = swipe(200f, 1200f, 900f, 1200f)

    // ==================== FIND ELEMENTS ====================

    fun findByText(text: String): List<AccessibilityNodeInfo> {
        return rootInActiveWindow?.let { root ->
            root.findAccessibilityNodeInfosByText(text).orEmpty().toList()
        } ?: emptyList()
    }

    fun findByViewId(viewId: String): List<AccessibilityNodeInfo> {
        return rootInActiveWindow?.let { root ->
            root.findAccessibilityNodeInfosByViewId(viewId).orEmpty().toList()
        } ?: emptyList()
    }

    fun clickByText(text: String): Boolean {
        val nodes = findByText(text)
        if (nodes.isNotEmpty()) {
            val node = nodes[0]
            var clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (!clicked) {
                clicked = node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            }
            nodes.forEach { it.recycle() }
            return clicked
        }
        return false
    }

    fun clickByViewId(viewId: String): Boolean {
        val nodes = findByViewId(viewId)
        if (nodes.isNotEmpty()) {
            val result = nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            nodes.forEach { it.recycle() }
            return result
        }
        return false
    }

    fun longPressByText(text: String): Boolean {
        val nodes = findByText(text)
        if (nodes.isNotEmpty()) {
            val result = nodes[0].performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            nodes.forEach { it.recycle() }
            return result
        }
        return false
    }

    // ==================== TEXT INPUT ====================

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun setTextByFind(targetText: String, newText: String): Boolean {
        val nodes = findByText(targetText)
        if (nodes.isNotEmpty()) {
            val result = setText(nodes[0], newText)
            nodes.forEach { it.recycle() }
            return result
        }
        return false
    }

    fun setTextByViewId(viewId: String, text: String): Boolean {
        val nodes = findByViewId(viewId)
        if (nodes.isNotEmpty()) {
            val result = setText(nodes[0], text)
            nodes.forEach { it.recycle() }
            return result
        }
        return false
    }

    // ==================== SCROLL ====================

    fun scrollForward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollBackward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    // ==================== READ SCREEN ====================

    fun getAllText(): List<String> {
        val texts = mutableListOf<String>()
        rootInActiveWindow?.let { collectText(it, texts) }
        return texts
    }

    fun getScreenContent(): String {
        return getAllText().joinToString("\n")
    }

    private fun collectText(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.let { if (it.isNotBlank()) texts.add(it.toString()) }
        node.contentDescription?.let { if (it.isNotBlank()) texts.add(it.toString()) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectText(child, texts)
                child.recycle()
            }
        }
    }

    fun waitForText(text: String, timeoutMs: Long = 10_000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (findByText(text).isNotEmpty()) return true
            Thread.sleep(500)
        }
        return false
    }

    // ==================== SCREENSHOT (API 30+) ====================

    fun takeScreenshotIfAvailable(callback: (Any?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayId = display?.displayId ?: return callback(null)
            takeScreenshot(
                displayId,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        callback(result)
                    }
                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed: $errorCode")
                        callback(null)
                    }
                }
            )
        } else {
            callback(null)
        }
    }

    // ==================== UTILITY ====================

    fun getNodeCenter(node: AccessibilityNodeInfo): Pair<Float, Float> {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return Pair(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        val (x, y) = getNodeCenter(node)
        return tap(x, y)
    }
}
