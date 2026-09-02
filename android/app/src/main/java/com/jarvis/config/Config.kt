package com.jarvis.config

import android.content.Context
import com.jarvis.BuildConfig

object Config {
    private const val PREFS_NAME = "jarvis_config"
    private const val KEY_DEVICE_UUID = "device_uuid"

    val BACKEND_WS_URL: String = BuildConfig.BACKEND_WS_URL
    val BACKEND_API_URL: String = BuildConfig.BACKEND_API_URL

    private var cachedDeviceId: String? = null

    fun getDeviceId(context: Context): String {
        cachedDeviceId?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var uuid = prefs.getString(KEY_DEVICE_UUID, null)
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
        }
        cachedDeviceId = uuid
        return uuid
    }
}
