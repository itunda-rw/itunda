package rw.itunda.merchant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.EatsOrderDto
import rw.itunda.merchant.network.MerchantDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.UpdateEatsOrderStatusRequest

private enum class MerchantTab { ORDERS, CATALOG, REGISTER, REPORTS }

@Composable
fun MerchantHomeScreen(merchant: MerchantDto, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(MerchantTab.ORDERS) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Itunda Merchant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(merchant.businessName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { NetworkClient.currentTokenStore().clearSession(); onLogout() }) {
                Icon(Icons.Filled.ExitToApp, contentDescription = "Log out")
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            listOf(
                MerchantTab.ORDERS to "Orders",
                MerchantTab.CATALOG to "Catalog",
                MerchantTab.REGISTER to "Register",
                MerchantTab.REPORTS to "Reports",
            ).forEach { (t, label) ->
                val selected = t == tab
                Text(
                    label,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 18.dp).clickable { tab = t },
                )
            }
        }

        when (tab) {
            MerchantTab.ORDERS -> OrdersTab()
            MerchantTab.CATALOG -> CatalogTab()
            MerchantTab.REGISTER -> PosTab()
            MerchantTab.REPORTS -> ReportsTab()
        }
    }
}

/**
 * Real incoming Eats orders (restaurant side) -- this previously only ever existed
 * in the consumer app's own bank-mfe (any merchant who wanted to manage incoming
 * orders had to use their buyer account's app, a real structural gap). Restaurant-
 * driven statuses only: PLACED -> ACCEPTED -> PREPARING -> READY_FOR_PICKUP; once a
 * rider claims it, this app's job is done (rider-driven statuses have no action here).
 */
@Composable
private fun OrdersTab() {
    var orders by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            orders = NetworkClient.apiService.getRestaurantOrders().orders
            error = null
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(8000)
            refresh()
        }
    }

    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

    val list = orders
    if (list == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val active = list.filter { it.status in setOf("PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP") }
    if (active.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No open orders right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(active, key = { it.id }) { order ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        StatusBadge(order.status)
                        Text("${"%,.0f".format(order.totalAmount)} RWF", fontWeight = FontWeight.Bold)
                    }
                    Text(order.deliveryAddress, style = MaterialTheme.typography.bodySmall)
                    order.deliveryNotes?.takeIf { it.isNotBlank() }?.let {
                        Text("Note: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    nextRestaurantAction(order.status)?.let { (nextStatus, label) ->
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.advanceRestaurantOrderStatus(order.id, UpdateEatsOrderStatusRequest(nextStatus))
                                        refresh()
                                    } catch (e: Exception) {
                                        error = "Couldn't update this order. Try again."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label) }
                    }
                }
            }
        }
    }
}

private fun nextRestaurantAction(status: String): Pair<String, String>? = when (status) {
    "PLACED" -> "ACCEPTED" to "Accept order"
    "ACCEPTED" -> "PREPARING" to "Start preparing"
    "PREPARING" -> "READY_FOR_PICKUP" to "Mark ready for pickup"
    else -> null
}

@Composable
internal fun StatusBadge(status: String) {
    val label = when (status) {
        "PLACED" -> "New order"
        "ACCEPTED" -> "Accepted"
        "PREPARING" -> "Preparing"
        "READY_FOR_PICKUP" -> "Ready for pickup"
        "RIDER_ASSIGNED" -> "Rider on the way"
        "PICKED_UP" -> "Out for delivery"
        "DELIVERED" -> "Delivered"
        "CANCELLED" -> "Cancelled"
        else -> status
    }
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
