package com.example.reconocedordinero.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibración con patrones distintos para inicio, éxito y error. Respeta el ajuste de vibración. */
class HapticManager(context: Context, private val enabled: () -> Boolean) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private fun oneShot(durationMs: Long) {
        if (!enabled()) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun waveform(timings: LongArray) {
        if (!enabled()) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    /** Inicio de escaneo: una vibración corta. */
    fun start() = oneShot(60)

    /** Éxito: dos pulsos ascendentes. */
    fun success() = waveform(longArrayOf(0, 80, 70, 140))

    /** Error: tres pulsos. */
    fun error() = waveform(longArrayOf(0, 120, 80, 120, 80, 220))
}
