package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids




internal val EATS_STATUS_LABEL = mapOf(
    "PLACED" to "Placed",
    "ACCEPTED" to "Accepted by restaurant",
    "PREPARING" to "Preparing",
    "READY_FOR_PICKUP" to "Ready for pickup",
    "RIDER_ASSIGNED" to "Rider on the way to restaurant",
    "PICKED_UP" to "Picked up — on the way",
    "DELIVERED" to "Delivered",
    "CANCELLED" to "Cancelled — refunded",
)

internal val DINE_IN_STATUS_LABEL = mapOf(
    "PLACED" to "Placed",
    "ACCEPTED" to "Accepted by restaurant",
    "PREPARING" to "Preparing",
    "SERVED" to "Served",
    "CANCELLED" to "Cancelled — refunded",
)

internal val RIDER_STATUS_CHAIN = listOf("RIDER_ASSIGNED", "PICKED_UP", "DELIVERED")

// Real order-status stepper (2026-08-10) -- Uber Eats/Coupang Eats/배민 all render the
// pipeline as a visual progress track, not just a status word, so a customer can see at
// a glance how close their food is without reading. Two variants because the real
// backend pipeline forks at READY_FOR_PICKUP (EatsOrderService.kt): a delivery order
// gets a rider hop (RIDER_ASSIGNED -> PICKED_UP), a pickup order goes straight to
// DELIVERED with no rider -- order.riderId being null throughout a pickup order's life
// is what distinguishes the two without needing a client-side fulfillmentType field.
internal val EATS_DELIVERY_STEPS = listOf(
    "PLACED" to "Placed",
    "ACCEPTED" to "Accepted",
    "PREPARING" to "Preparing",
    "READY_FOR_PICKUP" to "Ready",
    "RIDER_ASSIGNED" to "Rider assigned",
    "PICKED_UP" to "On the way",
    "DELIVERED" to "Delivered",
)
internal val EATS_PICKUP_STEPS = listOf(
    "PLACED" to "Placed",
    "ACCEPTED" to "Accepted",
    "PREPARING" to "Preparing",
    "READY_FOR_PICKUP" to "Ready for pickup",
    "DELIVERED" to "Picked up",
)

@Composable
internal fun EatsStatusStepper(status: String, hasRider: Boolean) {
    val steps = if (hasRider) EATS_DELIVERY_STEPS else EATS_PICKUP_STEPS
    val currentIndex = steps.indexOfFirst { it.first == status }
    if (currentIndex < 0) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, (_, label) ->
            val reached = i <= currentIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (reached) Ids.colors.brand else Ids.colors.surfaceSoft),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(label, fontSize = 10.sp, color = if (reached) Ids.colors.textPrimary else Ids.colors.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
            }
            if (i < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .height(2.dp)
                        .background(if (i < currentIndex) Ids.colors.brand else Ids.colors.surfaceSoft),
                )
            }
        }
    }
}

internal fun nextRiderStatus(current: String): String? {
    val idx = RIDER_STATUS_CHAIN.indexOf(current)
    return if (idx >= 0 && idx + 1 < RIDER_STATUS_CHAIN.size) RIDER_STATUS_CHAIN[idx + 1] else null
}

