package com.healthhand.admin.ui.catalog

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.healthhand.admin.data.models.Employee
import com.healthhand.admin.data.models.Service
import com.healthhand.admin.data.models.Shift
import com.healthhand.admin.ui.theme.EmptyStateCard
import com.healthhand.admin.ui.theme.MetricCard
import com.healthhand.admin.ui.theme.PremiumBackdrop
import com.healthhand.admin.ui.theme.ScreenHeader
import com.healthhand.admin.ui.theme.SectionCard
import com.healthhand.admin.ui.theme.StatusBadge

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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val tabs = listOf(
        stringResource(R.string.catalog_tab_services),
        stringResource(R.string.catalog_tab_employees),
        stringResource(R.string.catalog_tab_shifts)
    )

    val catalog = state.catalog
    val services = catalog?.services.orEmpty()
    val employees = catalog?.employees.orEmpty()
    val shifts = catalog?.shifts.orEmpty()

    PremiumBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ScreenHeader(
                    title = stringResource(R.string.catalog_title),
                    subtitle = "Панель керування сайтом: тут ви редагуєте ціни, послуги та майстрів, що клієнти бачать на health-hand сайті.",
                    trailing = {
                        IconButton(onClick = { vm.load(refresh = true) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Оновити", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                )

                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(label = "Послуги", value = services.size.toString(), caption = "активних ${services.count { it.is_active != 0 }}", modifier = Modifier.width(140.dp))
                    MetricCard(label = "Майстри", value = employees.size.toString(), caption = "видимих ${employees.count { it.show_on_site != 0 }}", modifier = Modifier.width(140.dp), accent = MaterialTheme.colorScheme.tertiary)
                    MetricCard(label = "Зміни", value = shifts.size.toString(), caption = "активних ${shifts.count { it.is_active != 0 }}", modifier = Modifier.width(140.dp), accent = MaterialTheme.colorScheme.secondary)
                }

                SectionCard {
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
                }

                if (state.loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    return@Scaffold
                }

                state.error?.let { err ->
                    SectionCard {
                        Text(stringResource(R.string.error_generic, err), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
                    }
                    return@Scaffold
                }

                if (catalog == null || !catalog.ok) {
                    EmptyStateCard(
                        title = stringResource(R.string.catalog_empty),
                        subtitle = "Дані каталогу ще не завантажені.",
                    )
                    return@Scaffold
                }

                when (selectedTab) {
                    0 -> {
                        if (services.isEmpty()) {
                            EmptyStateCard(title = stringResource(R.string.catalog_empty), subtitle = "Додайте першу послугу через кнопку +.")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(services) { svc ->
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
                        if (employees.isEmpty()) {
                            EmptyStateCard(title = stringResource(R.string.catalog_empty), subtitle = "Додайте першого майстра через кнопку +.")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(employees) { emp ->
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
                        if (shifts.isEmpty()) {
                            EmptyStateCard(title = stringResource(R.string.catalog_empty), subtitle = "Створіть хоча б одну зміну для графіка.")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(shifts) { shift ->
                                    ShiftRow(
                                        shift = shift,
                                        employees = employees,
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
        val assignedServiceIds = editingEmployee?.let { emp ->
            catalog?.employee_services?.filter { it.employee_id == emp.id }?.map { it.service_id }?.toSet()
        } ?: emptySet()
        EmployeeEditDialog(
            employee = editingEmployee,
            services = state.catalog?.services ?: emptyList(),
            assignedServiceIds = assignedServiceIds,
            onDismiss = { showEmployeeDialog = false },
            onSave = { name, role, bio, phone, sortOrder, isActive, showOnSite, serviceIds ->
                vm.saveEmployee(editingEmployee?.id, name, role, bio, phone, sortOrder, isActive, showOnSite, serviceIds)
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
        override val label = "Приховати послугу «$name» з сайту? Її можна знову увімкнути перемикачем «Активна»."
    }
    data class Employee(override val id: Int, val name: String) : DeleteTarget() {
        override val label = "Приховати майстра «$name» з сайту? Його можна знову увімкнути перемикачем «Активний»."
    }
    data class Shift(override val id: Int) : DeleteTarget() {
        override val label = "Видалити цю зміну назавжди? Цю дію неможливо скасувати."
    }
}

// --- Rows ---

@Composable
private fun ServiceRow(svc: Service, onEdit: () -> Unit, onDelete: () -> Unit) {
    SectionCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(svc.name.ifEmpty { "—" }, style = MaterialTheme.typography.titleMedium)
                    StatusBadge(
                        text = if (svc.is_active != 0) "Активна" else "Неактивна",
                        accent = if (svc.is_active != 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    )
                }
                if (svc.description.isNotEmpty()) {
                    Text(svc.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("${svc.duration_minutes} хв") })
                    AssistChip(onClick = {}, label = { Text("${svc.price} грн") })
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Редагувати") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
            }
        }
    }
}

@Composable
private fun EmployeeRow(emp: Employee, onEdit: () -> Unit, onDelete: () -> Unit) {
    SectionCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(emp.name.ifEmpty { "—" }, style = MaterialTheme.typography.titleMedium)
                    StatusBadge(
                        text = if (emp.show_on_site != 0) "На сайті" else "Прихований",
                        accent = if (emp.show_on_site != 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                    )
                }
                if (emp.role.isNotEmpty()) Text(emp.role, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (emp.bio.isNotEmpty()) Text(emp.bio, style = MaterialTheme.typography.bodyMedium)
                if (emp.phone.isNotEmpty()) Text(emp.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(if (emp.is_active != 0) "Активний" else "Неактивний") })
                    AssistChip(onClick = {}, label = { Text("Порядок ${emp.sort_order}") })
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Редагувати") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
            }
        }
    }
}

@Composable
private fun ShiftRow(shift: Shift, employees: List<Employee>, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dayNames = listOf("Нд", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
    val day = dayNames.getOrElse(shift.weekday) { "?" }
    val empName = employees.firstOrNull { it.id == shift.employee_id }?.name ?: "ID#${shift.employee_id}"
    SectionCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$empName", style = MaterialTheme.typography.titleMedium)
                    StatusBadge(
                        text = day,
                        accent = MaterialTheme.colorScheme.primary,
                    )
                    StatusBadge(
                        text = if (shift.is_active != 0) "Активна" else "Неактивна",
                        accent = if (shift.is_active != 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    )
                }
                Text("${shift.start_time} – ${shift.end_time}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("День тижня: $day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Редагувати") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
            }
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
    var categoryId by remember { mutableStateOf(service?.category_id) }
    var sortOrder by remember { mutableStateOf((service?.sort_order ?: 0).toString()) }
    var isActive by remember { mutableStateOf(service?.is_active != 0) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val categoryLabel = categories.firstOrNull { it.id == categoryId }?.name ?: "Без категорії"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (service == null) "Нова послуга" else "Редагувати послугу") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Назва") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Опис") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() } }, label = { Text("Тривалість (хв)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = price, onValueChange = { price = it.filter { c -> c.isDigit() } }, label = { Text("Ціна (грн)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                    OutlinedTextField(
                        value = categoryLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Категорія") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Без категорії") }, onClick = { categoryId = null; categoryMenuExpanded = false })
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat.name) }, onClick = { categoryId = cat.id; categoryMenuExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = sortOrder, onValueChange = { sortOrder = it.filter { c -> c.isDigit() } }, label = { Text("Порядок") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Активна")
                    Spacer(Modifier.fillMaxWidth(0.5f))
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                validationError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            val parsedDuration = duration.toIntOrNull() ?: 0
            val parsedPrice = price.toIntOrNull() ?: 0
            validationError = validateService(name, parsedDuration, parsedPrice)
            if (validationError == null) {
                onSave(name.trim(), desc.trim(), parsedDuration, parsedPrice, categoryId, sortOrder.toIntOrNull() ?: 0, isActive)
            }
        }) { Text("Зберегти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeEditDialog(
    employee: Employee?,
    services: List<Service>,
    assignedServiceIds: Set<Int>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int, Boolean, Boolean, List<Int>) -> Unit
) {
    var name by remember { mutableStateOf(employee?.name ?: "") }
    var role by remember { mutableStateOf(employee?.role ?: "massage_therapist") }
    var bio by remember { mutableStateOf(employee?.bio ?: "") }
    var phone by remember { mutableStateOf(employee?.phone ?: "") }
    var sortOrder by remember { mutableStateOf((employee?.sort_order ?: 0).toString()) }
    var isActive by remember { mutableStateOf(employee?.is_active != 0) }
    var showOnSite by remember { mutableStateOf(employee?.show_on_site != 0) }
    val selectedServiceIds = remember { mutableStateOf(assignedServiceIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (employee == null) "Новий майстер" else "Редагувати майстра") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                Spacer(Modifier.height(12.dp))
                Text("Послуги майстра", style = MaterialTheme.typography.titleSmall)
                if (services.isEmpty()) {
                    Text("Спершу додайте послуги в каталог.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    services.forEach { svc ->
                        val checked = selectedServiceIds.value.contains(svc.id)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("${svc.name} · ${svc.price} грн", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = checked,
                                onCheckedChange = { on ->
                                    selectedServiceIds.value = if (on) selectedServiceIds.value + svc.id else selectedServiceIds.value - svc.id
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name, role, bio, phone, sortOrder.toIntOrNull() ?: 0, isActive, showOnSite, selectedServiceIds.value.toList())
            }) { Text("Зберегти") }
        },
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
    var empId by remember { mutableStateOf(shift?.employee_id ?: (employees.firstOrNull()?.id ?: 0)) }
    var weekday by remember { mutableStateOf(shift?.weekday ?: 1) }
    var startTime by remember { mutableStateOf(shift?.start_time ?: "09:00") }
    var endTime by remember { mutableStateOf(shift?.end_time ?: "18:00") }
    var isActive by remember { mutableStateOf(shift?.is_active != 0) }
    var empMenuExpanded by remember { mutableStateOf(false) }
    var dayMenuExpanded by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val dayNames = listOf("Неділя", "Понеділок", "Вівторок", "Середа", "Четвер", "П'ятниця", "Субота")
    val empLabel = employees.firstOrNull { it.id == empId }?.name ?: "Оберіть майстра"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (shift == null) "Нова зміна" else "Редагувати зміну") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ExposedDropdownMenuBox(expanded = empMenuExpanded, onExpandedChange = { empMenuExpanded = it }) {
                    OutlinedTextField(
                        value = empLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Майстер") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = empMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = empMenuExpanded, onDismissRequest = { empMenuExpanded = false }) {
                        employees.forEach { emp ->
                            DropdownMenuItem(text = { Text(emp.name) }, onClick = { empId = emp.id; empMenuExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = dayMenuExpanded, onExpandedChange = { dayMenuExpanded = it }) {
                    OutlinedTextField(
                        value = dayNames.getOrElse(weekday) { "?" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("День тижня") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = dayMenuExpanded, onDismissRequest = { dayMenuExpanded = false }) {
                        dayNames.forEachIndexed { index, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { weekday = index; dayMenuExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Початок (HH:MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("Кінець (HH:MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Активна"); Spacer(Modifier.fillMaxWidth(0.5f)); Switch(checked = isActive, onCheckedChange = { isActive = it }) }
                validationError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            validationError = validateShift(empId, startTime, endTime)
            if (validationError == null) {
                onSave(empId, weekday.coerceIn(0, 6), startTime, endTime, isActive)
            }
        }) { Text("Зберегти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}