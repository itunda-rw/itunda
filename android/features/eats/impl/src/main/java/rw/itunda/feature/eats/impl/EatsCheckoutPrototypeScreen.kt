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

internal enum class EatsPaymentMethod { ITUNDA_PAY, ITUNDA_BANK, CASH }

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
    var selectedAddress by remember(address) { mutableStateOf(address) }
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
            currentAddress = selectedAddress,
            onBack = { page = EatsCheckoutPage.CART },
            onClose = { page = EatsCheckoutPage.CART },
            onUseAddress = {
                onAddressChange(selectedAddress)
                page = EatsCheckoutPage.CART
            },
            onSelect = { selectedAddress = it },
        )
        EatsCheckoutPage.PAYMENT -> EatsCheckoutPaymentPage(
            selected = paymentMethod,
            payBalance = initialPayBalance,
            bankBalance = initialBankBalance,
            onBack = { page = EatsCheckoutPage.CART },
            onSelect = { paymentMethod = it },
            onDone = { page = EatsCheckoutPage.CART },
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
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 112.dp),
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
                                    onClick = { selectedAddress = address; page = EatsCheckoutPage.ADDRESS },
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
                        }
                    }
                    EatsCheckoutPayButton(
                        label = when {
                            shortOfMinimum -> "Add ${formatEatsRwf(minimumOrderAmount - itemTotal)} to order"
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

private fun adjustEatsCheckoutCart(
    cart: SnapshotStateMap<String, EatsCartLine>,
    line: EatsCartLine,
    delta: Int,
) {
    val key = eatsCartKey(line.productId, line.choiceIds)
    val current = cart[key]?.quantity ?: line.quantity
    val next = (current + delta).coerceAtLeast(0)
    if (next == 0) cart.remove(key) else cart[key] = line.copy(quantity = next)
}

private fun formatEatsRwf(amount: Double): String =
    String.format(Locale.US, "%,.0f RWF", amount)

@Composable
private fun EatsCheckoutTopBar(onBack: () -> Unit, palette: EatsPrototypePalette) {
    Row(
        Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).pressScaleClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = palette.primary)
        }
        Text("Cart", color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun EatsCheckoutModeToggle(
    mode: EatsCheckoutMode,
    onSelect: (EatsCheckoutMode) -> Unit,
    palette: EatsPrototypePalette,
) {
    Row(
        Modifier.padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(palette.surfaceSoft).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(EatsCheckoutMode.DELIVERY to "Delivery", EatsCheckoutMode.PICKUP to "Pickup").forEach { (value, label) ->
            val selected = mode == value
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (selected) palette.selected else Color.Transparent)
                    .pressScaleClickable { onSelect(value) }
                    .padding(horizontal = if (value == EatsCheckoutMode.DELIVERY) 12.dp else 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected) palette.selectedText else palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EatsCheckoutAddressCard(
    mode: EatsCheckoutMode,
    address: String,
    palette: EatsPrototypePalette,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceSoft).pressScaleClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (mode == EatsCheckoutMode.PICKUP) "Pick up at" else "Deliver to", color = palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (mode == EatsCheckoutMode.PICKUP) "KG 11 Ave, Kimironko" else address,
                color = palette.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (mode == EatsCheckoutMode.DELIVERY) Text("Change", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EatsCheckoutCartLineRow(
    product: MerchantProductDto,
    line: EatsCartLine,
    palette: EatsPrototypePalette,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFE8C7)),
            contentAlignment = Alignment.Center,
        ) {
            val art = when {
                product.name.contains("mandazi", true) -> "mandazi"
                product.name.contains("juice", true) -> "juice"
                product.name.contains("tea", true) -> "tea"
                else -> "bread"
            }
            ItundaArt(art, ItundaArtSet.Color, 36.dp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                product.name + eatsOptionsSummary(product, line.choiceIds),
                color = palette.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(formatEatsRwf(eatsLineUnitPrice(product, line.choiceIds)), color = palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        EatsCheckoutQuantityButton("−", "Remove one", palette, onMinus)
        Text(line.quantity.toString(), color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        EatsCheckoutQuantityButton("+", "Add one", palette, onPlus)
    }
}

@Composable
private fun EatsCheckoutQuantityButton(
    label: String,
    description: String,
    palette: EatsPrototypePalette,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(palette.surfaceSoft)
            .pressScaleClickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = palette.primary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EatsCheckoutUpsellCard(
    product: MerchantProductDto,
    quantity: Int,
    palette: EatsPrototypePalette,
    onAdd: () -> Unit,
) {
    Column(Modifier.width(112.dp)) {
        Box(
            Modifier.size(112.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFE8C7)),
            contentAlignment = Alignment.Center,
        ) {
            ItundaArt(if (product.name.contains("juice", true)) "juice" else "tea", ItundaArtSet.Color, 44.dp)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(36.dp).clip(CircleShape)
                    .background(Color.White).pressScaleClickable(onClick = onAdd)
                    .semantics { contentDescription = "Add ${product.name}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (quantity == 0) "+" else quantity.toString(), color = palette.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(product.name, color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(formatEatsRwf(product.price), color = palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun EatsCheckoutTimeChip(
    label: String,
    selected: Boolean,
    width: androidx.compose.ui.unit.Dp,
    palette: EatsPrototypePalette,
    onClick: () -> Unit,
) {
    Box(
        Modifier.width(width).height(40.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) palette.selected else palette.surfaceSoft)
            .pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) palette.selectedText else palette.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun EatsCheckoutTipChip(
    label: String,
    selected: Boolean,
    palette: EatsPrototypePalette,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) palette.selected else palette.surfaceSoft)
            .pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) palette.selectedText else palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EatsCheckoutCouponRow(
    coupon: String,
    palette: EatsPrototypePalette,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.surfaceSoft)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (coupon == "EATS20") "EATS20 · 20% off items (max 2,000 RWF)" else "FREEDEL · Free delivery",
            color = palette.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text("Remove", color = palette.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressScaleClickable(onClick = onRemove).padding(start = 8.dp, vertical = 8.dp))
    }
}

@Composable
private fun EatsCheckoutSummaryRow(
    label: String,
    amount: Double,
    palette: EatsPrototypePalette,
    positive: Boolean = false,
    free: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = palette.secondary, fontSize = 15.sp)
        Text(
            if (free) "Free" else if (positive) "−${formatEatsRwf(-amount)}" else formatEatsRwf(amount),
            color = if (positive) Color(0xFF14966A) else palette.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EatsCheckoutPayButton(
    label: String,
    enabled: Boolean,
    palette: EatsPrototypePalette,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) palette.brandFill else palette.accentTint)
            .pressScaleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) palette.brandOn else palette.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EatsCheckoutEmptyPage(
    restaurantName: String,
    onBack: () -> Unit,
    palette: EatsPrototypePalette,
) {
    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize()) {
            EatsCheckoutTopBar(onBack, palette)
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(palette.surfaceSoft), contentAlignment = Alignment.Center) {
                    ItundaArt("bag", ItundaArtSet.Line, 24.dp, tint = Color(0xFF5287FF))
                }
                Text("Your cart is empty", color = palette.primary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp))
                Text("Add something from ${restaurantName} to order.", color = palette.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)
                .height(52.dp).clip(RoundedCornerShape(16.dp)).background(palette.brandFill)
                .pressScaleClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("Back to the menu", color = palette.brandOn, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
