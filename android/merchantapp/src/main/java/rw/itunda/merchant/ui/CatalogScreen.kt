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
import rw.itunda.merchant.network.AddMenuOptionGroupRequest
import rw.itunda.merchant.network.AddProductRequest
import rw.itunda.merchant.network.MenuOptionChoiceRequest
import rw.itunda.merchant.network.MenuOptionGroupDto
import rw.itunda.merchant.network.MerchantProductDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.PriceTierDto
import rw.itunda.merchant.network.SetPriceTiersRequest
import rw.itunda.merchant.network.UpdateProductStockRequest

@Composable
fun CatalogTab() {
    var products by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var durationMinutes by remember { mutableStateOf("") }
    var originalPrice by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var stockQuantity by remember { mutableStateOf("") }
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
                OutlinedTextField(value = originalPrice, onValueChange = { originalPrice = it }, label = { Text("Original price (optional, for a sale)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("Public image URL (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                OutlinedTextField(value = stockQuantity, onValueChange = { stockQuantity = it }, label = { Text("Stock (optional — blank means unlimited)") }, modifier = Modifier.fillMaxWidth())
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
                        val previousPrice = originalPrice.trim().ifBlank { null }?.toDoubleOrNull()
                        val stock = stockQuantity.trim().ifBlank { null }?.toIntOrNull()
                        if (durationMinutes.isNotBlank() && duration == null) {
                            error = "Booking duration must be a whole number of minutes."
                            return@Button
                        }
                        if (originalPrice.isNotBlank() && (previousPrice == null || previousPrice <= amount)) {
                            error = "Original price must be greater than the current price."
                            return@Button
                        }
                        if (stockQuantity.isNotBlank() && (stock == null || stock < 0)) {
                            error = "Stock must be a whole number of zero or more."
                            return@Button
                        }
                        submitting = true
                        error = null
                        scope.launch {
                            try {
                                NetworkClient.apiService.addProduct(AddProductRequest(
                                    name = name.trim(), price = amount, durationMinutes = duration,
                                    imageUrl = imageUrl.trim().ifBlank { null }, originalPrice = previousPrice,
                                    description = description.trim().ifBlank { null }, stockQuantity = stock,
                                ))
                                name = ""; price = ""; durationMinutes = ""; originalPrice = ""; imageUrl = ""; description = ""; stockQuantity = ""
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
            val lowStock = list.filter { it.stockQuantity != null && it.stockQuantity <= 5 }
            if (lowStock.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${lowStock.size} product${if (lowStock.size == 1) "" else "s"} need stock attention", fontWeight = FontWeight.Bold)
                        Text(
                            lowStock.joinToString(", ") { product ->
                                "${product.name} (${if (product.stockQuantity == 0) "out of stock" else "${product.stockQuantity} left"})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
    var showStockEditor by remember { mutableStateOf(false) }
    var stockDraft by remember { mutableStateOf(product.stockQuantity?.toString() ?: "") }
    var savingStock by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var optionGroups by remember { mutableStateOf<List<MenuOptionGroupDto>?>(null) }
    var newGroupName by remember { mutableStateOf("") }
    var newChoiceRows by remember { mutableStateOf(listOf("" to "0", "" to "0")) }
    var savingGroup by remember { mutableStateOf(false) }
    var removingGroupId by remember { mutableStateOf<String?>(null) }
    var optionsError by remember { mutableStateOf<String?>(null) }

    fun loadOptionGroups() {
        scope.launch {
            optionGroups = try { NetworkClient.apiService.getOptionGroups(product.id).optionGroups } catch (e: Exception) { emptyList() }
        }
    }

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
                    product.originalPrice?.let { original ->
                        Text("Was ${"%,.0f".format(original)} RWF · ${product.discountPercent ?: 0}% off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    product.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(
                        product.stockQuantity?.let { if (it == 0) "Out of stock" else "$it in stock" } ?: "Unlimited stock",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (product.stockQuantity == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    TextButton(onClick = {
                        stockDraft = product.stockQuantity?.toString() ?: ""
                        showStockEditor = !showStockEditor
                    }) { Text(if (showStockEditor) "Close stock" else "Adjust stock") }
                    TextButton(onClick = { if (showTiers) showTiers = false else openTierEditor() }) { Text(if (showTiers) "Close" else "Bulk pricing") }
                    TextButton(onClick = {
                        showOptions = !showOptions
                        if (showOptions && optionGroups == null) loadOptionGroups()
                    }) { Text(if (showOptions) "Close options" else "Options") }
                    TextButton(onClick = {
                        scope.launch {
                            try { NetworkClient.apiService.removeProduct(product.id); onRemoved() } catch (e: Exception) { onError("Couldn't remove this product.") }
                        }
                    }) { Text("Remove") }
                }
            }
            if (showStockEditor) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Set the units currently available. Leave blank for unlimited availability.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = stockDraft,
                        onValueChange = { stockDraft = it },
                        label = { Text("Available units") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.Button(
                        onClick = {
                            val stock = stockDraft.trim().ifBlank { null }?.toIntOrNull()
                            if (stockDraft.isNotBlank() && (stock == null || stock < 0)) {
                                onError("Stock must be a whole number of zero or more.")
                                return@Button
                            }
                            savingStock = true
                            scope.launch {
                                try {
                                    NetworkClient.apiService.updateProductStock(product.id, UpdateProductStockRequest(stock))
                                    showStockEditor = false
                                    onRemoved()
                                } catch (e: Exception) {
                                    onError("Couldn't update stock. Try again.")
                                } finally {
                                    savingStock = false
                                }
                            }
                        },
                        enabled = !savingStock,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (savingStock) "Saving…" else "Save stock") }
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
            if (showOptions) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Real option groups -- e.g. \"Size\" with Small/Medium/Large. A buyer picks exactly one choice per group.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    optionsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    val groups = optionGroups
                    if (groups == null) {
                        Text("Loading…", style = MaterialTheme.typography.bodySmall)
                    } else if (groups.isEmpty()) {
                        Text("No option groups yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        groups.forEach { group ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        group.choices.joinToString(", ") { c -> if (c.priceDelta > 0) "${c.name} (+${"%,.0f".format(c.priceDelta)} RWF)" else c.name },
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        removingGroupId = group.id
                                        scope.launch {
                                            try {
                                                NetworkClient.apiService.removeOptionGroup(product.id, group.id)
                                                loadOptionGroups()
                                            } catch (e: Exception) {
                                                optionsError = "Couldn't remove this option group."
                                            } finally {
                                                removingGroupId = null
                                            }
                                        }
                                    },
                                    enabled = removingGroupId != group.id,
                                ) { Text(if (removingGroupId == group.id) "Removing…" else "Remove") }
                            }
                        }
                    }
                    Text("Add an option group", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    OutlinedTextField(
                        value = newGroupName, onValueChange = { newGroupName = it },
                        label = { Text("Group name (e.g. Size)") }, modifier = Modifier.fillMaxWidth(),
                    )
                    newChoiceRows.forEachIndexed { index, (choiceName, priceDelta) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = choiceName,
                                onValueChange = { v -> newChoiceRows = newChoiceRows.toMutableList().also { it[index] = v to priceDelta } },
                                label = { Text("Choice") }, modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = priceDelta,
                                onValueChange = { v -> newChoiceRows = newChoiceRows.toMutableList().also { it[index] = choiceName to v } },
                                label = { Text("+RWF") }, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    TextButton(onClick = { newChoiceRows = newChoiceRows + ("" to "0") }) { Text("Add another choice") }
                    androidx.compose.material3.Button(
                        onClick = {
                            val choices = newChoiceRows.mapNotNull { (choiceName, priceDeltaText) ->
                                val trimmed = choiceName.trim()
                                if (trimmed.isEmpty()) return@mapNotNull null
                                MenuOptionChoiceRequest(trimmed, priceDeltaText.trim().toDoubleOrNull() ?: 0.0)
                            }
                            if (newGroupName.isBlank() || choices.size < 2) {
                                optionsError = "Enter a group name and at least 2 named choices."
                                return@Button
                            }
                            savingGroup = true
                            optionsError = null
                            scope.launch {
                                try {
                                    NetworkClient.apiService.addOptionGroup(product.id, AddMenuOptionGroupRequest(newGroupName.trim(), choices))
                                    newGroupName = ""
                                    newChoiceRows = listOf("" to "0", "" to "0")
                                    loadOptionGroups()
                                } catch (e: Exception) {
                                    optionsError = "Couldn't add this option group -- a required group needs at least one +0 RWF choice."
                                } finally {
                                    savingGroup = false
                                }
                            }
                        },
                        enabled = !savingGroup,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (savingGroup) "Saving…" else "Add option group") }
                }
            }
        }
    }
}
