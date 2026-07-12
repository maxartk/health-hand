package com.healthhand.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthhand.admin.core.*
import com.healthhand.admin.data.*
import com.healthhand.admin.data.models.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppState(
    val authenticated: Boolean = false,
    val checkingCredential: Boolean = false,
    val loginBusy: Boolean = false,
    val loginError: String? = null,
    val catalog: CatalogState = CatalogState(),
    val bookings: List<Booking> = emptyList(),
    val bookingsLoading: Boolean = false,
    val stats: EventsSummaryResponse? = null,
    val automationSummary: AutomationSummaryResponse? = null,
    val automationEvents: List<AutomationEvent> = emptyList(),
    val automationLoading: Boolean = false,
    val busy: Boolean = false
)

data class UiEvent(val message: String)

class AdminViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AdminRepository(SecureCredentialStore(app))
    private val _state = MutableStateFlow(AppState(checkingCredential = repo.hasCredential()))
    val state = _state.asStateFlow()
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (repo.hasCredential()) validateStoredCredential()
    }

    private fun validateStoredCredential() = viewModelScope.launch {
        runCatching { repo.catalog().toCatalogUi() }
            .onSuccess { content -> _state.value = AppState(authenticated = true, catalog = CatalogState(content = content)) }
            .onFailure {
                repo.logout()
                _state.value = AppState(loginError = "Збережений ключ більше не дійсний. Увійдіть знову.")
            }
    }

    fun signIn(key: String) {
        if (key.isBlank() || _state.value.loginBusy) return
        repo.saveCredential(key)
        _state.update { it.copy(loginBusy = true, loginError = null) }
        viewModelScope.launch {
            runCatching { repo.catalog().toCatalogUi() }
                .onSuccess { content -> _state.value = AppState(authenticated = true, catalog = CatalogState(content = content)) }
                .onFailure { error ->
                    repo.logout()
                    _state.update { it.copy(loginBusy = false, loginError = userMessageFor(error.httpCode(), error) ?: "Невірний ключ доступу") }
                }
        }
    }

    fun logout() { repo.logout(); _state.value = AppState() }

    fun loadCatalog(refresh: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(catalog = reduceCatalog(it.catalog, if (refresh) CatalogEvent.Refreshing else CatalogEvent.Loading)) }
        runCatching { repo.catalog().toCatalogUi() }
            .onSuccess { content -> _state.update { it.copy(catalog = reduceCatalog(it.catalog, CatalogEvent.Loaded(content))) } }
            .onFailure { failure(it, catalogFailure = true) }
    }

    fun saveService(draft: ServiceDraft, id: Int?, done: () -> Unit) {
        if (!validateService(draft).isValid) return
        catalogAction("Послугу збережено", done) { repo.save(draft.toRequest(id)) }
    }
    fun deleteService(id: Int) = catalogAction("Послугу приховано") { repo.deleteService(id) }

    fun saveEmployee(draft: EmployeeDraft, id: Int?, done: () -> Unit) {
        if (!validateEmployee(draft).isValid) return
        catalogAction("Працівника збережено", done) { repo.save(draft.toRequest(id)) }
    }
    fun deleteEmployee(id: Int) = catalogAction("Працівника приховано") { repo.deleteEmployee(id) }
    fun saveShift(draft: ShiftDraft, id: Int?, done: () -> Unit) {
        if (!validateShift(draft).isValid) return
        catalogAction("Графік збережено", done) { repo.save(draft.toRequest(id)) }
    }
    fun deleteShift(id: Int) = catalogAction("Зміну видалено") { repo.deleteShift(id) }

    private fun catalogAction(notice: String, done: () -> Unit = {}, block: suspend () -> Any) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        runCatching { block(); repo.catalog().toCatalogUi() }
            .onSuccess { content ->
                _state.update { it.copy(busy = false, catalog = CatalogState(content = content)) }
                _events.send(UiEvent(notice)); done()
            }
            .onFailure { failure(it) }
    }

    fun loadBookings() = viewModelScope.launch {
        _state.update { it.copy(bookingsLoading = true) }
        runCatching { repo.bookings() }
            .onSuccess { response -> _state.update { it.copy(bookings = response.bookings, bookingsLoading = false) } }
            .onFailure { failure(it) }
    }

    fun updateBooking(id: Int, status: String, note: String, done: () -> Unit) {
        if (!isEditableBookingStatus(status)) return
        bookingAction("Запис оновлено", done) { repo.status(id, status, note) }
    }
    fun deleteBooking(id: Int, done: () -> Unit = {}) = bookingAction("Запис видалено", done) { repo.deleteBooking(id) }
    private fun bookingAction(notice: String, done: () -> Unit, block: suspend () -> Any) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        runCatching { block(); repo.bookings() }
            .onSuccess { response -> _state.update { it.copy(busy = false, bookings = response.bookings) }; _events.send(UiEvent(notice)); done() }
            .onFailure { failure(it) }
    }

    fun loadStats() = viewModelScope.launch {
        runCatching { repo.stats() }.onSuccess { response -> _state.update { it.copy(stats = response) } }.onFailure { failure(it) }
    }

    fun loadAutomation() = viewModelScope.launch {
        _state.update { it.copy(automationLoading = true) }
        runCatching { repo.automationSummary() to repo.automationEvents().events }
            .onSuccess { (summary, events) -> _state.update { it.copy(automationSummary = summary, automationEvents = events, automationLoading = false) } }
            .onFailure { failure(it) }
    }

    fun retryAutomation(eventId: String) = automationAction("Повторний запуск виконано") { repo.retryAutomation(eventId) }
    fun testAutomation() = automationAction("Тестову подію надіслано") { repo.testAutomation() }
    private fun automationAction(notice: String, block: suspend () -> Any) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        runCatching { block(); repo.automationSummary() to repo.automationEvents().events }
            .onSuccess { (summary, events) ->
                _state.update { it.copy(busy = false, automationSummary = summary, automationEvents = events) }
                _events.send(UiEvent(notice))
            }
            .onFailure { failure(it) }
    }

    private fun failure(error: Throwable, catalogFailure: Boolean = false) {
        val message = userMessageFor(error.httpCode(), error) ?: "Не вдалося виконати дію. Спробуйте ще раз."
        if (error.httpCode() == 403) { repo.logout(); _state.value = AppState(loginError = message); return }
        _state.update { current -> current.copy(busy = false, bookingsLoading = false, automationLoading = false, catalog = if (catalogFailure) reduceCatalog(current.catalog, CatalogEvent.Failed(message)) else current.catalog) }
        viewModelScope.launch { _events.send(UiEvent(message)) }
    }
}
