package com.healthhand.admin.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthhand.admin.data.ApiClient
import com.healthhand.admin.data.models.Booking
import com.healthhand.admin.data.models.Employee
import com.healthhand.admin.data.models.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BookingsStatus(val apiValue: String?, val labelRes: Int) {
    ALL(null, 0),
    NEW("new", 1),
    CONTACTED("contacted", 2),
    CONFIRMED("confirmed", 3),
    COMPLETED("completed", 4),
    CANCELLED("cancelled", 5),
    NO_SHOW("no_show", 6),
    FOLLOWUP_SENT("followup_sent", 7);
}

data class BookingsUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val bookings: List<Booking> = emptyList(),
    val error: String? = null,
    val filter: BookingsStatus = BookingsStatus.ALL,
)

class BookingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(BookingsUiState())
    val state: StateFlow<BookingsUiState> = _state

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) {
                _state.update { it.copy(refreshing = true) }
            } else {
                _state.update { it.copy(loading = true) }
            }
            try {
                val resp = ApiClient.api().getBookings(status = _state.value.filter.apiValue)
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        bookings = resp.bookings,
                        error = if (resp.ok) null else (resp.error ?: "Помилка"),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message ?: "Помилка",
                    )
                }
            }
        }
    }

    fun setFilter(status: BookingsStatus) {
        _state.update { it.copy(filter = status) }
        load()
    }

    fun changeStatus(bookingId: Int, status: String, feedbackNote: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val req = com.healthhand.admin.data.models.StatusChangeRequest(
                    booking_id = bookingId,
                    status = status,
                    feedback_note = feedbackNote?.takeIf { it.isNotBlank() },
                )
                val resp = ApiClient.api().changeBookingStatus(req)
                if (resp.ok) {
                    load(refresh = true)
                } else {
                    _state.update { it.copy(error = resp.error ?: "Помилка") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Помилка") }
            } finally {
                onDone()
            }
        }
    }

    fun assignAppointment(
        bookingId: Int,
        startAt: String,
        endAt: String?,
        employeeId: Int?,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val req = com.healthhand.admin.data.models.AppointmentRequest(
                    booking_id = bookingId,
                    start_at = startAt,
                    end_at = endAt,
                    employee_id = employeeId,
                )
                val resp = ApiClient.api().setAppointment(req)
                if (resp.ok) {
                    load(refresh = true)
                } else {
                    _state.update { it.copy(error = resp.error ?: "Помилка") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Помилка") }
            } finally {
                onDone()
            }
        }
    }
}