package com.example.reconocedordinero.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val VOICE_RATE = floatPreferencesKey("voice_rate")
        val VOICE_VOLUME = intPreferencesKey("voice_volume")
        val VIBRATION = booleanPreferencesKey("vibration")
        val BEEP = booleanPreferencesKey("beep")
    }

    val settings: Flow<Preferences> = context.dataStore.data

    fun getVoiceRate(): Flow<Float> = settings.map { it[VOICE_RATE] ?: 1.0f }
    fun getVoiceVolume(): Flow<Int> = settings.map { it[VOICE_VOLUME] ?: 80 }
    fun getVibration(): Flow<Boolean> = settings.map { it[VIBRATION] ?: true }
    fun getBeep(): Flow<Boolean> = settings.map { it[BEEP] ?: true }
}
