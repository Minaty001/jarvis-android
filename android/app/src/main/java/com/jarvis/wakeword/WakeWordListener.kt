package com.jarvis.wakeword

/**
 * Callback fired by a [WakeWordDetector] when the wake phrase is detected
 * or when the detector hits an unrecoverable error.
 */
interface WakeWordListener {
    /** Called on a genuine wake-word detection (after cooldown gate). */
    fun onWakeWordDetected()

    /** Called when the detector fails to initialise or run. */
    fun onWakeWordError(error: Throwable)
}
