package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsOrderItemDto

@Composable
internal fun EatsTrackingStatusSummary(
    order: EatsOrderDto,
    items: List<EatsOrderItemDto>,
    orderCode: String,
    pickup: Boolean,
) {
    val palette = eatsPrototypePalette()
    val statusTitle = when {
        order.status == "CANCELLED" -> "Arrives by 16:52"
        pickup && order.status in setOf("READY_FOR_PICKUP", "PICKED_UP") -> "Ready by 16:52"
        pickup -> "Ready by 16:51"
        else -> "Arrives by 16:51"
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 22.dp)) {
        Text(statusTitle, color = palette.accent, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(top = 10.dp)) {
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().height(31.dp).padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${item.quantity} × ${item.productName}", color = palette.secondary, fontSize = 15.sp, lineHeight = 21.sp)
                    Text(
                        String.format(Locale.US, "%,.0f RWF", item.unitPrice * item.quantity),
                        color = palette.primary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().height(32.4.dp).padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total", color = palette.primary, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold)
                Text(
                    String.format(Locale.US, "%,.0f RWF", order.totalAmount),
                    color = palette.primary,
                    fontSize = 16.sp,
                    lineHeight = 22.4.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!pickup) {
                Row(
                    Modifier.fillMaxWidth().height(31.dp).padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Delivered to", color = palette.secondary, fontSize = 14.sp, lineHeight = 20.sp)
                    Text(order.deliveryAddress, color = palette.primary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(orderCode, color = palette.secondary, fontSize = 13.sp, lineHeight = 18.2.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
internal fun EatsTrackingOrderCard(
    order: EatsOrderDto,
    items: List<EatsOrderItemDto>,
    restaurantName: String,
    orderCode: String,
    pickup: Boolean,
    onCallRestaurant: (() -> Unit)? = null,
) {
    val palette = eatsPrototypePalette()
    val deliveryNotes = order.deliveryNotes
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surfaceSoft).padding(16.dp),
    ) {
        Text("Your order", color = palette.primary, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text("$restaurantName, $orderCode", color = palette.secondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${item.quantity} × ${item.productName}", color = palette.primary, fontSize = 15.sp, lineHeight = 22.sp)
                Text(
                    String.format(Locale.US, "%,.0f", item.unitPrice * item.quantity),
                    color = palette.secondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        EatsTrackingOrderCardRow(if (pickup) "Pick up at" else "Delivery to", if (pickup) restaurantName else order.deliveryAddress)
        if (!deliveryNotes.isNullOrBlank()) {
            Spacer(Modifier.height(7.dp))
            EatsTrackingOrderCardRow("Your note", deliveryNotes)
        }
        EatsTrackingOrderCardRow("Paid with", "itunda Pay")
        Row(
            Modifier.fillMaxWidth().height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Total", color = palette.primary, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                String.format(Locale.US, "%,.0f RWF", order.totalAmount),
                color = palette.primary,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (onCallRestaurant != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp).pressScaleClickable(onClick = onCallRestaurant),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Call restaurant", color = palette.primary, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold)
                Text("›", color = palette.primary, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EatsTrackingOrderCardRow(label: String, value: String) {
    val palette = eatsPrototypePalette()
    Row(
        Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = palette.secondary, fontSize = 14.sp, lineHeight = 20.sp)
        Text(value, color = palette.primary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}
