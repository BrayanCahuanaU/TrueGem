package com.example.reconocedordinero

import android.app.Application
import android.util.Log
import com.example.reconocedordinero.audio.BeepPlayer
import com.example.reconocedordinero.audio.HapticManager
import com.example.reconocedordinero.audio.SpeechManager
import com.example.reconocedordinero.data.SettingsRepository
import com.example.reconocedordinero.ml.ModelRunner

class AppContainer(val context: Application) {
    val settingsRepository by lazy { SettingsRepository(context) }
    val speechManager by lazy { SpeechManager(context) }
    val hapticManager by lazy { HapticManager(context) }
    val beepPlayer by lazy { BeepPlayer() }
    val modelRunner: ModelRunner? by lazy {
        try {
            ModelRunner(context)
        } catch (e: Exception) {
            Log.e("AppContainer", "Could not load ModelRunner", e)
            null
        }
    }
}

class ReconocedorApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
