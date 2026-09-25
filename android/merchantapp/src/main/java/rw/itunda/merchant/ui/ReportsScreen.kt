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
import java.util.Locale
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.core.designsystem.components.IdsLoading
import rw.itunda.core.designsystem.components.IdsErrorText
import rw.itunda.core.designsystem.components.IdsTabs
import rw.itunda.merchant.network.ReportDayDto
import rw.itunda.merchant.network.TopSellingProductDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReportsTab() {
    var days by remember { mutableStateOf<List<ReportDayDto>?>(null) }
    var topProducts by remember { mutableStateOf<List<TopSellingProductDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var rangeDays by remember { mutableStateOf(7) }

    LaunchedEffect(rangeDays) {
        days = null
        topProducts = null
        error = null
        try {
            val to = LocalDate.now()
            val from = to.minusDays((rangeDays - 1).toLong())
            val fromStr = from.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val toStr = to.format(DateTimeFormatter.ISO_LOCAL_DATE)
            days = NetworkClient.apiService.getReport(from = fromStr, to = toStr).days
            topProducts = NetworkClient.apiService.getTopSellingProducts(from = fromStr, to = toStr).products
        } catch (e: retrofit2.HttpException) {
            error = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't load your reports right now."
        } catch (e: Exception) {
            error = "Couldn't load your reports right now."
        }
    }

    error?.let { IdsErrorText(it, modifier = Modifier.padding(16.dp)) }

    val list = days
    if (list == null) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { IdsLoading() }
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
            IdsTabs(
                labels = listOf("Last 7 days", "Last 30 days"),
                selectedIndex = if (rangeDays == 7) 0 else 1,
                onSelectedIndexChange = { rangeDays = if (it == 0) 7 else 30 },
                modifier = Modifier.padding(top = 8.dp),
            )
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
                        Text("${String.format(Locale.US, "%,.0f", day.netAmount)} RWF net", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Gross ${String.format(Locale.US, "%,.0f", day.grossAmount)} RWF · Fees ${String.format(Locale.US, "%,.0f", day.fees)} RWF",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Real Coupang WING-style best-selling-products report -- merchant-mfe's own
        // ReportsScreen.tsx has had this since 2026-08-16, ported here via the
        // uncalled-endpoint sweep.
        item {
            Text("Top-selling products", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
        val products = topProducts
        if (products == null) {
            item {
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { IdsLoading() }
            }
        } else if (products.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No products sold in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            items(products, key = { it.productId }) { product ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column {
                            Text(product.productName, fontWeight = FontWeight.Bold)
                            Text("${product.unitsSold} sold", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${String.format(Locale.US, "%,.0f", product.revenue)} RWF", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
