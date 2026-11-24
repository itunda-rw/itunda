package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.LiveRiderMiniMap
import rw.itunda.core.designsystem.components.RouteMiniMap
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.ShoppingMerchantDto

/** Rider-side order row retained for the Deliver tab's route and live-tracking actions. */
@Composable
internal fun EatsOrderRow(
    order: EatsOrderDto,
    restaurant: ShoppingMerchantDto? = null,
    action: (@Composable () -> Unit)? = null,
) {
    var showRoute by remember { mutableStateOf(false) }
    var showLiveTracking by remember { mutableStateOf(false) }
    val restaurantLat = restaurant?.latitude
    val restaurantLng = restaurant?.longitude
    val deliveryLat = order.deliveryLatitude
    val deliveryLng = order.deliveryLongitude
    val isPickupOrder = order.deliveryAddress.isBlank()
    val canShowRoute = restaurantLat != null && restaurantLng != null && deliveryLat != null && deliveryLng != null
    val canShowLiveTracking = canShowRoute && (order.status == "RIDER_ASSIGNED" || order.status == "PICKED_UP")

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(restaurant?.businessName ?: "Eats order", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(EATS_STATUS_LABEL[order.status] ?: order.status, color = Ids.colors.brand, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(
                    if (isPickupOrder) "Pickup at restaurant" else order.deliveryAddress,
                    color = Ids.colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Text(String.format(Locale.US, "%,.0f RWF", order.totalAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        if (order.status != "DELIVERED" && order.status != "CANCELLED") {
            EatsStatusStepper(order.status, hasRider = order.deliveryLatitude != null || order.riderId != null)
        }
        if (order.riderName != null || order.estimatedArrivalMinutes != null) {
            Text(
                listOfNotNull(
                    order.riderName?.let { "Rider: $it" },
                    order.estimatedArrivalMinutes?.let { "Latest arrival by ~$it min" },
                ).joinToString(" · "),
                color = Ids.colors.textSecondary,
                fontSize = 12.sp,
            )
        }
        if (!order.deliveryNotes.isNullOrBlank()) {
            Text(
                "Note: ${order.deliveryNotes}",
                color = Ids.colors.textPrimary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(Ids.colors.surfaceSoft).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        if (canShowLiveTracking) {
            ListingActionButton(if (showLiveTracking) "Hide live tracking" else "🛵 Track your rider live", false) {
                showLiveTracking = !showLiveTracking
                showRoute = false
            }
        }
        if (showLiveTracking && restaurant != null && restaurantLat != null && restaurantLng != null && deliveryLat != null && deliveryLng != null) {
            LiveRiderMiniMap(order.id, restaurantLat, restaurantLng, deliveryLat, deliveryLng, restaurant.businessName, "Delivery address")
        }
        if (canShowRoute && !showLiveTracking) {
            ListingActionButton(if (showRoute) "Hide route" else "🚗 View real delivery route", false) { showRoute = !showRoute }
        }
        if (showRoute && !showLiveTracking && restaurant != null && restaurantLat != null && restaurantLng != null && deliveryLat != null && deliveryLng != null) {
            RouteMiniMap(restaurantLat, restaurantLng, deliveryLat, deliveryLng, restaurant.businessName, "Delivery address")
        }
        action?.invoke()
    }
    Divider(color = Ids.colors.divider, thickness = 0.5.dp)
}

