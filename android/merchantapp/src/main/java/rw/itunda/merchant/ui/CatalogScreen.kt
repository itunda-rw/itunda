package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.AddProductRequest
import rw.itunda.merchant.network.MerchantProductDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.PriceTierDto
import rw.itunda.merchant.network.SetPriceTiersRequest

@Composable
fun CatalogTab() {
    var products by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var durationMinutes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            products = try { NetworkClient.apiService.getProductCatalog().products } catch (e: Exception) { emptyList() }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add a product", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price (RWF)") }, modifier = Modifier.fillMaxWidth())
                // Real bookable-service duration (2026-07-25) -- leaving this blank
                // keeps the product a normal cataloged good; a real minute value marks
                // it bookable (e.g. "Haircut", 30) via the new Availability tab.
                OutlinedTextField(
                    value = durationMinutes, onValueChange = { durationMinutes = it },
                    label = { Text("Booking duration in minutes (optional -- makes this a bookable service)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                androidx.compose.material3.Button(
                    onClick = {
                        val amount = price.toDoubleOrNull()
                        if (name.isBlank() || amount == null || amount <= 0) {
                            error = "Enter a name and a real price."
                            return@Button
                        }
                        val duration = durationMinutes.trim().ifBlank { null }?.toIntOrNull()
                        if (durationMinutes.isNotBlank() && duration == null) {
                            error = "Booking duration must be a whole number of minutes."
                            return@Button
                        }
                        submitting = true
                        error = null
                        scope.launch {
                            try {
                                NetworkClient.apiService.addProduct(AddProductRequest(name.trim(), amount, duration))
                                name = ""; price = ""; durationMinutes = ""
                                load()
                            } catch (e: Exception) {
                                error = "Couldn't add this product. Try again."
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(if (submitting) "Adding…" else "Add") }
            }
        }

        val list = products
        if (list == null) {
            CircularProgressIndicator()
        } else if (list.isEmpty()) {
            Text("No products yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { product ->
                    ProductRow(product, onRemoved = ::load, onError = { error = it })
                }
            }
        }
    }
}

/**
 * Real bulk/wholesale pricing (2026-07-25) -- closes the gap named in Baemin's own real
 * 배민상회 B2B supplies marketplace research: the real differentiator between a B2B
 * wholesale listing and a normal retail one is that price genuinely depends on
 * quantity. Up to 3 real tiers, quantity + price fields -- see backend
 * MerchantProductService.setPriceTiers's own doc comment for the real "must actually be
 * a discount" validation this relies on server-side.
 */
@Composable
private fun ProductRow(product: MerchantProductDto, onRemoved: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var showTiers by remember { mutableStateOf(false) }
    var tierRows by remember { mutableStateOf(listOf("" to "", "" to "", "" to "")) }
    var loadedTiers by remember { mutableStateOf(false) }
    var savingTiers by remember { mutableStateOf(false) }

    fun openTierEditor() {
        showTiers = true
        if (!loadedTiers) {
            scope.launch {
                try {
                    val tiers = NetworkClient.apiService.getPriceTiers(product.id).tiers
                    tierRows = (tiers.map { it.minQuantity.toString() to it.unitPrice.toString() } +
                        listOf("" to "", "" to "", "" to "")).take(3)
                } catch (e: Exception) {
                    // Best-effort -- the editor just starts blank.
                } finally {
                    loadedTiers = true
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(product.name, fontWeight = FontWeight.Bold)
                    val priceLine = "${"%,.0f".format(product.price)} RWF" + (product.durationMinutes?.let { " · ${it} min booking" } ?: "")
                    Text(priceLine, style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    TextButton(onClick = { if (showTiers) showTiers = false else openTierEditor() }) { Text(if (showTiers) "Close" else "Bulk pricing") }
                    TextButton(onClick = {
                        scope.launch {
                            try { NetworkClient.apiService.removeProduct(product.id); onRemoved() } catch (e: Exception) { onError("Couldn't remove this product.") }
                        }
                    }) { Text("Remove") }
                }
            }
            if (showTiers) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Real bulk discounts -- e.g. buy 10+, pay less per unit. Leave a row blank to skip it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    tierRows.forEachIndexed { index, (qty, unitPrice) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = qty, onValueChange = { v -> tierRows = tierRows.toMutableList().also { it[index] = v to unitPrice } },
                                label = { Text("Min qty") }, modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = unitPrice, onValueChange = { v -> tierRows = tierRows.toMutableList().also { it[index] = qty to v } },
                                label = { Text("Price each (RWF)") }, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    androidx.compose.material3.Button(
                        onClick = {
                            val tiers = tierRows.mapNotNull { (qtyText, priceText) ->
                                val qty = qtyText.trim().toIntOrNull()
                                val unitPrice = priceText.trim().toDoubleOrNull()
                                if (qty != null && unitPrice != null) PriceTierDto(qty, unitPrice) else null
                            }
                            savingTiers = true
                            scope.launch {
                                try {
                                    NetworkClient.apiService.setPriceTiers(product.id, SetPriceTiersRequest(tiers))
                                    showTiers = false
                                } catch (e: Exception) {
                                    onError("Couldn't save bulk pricing -- each higher tier must cost less per unit.")
                                } finally {
                                    savingTiers = false
                                }
                            }
                        },
                        enabled = !savingTiers,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (savingTiers) "Saving…" else "Save bulk pricing") }
                }
            }
        }
    }
}
