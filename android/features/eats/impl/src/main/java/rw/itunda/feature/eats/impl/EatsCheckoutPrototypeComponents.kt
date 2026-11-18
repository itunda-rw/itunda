package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.network.MerchantProductDto
import java.util.Locale

internal fun adjustEatsCheckoutCart(
    cart: SnapshotStateMap<String, EatsCartLine>,
    line: EatsCartLine,
    delta: Int,
) {
    val key = eatsCartKey(line.productId, line.choiceIds)
    val current = cart[key]?.quantity ?: line.quantity
    val next = (current + delta).coerceAtLeast(0)
    if (next == 0) cart.remove(key) else cart[key] = line.copy(quantity = next)
}

internal fun formatEatsRwf(amount: Double): String =
    String.format(Locale.US, "%,.0f RWF", amount)

@Composable
internal fun EatsCheckoutTopBar(onBack: () -> Unit, palette: EatsPrototypePalette) {
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
internal fun EatsCheckoutModeToggle(
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
                Modifier.width(if (value == EatsCheckoutMode.DELIVERY) 79.3.dp else 67.8.dp).height(32.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (selected) palette.selected else Color.Transparent)
                    .pressScaleClickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected) palette.selectedText else palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun EatsCheckoutAddressCard(
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
internal fun EatsCheckoutCartLineRow(
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
internal fun EatsCheckoutQuantityButton(
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
internal fun EatsCheckoutUpsellCard(
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
internal fun EatsCheckoutTimeChip(
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
internal fun EatsCheckoutTipChip(
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
internal fun EatsCheckoutCouponRow(
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
internal fun EatsCheckoutSummaryRow(
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
internal fun EatsCheckoutPayButton(
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
internal fun EatsCheckoutEmptyPage(
    restaurantName: String,
    onBack: () -> Unit,
    palette: EatsPrototypePalette,
) {
    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize()) {
            EatsCheckoutTopBar(onBack, palette)
            Column(
                Modifier.fillMaxWidth().padding(top = 88.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(palette.surfaceSoft), contentAlignment = Alignment.Center) {
                    ItundaArt("bag", ItundaArtSet.Line, 32.dp, tint = Color(0xFF5287FF))
                }
                Text("Your cart is empty", color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp))
                Text("Add something from ${restaurantName} to order.", color = palette.secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
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
