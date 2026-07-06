package com.healthhand.admin.ui.catalog

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthhand.admin.R
import com.healthhand.admin.data.models.Employee
import com.healthhand.admin.data.models.Service
import com.healthhand.admin.data.models.Shift

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen() {
    val vm: CatalogViewModel = viewModel()
    val state by vm.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingService by remember { mutableStateOf<Service?>(null) }
    var editingEmployee by remember { mutableStateOf<Employee?>(null) }
    var editingShift by remember { mutableStateOf<Shift?>(null) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    var showServiceDialog by remember { mutableStateOf(false) }
    var showEmployeeDialog by remember { mutableStateOf(false) }
    var showShiftDialog by remember { mutableStateOf(false) }

    val tabs = listOf(
        stringResource(R.string.catalog_tab_services),
        stringResource(R.string.catalog_tab_employees),
        stringResource(R.string.catalog_tab_shifts)
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when (selectedTab) {
                    0 -> { editingService = null; showServiceDialog = true }
                    1 -> { editingEmployee = null; showEmployeeDialog = true }
                    2 -> { editingShift = null; showShiftDialog = true }
                }
            }) { Icon(Icons.Filled.Add, contentDescription = "Додати") }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                tabs = {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            )

            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            state.error?.let { err ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.error_generic, err), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
                }
                return@Column
            }

            val catalog = state.catalog
            if (catalog == null || !catalog.ok) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.catalog_empty))
                }
                return@Column
            }

            when (selectedTab) {
                0 -> {
                    if (catalog.services.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.catalog_empty)) }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(catalog.services) { svc ->
                                ServiceRow(
                                    svc = svc,
                                    onEdit = { editingService = svc; showServiceDialog = true },
                                    onDelete = { pendingDelete = DeleteTarget.Service(svc.id, svc.name) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (catalog.employees.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.catalog_empty)) }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(catalog.employees) { emp ->
                                EmployeeRow(
                                    emp = emp,
                                    onEdit = { editingEmployee = emp; showEmployeeDialog = true },
                                    onDelete = { pendingDelete = DeleteTarget.Employee(emp.id, emp.name) }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    if (catalog.shifts.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.catalog_empty)) }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(catalog.shifts) { shift ->
                                ShiftRow(
                                    shift = shift,
                                    employees = catalog.employees,
                                    onEdit = { editingShift = shift; showShiftDialog = true },
                                    onDelete = { pendingDelete = DeleteTarget.Shift(shift.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit dialogs
    if (showServiceDialog) {
        ServiceEditDialog(
            service = editingService,
            categories = state.catalog?.categories ?: emptyList(),
            onDismiss = { showServiceDialog = false },
            onSave = { name, desc, duration, price, catId, sortOrder, isActive ->
                vm.saveService(editingService?.id, name, desc, duration, price, catId, sortOrder, isActive)
                showServiceDialog = false
            }
        )
    }

    if (showEmployeeDialog) {
        EmployeeEditDialog(
            employee = editingEmployee,
            services = state.catalog?.services ?: emptyList(),
            onDismiss = { showEmployeeDialog = false },
            onSave = { name, role, bio, phone, sortOrder, isActive, showOnSite ->
                vm.saveEmployee(editingEmployee?.id, name, role, bio, phone, sortOrder, isActive, showOnSite)
                showEmployeeDialog = false
            }
        )
    }

    if (showShiftDialog) {
        ShiftEditDialog(
            shift = editingShift,
            employees = state.catalog?.employees ?: emptyList(),
            onDismiss = { showShiftDialog = false },
            onSave = { empId, weekday, start, end, isActive ->
                vm.saveShift(editingShift?.id, empId, weekday, start, end, isActive)
                showShiftDialog = false
            }
        )
    }

    // Delete confirmation
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Видалити?") },
            text = { Text(target.label) },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        is DeleteTarget.Service -> vm.deleteService(target.id)
                        is DeleteTarget.Employee -> vm.deleteEmployee(target.id)
                        is DeleteTarget.Shift -> vm.deleteShift(target.id)
                    }
                    pendingDelete = null
                }) { Text("Видалити", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Скасувати") }
            }
        )
    }
}

sealed class DeleteTarget {
    abstract val id: Int
    abstract val label: String
    data class Service(override val id: Int, val name: String) : DeleteTarget() {
        override val label = "Видалити послугу «$name»?"
    }
    data class Employee(override val id: Int, val name: String) : DeleteTarget() {
        override val label = "Видалити майстра «$name»?"
    }
    data class Shift(override val id: Int) : DeleteTarget() {
        override val label = "Видалити цю зміну?"
    }
}

// --- Rows ---

@Composable
private fun ServiceRow(svc: Service, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(svc.name.ifEmpty { "—" }, style = MaterialTheme.typography.titleSmall)
                Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Редагувати") }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
                }
            }
            if (svc.description.isNotEmpty()) {
                Text(svc.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val details = buildString {
                if (svc.duration_minutes > 0) append("${svc.duration_minutes} хв")
                if (svc.price > 0) { if (isNotEmpty()) append(" · "); append("${svc.price} грн") }
            }
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (svc.is_active == 0) Text("Неактивна", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EmployeeRow(emp: Employee, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(emp.name.ifEmpty { "—" }, style = MaterialTheme.typography.titleSmall)
                Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Редагувати") }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
                }
            }
            if (emp.role.isNotEmpty()) Text(emp.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (emp.phone.isNotEmpty()) Text(emp.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (emp.is_active == 0) Text("Неактивний", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (emp.show_on_site == 0) Text("Прихований на сайті", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShiftRow(shift: Shift, employees: List<Employee>, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dayNames = listOf("Нд", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
    val day = dayNames.getOrElse(shift.weekday) { "?" }
    val empName = employees.firstOrNull { it.id == shift.employee_id }?.name ?: "ID#${shift.employee_id}"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("$empName · $day", style = MaterialTheme.typography.titleSmall)
                Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Редагувати") }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
                }
            }
            Text("${shift.start_time} – ${shift.end_time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (shift.is_active == 0) Text("Неактивна", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

// --- Edit Dialogs ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceEditDialog(
    service: Service?,
    categories: List<com.healthhand.admin.data.models.Category>,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int, Int?, Int, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(service?.name ?: "") }
    var desc by remember { mutableStateOf(service?.description ?: "") }
    var duration by remember { mutableStateOf((service?.duration_minutes ?: 60).toString()) }
    var price by remember { mutableStateOf((service?.price ?: 0).toString()) }
    var catId by remember { mutableStateOf(service?.category_id?.toString() ?: "") }
    var sortOrder by remember { mutableStateOf((service?.sort_order ?: 0).toString()) }
    var isActive by remember { mutableStateOf(service?.is_active != 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (service == null) "Нова послуга" else "Редагувати послугу") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Назва") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Опис") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() } }, label = { Text("Тривалість (хв)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = price, onValueChange = { price = it.filter { c -> c.isDigit() } }, label = { Text("Ціна (грн)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = catId, onValueChange = { catId = it.filter { c -> c.isDigit() } }, label = { Text("ID категорії") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = sortOrder, onValueChange = { sortOrder = it.filter { c -> c.isDigit() } }, label = { Text("Порядок") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Активна")
                    Spacer(Modifier.fillMaxWidth(0.5f))
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, desc, duration.toIntOrNull() ?: 60, price.toIntOrNull() ?: 0, catId.toIntOrNull(), sortOrder.toIntOrNull() ?: 0, isActive) }) { Text("Зберегти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeEditDialog(
    employee: Employee?,
    services: List<Service>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int, Boolean, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(employee?.name ?: "") }
    var role by remember { mutableStateOf(employee?.role ?: "massage_therapist") }
    var bio by remember { mutableStateOf(employee?.bio ?: "") }
    var phone by remember { mutableStateOf(employee?.phone ?: "") }
    var sortOrder by remember { mutableStateOf((employee?.sort_order ?: 0).toString()) }
    var isActive by remember { mutableStateOf(employee?.is_active != 0) }
    var showOnSite by remember { mutableStateOf(employee?.show_on_site != 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (employee == null) "Новий майстер" else "Редагувати майстра") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ім'я") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Роль") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Про майстра") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Телефон") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = sortOrder, onValueChange = { sortOrder = it.filter { c -> c.isDigit() } }, label = { Text("Порядок") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Активний"); Spacer(Modifier.fillMaxWidth(0.5f)); Switch(checked = isActive, onCheckedChange = { isActive = it }) }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Показувати на сайті"); Spacer(Modifier.fillMaxWidth(0.4f)); Switch(checked = showOnSite, onCheckedChange = { showOnSite = it }) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, role, bio, phone, sortOrder.toIntOrNull() ?: 0, isActive, showOnSite) }) { Text("Зберегти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftEditDialog(
    shift: Shift?,
    employees: List<Employee>,
    onDismiss: () -> Unit,
    onSave: (Int, Int, String, String, Boolean) -> Unit
) {
    var empId by remember { mutableStateOf((shift?.employee_id ?: (employees.firstOrNull()?.id ?: 0)).toString()) }
    var weekday by remember { mutableStateOf((shift?.weekday ?: 1).toString()) }
    var startTime by remember { mutableStateOf(shift?.start_time ?: "09:00") }
    var endTime by remember { mutableStateOf(shift?.end_time ?: "18:00") }
    var isActive by remember { mutableStateOf(shift?.is_active != 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (shift == null) "Нова зміна" else "Редагувати зміну") },
        text = {
            Column {
                OutlinedTextField(value = empId, onValueChange = { empId = it.filter { c -> c.isDigit() } }, label = { Text("ID майстра") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = weekday, onValueChange = { weekday = it.filter { c -> c.isDigit() } }, label = { Text("День тижня (0=Нд, 6=Сб)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Початок (HH:MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("Кінець (HH:MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Активна"); Spacer(Modifier.fillMaxWidth(0.5f)); Switch(checked = isActive, onCheckedChange = { isActive = it }) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(empId.toIntOrNull() ?: 0, weekday.toIntOrNull()?.coerceIn(0, 6) ?: 1, startTime, endTime, isActive) }) { Text("Зберегти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}