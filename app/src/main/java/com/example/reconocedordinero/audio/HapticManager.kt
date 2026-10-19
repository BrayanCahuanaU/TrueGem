package com.example.reconocedordinero.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class HapticManager(private val context: Context) {
    private val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    fun vibrateShort() {
        if (!vib.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE) else null
        if (effect != null) vib.vibrate(effect) else vib.vibrate(50)
    }

    fun vibrateSuccess() {
        if (!vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)) else vib.vibrate(150)
    }

    fun vibrateError() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)) else vib.vibrate(300) }
}
