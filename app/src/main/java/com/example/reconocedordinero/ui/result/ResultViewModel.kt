package com.example.reconocedordinero.ui.result

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ResultState(val totalCents: Int = 0, val counts: Map<String, Int> = emptyMap())

class ResultViewModel : ViewModel() {
    private val _state = MutableStateFlow(ResultState())
    val state: StateFlow<ResultState> = _state

    fun setResult(total: Int, counts: Map<String, Int>) { _state.value = ResultState(total, counts) }
}
