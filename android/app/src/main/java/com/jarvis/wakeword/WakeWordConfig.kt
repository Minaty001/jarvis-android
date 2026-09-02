package com.jarvis.wakeword

/**
 * Central wake-word configuration with strict mode defaults.
 */
data class WakeWordConfig(
    val enabled: Boolean = true,

    // Strict mode
    var sensitivity: Float = 0.35f,

    val cooldownMs: Long = 4000L,

    val backgroundListeningEnabled: Boolean = true,

    val fallbackTextMatchingEnabled: Boolean = false,

    // Stronger temporal confirmation
    val temporalWindowSize: Int = 7,

    val temporalPositiveCount: Int = 5,

    // Higher per-window confidence
    val minConfidenceForPositive: Float = 0.68f
) {
    companion object {

        fun thresholdForSensitivity(
            sensitivity: Float
        ): Float {

            val s =
                sensitivity.coerceIn(0f, 1f)

            return (
                0.90f -
                    (0.30f * s)
            ).coerceIn(
                0.55f,
                0.92f
            )
        }
    }
}
