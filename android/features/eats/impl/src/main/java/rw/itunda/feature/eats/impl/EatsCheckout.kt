package rw.itunda.feature.eats.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.snapshots.SnapshotStateMap
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




enum class EatsCheckoutMode { DELIVERY, PICKUP, DINE_IN }

@Composable
fun EatsCheckoutView(
    restaurant: ShoppingMerchantDto,
    cart: SnapshotStateMap<String, EatsCartLine>,
    menu: List<MerchantProductDto>,
    initialMode: EatsCheckoutMode = EatsCheckoutMode.DELIVERY,
    initialAddress: String = "",
    initialDeliveryNotes: String = "",
    savedCoupon: String? = null,
    onModeChange: (EatsCheckoutMode) -> Unit = {},
    onBack: () -> Unit,
    onOrderPlaced: (EatsOrderDto) -> Unit,
    onDineInOrderPlaced: (DineInOrderDto) -> Unit,
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
    initialPayBalance: Double = 120500.0,
    initialBankBalance: Double = 842300.0,
    initialPaymentMethod: EatsPaymentMethod = EatsPaymentMethod.ITUNDA_PAY,
    initialTipAmount: Int = 0,
    initialScheduledTime: String? = null,
    initialGroupSummary: EatsCheckoutGroupSummary? = null,
    onManageGroup: (() -> Unit)? = null,
    onGroupSplitBillChange: (Boolean) -> Unit = {},
) {
    var mode by remember { mutableStateOf(initialMode) }
    var address by remember { mutableStateOf(initialAddress) }
    var groupSummary by remember(initialGroupSummary) { mutableStateOf(initialGroupSummary) }
    var addressLatitude by remember { mutableStateOf<Double?>(null) }
    var addressLongitude by remember { mutableStateOf<Double?>(null) }
    var deliveryNotes by remember(initialDeliveryNotes) { mutableStateOf(initialDeliveryNotes) }
    var requestForRider by remember { mutableStateOf("Leave at the door") }
    // Real 배민오더-style table/QR ordering (2026-07-25) -- the table number a buyer
    // reads off the physical tag/QR at their table. See DineInOrder.kt's own doc
    // comment for why this is free text rather than a validated table registry.
    var tableNumber by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real device binding step-up (2026-07-21) -- Eats checkout was a real gap:
    // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
    // showed only a generic error, same fix already applied to Transfer/Savings.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val idempotencyKey = remember { UUID.randomUUID().toString() }

    val lines = cart.values.filter { it.quantity > 0 }.mapNotNull { line -> menu.find { it.id == line.productId }?.let { it to line } }
    val total = lines.sumOf { (p, line) -> eatsLineUnitPrice(p, line.choiceIds) * line.quantity }
    // The prototype never allows an empty cart to reach payment. Also protects
    // against stale menu entries disappearing between browse and checkout.
    val minimumOrder = restaurant.minOrderAmount ?: 0.0
    val shortOfMinimum = total < minimumOrder
    val canSubmit = lines.isNotEmpty() && !shortOfMinimum && when (mode) {
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
                deliveryNotes = listOfNotNull(requestForRider, deliveryNotes.trim().ifBlank { null }).joinToString(" · ").ifBlank { null },
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

    EatsCheckoutPrototypeScreen(
        restaurant = restaurant,
        cart = cart,
        menu = menu,
        initialMode = mode,
        address = address,
        initialCoupon = savedCoupon,
        minimumOrderAmount = minimumOrder,
        submitting = submitting,
        error = error,
        initialPayBalance = initialPayBalance,
        initialBankBalance = initialBankBalance,
        initialPaymentMethod = initialPaymentMethod,
        initialTipAmount = initialTipAmount,
        initialScheduledTime = initialScheduledTime,
        groupSummary = groupSummary,
        onManageGroup = onManageGroup,
        onGroupSplitBillChange = { splitBill ->
            groupSummary = groupSummary?.copy(splitBill = splitBill)
            onGroupSplitBillChange(splitBill)
        },
        onModeChange = { selected ->
            mode = selected
            error = null
            onModeChange(selected)
        },
        onAddressChange = { selected ->
            address = selected
            addressLatitude = null
            addressLongitude = null
        },
        onBack = onBack,
        onAddMoreItems = onBack,
        onPlaceOrder = {
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
                    } catch (e: HttpException) {
                        if (isDeviceNotVerifiedError(e)) needsDeviceVerification = true
                        else error = superAppErrorMessage(e)
                    } catch (e: IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } finally {
                        submitting = false
                    }
                }
            }
        },
    )
    deviceStepUpHost(
        needsDeviceVerification,
        { needsDeviceVerification = false },
        {
            needsDeviceVerification = false
            submitting = true
            try {
                when (mode) {
                    EatsCheckoutMode.DELIVERY -> submitDelivery()
                    EatsCheckoutMode.PICKUP -> submitPickup()
                    EatsCheckoutMode.DINE_IN -> submitDineIn()
                }
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
data class EatsCartLine(val productId: String, val quantity: Int, val choiceIds: List<String> = emptyList())

internal fun eatsCartKey(productId: String, choiceIds: List<String>): String =
    if (choiceIds.isEmpty()) productId else "$productId::${choiceIds.sorted().joinToString(",")}"
