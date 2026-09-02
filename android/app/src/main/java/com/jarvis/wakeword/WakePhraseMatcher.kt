package com.jarvis.wakeword

import java.util.Locale

/**
 * Exact wake phrase matcher for the STT fallback path.
 *
 * Accepts only the wake words the user asked for: "jarvis" or "hey jarvis".
 * Normalization removes punctuation so phrases like "hey jarvis." still match,
 * while longer commands such as "hey jarvis open youtube" do not.
 */
object WakePhraseMatcher {
    private val whitespace = Regex("\\s+")
    private val punctuation = Regex("[^a-z0-9\\s]")

    fun matches(text: String): Boolean {
        val normalized = text
            .lowercase(Locale.ROOT)
            .trim()
            .replace(punctuation, " ")
            .replace(whitespace, " ")
            .trim()
        return normalized == "jarvis" || normalized == "hey jarvis"
    }
}
