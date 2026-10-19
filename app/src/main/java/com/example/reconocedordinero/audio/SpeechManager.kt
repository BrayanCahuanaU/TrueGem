package com.example.reconocedordinero.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class SpeechManager(context: Context) {
    private val tts: TextToSpeech
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ok = tts.setLanguage(Locale.forLanguageTag("es-PE"))
                if (ok == TextToSpeech.LANG_MISSING_DATA || ok == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.forLanguageTag("es-419"))
                }
                ready = true
            } else {
                Log.w(TAG, "TTS init failed: $status")
            }
        }
    }

    fun speak(text: String, rate: Float = 1.0f, volume: Float = 0.8f) {
        if (!ready) return
        tts.setSpeechRate(rate)
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, null)
    }

    fun shutdown() { tts.shutdown() }

    companion object { const val TAG = "SpeechManager" }
}
