package rw.itunda.rider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.formatMoney
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography

/**
 * One delivery as a flat row on the white page (UI_UX_GUIDELINES rule 10: no card around
 * a linear list), a hairline divider between rows like a ledger. [showClaim] is true only
 * for the open-deliveries list; a claimed row opens its detail on tap instead.
 */
@Composable
fun RiderDeliveryRow(
    delivery: RiderDelivery,
    merchantName: String,
    showClaim: Boolean,
    onClaim: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().pressScaleClickable(enabled = !showClaim, onClick = onOpen)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (delivery.source == DeliverySource.EATS) "Food" else "Package",
                        style = IdsTypography.Typography7,
                        color = Ids.colors.textSecondary,
                    )
                    Text(merchantName, style = IdsTypography.Subtitle1, color = Ids.colors.textPrimary)
                }
                when (delivery) {
                    is RiderDelivery.Eats -> Text(
                        "${formatMoney(delivery.order.deliveryFee)} RWF",
                        style = IdsTypography.Subtitle1,
                        color = Ids.colors.brand,
                    )
                    // Commerce's Order entity models no separate rider-payout field --
                    // showing the real order total rather than fabricating a delivery-fee
                    // figure the backend doesn't compute.
                    is RiderDelivery.Commerce -> Text(
                        "${formatMoney(delivery.order.totalAmount)} RWF order",
                        style = IdsTypography.Subtitle1,
                        color = Ids.colors.brand,
                    )
                }
            }
            Text(delivery.deliveryAddress, style = IdsTypography.Body2, color = Ids.colors.textSecondary)
            if (delivery is RiderDelivery.Eats) {
                delivery.order.distanceKm?.let {
                    Text(
                        "${String.format(Locale.US, "%.1f", it)} km away",
                        style = IdsTypography.Body2,
                        color = Ids.colors.textSecondary,
                    )
                }
            }
            StatusBadge(delivery.status)
            if (showClaim) {
                Spacer(modifier = Modifier.height(12.dp))
                IdsButton(text = "Claim this delivery", onClick = onClaim, modifier = Modifier.fillMaxWidth())
            }
        }
        Divider(color = Ids.colors.divider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
internal fun StatusBadge(status: String) {
    val label = when (status) {
        "RIDER_ASSIGNED" -> "Heading to pickup"
        "PICKED_UP" -> "On the way"
        "PACKED" -> "Ready for pickup"
        "SHIPPED" -> "On the way"
        "DELIVERED" -> "Delivered"
        else -> status
    }
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .background(Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, style = IdsTypography.Typography7, color = Ids.colors.textSecondary)
    }
}
