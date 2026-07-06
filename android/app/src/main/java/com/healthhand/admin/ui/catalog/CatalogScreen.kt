package com.healthhand.admin.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen() {
    val vm: CatalogViewModel = viewModel()
    val state by vm.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.catalog_tab_services),
        stringResource(R.string.catalog_tab_employees),
        stringResource(R.string.catalog_tab_shifts)
    )

    Column(modifier = Modifier.fillMaxSize()) {
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
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.error_generic, err),
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { vm.load() }) {
                    Text(stringResource(R.string.retry))
                }
            }
            return@Column
        }

        val catalog = state.catalog
        if (catalog == null || !catalog.ok) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.catalog_empty)) }
            return@Column
        }

        when (selectedTab) {
            0 -> {
                if (catalog.services.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(stringResource(R.string.catalog_empty))
                    }
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        items(catalog.services) { svc ->
                            ServiceCard(svc)
                        }
                    }
                }
            }
            1 -> {
                if (catalog.employees.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(stringResource(R.string.catalog_empty))
                    }
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        items(catalog.employees) { emp ->
                            EmployeeCard(emp)
                        }
                    }
                }
            }
            2 -> {
                if (catalog.shifts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(stringResource(R.string.catalog_empty))
                    }
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        items(catalog.shifts) { shift ->
                            ShiftCard(shift, catalog.employees)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(svc: Service) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = svc.name.ifEmpty { "—" },
                style = MaterialTheme.typography.titleSmall
            )
            if (svc.description.isNotEmpty()) {
                Text(
                    text = svc.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val details = buildString {
                if (svc.duration_minutes > 0) append("${svc.duration_minutes} хв")
                if (svc.price > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${svc.price} грн")
                }
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmployeeCard(emp: Employee) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = emp.name.ifEmpty { "—" },
                style = MaterialTheme.typography.titleSmall
            )
            if (emp.role.isNotEmpty()) {
                Text(
                    text = emp.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (emp.phone.isNotEmpty()) {
                Text(
                    text = emp.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (emp.bio.isNotEmpty()) {
                Text(
                    text = emp.bio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ShiftCard(shift: Shift, employees: List<Employee>) {
    val dayNames = listOf("Нд", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
    val day = dayNames.getOrElse(shift.weekday) { "?" }
    val empName = employees.firstOrNull { it.id == shift.employee_id }?.name ?: "ID#${shift.employee_id}"

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "$empName · $day",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${shift.start_time} – ${shift.end_time}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}