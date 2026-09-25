package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.IdsButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.AddProductRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.merchant.network.MerchantProductDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.TimeDealViewDto
import java.time.Instant

@Composable
fun CatalogTab() {
    var products by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    var timeDeals by remember { mutableStateOf<List<TimeDealViewDto>>(emptyList()) }
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
            timeDeals = try { NetworkClient.apiService.getMyTimeDeals().deals } catch (e: Exception) { emptyList() }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add a product", fontWeight = FontWeight.Bold)
                IdsTextField(value = name, onValueChange = { name = it }, label = "Name", modifier = Modifier.fillMaxWidth())
                IdsTextField(value = price, onValueChange = { price = it }, label = "Price (RWF)", modifier = Modifier.fillMaxWidth())
                IdsTextField(value = originalPrice, onValueChange = { originalPrice = it }, label = "Original price (optional, for a sale)", modifier = Modifier.fillMaxWidth())
                IdsTextField(value = imageUrl, onValueChange = { imageUrl = it }, label = "Public image URL (optional)", modifier = Modifier.fillMaxWidth())
                IdsTextField(value = description, onValueChange = { description = it }, label = "Description (optional)", modifier = Modifier.fillMaxWidth())
                IdsTextField(value = stockQuantity, onValueChange = { stockQuantity = it }, label = "Stock (optional — blank means unlimited)", modifier = Modifier.fillMaxWidth())
                // Real bookable-service duration (2026-07-25) -- leaving this blank
                // keeps the product a normal cataloged good; a real minute value marks
                // it bookable (e.g. "Haircut", 30) via the new Availability tab.
                IdsTextField(value = durationMinutes, onValueChange = { durationMinutes = it }, label = "Booking duration in minutes (optional -- makes this a bookable service)", modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                IdsButton(
                    text = if (submitting) "Adding…" else "Add",
                    onClick = {
                        val amount = price.toDoubleOrNull()
                        if (name.isBlank() || amount == null || amount <= 0) {
                            error = "Enter a name and a real price."
                            return@IdsButton
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
                            } catch (e: retrofit2.HttpException) {
                                error = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't add this product. Try again."
                            } catch (e: Exception) {
                                error = "Couldn't add this product. Try again."
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    enabled = !submitting,
                    loading = submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val list = products
        if (list == null) {
            CircularProgressIndicator()
        } else if (list.isEmpty()) {
            EmptyState("No products yet — add your first one above.", icon = Icons.Outlined.Inventory2)
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
                    val activeDeal = timeDeals.find { it.deal.productId == product.id && Instant.parse(it.deal.endsAt).isAfter(Instant.now()) }
                    ProductRow(product, activeDeal = activeDeal, onRemoved = ::load, onError = { error = it })
                }
            }
        }
    }
}
