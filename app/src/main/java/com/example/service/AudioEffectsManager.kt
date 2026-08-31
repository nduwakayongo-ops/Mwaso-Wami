package com.example.service

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

class AudioEffectsManager(private val context: Context) {
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (audioSessionId == currentSessionId && loudnessEnhancer != null) return

        release()
        currentSessionId = audioSessionId

        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                // Unity gain (0 mB = transparent audio without distortion or manual boost)
                setTargetGain(0)
                enabled = false
            }
        } catch (e: Exception) {
            Log.w("AudioEffectsManager", "LoudnessEnhancer not supported on this device/session", e)
        }
    }

    fun release() {
        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w("AudioEffectsManager", "Error releasing LoudnessEnhancer: ${e.message}")
        }
        loudnessEnhancer = null
        currentSessionId = 0
    }
}
