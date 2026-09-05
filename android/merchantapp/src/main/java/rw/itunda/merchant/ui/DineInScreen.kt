package rw.itunda.merchant.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import java.util.Locale
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import rw.itunda.core.designsystem.components.IdsTextField
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
import rw.itunda.merchant.network.DineInOrderDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.UpdateDineInOrderStatusRequest
import rw.itunda.merchant.network.apiErrorMessage
import rw.itunda.merchant.network.dineInTableQrPayload

/**
 * Real 배민오더-style table/QR in-store ordering, restaurant side (2026-07-25) --
 * two real, independent jobs on one tab: print/display a real per-table QR (top), and
 * watch + progress real incoming table orders (below), same restaurant-driven-only
 * status chain (PLACED -> ACCEPTED -> PREPARING -> SERVED) OrdersTab's own Eats queue
 * already established, minus the rider-handoff step this order type never has.
 */
@Composable
fun DineInTab(restaurantId: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        TableQrGenerator(restaurantId)
        androidx.compose.material3.Divider(modifier = Modifier.padding(vertical = 4.dp))
        DineInOrdersQueue()
    }
}

@Composable
private fun TableQrGenerator(restaurantId: String) {
    var tableNumber by remember { mutableStateOf("") }
    var qrContent by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Table QR codes", fontWeight = FontWeight.Bold)
        Text(
            "Print this and leave it on a table -- a customer scans it to order straight to that table.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IdsTextField(value = tableNumber, onValueChange = { if (it.length <= 50) tableNumber = it }, label = "Table number", modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.padding(start = 8.dp))
            IdsButton(
                text = "Generate",
                enabled = tableNumber.isNotBlank(),
                size = IdsButtonSize.Medium,
                onClick = { qrContent = dineInTableQrPayload(restaurantId, tableNumber.trim()) },
            )
        }
        val content = qrContent
        if (content != null) {
            Spacer(modifier = Modifier.padding(top = 12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(bitmap = generateQrBitmap(content), contentDescription = "Table QR code", modifier = Modifier.size(200.dp))
                Text("Table $tableNumber", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun DineInOrdersQueue() {
    var orders by remember { mutableStateOf<List<DineInOrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            orders = NetworkClient.apiService.getDineInOrders().orders
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

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Table orders", fontWeight = FontWeight.Bold)
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

    val list = orders
    if (list == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val active = list.filter { it.status in setOf("PLACED", "ACCEPTED", "PREPARING") }
    if (active.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No open table orders right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(active, key = { it.id }) { order ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        DineInStatusBadge(order.status)
                        Text("${String.format(Locale.US, "%,.0f", order.totalAmount)} RWF", fontWeight = FontWeight.Bold)
                    }
                    Text("Table ${order.tableNumber}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    order.notes?.takeIf { it.isNotBlank() }?.let {
                        Text("Note: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    nextDineInAction(order.status)?.let { (nextStatus, label) ->
                        Spacer(modifier = Modifier.padding(top = 8.dp))
                        IdsButton(
                            text = label,
                            onClick = {
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.advanceDineInOrderStatus(order.id, UpdateDineInOrderStatusRequest(nextStatus))
                                        refresh()
                                    } catch (e: retrofit2.HttpException) {
                                        error = apiErrorMessage(e) ?: "Couldn't update this order. Try again."
                                    } catch (e: Exception) {
                                        error = "Couldn't update this order. Try again."
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun nextDineInAction(status: String): Pair<String, String>? = when (status) {
    "PLACED" -> "ACCEPTED" to "Accept order"
    "ACCEPTED" -> "PREPARING" to "Start preparing"
    "PREPARING" -> "SERVED" to "Mark served"
    else -> null
}

@Composable
private fun DineInStatusBadge(status: String) {
    val label = when (status) {
        "PLACED" -> "New order"
        "ACCEPTED" -> "Accepted"
        "PREPARING" -> "Preparing"
        "SERVED" -> "Served"
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
