package com.jarvis.automation

import android.util.Log

object PrivacyFilter {
    private const val TAG = "PrivacyFilter"

    private val sensitivePackages = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "com.google.android.gm", "com.google.android.apps.messaging",
        "com-phone-vault", "com.applock",
    )

    private val sensitivePatterns = listOf(
        Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"),
        Regex("\\b\\d{6}\\b"),
        Regex("\\bpassword\\b", RegexOption.IGNORE_CASE),
        Regex("\\bOTP\\b"),
        Regex("\\bCVV\\b"),
        Regex("\\bPIN\\b"),
        Regex("\\b\\d{10,12}\\b"),
    )

    fun redactScreenContent(content: String, packageName: String): String {
        if (shouldBlockSending(packageName)) {
            Log.w(TAG, "Blocking screen content from sensitive package: $packageName")
            return "[Content blocked for privacy]"
        }
        var redacted = content
        for (pattern in sensitivePatterns) {
            redacted = pattern.replace(redacted, "[REDACTED]")
        }
        return redacted
    }

    fun shouldBlockSending(packageName: String): Boolean {
        return packageName in sensitivePackages
    }
}
