package rw.itunda.merchant.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import rw.itunda.core.designsystem.components.IdsButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.merchant.network.ChargeCardRequest
import rw.itunda.merchant.network.GenerateQrRequest
import rw.itunda.merchant.network.MerchantProductDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.paymentIntentQrPayload

private data class CartLine(val product: MerchantProductDto, val quantity: Int)

/**
 * Real cash-register/POS UI, ported field-for-field from merchant-mfe's own
 * PosScreen.tsx -- checkout reuses the already-real generateQr/chargeCard flows
 * unmodified, just with a cart-derived amount/description instead of a single
 * typed-in amount.
 */
@Composable
fun PosTab() {
    var products by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    var cart by remember { mutableStateOf<List<CartLine>>(emptyList()) }
    var checkingOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        products = try { NetworkClient.apiService.getProductCatalog().products } catch (e: Exception) { emptyList() }
    }

    val total = cart.sumOf { it.product.price * it.quantity }
    val description = cart.joinToString(", ") { "${it.quantity}x ${it.product.name}" }

    if (checkingOut) {
        CheckoutView(
            total = total,
            description = description,
            onDone = { cart = emptyList(); checkingOut = false },
            onCancel = { checkingOut = false },
        )
        return
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.weight(2f)) {
            val list = products
            if (list == null) {
                SkeletonBlock()
            } else if (list.isEmpty()) {
                EmptyState("No products yet — add some in the Catalog tab first.", icon = Icons.Outlined.Inventory2)
            } else {
                ProductGrid(
                    products = list,
                    onAdd = { product ->
                        cart = cart.toMutableList().apply {
                            val index = indexOfFirst { it.product.id == product.id }
                            val nextQuantity = if (index >= 0) this[index].quantity + 1 else 1
                            if (product.stockQuantity == null || nextQuantity <= product.stockQuantity) {
                                if (index >= 0) this[index] = this[index].copy(quantity = nextQuantity)
                                else add(CartLine(product, 1))
                            }
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.padding(horizontal = 8.dp))

        Card(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Cart", fontWeight = FontWeight.Bold)
                if (cart.isEmpty()) {
                    Text("Tap a product to add it.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    cart.forEach { line ->
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("${line.quantity}x ${line.product.name}", style = MaterialTheme.typography.bodySmall)
                            Text("${"%,.0f".format(line.product.price * line.quantity)} RWF", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    Text("${"%,.0f".format(total)} RWF", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.padding(top = 8.dp))
                IdsButton(text = "Checkout", enabled = cart.isNotEmpty(), onClick = { checkingOut = true })
            }
        }
    }
}

@Composable
private fun ProductGrid(products: List<MerchantProductDto>, onAdd: (MerchantProductDto) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 130.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(products, key = { it.id }) { product ->
            val soldOut = product.stockQuantity == 0
            Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !soldOut) { onAdd(product) }) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(product.name, fontWeight = FontWeight.Bold)
                    Text("${"%,.0f".format(product.price)} RWF", style = MaterialTheme.typography.bodySmall)
                    Text(
                        when (product.stockQuantity) {
                            null -> "Unlimited stock"
                            0 -> "Out of stock"
                            else -> "${product.stockQuantity} in stock"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (soldOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckoutView(total: Double, description: String, onDone: () -> Unit, onCancel: () -> Unit) {
    var mode by remember { mutableStateOf("QR") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Checkout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("${"%,.0f".format(total)} RWF", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.padding(top = 16.dp))

        Row {
            listOf("QR" to "QR code", "CARD" to "Card").forEach { (v, label) ->
                val selected = mode == v
                Text(
                    label,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp).clickable { mode = v },
                )
            }
        }
        Spacer(modifier = Modifier.padding(top = 12.dp))

        if (mode == "QR") {
            QrCheckout(amount = total, description = description, onDone = onDone)
        } else {
            CardCheckout(amount = total, description = description, onDone = onDone)
        }

        Spacer(modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = onCancel) { Text("Back to cart") }
    }
}

@Composable
private fun QrCheckout(amount: Double, description: String, onDone: () -> Unit) {
    var qrContent by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun generate() {
        error = null
        qrContent = null
        scope.launch {
            try {
                val intent = NetworkClient.apiService.generateQr(GenerateQrRequest(amount, description)).paymentIntent
                qrContent = paymentIntentQrPayload(intent.id)
            } catch (e: Exception) {
                error = "Could not generate a QR code."
            }
        }
    }

    LaunchedEffect(Unit) { generate() }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val content = qrContent
        if (content != null) {
            Image(bitmap = generateQrBitmap(content), contentDescription = "Payment QR code", modifier = Modifier.size(240.dp))
            Spacer(modifier = Modifier.padding(top = 12.dp))
            IdsButton(text = "Done — new sale", onClick = onDone)
        } else {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { generate() }) { Text("Retry") }
        }
    }
}

@Composable
private fun CardCheckout(amount: Double, description: String, onDone: () -> Unit) {
    var cardNumber by remember { mutableStateOf("") }
    var expiryMonth by remember { mutableStateOf("") }
    var expiryYear by remember { mutableStateOf("") }
    var cvc by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    // Real device step-up (2026-07-28 port) -- a real 403 DEVICE_NOT_VERIFIED (this
    // device hasn't been step-up-verified yet) gets its own case, not a generic error.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (needsDeviceVerification) {
        rw.itunda.merchant.ui.DeviceStepUpDialog(
            onVerified = { needsDeviceVerification = false },
            onCancel = { needsDeviceVerification = false },
        )
    }

    if (result != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Card charged — •••• $result", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.padding(top = 12.dp))
            IdsButton(text = "Done — new sale", onClick = onDone)
        }
        return
    }

    Column {
        IdsTextField(
            value = cardNumber, onValueChange = { cardNumber = it }, label = "Card number",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            IdsTextField(value = expiryMonth, onValueChange = { expiryMonth = it }, label = "MM", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            IdsTextField(value = expiryYear, onValueChange = { expiryYear = it }, label = "YYYY", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            IdsTextField(value = cvc, onValueChange = { cvc = it }, label = "CVC", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        IdsButton(
            text = if (submitting) "Charging…" else "Charge ${"%,.0f".format(amount)} RWF",
            enabled = !submitting,
            onClick = {
                val month = expiryMonth.toIntOrNull()
                val year = expiryYear.toIntOrNull()
                if (cardNumber.isBlank() || month == null || year == null || cvc.isBlank()) {
                    error = "Fill in every card field."
                    return@IdsButton
                }
                submitting = true
                error = null
                scope.launch {
                    try {
                        val charge = NetworkClient.apiService.chargeCard(
                            request = ChargeCardRequest(amount, description, cardNumber.replace(" ", ""), month, year, cvc),
                        )
                        result = charge.cardLast4
                    } catch (e: retrofit2.HttpException) {
                        if (rw.itunda.merchant.network.isDeviceNotVerifiedError(e)) {
                            needsDeviceVerification = true
                        } else {
                            error = "Could not charge this card."
                        }
                    } catch (e: Exception) {
                        error = "Could not charge this card."
                    } finally {
                        submitting = false
                    }
                }
            },
        )
    }
}
