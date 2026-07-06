package com.healthhand.admin.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

@Composable
fun StatsScreen() {
    val vm: StatsViewModel = viewModel()
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Period selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
        Spacer(Modifier.height(16.dp))

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
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.error_generic, err),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.load(state.days) }) {
                    Text(stringResource(R.string.retry))
                }
            }
            return@Column
        }

        val summary = state.summary
        if (summary == null || !summary.ok) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.stats_empty))
            }
            return@Column
        }

        // Events
        if (summary.events.isNotEmpty()) {
            Text(
                text = stringResource(R.string.stats_events),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            val maxCount = summary.events.values.max().coerceAtLeast(1)
            summary.events.forEach { (name, count) ->
                EventBarRow(
                    name = name,
                    count = count,
                    maxCount = maxCount,
                    barColor = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(20.dp))
        }

        // Top services
        if (summary.top_services.isNotEmpty()) {
            Text(
                text = stringResource(R.string.stats_top_services),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            val maxSvc = summary.top_services.maxOf { it.count }.coerceAtLeast(1)
            summary.top_services.forEach { ts ->
                EventBarRow(
                    name = ts.service,
                    count = ts.count,
                    maxCount = maxSvc,
                    barColor = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        if (summary.events.isEmpty() && summary.top_services.isEmpty()) {
            Text(stringResource(R.string.stats_empty))
        }
    }
}

@Composable
private fun EventBarRow(
    name: String,
    count: Int,
    maxCount: Int,
    barColor: Color
) {
    val fraction = count.toFloat() / maxCount.toFloat()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
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