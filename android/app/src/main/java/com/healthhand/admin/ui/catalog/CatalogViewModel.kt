package com.healthhand.admin.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthhand.admin.data.ApiClient
import com.healthhand.admin.data.models.CatalogResponse
import com.healthhand.admin.data.models.EmployeeRequest
import com.healthhand.admin.data.models.ServiceRequest
import com.healthhand.admin.data.models.ShiftRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val catalog: CatalogResponse? = null,
    val error: String? = null,
    val toast: String? = null,
)

class CatalogViewModel : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state

    init { load() }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) _state.update { it.copy(refreshing = true) }
            else _state.update { it.copy(loading = true, error = null) }
            try {
                val resp = ApiClient.api().getCatalog()
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        catalog = resp,
                        error = if (resp.ok) null else (resp.error ?: "Помилка")
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, refreshing = false, error = e.message ?: "Помилка")
                }
            }
        }
    }

    private fun reportError(e: Exception) {
        _state.update { it.copy(toast = e.message ?: "Помилка") }
    }

    /* ----- Services ----- */

    fun saveService(
        id: Int?, name: String, description: String, duration: Int, price: Int,
        categoryId: Int?, sortOrder: Int, isActive: Boolean,
    ) {
        viewModelScope.launch {
            val snapshot = _state.value.catalog
            // optimistic update for edits
            if (id != null && snapshot != null) {
                _state.update { it.copy(catalog = snapshot.copy(services = snapshot.services.map { s ->
                    if (s.id == id) s.copy(name = name, description = description,
                        duration_minutes = duration, price = price, category_id = categoryId,
                        sort_order = sortOrder, is_active = if (isActive) 1 else 0) else s
                })) }
            }
            try {
                val req = ServiceRequest(
                    id = id, name = name, description = description,
                    duration_minutes = duration, price = price,
                    category_id = categoryId, sort_order = sortOrder,
                    is_active = if (isActive) 1 else 0,
                )
                val resp = if (id == null) ApiClient.api().createService(req)
                else ApiClient.api().updateService(req)
                if (resp.ok) { _state.update { it.copy(toast = "Збережено") }; load(refresh = true) }
                else { _state.update { it.copy(catalog = snapshot, toast = resp.error ?: "Помилка") } }
            } catch (e: Exception) {
                _state.update { it.copy(catalog = snapshot) }
                reportError(e)
            }
        }
    }

    fun deleteService(id: Int) {
        viewModelScope.launch {
            val snapshot = _state.value.catalog
            if (snapshot != null) {
                _state.update { it.copy(catalog = snapshot.copy(services = snapshot.services.filter { s -> s.id != id })) }
            }
            try {
                val resp = ApiClient.api().deleteService(id)
                if (resp.ok) { _state.update { it.copy(toast = "Видалено") } }
                else { _state.update { it.copy(catalog = snapshot, toast = resp.error ?: "Помилка") } }
            } catch (e: Exception) {
                _state.update { it.copy(catalog = snapshot) }
                reportError(e)
            }
        }
    }

    /* ----- Employees ----- */

    fun saveEmployee(
        id: Int?, name: String, role: String, bio: String, phone: String,
        sortOrder: Int, isActive: Boolean, showOnSite: Boolean, serviceIds: List<Int>,
    ) {
        viewModelScope.launch {
            val snapshot = _state.value.catalog
            if (id != null && snapshot != null) {
                _state.update { it.copy(catalog = snapshot.copy(employees = snapshot.employees.map { e ->
                    if (e.id == id) e.copy(name = name, role = role, bio = bio, phone = phone,
                        sort_order = sortOrder, is_active = if (isActive) 1 else 0,
                        show_on_site = if (showOnSite) 1 else 0) else e
                })) }
            }
            try {
                val req = EmployeeRequest(
                    id = id, name = name, role = role, bio = bio, phone = phone,
                    is_active = if (isActive) 1 else 0, sort_order = sortOrder,
                    show_on_site = if (showOnSite) 1 else 0, service_ids = serviceIds,
                )
                val resp = if (id == null) ApiClient.api().createEmployee(req)
                else ApiClient.api().updateEmployee(req)
                if (resp.ok) { _state.update { it.copy(toast = "Збережено") }; load(refresh = true) }
                else { _state.update { it.copy(catalog = snapshot, toast = resp.error ?: "Помилка") } }
            } catch (e: Exception) {
                _state.update { it.copy(catalog = snapshot) }
                reportError(e)
            }
        }
    }

    fun deleteEmployee(id: Int) {
        viewModelScope.launch {
            val snapshot = _state.value.catalog
            if (snapshot != null) {
                _state.update { it.copy(catalog = snapshot.copy(employees = snapshot.employees.filter { e -> e.id != id })) }
            }
            try {
                val resp = ApiClient.api().deleteEmployee(id)
                if (resp.ok) { _state.update { it.copy(toast = "Видалено") } }
                else { _state.update { it.copy(catalog = snapshot, toast = resp.error ?: "Помилка") } }
            } catch (e: Exception) {
                _state.update { it.copy(catalog = snapshot) }
                reportError(e)
            }
        }
    }

    /* ----- Shifts ----- */

    fun saveShift(
        id: Int?, employeeId: Int, weekday: Int, startTime: String,
        endTime: String, isActive: Boolean,
    ) {
        viewModelScope.launch {
            val snapshot = _state.value.catalog
            if (id != null && snapshot != null) {
                _state.update { it.copy(catalog = snapshot.copy(shifts = snapshot.shifts.map { s ->
                    if (s.id == id) s.copy(employee_id = employeeId, weekday = weekday,
                        start_time = startTime, end_time = endTime,
                        is_active = if (isActive) 1 else 0) else s
                })) }
            }
            try {
                val req = ShiftRequest(
                    id = id, employee_id = employeeId, weekday = weekday,
                    start_time = startTime, end_time = endTime,
                    is_active = if (isActive) 1 else 0,
                )
                val resp = if (id == null) ApiClient.api().createShift(req)
                else ApiClient.api().updateShift(req)
                if (resp.ok) { _state.update { it.copy(toast = "Збережено") }; load(refresh = true) }
                else { _state.update { it.copy(catalog = snapshot, toast = resp.error ?: "Помилка") } }
            } catch (e: Exception) {
                _state.update { it.copy(catalog = snapshot) }
                reportError(e)
            }
        }
    }

    fun deleteShift(id: Int) {
        viewModelScope.launch {
            val snapshot = _state.value.catalog
            if (snapshot != null) {
                _state.update { it.copy(catalog = snapshot.copy(shifts = snapshot.shifts.filter { s -> s.id != id })) }
            }
            try {
                val resp = ApiClient.api().deleteShift(id)
                if (resp.ok) { _state.update { it.copy(toast = "Видалено") } }
                else { _state.update { it.copy(catalog = snapshot, toast = resp.error ?: "Помилка") } }
            } catch (e: Exception) {
                _state.update { it.copy(catalog = snapshot) }
                reportError(e)
            }
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }
}