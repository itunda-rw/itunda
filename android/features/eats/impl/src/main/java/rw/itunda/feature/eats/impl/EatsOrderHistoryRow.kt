package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsOrderItemDto
import rw.itunda.core.network.ShoppingMerchantDto

@Composable
internal fun EatsOrderHistoryRow(
    order: EatsOrderDto,
    restaurant: ShoppingMerchantDto?,
    items: List<EatsOrderItemDto>?,
    reordering: Boolean,
    cancelling: Boolean,
    onTrack: () -> Unit,
    onReorder: () -> Unit,
    onReceipt: () -> Unit,
    onHelp: () -> Unit,
    onCancel: () -> Unit,
) {
    val p = eatsPrototypePalette()
    val prototype = restaurant?.toEatsPrototypeRestaurant()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                .background(prototype?.background ?: Color(0xFFFFE8C7)),
            contentAlignment = Alignment.Center,
        ) {
            ItundaArt(prototype?.art ?: "bread", ItundaArtSet.Proto, 34.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier.weight(1f).height(114.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(22.4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    restaurant?.businessName ?: "Eats order",
                    modifier = Modifier.weight(1f),
                    color = p.primary, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(formatEatsOrderRwf(order.totalAmount), color = p.primary, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                EATS_STATUS_LABEL[order.status] ?: order.status,
                color = if (order.status == "CANCELLED") p.accent else p.secondary,
                fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            val itemSummary = items?.takeIf { it.isNotEmpty() }
                ?.joinToString(" · ") { "${it.quantity} × ${it.productName}" }
                ?: if (items == null) "Loading items…" else "Items unavailable"
            Text(itemSummary, color = p.secondary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 2.dp).fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (order.status != "DELIVERED") EatsOrderAction("Track", p.accent, onClick = onTrack)
                if (order.status == "DELIVERED" || order.status == "CANCELLED") {
                    EatsOrderAction(if (reordering) "Reordering…" else "Reorder", p.accent, enabled = !reordering, onClick = onReorder)
                    EatsOrderAction("Receipt", p.secondary, onClick = onReceipt)
                    EatsOrderAction("Help", p.secondary, onClick = onHelp)
                }
                if (order.status == "PLACED") {
                    EatsOrderAction(if (cancelling) "Cancelling…" else "Cancel", p.secondary, enabled = !cancelling, onClick = onCancel)
                }
            }
        }
    }
}

@Composable
private fun EatsOrderAction(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = color,
        fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.height(44.dp).pressScaleClickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
    )
}

private fun formatEatsOrderRwf(amount: Double): String = String.format(Locale.US, "%,.0f RWF", amount)
