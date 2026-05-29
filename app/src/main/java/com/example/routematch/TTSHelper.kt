package com.example.routematch

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

// Singleton for offline TextToSpeech voice announcements
object TTSHelper {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isEnabled = true

    private const val TAG = "TTSHelper"

    /**
     * Initialize TTS engine.
     * Must be called before speak().
     * Uses Chinese locale for Chinese text, falls back to default.
     */
    fun init(context: Context) {
        if (tts != null) return

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Chinese TTS not available, falling back to default")
                    tts?.setLanguage(Locale.getDefault())
                }
                isInitialized = true
                Log.d(TAG, "TTS initialized")
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
            }
        }
    }

    /**
     * Set whether TTS announcements are enabled.
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    /**
     * Check if TTS is enabled.
     */
    fun isEnabled(): Boolean = isEnabled && isInitialized

    /**
     * Speak a text string.
     * Falls back silently if TTS is not initialized or disabled.
     */
    fun speak(text: String) {
        if (!isEnabled || !isInitialized || tts == null) {
            Log.d(TAG, "TTS not available: enabled=$isEnabled initialized=$isInitialized")
            return
        }

        Log.d(TAG, "Speaking: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
    }

    /**
     * Announce a new order arrival.
     */
    fun announceOrder(platform: String, amount: Double, distance: Double) {
        val text = "$platform 新订单，金额 ${amount.toInt()} 元，距离 ${distance.toInt()} 米"
        speak(text)
    }

    /**
     * Announce matched orders count.
     */
    fun announceMatchCount(count: Int) {
        if (count > 0) {
            speak("当前有 $count 个顺路单")
        }
    }

    /**
     * Release TTS resources.
     */
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
