package com.jarvis.backend

import org.json.JSONObject

sealed interface WsMessage {
    data class CommandResponse(
        val intent: String,
        val response: String,
        val actions: List<WsAction>,
        val provider: String?
    ) : WsMessage

    data class WsAction(
        val type: String,
        val params: Map<String, String>
    )

    data class Error(val message: String) : WsMessage
    data class Ping(val timestamp: Long) : WsMessage
    data class Pong(val timestamp: Long) : WsMessage
    data class AuthRequired(val message: String) : WsMessage
    data class Unknown(val raw: String) : WsMessage

    companion object {
        fun parse(json: String): WsMessage {
            return try {
                val obj = JSONObject(json)
                val type = obj.optString("type", "")
                when (type) {
                    "command_response", "response" -> {
                        val actionsArr = obj.optJSONArray("actions")
                        val actions = mutableListOf<WsAction>()
                        if (actionsArr != null) {
                            for (i in 0 until actionsArr.length()) {
                                val actionObj = actionsArr.getJSONObject(i)
                                val paramsObj = actionObj.optJSONObject("params")
                                val params = mutableMapOf<String, String>()
                                if (paramsObj != null) {
                                    for (key in paramsObj.keys()) {
                                        params[key] = paramsObj.optString(key, "")
                                    }
                                }
                                actions.add(WsAction(
                                    type = actionObj.optString("type", ""),
                                    params = params
                                ))
                            }
                        }
                        CommandResponse(
                            intent = obj.optString("intent", "unknown"),
                            response = obj.optString("response", ""),
                            actions = actions,
                            provider = obj.optString("provider", null)
                        )
                    }
                    "error" -> Error(obj.optString("message", "Unknown error"))
                    "ping" -> Ping(obj.optLong("timestamp", System.currentTimeMillis()))
                    "pong" -> Pong(obj.optLong("timestamp", System.currentTimeMillis()))
                    "auth_required" -> AuthRequired(obj.optString("message", "Authentication required"))
                    else -> Unknown(json)
                }
            } catch (e: Exception) {
                Unknown(json)
            }
        }
    }
}
