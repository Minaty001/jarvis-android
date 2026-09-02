package com.jarvis.wakeword

/**
 * A low-power, offline wake-word detector. Owns the microphone while active
 * (via [com.jarvis.assistant.voice.MicController]) and fires [WakeWordListener]
 * when the phrase ("Hey Jarvis" / "Jarvis") is detected.
 *
 * The command recognizer ([com.jarvis.assistant.voice.SpeechController]) must
 * never share the mic with it — call [pause] before activating speech
 * recognition and [resume] afterwards.
 */
interface WakeWordDetector {
    fun start()
    fun stop()
    fun pause()
    fun resume()
    fun release()

    fun setListener(listener: WakeWordListener)

    /** True when the detector can run (all 3 ONNX models loaded). */
    fun isAvailable(): Boolean
}
