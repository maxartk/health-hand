package com.healthhand.admin.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healthhand.admin.R
import com.healthhand.admin.ui.theme.EmptyStateCard
import com.healthhand.admin.ui.theme.MetricCard
import com.healthhand.admin.ui.theme.PremiumBackdrop
import com.healthhand.admin.ui.theme.ScreenHeader
import com.healthhand.admin.ui.theme.SectionCard

@Composable
fun StatsScreen() {
    val vm: StatsViewModel = viewModel()
    val state by vm.state.collectAsState()
    val summary = state.summary
    val totalEvents = summary?.events?.values?.sum() ?: 0
    val topService = summary?.top_services?.firstOrNull()
    val topEvent = summary?.events?.entries?.maxByOrNull { it.value }

    PremiumBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenHeader(
                title = stringResource(R.string.stats_title),
                subtitle = "Аналітика подій і послуг з преміальним dashboard-виглядом.",
            )

            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(label = "Період", value = "${state.days} дн.", caption = "поточне вікно", modifier = Modifier.width(140.dp))
                MetricCard(label = "Події", value = totalEvents.toString(), caption = "усіх записів", modifier = Modifier.width(140.dp))
                MetricCard(label = "Типів", value = (summary?.events?.size ?: 0).toString(), caption = "унікальних подій", modifier = Modifier.width(140.dp), accent = MaterialTheme.colorScheme.tertiary)
                MetricCard(label = "Топ послуга", value = topService?.count?.toString() ?: "0", caption = topService?.service ?: "немає", modifier = Modifier.width(160.dp), accent = MaterialTheme.colorScheme.secondary)
            }

            SectionCard {
                Text("Період", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(7, 30, 90).forEach { days ->
                        val labelRes = when (days) {
                            7 -> R.string.stats_days_7
                            30 -> R.string.stats_days_30
                            else -> R.string.stats_days_90
                        }
                        FilterChip(
                            selected = state.days == days,
                            onClick = { vm.load(days) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
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
                    Text(text = stringResource(R.string.error_generic, err), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.load(state.days) }) { Text(stringResource(R.string.retry)) }
                }
                return@Column
            }

            if (summary == null || !summary.ok) {
                EmptyStateCard(
                    title = stringResource(R.string.stats_empty),
                    subtitle = "Статистика ще не готова для цього періоду.",
                )
                return@Column
            }

            SectionCard {
                Text(text = stringResource(R.string.stats_events), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                if (summary.events.isEmpty()) {
                    Text(stringResource(R.string.stats_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val maxCount = summary.events.values.max().coerceAtLeast(1)
                    summary.events.entries.sortedByDescending { it.value }.forEach { (name, count) ->
                        EventBarRow(name = name, count = count, maxCount = maxCount, barColor = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            SectionCard {
                Text(text = stringResource(R.string.stats_top_services), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                if (summary.top_services.isEmpty()) {
                    Text(stringResource(R.string.stats_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val maxSvc = summary.top_services.maxOf { it.count }.coerceAtLeast(1)
                    summary.top_services.forEach { ts ->
                        EventBarRow(name = ts.service, count = ts.count, maxCount = maxSvc, barColor = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            if (topEvent != null) {
                SectionCard {
                    Text("Найпомітніша подія", style = MaterialTheme.typography.titleSmall)
                    Text(topEvent.key, style = MaterialTheme.typography.titleMedium)
                    Text("${topEvent.value} разів за обраний період", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EventBarRow(
    name: String,
    count: Int,
    maxCount: Int,
    barColor: Color,
) {
    val fraction = count.toFloat() / maxCount.toFloat()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            drawRect(
                color = trackColor,
                topLeft = Offset.Zero,
                size = size
            )
            drawRect(
                color = barColor,
                topLeft = Offset.Zero,
                size = Size(size.width * fraction, size.height)
            )
        }
    }
}
