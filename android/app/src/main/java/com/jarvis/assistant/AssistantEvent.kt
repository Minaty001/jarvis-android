package com.jarvis.assistant

sealed interface AssistantEvent {
    data object Initialized : AssistantEvent
    data object Authenticated : AssistantEvent
    data object Connected : AssistantEvent
    data object Disconnected : AssistantEvent
    data class Error(val message: String) : AssistantEvent
    data class ResponseReceived(val text: String) : AssistantEvent
    data class ActionPlanned(val actions: List<String>) : AssistantEvent
    data class ConfirmationRequired(val actionType: String) : AssistantEvent
}
