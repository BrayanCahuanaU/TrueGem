package com.example.reconocedordinero.ui.scan

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ScanState(
    val detections: List<Any> = emptyList(),
    val hasPieces: Boolean = false,
    val message: String? = null
)

class ScanViewModel : ViewModel() {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    fun setMessage(msg: String?) { _state.value = _state.value.copy(message = msg) }
}
