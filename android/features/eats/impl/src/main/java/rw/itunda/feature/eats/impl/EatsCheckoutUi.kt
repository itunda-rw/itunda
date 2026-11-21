package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.network.MerchantProductDto
import java.util.Locale

private fun eatsMoney(value: Double): String = String.format(Locale.US, "%,.0f", value)

@Composable
internal fun EatsCheckoutMainPage(
    restaurantName: String,
    lines: List<Pair<MerchantProductDto, EatsCartLine>>,
    menu: List<MerchantProductDto>,
    isPickup: Boolean,
    address: String,
    appliedCoupon: String?,
    selectedSchedule: String?,
    selectedTip: Int,
    paymentMethod: String,
    subtotal: Double,
    discount: Double,
    total: Double,
    points: Int,
    minimumOrder: Double,
    insufficientBalance: String?,
    canPay: Boolean,
    onBack: () -> Unit,
    onModeChange: (Boolean) -> Unit,
    onAddressClick: () -> Unit,
    onAddMoreItems: () -> Unit,
    onQuantityChange: (MerchantProductDto, EatsCartLine, Int) -> Unit,
    onAddProduct: (MerchantProductDto) -> Unit,
    onScheduleChange: (String?) -> Unit,
    onTipChange: (Int) -> Unit,
    onRemoveCoupon: () -> Unit,
    onPaymentClick: () -> Unit,
    onPay: () -> Unit,
) {
    val palette = eatsPrototypePalette()
    Column(Modifier.fillMaxSize().background(palette.background)) {
        EatsCheckoutTopBar("Cart", onBack)
        if (lines.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(palette.surfaceSoft), contentAlignment = Alignment.Center) {
                        Text("🔒", fontSize = 30.sp)
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("Your cart is empty", color = palette.primary, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Add something from $restaurantName to order.", color = palette.secondary, fontSize = 16.sp)
                }
            }
            EatsCheckoutBottomButton("Back to the menu", true, onAddMoreItems)
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
                    EatsModeSwitch(isPickup, onModeChange)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().height(59.dp).clip(RoundedCornerShape(16.dp)).background(palette.surfaceSoft)
                            .semantics { contentDescription = if (isPickup) "Pickup address" else "Change delivery address" }
                            .pressScaleClickable(onClick = onAddressClick).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(if (isPickup) "Pick up at" else "Deliver to", color = palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (isPickup) "KG 11 Ave, Kimironko" else address, color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text("Change", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Your order", color = palette.secondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(restaurantName, color = palette.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    lines.forEach { (product, line) ->
                        Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFE8C7)), contentAlignment = Alignment.Center) {
                                Text(if (product.name.contains("croissant", true) || product.name.contains("bread", true)) "🥐" else "🍽️", fontSize = 27.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                Text(product.name + eatsOptionsSummary(product, line.choiceIds), color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                Text("${eatsMoney(eatsLineUnitPrice(product, line.choiceIds))} RWF", color = palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            EatsQuantityButton("−", "Remove one") { onQuantityChange(product, line, -1) }
                            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                                Text(line.quantity.toString(), color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            EatsQuantityButton("+", "Add one") { onQuantityChange(product, line, 1) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = "Add more items" }
                            .pressScaleClickable(onClick = onAddMoreItems),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("+", color = palette.accent, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(10.dp))
                        Text("Add more items", color = palette.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (!isPickup) {
                    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                        Text("Add a side or drink", color = palette.primary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("Hot milk tea", "Fruit juice").forEach { name ->
                                val product = menu.firstOrNull { it.name.equals(name, ignoreCase = true) && it.active }
                                if (product != null) {
                                    Column(Modifier.width(112.dp)) {
                                        Box(Modifier.size(112.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFE8C7))) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(if (name == "Hot milk tea") "☕" else "🧃", fontSize = 36.sp)
                                            }
                                            Box(
                                                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(36.dp).clip(CircleShape)
                                                    .background(Color.White).semantics { contentDescription = "Add $name" }
                                                    .pressScaleClickable { onAddProduct(product) },
                                                contentAlignment = Alignment.Center,
                                            ) { Text("+", color = palette.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(name, color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                        Spacer(Modifier.height(7.dp))
                                        Text("${eatsMoney(product.price)} RWF", color = palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp)) {
                        Text("Delivery time", color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(null, "12:30", "13:00", "13:30", "14:00").forEach { slot ->
                                val label = slot ?: "As soon as possible"
                                val selected = selectedSchedule == slot
                                Box(
                                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) palette.selected else palette.surfaceSoft)
                                        .pressScaleClickable { onScheduleChange(slot) }
                                        .padding(horizontal = if (slot == null) 16.dp else 18.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(label, color = if (selected) palette.selectedText else palette.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tip your rider", color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(" (optional)", color = palette.secondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 500, 1000, 2000).forEach { amount ->
                                val selected = selectedTip == amount
                                val label = if (amount == 0) "No tip" else eatsMoney(amount.toDouble())
                                Box(
                                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                                        .background(if (selected) palette.selected else palette.surfaceSoft)
                                        .pressScaleClickable { onTipChange(amount) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(label, color = if (selected) palette.selectedText else palette.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 22.dp)) {
                    if (appliedCoupon != null) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surfaceSoft)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (appliedCoupon == "EATS20") "EATS20 · 20% off items (max 2,000 RWF)" else "FREEDEL · Free delivery",
                                color = palette.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                            )
                            Text("Remove", color = palette.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.pressScaleClickable(onClick = onRemoveCoupon))
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    EatsCheckoutSummaryRow("Items", "${eatsMoney(subtotal)} RWF", palette.secondary, palette.primary)
                    if (discount > 0) EatsCheckoutSummaryRow("Discount", "−${eatsMoney(discount)} RWF", palette.secondary, Color(0xFF08794D))
                    EatsCheckoutSummaryRow(if (isPickup) "Pickup" else "Delivery", "Free", palette.secondary, palette.primary)
                    if (selectedTip > 0 && !isPickup) EatsCheckoutSummaryRow("Rider tip", "${eatsMoney(selectedTip.toDouble())} RWF", palette.secondary, palette.primary)
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth().height(45.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", color = palette.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${eatsMoney(total)} RWF", color = palette.primary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("You earn $points points with this order", color = palette.secondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "Pay with $paymentMethod" }
                            .pressScaleClickable(onClick = onPaymentClick),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Pay with", color = palette.secondary, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        val methodLabel = when (paymentMethod) {
                            "bank" -> "itunda Bank · 842,300 RWF"
                            "cash" -> "Cash on delivery"
                            else -> "itunda Pay · 120,500 RWF"
                        }
                        Text(methodLabel, color = palette.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(5.dp))
                        Text("›", color = palette.secondary, fontSize = 18.sp)
                    }
                    insufficientBalance?.let {
                        Text(it, color = Color(0xFFDE3B40), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (minimumOrder > subtotal) {
                        Text("Add ${eatsMoney(minimumOrder - subtotal)} RWF more to reach the minimum order.", color = Color(0xFFDE3B40), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            EatsCheckoutBottomButton("Pay ${eatsMoney(total)} RWF", canPay, onPay)
        }
    }
}

@Composable
internal fun EatsCheckoutAddressPage(
    currentAddress: String,
    selectedAddress: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onUseAddress: () -> Unit,
) {
    val palette = eatsPrototypePalette()
    Column(Modifier.fillMaxSize().background(palette.background)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = palette.primary, fontSize = 22.sp, modifier = Modifier.semantics { contentDescription = "Back" }.pressScaleClickable(onClick = onClose))
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Delivery address", color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Your rider will go to the address you pick.", color = palette.secondary, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            val addresses = listOf("Kimironko, KG 11 Ave" to "Home", "Kacyiru, KG 7 Ave" to "Work")
            addresses.forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().height(50.dp).pressScaleClickable { onSelect(value) }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(palette.surfaceSoft), contentAlignment = Alignment.Center) {
                        Text(if (label == "Home") "⌂" else "▣", color = palette.accent, fontSize = 17.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, color = palette.primary, fontSize = 14.sp, fontWeight = if (selectedAddress == value) FontWeight.Bold else FontWeight.Medium)
                        Text(value, color = palette.secondary, fontSize = 11.sp)
                    }
                    if (selectedAddress == value) Text("✓", color = palette.accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (currentAddress !in addresses.map { it.first }) {
                Row(Modifier.fillMaxWidth().height(50.dp).pressScaleClickable { onSelect(currentAddress) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Other", color = palette.primary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (selectedAddress == currentAddress) Text("✓", color = palette.accent)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("+   Add a new address", color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EatsCheckoutSmallButton("Close", false, Modifier.weight(1f), onClose)
            EatsCheckoutSmallButton("Use this address", true, Modifier.weight(1.6f), onUseAddress)
        }
    }
}

@Composable
internal fun EatsCheckoutPaymentPage(
    selectedMethod: String,
    payBalance: Double,
    bankBalance: Double,
    isPickup: Boolean,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit,
) {
    val palette = eatsPrototypePalette()
    Column(Modifier.fillMaxSize().background(palette.background)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = palette.primary, fontSize = 22.sp, modifier = Modifier.semantics { contentDescription = "Back" }.pressScaleClickable(onClick = onClose))
        }
        Text("Pay with", color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Spacer(Modifier.height(10.dp))
        EatsPaymentChoice("itunda Pay", "${eatsMoney(payBalance)} RWF", selectedMethod == "pay", palette) { onSelect("pay") }
        EatsPaymentChoice("itunda Bank", "${eatsMoney(bankBalance)} RWF", selectedMethod == "bank", palette) { onSelect("bank") }
        if (!isPickup) EatsPaymentChoice("Cash on delivery", "Pay your rider when the food arrives", selectedMethod == "cash", palette) { onSelect("cash") }
        Spacer(Modifier.weight(1f))
        EatsCheckoutSmallButton("Done", true, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), onDone)
    }
}

@Composable
internal fun EatsCheckoutConfirmPage(
    total: Double,
    restaurantName: String,
    submitting: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = eatsPrototypePalette()
    Column(Modifier.fillMaxSize().background(palette.background)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = palette.primary, fontSize = 22.sp, modifier = Modifier.semantics { contentDescription = "Back" }.pressScaleClickable(onClick = onBack))
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(palette.brandFill), contentAlignment = Alignment.Center) {
                Text("✓", color = palette.brandOn, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Text("Confirm it’s you", color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Pay ${eatsMoney(total)} RWF · $restaurantName", color = palette.secondary, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        EatsCheckoutSmallButton(if (submitting) "Confirming…" else "Confirm", true, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), onConfirm, enabled = !submitting)
    }
}

@Composable
private fun EatsPaymentChoice(title: String, subtitle: String, selected: Boolean, palette: EatsPrototypePalette, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).pressScaleClickable(onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (selected) palette.brandFill else palette.surfaceSoft), contentAlignment = Alignment.Center) {
            if (selected) Text("✓", color = palette.brandOn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = palette.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = palette.secondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EatsCheckoutTopBar(title: String, onBack: () -> Unit) {
    val palette = eatsPrototypePalette()
    Row(Modifier.fillMaxWidth().height(70.dp).background(palette.background), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).padding(start = 6.dp).semantics { contentDescription = "Back" }.pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Text("←", color = palette.primary, fontSize = 24.sp)
        }
        Text(title, color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun EatsQuantityButton(label: String, description: String, onClick: () -> Unit) {
    val palette = eatsPrototypePalette()
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(palette.surfaceSoft)
        .semantics { contentDescription = description }.pressScaleClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = palette.primary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EatsCheckoutSummaryRow(label: String, value: String, labelColor: Color, valueColor: Color) {
    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = labelColor, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EatsCheckoutBottomButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = eatsPrototypePalette()
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp).height(60.dp)
        .clip(RoundedCornerShape(18.dp)).background(if (enabled) palette.brandFill else palette.surfaceSoft)
        .semantics { contentDescription = label }.pressScaleClickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) palette.brandOn else palette.secondary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EatsCheckoutSmallButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, enabled: Boolean = true) {
    val palette = eatsPrototypePalette()
    Box(modifier.height(44.dp).clip(RoundedCornerShape(14.dp))
        .background(if (primary) palette.brandFill else palette.accentTint)
        .pressScaleClickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (primary) palette.brandOn else palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
