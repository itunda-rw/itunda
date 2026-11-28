package rw.itunda.feature.eats.impl

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsOrderItemDto

@Composable
internal fun EatsOrdersPageHeader(title: String?, onBack: () -> Unit, topInset: Dp = 0.dp) {
    val p = eatsPrototypePalette()
    Row(
        Modifier.fillMaxWidth().padding(top = topInset).height(70.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.padding(start = 6.dp).size(48.dp).offset(y = 5.dp).pressScaleClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp), tint = p.primary)
        }
        if (!title.isNullOrBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(title, modifier = Modifier.offset(y = 5.dp), color = p.primary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        }
    }
}

@Composable
fun EatsReceiptSheet(
    order: EatsOrderDto,
    restaurantName: String,
    items: List<EatsOrderItemDto>?,
    onDismiss: () -> Unit,
) {
    val p = eatsPrototypePalette()
    BackHandler(onBack = onDismiss)
    Column(Modifier.fillMaxSize().background(p.background)) {
        EatsOrdersPageHeader(title = null, onBack = onDismiss, topInset = 6.dp)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(top = 6.dp),
        ) {
            Text("Receipt", color = p.primary, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            Text(
                "EA-${order.id.takeLast(4)} · $restaurantName",
                color = p.secondary, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(10.dp))
            if (items == null) {
                Text("Loading order items…", color = p.secondary, fontSize = 14.sp, lineHeight = 20.sp)
            } else {
                items.forEach { item ->
                    EatsReceiptItemRow(
                        label = "${item.quantity} × ${item.productName}",
                        amount = formatEatsReceiptRwf(item.unitPrice * item.quantity),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            EatsReceiptAmountRow("Items", formatEatsReceiptRwf(order.itemsSubtotal))
            EatsReceiptAmountRow("Delivery", if (order.deliveryFee <= 0.0) "Free" else formatEatsReceiptRwf(order.deliveryFee))
            if (order.platformFee > 0.0) EatsReceiptAmountRow("Service fee", formatEatsReceiptRwf(order.platformFee))
            if (order.promotionDiscount > 0.0) EatsReceiptAmountRow("Discount", formatEatsReceiptRwf(-order.promotionDiscount))
            EatsReceiptAmountRow("Total", formatEatsReceiptRwf(order.totalAmount), strong = true)
            EatsReceiptAmountRow("Status", EATS_STATUS_LABEL[order.status] ?: order.status)
            EatsReceiptAmountRow("Paid with", "itunda Pay")
            if (order.deliveryAddress.isNotBlank()) EatsReceiptAmountRow("Address", order.deliveryAddress)
            order.deliveryNotes?.takeIf { it.isNotBlank() }?.let { EatsReceiptAmountRow("Note", it) }
        }
        Box(
            Modifier.fillMaxWidth().height(114.dp).padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp))
                    .background(p.brandFill).pressScaleClickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("OK", color = p.brandOn, fontSize = 18.sp, lineHeight = 25.2.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun EatsReceiptItemRow(label: String, amount: String) {
    val p = eatsPrototypePalette()
    Row(
        Modifier.fillMaxWidth().height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.secondary, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(amount, color = p.primary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EatsReceiptAmountRow(label: String, value: String, strong: Boolean = false) {
    val p = eatsPrototypePalette()
    Row(
        Modifier.fillMaxWidth().height(if (strong) 44.dp else 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (strong) p.primary else p.secondary,
            fontSize = if (strong) 18.sp else 15.sp,
            lineHeight = if (strong) 25.2.sp else 21.sp,
            fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Medium,
        )
        Text(
            value,
            color = p.primary,
            fontSize = if (strong) 24.sp else 15.sp,
            lineHeight = if (strong) 33.6.sp else 21.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EatsHelpSheet(
    order: EatsOrderDto,
    restaurantName: String,
    selectedReason: String?,
    onReasonSelected: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = eatsPrototypePalette()
    BackHandler(onBack = onDismiss)
    Column(Modifier.fillMaxSize().background(p.background)) {
        EatsOrdersPageHeader(title = null, onBack = onDismiss, topInset = 6.dp)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(top = 6.dp),
        ) {
            Text("Report a problem", color = p.primary, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            Text(
                "EA-${order.id.takeLast(4)} · $restaurantName",
                color = p.secondary, fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "What went wrong?",
                color = p.primary, fontSize = 15.sp, lineHeight = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 18.dp),
            )
            listOf("Missing item", "Wrong item", "Food was cold", "Order was late", "Something else").forEach { reason ->
                Row(
                    Modifier.fillMaxWidth().height(50.dp).pressScaleClickable { onReasonSelected(reason) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        reason,
                        color = if (selectedReason == reason) p.accent else p.primary,
                        fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectedReason == reason) {
                        Text("✓", color = p.accent, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(98.dp).padding(horizontal = 24.dp).padding(top = 14.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EatsHelpButton(
                label = "Cancel",
                modifier = Modifier.width(116.dp),
                background = p.accentTint,
                foreground = p.accent,
                enabled = true,
                onClick = onDismiss,
            )
            EatsHelpButton(
                label = "Send report",
                modifier = Modifier.weight(1f),
                background = if (selectedReason == null) p.accentTint else p.brandFill,
                foreground = if (selectedReason == null) p.brandFill else p.brandOn,
                enabled = selectedReason != null,
                onClick = { selectedReason?.let(onSubmit) },
            )
        }
    }
}

@Composable
private fun EatsHelpButton(
    label: String,
    modifier: Modifier,
    background: Color,
    foreground: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(18.dp)).background(background)
            .pressScaleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatEatsReceiptRwf(amount: Double): String = String.format(Locale.US, "%,.0f RWF", amount)
