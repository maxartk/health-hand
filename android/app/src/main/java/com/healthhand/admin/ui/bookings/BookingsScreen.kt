package com.healthhand.admin.ui.bookings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthhand.admin.R
import com.healthhand.admin.data.models.Booking
import com.healthhand.admin.ui.theme.EmptyStateCard
import com.healthhand.admin.ui.theme.MetricCard
import com.healthhand.admin.ui.theme.PremiumBackdrop
import com.healthhand.admin.ui.theme.ScreenHeader
import com.healthhand.admin.ui.theme.SectionCard
import com.healthhand.admin.ui.theme.StatusBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookingsScreen() {
    val vm: BookingsViewModel = viewModel()
    val state by vm.state.collectAsState()
    var pendingDelete by remember { mutableStateOf<Booking?>(null) }

    val visible = state.bookings
    val newCount = visible.count { it.status == "new" }
    val confirmedCount = visible.count { it.status == "confirmed" }
    val completedCount = visible.count { it.status == "completed" }
    val actionCount = visible.count { it.status == "new" || it.status == "contacted" }

    PremiumBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(
                title = stringResource(R.string.bookings_title),
                subtitle = "Потік заявок, контактів і підтверджень в одному місці.",
                trailing = {
                    IconButton(onClick = { vm.load(refresh = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.bookings_retry), tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(label = "Усього", value = visible.size.toString(), caption = "видимі заявки", modifier = Modifier.width(140.dp))
                MetricCard(label = "Нові", value = newCount.toString(), caption = "очікують дії", modifier = Modifier.width(140.dp), accent = MaterialTheme.colorScheme.primary)
                MetricCard(label = "Підтв.", value = confirmedCount.toString(), caption = "у роботі", modifier = Modifier.width(140.dp), accent = MaterialTheme.colorScheme.tertiary)
                MetricCard(label = "Закриті", value = completedCount.toString(), caption = "завершені", modifier = Modifier.width(140.dp), accent = MaterialTheme.colorScheme.secondary)
            }

            SectionCard {
                Text("Фільтр статусу", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BookingFilterChip(state.filter == BookingsStatus.ALL, stringResource(R.string.bookings_status_all)) { vm.setFilter(BookingsStatus.ALL) }
                    BookingFilterChip(state.filter == BookingsStatus.NEW, stringResource(R.string.bookings_status_new)) { vm.setFilter(BookingsStatus.NEW) }
                    BookingFilterChip(state.filter == BookingsStatus.CONTACTED, stringResource(R.string.bookings_status_contacted)) { vm.setFilter(BookingsStatus.CONTACTED) }
                    BookingFilterChip(state.filter == BookingsStatus.CONFIRMED, stringResource(R.string.bookings_status_confirmed)) { vm.setFilter(BookingsStatus.CONFIRMED) }
                    BookingFilterChip(state.filter == BookingsStatus.COMPLETED, stringResource(R.string.bookings_status_completed)) { vm.setFilter(BookingsStatus.COMPLETED) }
                    BookingFilterChip(state.filter == BookingsStatus.CANCELLED, stringResource(R.string.bookings_status_cancelled)) { vm.setFilter(BookingsStatus.CANCELLED) }
                    BookingFilterChip(state.filter == BookingsStatus.NO_SHOW, stringResource(R.string.bookings_status_no_show)) { vm.setFilter(BookingsStatus.NO_SHOW) }
                    BookingFilterChip(state.filter == BookingsStatus.FOLLOWUP_SENT, stringResource(R.string.bookings_status_followup_sent)) { vm.setFilter(BookingsStatus.FOLLOWUP_SENT) }
                }
            }

            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }

            state.error?.let { err ->
                SectionCard {
                    Text(text = stringResource(R.string.bookings_error, err), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.load() }) { Text(stringResource(R.string.bookings_retry)) }
                }
                return@Column
            }

            if (state.bookings.isEmpty()) {
                EmptyStateCard(
                    title = stringResource(R.string.bookings_empty),
                    subtitle = "Коли з’являться заявки, вони тут будуть у вигляді чітких карток з діями і статусами.",
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.bookings) { booking ->
                    BookingCard(
                        booking = booking,
                        employees = state.employees,
                        onChangeStatus = { status, note -> vm.changeStatus(booking.id, status, note) {} },
                        onAssign = { startAt, endAt, empId -> vm.assignAppointment(booking.id, startAt, endAt, empId) {} },
                        onDelete = { pendingDelete = booking },
                    )
                }
            }

            if (pendingDelete != null) {
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text("Видалити заявку №${pendingDelete?.id}?") },
                    text = { Text("Цю дію неможливо скасувати.") },
                    confirmButton = {
                        TextButton(onClick = {
                            val del = pendingDelete
                            pendingDelete = null
                            if (del != null) vm.deleteBooking(del.id) {}
                        }) { Text("Видалити", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) { Text("Скасувати") }
                    }
                )
            }
        }
    }
}

@Composable
private fun BookingFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
fun BookingCard(
    booking: Booking,
    employees: List<com.healthhand.admin.data.models.Employee> = emptyList(),
    onChangeStatus: (String, String?) -> Unit,
    onAssign: (String, String?, Int?) -> Unit,
    onDelete: () -> Unit = {},
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    var showApptDialog by remember { mutableStateOf(false) }

    val statusAccent = when (booking.status) {
        "confirmed" -> MaterialTheme.colorScheme.tertiary
        "completed" -> Color(0xFF7CD992)
        "cancelled", "no_show" -> MaterialTheme.colorScheme.error
        "contacted" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    val statusText = when (booking.status) {
        "new" -> "Новий"
        "contacted" -> "Контакт"
        "confirmed" -> "Підтверджено"
        "completed" -> "Завершено"
        "cancelled" -> "Скасовано"
        "no_show" -> "Неявка"
        "followup_sent" -> "Follow-up"
        else -> booking.status
    }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.bookings_id, booking.id),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    StatusBadge(text = statusText, accent = statusAccent)
                }

                Text(
                    text = booking.lead_name.ifEmpty { "Клієнт без імені" },
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = booking.lead_contact.ifEmpty { "Контакт не вказаний" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_delete))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AssistChip(onClick = {}, label = { Text(booking.service.ifEmpty { "Без послуги" }) })
            AssistChip(onClick = {}, label = { Text(booking.channel.ifEmpty { "Канал: —" }) })
        }

        BookingInfoRow(label = "Дата", value = formatBookingDate(booking))
        BookingInfoRow(label = "Послуга", value = booking.service.ifEmpty { "—" })
        BookingInfoRow(label = "Примітка", value = booking.note.ifEmpty { "—" })
        if (booking.feedback_note.isNotBlank()) {
            BookingInfoRow(label = "Feedback", value = booking.feedback_note)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { showStatusDialog = true }) { Text(stringResource(R.string.bookings_change_status)) }
            OutlinedButton(onClick = { showApptDialog = true }) { Text(stringResource(R.string.bookings_assign_appt)) }
        }
    }

    if (showStatusDialog) {
        StatusChangeDialog(
            currentStatus = booking.status,
            onDismiss = { showStatusDialog = false },
            onConfirm = { status, note ->
                onChangeStatus(status, note)
                showStatusDialog = false
            }
        )
    }

    if (showApptDialog) {
        AppointmentDialog(
            employees = employees,
            onDismiss = { showApptDialog = false },
            onConfirm = { startAt, endAt, employeeId ->
                onAssign(startAt, endAt, employeeId)
                showApptDialog = false
            }
        )
    }
}

@Composable
private fun BookingInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusChangeDialog(currentStatus: String, onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var status by remember { mutableStateOf(currentStatus) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookings_change_status)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val options = listOf(
                    "new" to "Новий",
                    "contacted" to "Контакт",
                    "confirmed" to "Підтверджено",
                    "completed" to "Завершено",
                    "cancelled" to "Скасовано",
                    "no_show" to "Неявка",
                    "followup_sent" to "Follow-up",
                )
                options.forEach { (value, label) ->
                    FilterChip(selected = status == value, onClick = { status = value }, label = { Text(label) })
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.bookings_feedback_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(status, note.ifBlank { null }) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppointmentDialog(
    employees: List<com.healthhand.admin.data.models.Employee>,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Int?) -> Unit,
) {
    var startAt by remember { mutableStateOf("") }
    var endAt by remember { mutableStateOf("") }
    var employeeId by remember { mutableStateOf<Int?>(null) }
    var employeeMenuExpanded by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookings_assign_appt)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = startAt,
                    onValueChange = { startAt = it; validationError = null },
                    label = { Text(stringResource(R.string.bookings_start_at)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endAt,
                    onValueChange = { endAt = it; validationError = null },
                    label = { Text("Кінець (опц.)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = employeeMenuExpanded,
                    onExpandedChange = { employeeMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = employees.firstOrNull { it.id == employeeId }?.name ?: "Оберіть майстра",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.bookings_employee)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(employeeMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = employeeMenuExpanded,
                        onDismissRequest = { employeeMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Без призначеного майстра") },
                            onClick = { employeeId = null; validationError = null; employeeMenuExpanded = false },
                        )
                        employees.filter { it.is_active != 0 }.forEach { employee ->
                            DropdownMenuItem(
                                text = { Text(employee.name) },
                                onClick = { employeeId = employee.id; validationError = null; employeeMenuExpanded = false },
                            )
                        }
                    }
                }
                validationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                validationError = validateAppointment(startAt, endAt)
                if (validationError == null) {
                    onConfirm(startAt.trim(), endAt.trim().ifBlank { null }, employeeId)
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private fun formatBookingDate(booking: Booking): String {
    val raw = listOfNotNull(
        booking.date.takeIf { it.isNotBlank() },
        booking.time.takeIf { it.isNotBlank() }
    ).joinToString(" ")
    if (raw.isNotBlank()) return raw
    return if (booking.created_at > 0) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(booking.created_at * 1000L))
    } else {
        "Дата уточнюється"
    }
}
