package com.jarvis.privacy

import android.util.Log

object PrivacyBoundary {
    private const val TAG = "PrivacyBoundary"

    private val blockedPackages = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "com.google.android.gm", "com.google.android.apps.messaging",
        "com-phone-vault", "com.applock",
        "com.bank", "com.bank.mobile",
        "com.paypal", "com.venmo", "com.cashapp",
        "com.password.manager", "com.lastpass.android",
        "com.google.android.apps.authy2",
    )

    private val sensitivePatterns = listOf(
        Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"),  // credit card
        Regex("\\b\\d{16}\\b"),  // card number
        Regex("\\b\\d{6}\\b"),  // OTP
        Regex("\\bpassword\\b", RegexOption.IGNORE_CASE),
        Regex("\\bOTP\\b"),
        Regex("\\bCVV\\b"),
        Regex("\\bPIN\\b"),
        Regex("\\b\\d{10,12}\\b"),  // account numbers
        Regex("\\bSSN\\b", RegexOption.IGNORE_CASE),
        Regex("\\bIFSC\\b", RegexOption.IGNORE_CASE),
        Regex("\\brouting\\b", RegexOption.IGNORE_CASE),
    )

    enum class PrivacyAction {
        ALLOW,
        REDACT,
        BLOCK
    }

    fun evaluate(packageName: String?, content: String?): PrivacyAction {
        if (packageName != null && packageName in blockedPackages) {
            Log.w(TAG, "BLOCKED: sensitive package $packageName")
            return PrivacyAction.BLOCK
        }
        if (content != null && containsSensitiveData(content)) {
            Log.w(TAG, "REDACT: sensitive data detected")
            return PrivacyAction.REDACT
        }
        return PrivacyAction.ALLOW
    }

    fun redactScreenContent(content: String, packageName: String): String {
        if (packageName in blockedPackages) {
            Log.w(TAG, "Blocking screen content from sensitive package: $packageName")
            return "[Content blocked for privacy]"
        }
        var redacted = content
        for (pattern in sensitivePatterns) {
            redacted = pattern.replace(redacted, "[REDACTED]")
        }
        return redacted
    }

    fun shouldBlockSending(packageName: String): Boolean = packageName in blockedPackages

    fun containsSensitiveData(text: String): Boolean {
        return sensitivePatterns.any { it.containsMatchIn(text) }
    }

    fun isBankingApp(packageName: String): Boolean {
        val bankingKeywords = listOf("bank", "finance", "wallet", "pay", "upi")
        return bankingKeywords.any { packageName.contains(it, ignoreCase = true) }
    }

    fun isPasswordManager(packageName: String): Boolean {
        val pwKeywords = listOf("password", "vault", "keychain", "1password", "lastpass", "bitwarden")
        return pwKeywords.any { packageName.contains(it, ignoreCase = true) }
    }
}
