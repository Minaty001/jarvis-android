package com.jarvis.config

import com.jarvis.BuildConfig

object Config {
    val BACKEND_WS_URL: String = BuildConfig.BACKEND_WS_URL
    val BACKEND_API_URL: String = BuildConfig.BACKEND_API_URL
    val DEVICE_ID: String = "android-${android.os.Build.MODEL?.replace(" ", "-") ?: "unknown"}"
}
