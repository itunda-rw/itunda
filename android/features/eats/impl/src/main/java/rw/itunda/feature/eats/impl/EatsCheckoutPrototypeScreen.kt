package rw.itunda.feature.eats.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.ShoppingMerchantDto
import java.util.Locale

enum class EatsPaymentMethod { ITUNDA_PAY, ITUNDA_BANK, CASH }

@Composable
internal fun EatsCheckoutPrototypeScreen(
    restaurant: ShoppingMerchantDto,
    cart: SnapshotStateMap<String, EatsCartLine>,
    menu: List<MerchantProductDto>,
    initialMode: EatsCheckoutMode,
    address: String,
    initialCoupon: String?,
    minimumOrderAmount: Double,
    submitting: Boolean,
    error: String?,
    initialPayBalance: Double = 120500.0,
    initialBankBalance: Double = 842300.0,
    initialPaymentMethod: EatsPaymentMethod = EatsPaymentMethod.ITUNDA_PAY,
    initialTipAmount: Int = 0,
    initialScheduledTime: String? = null,
    onModeChange: (EatsCheckoutMode) -> Unit,
    onAddressChange: (String) -> Unit,
    onBack: () -> Unit,
    onAddMoreItems: () -> Unit,
    onPlaceOrder: () -> Unit,
) {
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var coupon by remember(initialCoupon) { mutableStateOf(initialCoupon?.uppercase(Locale.ROOT)) }
    var tip by remember(initialTipAmount) { mutableStateOf(initialTipAmount) }
    var scheduledTime by remember(initialScheduledTime) { mutableStateOf(initialScheduledTime) }
    var paymentMethod by remember(initialPaymentMethod) { mutableStateOf(initialPaymentMethod) }
    var page by remember { mutableStateOf(EatsCheckoutPage.CART) }
    val palette = eatsPrototypePalette()

    val lines = cart.values.filter { it.quantity > 0 }
        .mapNotNull { line -> menu.firstOrNull { it.id == line.productId }?.let { it to line } }
    val itemTotal = lines.sumOf { (product, line) -> eatsLineUnitPrice(product, line.choiceIds) * line.quantity }
    val shortOfMinimum = lines.isNotEmpty() && itemTotal < minimumOrderAmount
    val deliveryFee = restaurant.toEatsPrototypeRestaurant().deliveryFeeRwf.toDouble().coerceAtLeast(0.0)
    val discount = when (coupon) {
        "EATS20" -> minOf(2000.0, itemTotal * 0.20)
        else -> 0.0
    }
    val freeDelivery = mode == EatsCheckoutMode.PICKUP || coupon == "FREEDEL" || deliveryFee <= 0.0
    val payableDelivery = if (freeDelivery) 0.0 else deliveryFee
    val total = (itemTotal - discount + payableDelivery + if (mode == EatsCheckoutMode.DELIVERY) tip.toDouble() else 0.0).coerceAtLeast(0.0)
    val payBalance = if (paymentMethod == EatsPaymentMethod.ITUNDA_BANK) initialBankBalance else initialPayBalance
    val insufficientBalance = paymentMethod != EatsPaymentMethod.CASH && total > payBalance
    val canPay = lines.isNotEmpty() && !shortOfMinimum && !insufficientBalance &&
        (mode != EatsCheckoutMode.DELIVERY || address.isNotBlank()) && !submitting

    BackHandler(enabled = page != EatsCheckoutPage.CART) { page = EatsCheckoutPage.CART }
    BackHandler(enabled = page == EatsCheckoutPage.CART) { onBack() }

    when (page) {
        EatsCheckoutPage.ADDRESS -> EatsCheckoutAddressPage(
            currentAddress = address,
            onBack = { page = EatsCheckoutPage.CART },
            onClose = { page = EatsCheckoutPage.CART },
            onUseAddress = { selected ->
                onAddressChange(selected)
                page = EatsCheckoutPage.CART
            },
        )
        EatsCheckoutPage.PAYMENT -> EatsCheckoutPaymentPage(
            selected = paymentMethod,
            payBalance = initialPayBalance,
            bankBalance = initialBankBalance,
            isPickup = mode == EatsCheckoutMode.PICKUP,
            onBack = { page = EatsCheckoutPage.CART },
            onDone = { selected ->
                paymentMethod = selected
                page = EatsCheckoutPage.CART
            },
        )
        EatsCheckoutPage.CONFIRM -> EatsCheckoutConfirmPage(
            amount = total,
            restaurantName = restaurant.businessName,
            submitting = submitting,
            error = error,
            onBack = { page = EatsCheckoutPage.CART },
            onConfirm = onPlaceOrder,
        )
        EatsCheckoutPage.CART -> {
            if (lines.isEmpty()) {
                EatsCheckoutEmptyPage(
                    restaurantName = restaurant.businessName,
                    onBack = onBack,
                    palette = palette,
                )
            } else {
                Box(Modifier.fillMaxSize().background(palette.background)) {
                    Column(Modifier.fillMaxSize()) {
                        EatsCheckoutTopBar(onBack = onBack, palette = palette)
                        Column(
                            Modifier.fillMaxWidth().weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                                EatsCheckoutModeToggle(
                                    mode = mode,
                                    onSelect = { selected ->
                                        mode = selected
                                        if (selected != EatsCheckoutMode.DELIVERY && coupon == "FREEDEL") coupon = null
                                        if (selected == EatsCheckoutMode.PICKUP) tip = 0
                                        onModeChange(selected)
                                    },
                                    palette = palette,
                                )
                                EatsCheckoutAddressCard(
                                    mode = mode,
                                    address = address,
                                    palette = palette,
                                    onClick = { page = EatsCheckoutPage.ADDRESS },
                                )
                                Text(
                                    "Your order",
                                    color = palette.secondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 18.dp),
                                )
                                Text(
                                    restaurant.businessName,
                                    color = palette.primary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                Column(Modifier.padding(top = 0.dp)) {
                                    lines.forEach { (product, line) ->
                                        EatsCheckoutCartLineRow(
                                            product = product,
                                            line = line,
                                            palette = palette,
                                            onMinus = { adjustEatsCheckoutCart(cart, line, -1) },
                                            onPlus = { adjustEatsCheckoutCart(cart, line, 1) },
                                        )
                                    }
                                }
                                Row(
                                    Modifier.height(44.dp).pressScaleClickable(onClick = onAddMoreItems),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text("+", color = palette.accent, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                                    Text("Add more items", color = palette.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (mode == EatsCheckoutMode.DELIVERY) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp)) {
                                    Text("Add a side or drink", color = palette.primary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                                    val upsells = listOf("Hot milk tea", "Fruit juice").mapNotNull { name ->
                                        menu.firstOrNull { it.name.equals(name, ignoreCase = true) }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        upsells.forEach { product ->
                                            EatsCheckoutUpsellCard(
                                                product = product,
                                                quantity = cart[eatsCartKey(product.id, emptyList())]?.quantity ?: 0,
                                                palette = palette,
                                                onAdd = { adjustEatsCheckoutCart(cart, EatsCartLine(product.id, 0), 1) },
                                            )
                                        }
                                    }
                                    Text("Delivery time", color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        EatsCheckoutTimeChip(
                                            label = "As soon as possible",
                                            selected = scheduledTime == null,
                                            width = 200.dp,
                                            palette = palette,
                                            onClick = { scheduledTime = null },
                                        )
                                        listOf("12:30", "13:00", "13:30", "14:00", "14:30", "15:00", "15:30", "16:00", "16:30", "17:00", "17:30", "18:00", "18:30", "19:00").forEach { time ->
                                            EatsCheckoutTimeChip(
                                                label = time,
                                                selected = scheduledTime == time,
                                                width = 84.dp,
                                                palette = palette,
                                                onClick = { scheduledTime = time },
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.padding(top = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Tip your rider ", color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("(optional)", color = palette.secondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        listOf(0, 500, 1000, 2000).forEach { amount ->
                                            EatsCheckoutTipChip(
                                                label = if (amount == 0) "No tip" else amount.toString().reversed().chunked(3).joinToString(",").reversed(),
                                                selected = tip == amount,
                                                palette = palette,
                                                modifier = Modifier.weight(1f),
                                                onClick = { tip = amount },
                                            )
                                        }
                                    }
                                    if (coupon == "EATS20" || coupon == "FREEDEL") {
                                        EatsCheckoutCouponRow(
                                            coupon = coupon!!,
                                            palette = palette,
                                            onRemove = { coupon = null },
                                            modifier = Modifier.padding(top = 12.dp),
                                        )
                                    }
                                }
                            } else if (coupon == "EATS20") {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp)) {
                                    EatsCheckoutCouponRow(coupon!!, palette, onRemove = { coupon = null })
                                }
                            }

                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = if (mode == EatsCheckoutMode.DELIVERY) 16.dp else 16.dp)) {
                                EatsCheckoutSummaryRow("Items", itemTotal, palette)
                                if (discount > 0) EatsCheckoutSummaryRow("Discount", -discount, palette, positive = true)
                                if (mode == EatsCheckoutMode.DELIVERY) {
                                    EatsCheckoutSummaryRow("Delivery", payableDelivery, palette, free = freeDelivery)
                                    if (tip > 0) EatsCheckoutSummaryRow("Rider tip", tip.toDouble(), palette)
                                } else {
                                    EatsCheckoutSummaryRow("Pickup", 0.0, palette, free = true)
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Total", color = palette.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text(formatEatsRwf(total), color = palette.primary, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                                }
                                Text(
                                    "You earn ${(itemTotal / 100).toInt()} points with this order",
                                    color = palette.secondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 14.dp)
                                        .semantics { contentDescription = "Open payment method" }
                                        .pressScaleClickable { page = EatsCheckoutPage.PAYMENT },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Pay with", color = palette.secondary, fontSize = 14.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        when (paymentMethod) {
                                            EatsPaymentMethod.ITUNDA_PAY -> "itunda Pay · ${formatEatsRwf(initialPayBalance)}"
                                            EatsPaymentMethod.ITUNDA_BANK -> "itunda Bank · ${formatEatsRwf(initialBankBalance)}"
                                            EatsPaymentMethod.CASH -> "Cash on delivery"
                                        },
                                        color = palette.primary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("›", color = palette.secondary, fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp))
                                }
                                if (insufficientBalance) {
                                    Text(
                                        "Not enough balance — you have ${formatEatsRwf(payBalance)}",
                                        color = Color(0xFFE5484D),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                if (shortOfMinimum) {
                                    Text(
                                        "Add ${formatEatsRwf(minimumOrderAmount - itemTotal)} more to reach the minimum order.",
                                        color = Color(0xFFE5484D),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                error?.let { Text(it, color = Color(0xFFE5484D), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                            }
                            Spacer(Modifier.height(110.dp))
                        }
                    }
                    EatsCheckoutPayButton(
                        label = when {
                            shortOfMinimum -> "Add ${formatEatsRwf(minimumOrderAmount - itemTotal)} to order"
                            paymentMethod == EatsPaymentMethod.CASH -> "Place order · ${formatEatsRwf(total)}"
                            else -> "Pay ${formatEatsRwf(total)}"
                        },
                        enabled = canPay,
                        palette = palette,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).padding(bottom = 16.dp),
                        onClick = { page = EatsCheckoutPage.CONFIRM },
                    )
                }
            }
        }
    }
}

private enum class EatsCheckoutPage { CART, ADDRESS, PAYMENT, CONFIRM }

