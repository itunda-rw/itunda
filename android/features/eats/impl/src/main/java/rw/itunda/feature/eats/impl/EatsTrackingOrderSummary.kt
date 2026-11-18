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
internal fun EatsTrackingOrderSummary(
    order: EatsOrderDto, items: List<EatsOrderItemDto>, restaurantName: String, orderCode: String,
    pickup: Boolean, status: String, onCancel: () -> Unit,
) {
    val palette = eatsPrototypePalette()
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp)) {
        Text(when { status == "CANCELLED" -> "Refunded to itunda Pay"; status == "DELIVERED" -> "Delivered at 16:50"; pickup && status == "PICKED_UP" -> "Picked up"; pickup -> "Ready by 16:50"; else -> "Arrives by 16:50" }, color = palette.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        items.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.quantity} × ${item.productName}", color = palette.secondary, fontSize = 15.sp)
                Text(String.format(Locale.US, "%,.0f RWF", item.unitPrice * item.quantity), color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", color = palette.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%,.0f RWF", order.totalAmount), color = palette.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        if (!pickup) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Delivered to", color = palette.secondary, fontSize = 15.sp)
                Text(order.deliveryAddress, color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(orderCode, color = palette.secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        if (status == "PLACED" && !pickup) {
            Text("Cancel order", color = palette.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp).pressScaleClickable(onClick = onCancel))
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(palette.surfaceSoft).padding(16.dp)) {
            Text("Your order", color = palette.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("$restaurantName, $orderCode", color = palette.secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.quantity} × ${item.productName}", color = palette.primary, fontSize = 15.sp)
                    Text(String.format(Locale.US, "%,.0f", item.unitPrice * item.quantity), color = palette.secondary, fontSize = 15.sp)
                }
            }
            order.deliveryNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text("Your note", color = palette.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
                Text(notes, color = palette.primary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text("Paid with", color = palette.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            Text("itunda Pay", color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", color = palette.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(String.format(Locale.US, "%,.0f RWF", order.totalAmount), color = palette.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
