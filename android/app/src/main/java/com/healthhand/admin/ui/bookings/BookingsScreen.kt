package com.healthhand.admin.ui.bookings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthhand.admin.R
import com.healthhand.admin.data.models.Booking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingsScreen(contentPadding: PaddingValues = PaddingValues()) {
    val vm: BookingsViewModel = viewModel()
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = state.filter == BookingsStatus.ALL,
                onClick = { vm.setFilter(BookingsStatus.ALL) },
                label = { Text(stringResource(R.string.bookings_status_all)) }
            )
            FilterChip(
                selected = state.filter == BookingsStatus.NEW,
                onClick = { vm.setFilter(BookingsStatus.NEW) },
                label = { Text(stringResource(R.string.bookings_status_new)) }
            )
            FilterChip(
                selected = state.filter == BookingsStatus.CONTACTED,
                onClick = { vm.setFilter(BookingsStatus.CONTACTED) },
                label = { Text(stringResource(R.string.bookings_status_contacted)) }
            )
            FilterChip(
                selected = state.filter == BookingsStatus.CONFIRMED,
                onClick = { vm.setFilter(BookingsStatus.CONFIRMED) },
                label = { Text(stringResource(R.string.bookings_status_confirmed)) }
            )
            FilterChip(
                selected = state.filter == BookingsStatus.COMPLETED,
                onClick = { vm.setFilter(BookingsStatus.COMPLETED) },
                label = { Text(stringResource(R.string.bookings_status_completed)) }
            )
            FilterChip(
                selected = state.filter == BookingsStatus.CANCELLED,
                onClick = { vm.setFilter(BookingsStatus.CANCELLED) },
                label = { Text(stringResource(R.string.bookings_status_cancelled)) }
            )
            FilterChip(
                selected = state.filter == BookingsStatus.NO_SHOW,
                onClick = { vm.setFilter(BookingsStatus.NO_SHOW) },
                label = { Text(stringResource(R.string.bookings_status_no_show)) }
            )
            FilterChip(
                selected = state.filter == BookingsStatus.FOLLOWUP_SENT,
                onClick = { vm.setFilter(BookingsStatus.FOLLOWUP_SENT) },
                label = { Text(stringResource(R.string.bookings_status_followup_sent)) }
            )
        }

        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        state.error?.let { err ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.bookings_error, err),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.load() }) {
                    Text(stringResource(R.string.bookings_retry))
                }
            }
            return@Column
        }

        if (state.bookings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.bookings_empty))
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.bookings) { booking ->
                BookingCard(
                    booking = booking,
                    onRefresh = { vm.load(refresh = true) },
                    onChangeStatus = { status, note ->
                        vm.changeStatus(booking.id, status, note) {}
                    },
                    onAssign = { startAt, endAt, empId ->
                        vm.assignAppointment(booking.id, startAt, endAt, empId) {}
                    }
                )
            }
        }
    }
}

@Composable
private fun BookingCardOld(
    booking: Booking,
    onChangeStatus: (String) -> Unit,
    onAssign: (String) -> Unit
) {
    BookingCard(
        booking = booking,
        onRefresh = {},
        onChangeStatus = { status, _ -> onChangeStatus(status) },
        onAssign = { startAt, _, _ -> onAssign(startAt) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingCard(
    booking: Booking,
    onRefresh: () -> Unit,
    onChangeStatus: (String, String?) -> Unit,
    onAssign: (String, String?, Int?) -> Unit
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    var showApptDialog by remember { mutableStateOf(false) }

    val statusLabels = mapOf(
        "new" to "Новий",
        "contacted" to "Контакт",
        "confirmed" to "Підтверджено",
        "completed" to "Завершено",
        "cancelled" to "Скасовано",
        "no_show" to "Неявка",
        "followup_sent" to "Follow-up"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.bookings_id, booking.id),
                    style = MaterialTheme.typography.titleSmall
                )
                AssistChip(
                    onClick = {},
                    label = { Text(statusLabels[booking.status] ?: booking.status) },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${booking.lead_name.ifEmpty { "—" }} · ${booking.lead_contact.ifEmpty { "—" }}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.bookings_service, booking.service.ifEmpty { "—" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (booking.date.isNotEmpty() || booking.time.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.bookings_date, booking.date, booking.time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (booking.channel.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.bookings_channel, booking.channel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (booking.created_at > 0) {
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                Text(
                    text = stringResource(R.string.bookings_created, sdf.format(Date(booking.created_at * 1000L))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (booking.note.isNotEmpty()) {
                Text(
                    text = booking.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showStatusDialog = true }) {
                    Text(stringResource(R.string.bookings_change_status))
                }
                OutlinedButton(onClick = { showApptDialog = true }) {
                    Text(stringResource(R.string.bookings_assign_appt))
                }
            }
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
            onDismiss = { showApptDialog = false },
            onConfirm = { startAt, endAt, empId ->
                onAssign(startAt, endAt, empId)
                showApptDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusChangeDialog(
    currentStatus: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    val statuses = listOf(
        "new" to "Новий",
        "contacted" to "Контакт",
        "confirmed" to "Підтверджено",
        "completed" to "Завершено",
        "cancelled" to "Скасовано",
        "no_show" to "Неявка",
        "followup_sent" to "Follow-up"
    )
    var selected by remember { mutableStateOf(currentStatus) }
    var note by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookings_select_status)) },
        text = {
            Column {
                statuses.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selected == value,
                            onClick = { selected = value }
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { selected = value }) {
                            Text(label)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.bookings_feedback_note)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected, note.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(R.string.bookings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bookings_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppointmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Int?) -> Unit
) {
    var startAt by remember { mutableStateOf("") }
    var endAt by remember { mutableStateOf("") }
    var empId by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookings_assign_appt)) },
        text = {
            Column {
                OutlinedTextField(
                    value = startAt,
                    onValueChange = { startAt = it },
                    label = { Text(stringResource(R.string.bookings_start_at)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = endAt,
                    onValueChange = { endAt = it },
                    label = { Text("Кінець (опц., YYYY-MM-DDTHH:MM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = empId,
                    onValueChange = { empId = it.filter { c -> c.isDigit() } },
                    label = { Text("ID майстра (опц.)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    startAt.trim(),
                    endAt.trim().takeIf { it.isNotBlank() },
                    empId.trim().toIntOrNull()
                )
            }) {
                Text(stringResource(R.string.bookings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bookings_cancel))
            }
        }
    )
}