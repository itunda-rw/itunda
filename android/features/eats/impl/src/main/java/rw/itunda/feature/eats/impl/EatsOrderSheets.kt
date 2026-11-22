@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsOrderItemDto

@Composable
fun EatsReceiptSheet(
    order: EatsOrderDto,
    restaurantName: String,
    items: List<EatsOrderItemDto>?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Receipt", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("EA-${order.id.takeLast(4)} · $restaurantName", color = Ids.colors.textSecondary, fontSize = 13.sp)
            Divider(color = Ids.colors.divider)
            if (items == null) Text("Loading order items…", color = Ids.colors.textSecondary, fontSize = 14.sp)
            else items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.quantity} × ${item.productName}", color = Ids.colors.textPrimary, fontSize = 14.sp)
                    Text(String.format(Locale.US, "%,.0f RWF", item.unitPrice * item.quantity), color = Ids.colors.textPrimary, fontSize = 14.sp)
                }
            }
            Divider(color = Ids.colors.divider)
            ReceiptAmountRow("Items", order.itemsSubtotal)
            if (order.deliveryFee <= 0.0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Delivery", color = Ids.colors.textSecondary, fontSize = 14.sp)
                    Text("Free", color = Ids.colors.textPrimary, fontSize = 14.sp)
                }
            } else {
                ReceiptAmountRow("Delivery", order.deliveryFee)
            }
            if (order.platformFee > 0) ReceiptAmountRow("Service fee", order.platformFee)
            if (order.promotionDiscount > 0) ReceiptAmountRow("Discount", -order.promotionDiscount)
            ReceiptAmountRow("Total", order.totalAmount, strong = true)
            Text("Status · ${EATS_STATUS_LABEL[order.status] ?: order.status}", color = Ids.colors.textSecondary, fontSize = 13.sp)
            Text("Paid with itunda Pay", color = Ids.colors.textSecondary, fontSize = 13.sp)
            if (order.deliveryAddress.isNotBlank()) Text("Address · ${order.deliveryAddress}", color = Ids.colors.textSecondary, fontSize = 13.sp)
            if (!order.deliveryNotes.isNullOrBlank()) Text("Note · ${order.deliveryNotes}", color = Ids.colors.textSecondary, fontSize = 13.sp)
            ListingActionButton("OK", false, onClick = onDismiss)
        }
    }
}

@Composable
fun EatsHelpSheet(
    order: EatsOrderDto,
    restaurantName: String,
    selectedReason: String?,
    onReasonSelected: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Report a problem", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("EA-${order.id.takeLast(4)} · $restaurantName", color = Ids.colors.textSecondary, fontSize = 13.sp)
            Text("What went wrong?", color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            listOf("Missing item", "Wrong item", "Food was cold", "Order was late", "Something else").forEach { reason ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (selectedReason == reason) Ids.colors.surfaceSoft else Ids.colors.surface)
                        .pressScaleClickable { onReasonSelected(reason) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    Text(reason, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = if (selectedReason == reason) FontWeight.Bold else FontWeight.Normal)
                }
            }
            ListingActionButton("Cancel", false, onClick = onDismiss)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (selectedReason == null) Ids.colors.surfaceSoft else Ids.colors.brand)
                    .pressScaleClickable(enabled = selectedReason != null, onClick = onSubmit)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("Send report", color = if (selectedReason == null) Ids.colors.textSecondary else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ReceiptAmountRow(label: String, amount: Double, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (strong) Ids.colors.textPrimary else Ids.colors.textSecondary, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
        Text(String.format(Locale.US, "%,.0f RWF", amount), color = Ids.colors.textPrimary, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
    }
}
