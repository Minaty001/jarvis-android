package com.jarvis.automation

import android.view.accessibility.AccessibilityEvent
import org.json.JSONArray
import org.json.JSONObject

data class RecordedAction(
    val type: String,
    val params: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("params", JSONObject(params))
    }
}

class ActionRecorder {
    private val recordedActions = mutableListOf<RecordedAction>()
    private var isRecording = false
    private var recordListener: (() -> Unit)? = null

    fun startRecording() {
        isRecording = true
        recordedActions.clear()
    }

    fun stopRecording(): List<RecordedAction> {
        isRecording = false
        return recordedActions.toList()
    }

    fun isRecording(): Boolean = isRecording

    fun setRecordListener(listener: () -> Unit) {
        recordListener = listener
    }

    fun recordAppLaunch(packageName: String) {
        if (!isRecording) return
        recordedActions.add(RecordedAction(
            type = "open_app",
            params = mapOf("package" to packageName)
        ))
        recordListener?.invoke()
    }

    fun recordTap(text: String) {
        if (!isRecording) return
        recordedActions.add(RecordedAction(
            type = "tap",
            params = mapOf("text" to text)
        ))
        recordListener?.invoke()
    }

    fun recordType(text: String) {
        if (!isRecording) return
        recordedActions.add(RecordedAction(
            type = "type",
            params = mapOf("text" to text)
        ))
        recordListener?.invoke()
    }

    fun recordSwipe(direction: String) {
        if (!isRecording) return
        recordedActions.add(RecordedAction(
            type = "swipe",
            params = mapOf("direction" to direction)
        ))
        recordListener?.invoke()
    }

    fun recordWait(durationMs: Long = 1000) {
        if (!isRecording) return
        recordedActions.add(RecordedAction(
            type = "wait",
            params = mapOf("durationMs" to durationMs.toString())
        ))
        recordListener?.invoke()
    }

    fun recordAccessibilityEvent(event: AccessibilityEvent) {
        if (!isRecording) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val text = event.text?.firstOrNull()?.toString() ?: return
                recordTap(text)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text?.firstOrNull()?.toString() ?: return
                if (text.isNotBlank()) recordType(text)
            }
        }
    }

    fun getActionsAsJson(): JSONArray {
        val arr = JSONArray()
        recordedActions.forEach { arr.put(it.toJson()) }
        return arr
    }

    fun getActionCount(): Int = recordedActions.size
}
