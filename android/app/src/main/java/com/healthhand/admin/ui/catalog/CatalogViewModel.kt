package com.healthhand.admin.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthhand.admin.data.ApiClient
import com.healthhand.admin.data.models.CatalogResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogUiState(
    val loading: Boolean = false,
    val catalog: CatalogResponse? = null,
    val error: String? = null,
)

class CatalogViewModel : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val resp = ApiClient.api().getCatalog()
                _state.update {
                    it.copy(
                        loading = false,
                        catalog = resp,
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