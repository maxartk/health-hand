package com.healthhand.admin.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthhand.admin.data.ApiClient
import com.healthhand.admin.data.models.EventsSummaryResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsUiState(
    val loading: Boolean = false,
    val days: Int = 7,
    val summary: EventsSummaryResponse? = null,
    val error: String? = null,
)

class StatsViewModel : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state

    init { load(7) }

    fun load(days: Int) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, days = days, error = null) }
            try {
                val resp = ApiClient.api().getEventsSummary(days)
                _state.update {
                    it.copy(
                        loading = false,
                        summary = resp,
                        error = if (resp.ok) null else (resp.error ?: "Помилка")
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Помилка")
                }
            }
        }
    }
}