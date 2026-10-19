package com.example.reconocedordinero

import android.app.Application
import android.util.Log
import com.example.reconocedordinero.audio.BeepPlayer
import com.example.reconocedordinero.audio.HapticManager
import com.example.reconocedordinero.audio.SpeechManager
import com.example.reconocedordinero.data.SettingsRepository
import com.example.reconocedordinero.domain.ScanResult
import com.example.reconocedordinero.ml.ModelRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Inyección de dependencias manual. Se crean una sola vez al arrancar la app.
 * `modelRunner` puede ser null si falta `best.tflite`: la app no debe cerrarse.
 */
class AppContainer(val context: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context, scope) }

    /** Ajustes actuales, accesibles de forma síncrona desde los managers. */
    val settings = settingsRepository.settings

    val speechManager: SpeechManager by lazy { SpeechManager(context) { settings.value } }
    val hapticManager: HapticManager by lazy { HapticManager(context) { settings.value.vibration } }
    val beepPlayer: BeepPlayer by lazy { BeepPlayer { settings.value.beep } }

    /**
     * Un único hilo dedicado para TODA la inferencia: `ModelRunner` no es seguro entre hilos
     * y se reutiliza entre escaneos.
     */
    val analysisExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    val modelRunner: ModelRunner? by lazy {
        try {
            ModelRunner(context)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo cargar best.tflite / labels.txt", e)
            null
        }
    }

    /** Resultado del último escaneo, compartido entre pantallas (no por argumentos de navegación). */
    private val _result = MutableStateFlow<ScanResult?>(null)
    val result: StateFlow<ScanResult?> = _result.asStateFlow()

    fun publishResult(result: ScanResult) {
        _result.value = result
    }

    /** El saludo de inicio solo se dice una vez por apertura de la app. */
    var homeAnnounced = false

    init {
        // Precarga del modelo en segundo plano: evita bloquear el primer escaneo.
        scope.launch { runCatching { modelRunner } }
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}

class ReconocedorApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
