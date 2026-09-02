package com.jarvis.wakeword

import android.os.SystemClock

/**
 * Simple monotonic-clock debounce shared by both the offline detector and the
 * STT fallback path. Prevents a single spoken wake word from triggering the
 * command flow multiple times in a row.
 */
class WakeCooldown(private val cooldownMs: Long) {
    @Volatile
    private var lastAllowedMs = 0L

    fun reset() {
        lastAllowedMs = 0L
    }

    /** Manually triggers/extends the cooldown from the current time. */
    fun triggerCooldown() {
        lastAllowedMs = SystemClock.elapsedRealtime()
    }

    /** Returns true (and records the time) if enough time has elapsed. */
    fun allow(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAllowedMs >= cooldownMs) {
            lastAllowedMs = now
            return true
        }
        return false
    }
}
