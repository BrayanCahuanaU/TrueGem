package com.example.reconocedordinero.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Ajustes de la app. Valores por defecto = los que pide el diseño. */
data class AppSettings(
    val voiceRate: Float = 1.0f,
    val voiceVolume: Int = 80,
    val vibration: Boolean = true,
    val beep: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Persistencia de ajustes en DataStore Preferences. Se guarda y aplica al instante. */
class SettingsRepository(context: Context, scope: CoroutineScope) {

    companion object {
        val VOICE_RATE = floatPreferencesKey("voice_rate")
        val VOICE_VOLUME = intPreferencesKey("voice_volume")
        val VIBRATION = booleanPreferencesKey("vibration")
        val BEEP = booleanPreferencesKey("beep")
    }

    private val appContext = context.applicationContext

    val settings: StateFlow<AppSettings> = appContext.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun Preferences.toSettings() = AppSettings(
        voiceRate = this[VOICE_RATE] ?: 1.0f,
        voiceVolume = this[VOICE_VOLUME] ?: 80,
        vibration = this[VIBRATION] ?: true,
        beep = this[BEEP] ?: true,
    )

    suspend fun setVoiceRate(value: Float) {
        appContext.dataStore.edit { it[VOICE_RATE] = value.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setVoiceVolume(value: Int) {
        appContext.dataStore.edit { it[VOICE_VOLUME] = value.coerceIn(0, 100) }
    }

    suspend fun setVibration(value: Boolean) {
        appContext.dataStore.edit { it[VIBRATION] = value }
    }

    suspend fun setBeep(value: Boolean) {
        appContext.dataStore.edit { it[BEEP] = value }
    }
}
