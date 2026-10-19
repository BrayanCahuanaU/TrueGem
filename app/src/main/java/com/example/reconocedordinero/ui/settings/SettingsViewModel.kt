package com.example.reconocedordinero.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reconocedordinero.data.AppSettings
import com.example.reconocedordinero.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Ajustes persistidos en DataStore: cada cambio se guarda y se aplica al instante. */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings

    fun setVoiceRate(value: Float) {
        viewModelScope.launch { repository.setVoiceRate(value) }
    }

    fun setVoiceVolume(value: Int) {
        viewModelScope.launch { repository.setVoiceVolume(value) }
    }

    fun setVibration(value: Boolean) {
        viewModelScope.launch { repository.setVibration(value) }
    }

    fun setBeep(value: Boolean) {
        viewModelScope.launch { repository.setBeep(value) }
    }

    companion object {
        fun factory(repository: SettingsRepository) = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
