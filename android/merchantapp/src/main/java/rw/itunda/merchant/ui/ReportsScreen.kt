package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.ReportDayDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReportsTab() {
    var days by remember { mutableStateOf<List<ReportDayDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var rangeDays by remember { mutableStateOf(7) }

    LaunchedEffect(rangeDays) {
        days = null
        error = null
        try {
            val to = LocalDate.now()
            val from = to.minusDays((rangeDays - 1).toLong())
            days = NetworkClient.apiService.getReport(
                from = from.format(DateTimeFormatter.ISO_LOCAL_DATE),
                to = to.format(DateTimeFormatter.ISO_LOCAL_DATE),
            ).days
        } catch (e: Exception) {
            error = "Couldn't load your reports right now."
        }
    }

    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

    val list = days
    if (list == null) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Collections report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Settled collections only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(7, 30).forEach { period ->
                    FilterChip(
                        selected = rangeDays == period,
                        onClick = { rangeDays = period },
                        label = { Text("Last $period days") },
                    )
                }
            }
        }
        if (list.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No settled collections in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            item {
                val collectionCount = list.sumOf { it.collectionCount }
                val channelCounts = list.flatMap { it.byChannel.entries }.groupingBy { it.key }.fold(0.0) { total, entry -> total + entry.value }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$collectionCount collections", fontWeight = FontWeight.Bold)
                        Text(
                            channelCounts.entries.sortedByDescending { it.value }.joinToString(" · ") { (channel, count) -> "${channel.replace('_', ' ')} ${count.toInt()}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(list, key = { it.date }) { day ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(day.date, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${day.collectionCount} sales", style = MaterialTheme.typography.bodySmall)
                        Text("${"%,.0f".format(day.netAmount)} RWF net", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Gross ${"%,.0f".format(day.grossAmount)} RWF · Fees ${"%,.0f".format(day.fees)} RWF",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
