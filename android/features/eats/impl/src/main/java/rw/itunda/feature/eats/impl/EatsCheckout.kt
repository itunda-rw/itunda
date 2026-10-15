package rw.itunda.feature.eats.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.DineInOrderDto
import rw.itunda.core.network.DineInOrderItemRequest
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsOrderItemRequest
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PlaceDineInOrderRequest
import rw.itunda.core.network.PlaceEatsOrderRequest
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID




internal enum class EatsCheckoutMode { DELIVERY, PICKUP, DINE_IN }

@Composable
internal fun EatsCheckoutView(
    restaurant: ShoppingMerchantDto,
    cart: Map<String, EatsCartLine>,
    menu: List<MerchantProductDto>,
    onBack: () -> Unit,
    onOrderPlaced: (EatsOrderDto) -> Unit,
    onDineInOrderPlaced: (DineInOrderDto) -> Unit,
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(EatsCheckoutMode.DELIVERY) }
    var address by remember { mutableStateOf("") }
    var addressLatitude by remember { mutableStateOf<Double?>(null) }
    var addressLongitude by remember { mutableStateOf<Double?>(null) }
    var deliveryNotes by remember { mutableStateOf("") }
    // Real 배민오더-style table/QR ordering (2026-07-25) -- the table number a buyer
    // reads off the physical tag/QR at their table. See DineInOrder.kt's own doc
    // comment for why this is free text rather than a validated table registry.
    var tableNumber by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var showOrderReview by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real device binding step-up (2026-07-21) -- Eats checkout was a real gap:
    // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
    // showed only a generic error, same fix already applied to Transfer/Savings.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val idempotencyKey = remember { UUID.randomUUID().toString() }

    val lines = cart.values.filter { it.quantity > 0 }.mapNotNull { line -> menu.find { it.id == line.productId }?.let { it to line } }
    val total = lines.sumOf { (p, line) -> eatsLineUnitPrice(p, line.choiceIds) * line.quantity }
    val canSubmit = when (mode) {
        EatsCheckoutMode.DELIVERY -> address.isNotBlank()
        EatsCheckoutMode.PICKUP -> true
        EatsCheckoutMode.DINE_IN -> tableNumber.isNotBlank()
    }

    suspend fun submitDelivery() {
        val res = NetworkClient.apiService.placeEatsOrder(
            idempotencyKey = idempotencyKey,
            request = PlaceEatsOrderRequest(
                restaurantId = restaurant.merchantId,
                items = lines.map { (p, line) -> EatsOrderItemRequest(p.id, line.quantity, line.choiceIds.ifEmpty { null }) },
                deliveryAddress = address.trim(),
                deliveryLatitude = addressLatitude,
                deliveryLongitude = addressLongitude,
                deliveryNotes = deliveryNotes.trim().ifBlank { null },
            ),
        )
        if (res.success) onOrderPlaced(res.order)
    }

    // Real Baemin-style 포장주문 (Pickup) order type (item 208) -- see
    // PlaceEatsOrderRequest.fulfillmentType's own doc comment.
    suspend fun submitPickup() {
        val res = NetworkClient.apiService.placeEatsOrder(
            idempotencyKey = idempotencyKey,
            request = PlaceEatsOrderRequest(
                restaurantId = restaurant.merchantId,
                items = lines.map { (p, line) -> EatsOrderItemRequest(p.id, line.quantity, line.choiceIds.ifEmpty { null }) },
                deliveryAddress = "",
                deliveryNotes = deliveryNotes.trim().ifBlank { null },
                fulfillmentType = "PICKUP",
            ),
        )
        if (res.success) onOrderPlaced(res.order)
    }

    suspend fun submitDineIn() {
        val res = NetworkClient.apiService.placeDineInOrder(
            idempotencyKey = idempotencyKey,
            request = PlaceDineInOrderRequest(
                restaurantId = restaurant.merchantId,
                tableNumber = tableNumber.trim(),
                items = lines.map { (p, line) -> DineInOrderItemRequest(p.id, line.quantity, line.choiceIds.ifEmpty { null }) },
            ),
        )
        if (res.success) onDineInOrderPlaced(res.order)
    }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = Ids.layout.screenVertical)) {
        BackTopBar("Checkout", onBack)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(lines, key = { (p, line) -> eatsCartKey(p.id, line.choiceIds) }) { (p, line) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.name}${eatsOptionsSummary(p, line.choiceIds)} x${line.quantity}", color = Ids.colors.textPrimary, fontSize = 14.sp)
                    Text(String.format(Locale.US, "%,.0f RWF", eatsLineUnitPrice(p, line.choiceIds) * line.quantity), color = Ids.colors.textPrimary, fontSize = 14.sp)
                }
            }
            item { Divider(color = Ids.colors.divider, modifier = Modifier.padding(vertical = 10.dp)) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = Ids.colors.textPrimary, fontSize = 14.sp)
                    Text(String.format(Locale.US, "%,.0f RWF", total), color = Ids.colors.textPrimary, fontSize = 14.sp)
                }
            }
            item { Spacer(modifier = Modifier.height(14.dp)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ids.colors.surfaceSoft),
                ) {
                    listOf(EatsCheckoutMode.DELIVERY to "Delivery", EatsCheckoutMode.PICKUP to "Pickup", EatsCheckoutMode.DINE_IN to "Order at table").forEach { (m, label) ->
                        val selected = m == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Ids.colors.brand else Color.Transparent)
                                .pressScaleClickable(enabled = !submitting) { mode = m; error = null }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (selected) Color.White else Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (mode == EatsCheckoutMode.DELIVERY) {
                item { Text("Plus a real delivery fee, added at checkout", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
                item {
                    AddressAutocompleteField(
                        address = address,
                        onAddressChange = { address = it; addressLatitude = null; addressLongitude = null },
                        onSuggestionSelected = { s ->
                            address = s.displayName
                            addressLatitude = s.latitude
                            addressLongitude = s.longitude
                        },
                    )
                }
                if (addressLatitude != null) {
                    item { Text("Pinned -- real distance-based delivery fee applies", color = Ids.colors.success, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
                item {
                    IdsTextField(
                        value = deliveryNotes,
                        onValueChange = { if (it.length <= 500) deliveryNotes = it },
                        label = "Delivery notes (optional) -- e.g. Leave at the gate",
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            } else if (mode == EatsCheckoutMode.PICKUP) {
                item { Text("No delivery fee -- collect your order at the restaurant once it's ready", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
                item {
                    IdsTextField(
                        value = deliveryNotes,
                        onValueChange = { if (it.length <= 500) deliveryNotes = it },
                        label = "Pickup notes (optional)",
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            } else {
                item { Text("No delivery fee -- served straight to your table", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
                item {
                    IdsTextField(
                        value = tableNumber,
                        onValueChange = { if (it.length <= 50) tableNumber = it },
                        label = "Table number -- e.g. 12",
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) } }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (submitting || !canSubmit) Ids.colors.textTertiary else Ids.colors.brand)
                .pressScaleClickable(enabled = !submitting && canSubmit) {
                    error = null
                    showOrderReview = true
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (submitting) "Placing order…" else "Place order", color = Color.White, fontWeight = FontWeight.Bold) }
        if (showOrderReview) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { if (!submitting) showOrderReview = false },
                sheetState = androidx.compose.material3.rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                ),
                containerColor = Ids.colors.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Confirm it’s you",
                        color = Ids.colors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Review your order to ${restaurant.businessName}",
                        color = Ids.colors.textSecondary,
                        fontSize = 14.sp,
                    )
                    lines.forEach { (product, line) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${product.name} × ${line.quantity}",
                                color = Ids.colors.textPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                String.format(Locale.US, "%,.0f RWF", eatsLineUnitPrice(product, line.choiceIds) * line.quantity),
                                color = Ids.colors.textPrimary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Subtotal", color = Ids.colors.textSecondary, fontSize = 14.sp)
                        Text(
                            String.format(Locale.US, "%,.0f RWF", total),
                            color = Ids.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                    Text(
                        when (mode) {
                            EatsCheckoutMode.DELIVERY -> "Delivery to ${address.trim()}"
                            EatsCheckoutMode.PICKUP -> "Pickup at ${restaurant.businessName}"
                            EatsCheckoutMode.DINE_IN -> "Table ${tableNumber.trim()}"
                        },
                        color = Ids.colors.textSecondary,
                        fontSize = 13.sp,
                    )
                    if (error != null) {
                        Text(error!!, color = Ids.colors.danger, fontSize = 13.sp)
                    }
                    androidx.compose.material3.Button(
                        onClick = {
                            if (!submitting) {
                                submitting = true
                                error = null
                                needsDeviceVerification = false
                                coroutineScope.launch {
                                    try {
                                        when (mode) {
                                            EatsCheckoutMode.DELIVERY -> submitDelivery()
                                            EatsCheckoutMode.PICKUP -> submitPickup()
                                            EatsCheckoutMode.DINE_IN -> submitDineIn()
                                        }
                                        showOrderReview = false
                                    } catch (e: HttpException) {
                                        if (isDeviceNotVerifiedError(e)) {
                                            needsDeviceVerification = true
                                        } else {
                                            error = superAppErrorMessage(e)
                                        }
                                    } catch (e: IOException) {
                                        error = "Couldn't reach itunda. Check your connection and try again."
                                    } finally {
                                        submitting = false
                                    }
                                }
                            }
                        },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (submitting) "Placing order…" else "Confirm order")
                    }
                    androidx.compose.material3.TextButton(
                        onClick = { if (!submitting) showOrderReview = false },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
        deviceStepUpHost(
            needsDeviceVerification,
            { needsDeviceVerification = false },
            {
                needsDeviceVerification = false
                submitting = true
                try {
                    when (mode) { EatsCheckoutMode.DELIVERY -> submitDelivery(); EatsCheckoutMode.PICKUP -> submitPickup(); EatsCheckoutMode.DINE_IN -> submitDineIn() }
                    showOrderReview = false
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                } catch (e: IOException) {
                    error = "Couldn't reach itunda. Check your connection and try again."
                } finally {
                    submitting = false
                }
            },
        )
    }
}

@Composable
internal fun EatsOrderConfirmationView(order: EatsOrderDto, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Order placed") },
        text = {
            Column {
                Text(String.format(Locale.US, "%,.0f RWF", order.totalAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                // Real Baemin-style tiered order-amount promotion (2026-08-16) -- see
                // EatsPromotionCalculator's own doc comment on the backend.
                if (order.promotionDiscount > 0) {
                    Text(String.format(Locale.US, "%,.0f RWF off, on us", order.promotionDiscount), color = Ids.colors.success, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Delivering to ${order.deliveryAddress}", color = Ids.colors.textSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Track order") } },
    )
}

@Composable
internal fun DineInOrderConfirmationView(order: DineInOrderDto, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Order placed") },
        text = {
            Column {
                Text(String.format(Locale.US, "%,.0f RWF", order.totalAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Table ${order.tableNumber} -- the kitchen has your order", color = Ids.colors.textSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}


// Real per-configuration cart line (2026-07-21) -- ports bank-mfe's own EatsCartLine
// (BankDashboard.tsx) 1:1. `choiceIds` is empty for any item with no option groups --
// the pre-existing, unaffected case. Two lines for the same productId with DIFFERENT
// choiceIds are genuinely distinct cart entries (e.g. a Regular and a Large of the same
// burger, side by side) -- closes docs/DESIGN_REFERENCES.md's Eats recommendation #6.
internal data class EatsCartLine(val productId: String, val quantity: Int, val choiceIds: List<String> = emptyList())

internal fun eatsCartKey(productId: String, choiceIds: List<String>): String =
    if (choiceIds.isEmpty()) productId else "$productId::${choiceIds.sorted().joinToString(",")}"
