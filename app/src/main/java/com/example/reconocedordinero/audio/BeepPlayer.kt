package com.example.reconocedordinero.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/** Señal sonora breve al detectar dinero. Respeta el ajuste de señal sonora. */
class BeepPlayer(private val enabled: () -> Boolean) {

    private var tone: ToneGenerator? = null

    fun beep() {
        if (!enabled()) return
        val generator = try {
            tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME).also { tone = it }
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo crear ToneGenerator", e)
            return
        }
        runCatching { generator.startTone(ToneGenerator.TONE_PROP_BEEP, DURATION_MS) }
    }

    fun release() {
        runCatching { tone?.release() }
        tone = null
    }

    private companion object {
        const val TAG = "BeepPlayer"
        const val VOLUME = 80
        const val DURATION_MS = 120
    }
}
