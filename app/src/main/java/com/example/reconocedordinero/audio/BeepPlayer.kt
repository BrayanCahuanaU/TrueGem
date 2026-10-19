package com.example.reconocedordinero.audio

import android.media.AudioManager
import android.media.ToneGenerator

class BeepPlayer {
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    fun beep() { tone.startTone(ToneGenerator.TONE_PROP_BEEP) }
    fun release() { tone.release() }
}
