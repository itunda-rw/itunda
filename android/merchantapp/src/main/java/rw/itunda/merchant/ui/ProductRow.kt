package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import rw.itunda.merchant.network.CreateTimeDealRequest
import rw.itunda.merchant.network.MenuOptionChoiceRequest
import rw.itunda.merchant.network.MenuOptionGroupDto
import rw.itunda.merchant.network.MerchantProductDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.PriceTierDto
import rw.itunda.merchant.network.SetPriceTiersRequest
import rw.itunda.merchant.network.TimeDealViewDto
import rw.itunda.merchant.network.UpdateProductStockRequest
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Real bulk/wholesale pricing (2026-07-25) -- closes the gap named in Baemin's own real
 * 배민상회 B2B supplies marketplace research: the real differentiator between a B2B
 * wholesale listing and a normal retail one is that price genuinely depends on
 * quantity. Up to 3 real tiers, quantity + price fields -- see backend
 * MerchantProductService.setPriceTiers's own doc comment for the real "must actually be
 * a discount" validation this relies on server-side.
 *
 * Extracted out of CatalogScreen.kt (2026-09-05) once that file crossed the 500-line
 * guideline for the first time -- this composable was already fully self-contained
 * (its own state, no CatalogTab coupling beyond the product/activeDeal/onRemoved/
 * onError parameters it already took), so this is a pure move, not a redesign.
 */
@Composable
internal fun ProductRow(product: MerchantProductDto, activeDeal: TimeDealViewDto?, onRemoved: () -> Unit, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var showTimeDeal by remember { mutableStateOf(false) }
    var dealPrice by remember { mutableStateOf("") }
    var dealQuantity by remember { mutableStateOf("") }
    var dealHours by remember { mutableStateOf("24") }
    var savingDeal by remember { mutableStateOf(false) }
    var showTiers by remember { mutableStateOf(false) }
    var tierRows by remember { mutableStateOf(listOf("" to "", "" to "", "" to "")) }
    var loadedTiers by remember { mutableStateOf(false) }
    var savingTiers by remember { mutableStateOf(false) }
    var showStockEditor by remember { mutableStateOf(false) }
    var stockDraft by remember { mutableStateOf(product.stockQuantity?.toString() ?: "") }
    var savingStock by remember { mutableStateOf(false) }
    var showAnalytics by remember { mutableStateOf(false) }
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
                    TextButton(onClick = { showTimeDeal = !showTimeDeal }) { Text(if (showTimeDeal) "Close" else if (activeDeal != null) "Time deal running" else "Time deal") }
                    TextButton(onClick = { if (showTiers) showTiers = false else openTierEditor() }) { Text(if (showTiers) "Close" else "Bulk pricing") }
                    TextButton(onClick = { showAnalytics = !showAnalytics }) { Text(if (showAnalytics) "Close" else "Analytics") }
                    TextButton(onClick = {
                        showOptions = !showOptions
                        if (showOptions && optionGroups == null) loadOptionGroups()
                    }) { Text(if (showOptions) "Close options" else "Options") }
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                NetworkClient.apiService.removeProduct(product.id); onRemoved()
                            } catch (e: retrofit2.HttpException) {
                                onError(rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't remove this product.")
                            } catch (e: Exception) {
                                onError("Couldn't remove this product.")
                            }
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
                    IdsTextField(value = stockDraft, onValueChange = { stockDraft = it }, label = "Available units", modifier = Modifier.fillMaxWidth())
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
                                } catch (e: retrofit2.HttpException) {
                                    onError(rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't update stock. Try again.")
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
            if (showTimeDeal) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (activeDeal != null) {
                        val deal = activeDeal.deal
                        Text(
                            "Running: ${"%,.0f".format(deal.dealPrice)} RWF (was ${"%,.0f".format(deal.originalPrice)}), ${deal.remainingQuantity}/${deal.totalQuantity} left, ends ${deal.endsAt}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        androidx.compose.material3.Button(
                            onClick = {
                                savingDeal = true
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.endTimeDeal(deal.id)
                                        onRemoved()
                                    } catch (e: retrofit2.HttpException) {
                                        onError(rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't end this time deal.")
                                    } catch (e: Exception) {
                                        onError("Couldn't end this time deal.")
                                    } finally {
                                        savingDeal = false
                                    }
                                }
                            },
                            enabled = !savingDeal,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (savingDeal) "Ending…" else "End deal now") }
                    } else {
                        Text(
                            "Real Coupang 타임특가-style scarcity pricing: a time-boxed, quantity-capped discount on this product.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IdsTextField(
                            value = dealPrice, onValueChange = { dealPrice = it },
                            label = "Deal price (RWF, must be less than ${"%,.0f".format(product.price)})",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        IdsTextField(value = dealQuantity, onValueChange = { dealQuantity = it }, label = "Total quantity", modifier = Modifier.fillMaxWidth())
                        IdsTextField(value = dealHours, onValueChange = { dealHours = it }, label = "Runs for how many hours", modifier = Modifier.fillMaxWidth())
                        androidx.compose.material3.Button(
                            onClick = {
                                val priceValue = dealPrice.trim().toDoubleOrNull()
                                val quantityValue = dealQuantity.trim().toIntOrNull()
                                val hoursValue = dealHours.trim().toLongOrNull()
                                if (priceValue == null || priceValue <= 0 || priceValue >= product.price) {
                                    onError("Deal price must be greater than zero and less than the regular price.")
                                    return@Button
                                }
                                if (quantityValue == null || quantityValue <= 0) {
                                    onError("Total quantity must be at least 1.")
                                    return@Button
                                }
                                if (hoursValue == null || hoursValue <= 0) {
                                    onError("Runtime must be at least 1 hour.")
                                    return@Button
                                }
                                val now = Instant.now()
                                savingDeal = true
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.createTimeDeal(
                                            CreateTimeDealRequest(
                                                productId = product.id, dealPrice = priceValue, totalQuantity = quantityValue,
                                                startsAt = now.toString(), endsAt = now.plus(hoursValue, ChronoUnit.HOURS).toString(),
                                            )
                                        )
                                        dealPrice = ""; dealQuantity = ""; dealHours = "24"
                                        showTimeDeal = false
                                        onRemoved()
                                    } catch (e: retrofit2.HttpException) {
                                        onError(rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't start this time deal.")
                                    } catch (e: Exception) {
                                        onError("Couldn't start this time deal.")
                                    } finally {
                                        savingDeal = false
                                    }
                                }
                            },
                            enabled = !savingDeal,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (savingDeal) "Starting…" else "Start time deal") }
                    }
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
                            IdsTextField(value = qty, onValueChange = { v -> tierRows = tierRows.toMutableList().also { it[index] = v to unitPrice } }, label = "Min qty", modifier = Modifier.weight(1f))
                            IdsTextField(value = unitPrice, onValueChange = { v -> tierRows = tierRows.toMutableList().also { it[index] = qty to v } }, label = "Price each (RWF)", modifier = Modifier.weight(1f))
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
                                } catch (e: retrofit2.HttpException) {
                                    onError(rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't save bulk pricing.")
                                } catch (e: Exception) {
                                    onError("Couldn't save bulk pricing.")
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
            if (showAnalytics) {
                ProductAnalyticsPanel(product.id)
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
                                            } catch (e: retrofit2.HttpException) {
                                                optionsError = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't remove this option group."
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
                    IdsTextField(value = newGroupName, onValueChange = { newGroupName = it }, label = "Group name (e.g. Size)", modifier = Modifier.fillMaxWidth())
                    newChoiceRows.forEachIndexed { index, (choiceName, priceDelta) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IdsTextField(value = choiceName, onValueChange = { v -> newChoiceRows = newChoiceRows.toMutableList().also { it[index] = v to priceDelta } }, label = "Choice", modifier = Modifier.weight(1f))
                            IdsTextField(value = priceDelta, onValueChange = { v -> newChoiceRows = newChoiceRows.toMutableList().also { it[index] = choiceName to v } }, label = "+RWF", modifier = Modifier.weight(1f))
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
                                } catch (e: retrofit2.HttpException) {
                                    optionsError = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't add this option group."
                                } catch (e: Exception) {
                                    optionsError = "Couldn't add this option group."
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
