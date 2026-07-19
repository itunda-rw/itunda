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

@Composable
fun CatalogTab() {
    var products by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
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
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                androidx.compose.material3.Button(
                    onClick = {
                        val amount = price.toDoubleOrNull()
                        if (name.isBlank() || amount == null || amount <= 0) {
                            error = "Enter a name and a real price."
                            return@Button
                        }
                        submitting = true
                        error = null
                        scope.launch {
                            try {
                                NetworkClient.apiService.addProduct(AddProductRequest(name.trim(), amount))
                                name = ""; price = ""
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
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(product.name, fontWeight = FontWeight.Bold)
                                Text("${"%,.0f".format(product.price)} RWF", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    try { NetworkClient.apiService.removeProduct(product.id); load() } catch (e: Exception) { error = "Couldn't remove this product." }
                                }
                            }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}
