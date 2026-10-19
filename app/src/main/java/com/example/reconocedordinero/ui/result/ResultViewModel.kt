package com.example.reconocedordinero.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reconocedordinero.AppContainer
import com.example.reconocedordinero.domain.ScanResult
import kotlinx.coroutines.flow.StateFlow

/** Expone el resultado guardado en [AppContainer] (compartido entre pantallas). */
class ResultViewModel(container: AppContainer) : ViewModel() {

    val result: StateFlow<ScanResult?> = container.result

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ResultViewModel(container) }
        }
    }
}
