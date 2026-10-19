package com.example.reconocedordinero.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.reconocedordinero.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Voz de la app. Aplica velocidad y volumen de los ajustes, pide audio focus transitorio con
 * ducking y ofrece un retardo corto para no pisar el anuncio de TalkBack al navegar.
 */
class SpeechManager(
    context: Context,
    private val currentSettings: () -> AppSettings,
) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var tts: TextToSpeech? = null
    private var ready = false
    private var shutdown = false
    private var pendingText: String? = null

    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val abandonFocus = Runnable { abandonAudioFocus() }

    init {
        tts = TextToSpeech(appContext) { status ->
            // Se publica en el hilo principal: garantiza que `tts` ya está asignado.
            handler.post {
                if (shutdown) return@post
                if (status == TextToSpeech.SUCCESS) {
                    configureLanguage()
                    ready = true
                    pendingText?.let { texto ->
                        pendingText = null
                        speak(texto)
                    }
                } else {
                    Log.w(TAG, "No se pudo iniciar TextToSpeech: $status")
                    _available.value = false
                }
            }
        }
    }

    // TODO v2: otros idiomas además de español.

    private fun configureLanguage() {
        val engine = tts ?: return
        val result = engine.setLanguage(Locale.forLanguageTag("es-PE"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = engine.setLanguage(Locale.forLanguageTag("es-419"))
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.forLanguageTag("es-ES"))
            }
        }
    }

    /** Habla de inmediato (interrumpe lo anterior: QUEUE_FLUSH). */
    fun speak(text: String) {
        if (shutdown || text.isBlank()) return
        val engine = tts
        if (engine == null || !ready) {
            // Si el motor aún está arrancando, se guarda el último mensaje y se dice al estar listo.
            pendingText = text
            return
        }
        val settings = currentSettings()
        engine.setSpeechRate(settings.voiceRate)
        requestAudioFocus()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, (settings.voiceVolume / 100f).coerceIn(0f, 1f))
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    /** Habla unos 500 ms después, para no pisar el anuncio de TalkBack tras navegar. */
    fun speakAfterNavigation(text: String) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ speak(text) }, TALKBACK_DELAY_MS)
    }

    private fun requestAudioFocus() {
        runCatching {
            audioManager.requestAudioFocus(buildFocusRequest())
            handler.removeCallbacks(abandonFocus)
            handler.postDelayed(abandonFocus, FOCUS_TIMEOUT_MS)
        }
    }

    private fun abandonAudioFocus() {
        runCatching { audioManager.abandonAudioFocusRequest(buildFocusRequest()) }
    }

    private fun buildFocusRequest(): AudioFocusRequest {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
    }

    fun shutdown() {
        shutdown = true
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private companion object {
        const val TAG = "SpeechManager"
        const val UTTERANCE_ID = "reconocedor"
        const val TALKBACK_DELAY_MS = 500L
        const val FOCUS_TIMEOUT_MS = 15_000L
    }
}
