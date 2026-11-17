package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import rw.itunda.core.network.ShoppingMerchantDto

/** The two-column v1038 restaurant card: square illustration, tag, save affordance and compact facts. */
@Composable
fun EatsRestaurantTile(
    restaurant: EatsPrototypeRestaurant,
    rating: String,
    eta: String,
    deliveryLine: String,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = eatsPrototypePalette()
    Column(modifier.pressScaleClickable(onClick = onOpen)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(restaurant.background),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ItundaArt(restaurant.art, ItundaArtSet.Color, 84.dp)
            }
            restaurant.tag?.let { tag ->
                Text(
                    tag,
                    color = Color(0xFF191F28),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(10.dp)).background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(36.dp).clip(CircleShape)
                    .background(Color.White)
                    .pressScaleClickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) {
                ItundaArt("heartred", ItundaArtSet.Proto, 20.dp)
            }
        }
        Text(
            restaurant.name,
            color = palette.primary,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("★", color = Color(0xFFFFB321), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text("$rating · $eta", color = palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(
            deliveryLine,
            color = if (deliveryLine == "Free delivery") palette.accent else palette.secondary,
            fontSize = 13.sp,
            fontWeight = if (deliveryLine == "Free delivery") FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** One measured v1038 row; 169dp cards with a 12dp inter-column gap at 390dp width. */
@Composable
fun EatsRestaurantGridRow(
    row: List<ShoppingMerchantDto>,
    favoriteIds: Set<String>,
    onOpen: (ShoppingMerchantDto) -> Unit,
    onToggleFavorite: (ShoppingMerchantDto) -> Unit,
    isPickup: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        row.forEach { merchant ->
            val restaurant = merchant.toEatsPrototypeRestaurant()
            val minutes = if (isPickup) maxOf(10, restaurant.deliveryMinutes - 10) else restaurant.deliveryMinutes
            val delivery = when {
                isPickup -> "Pickup"
                restaurant.deliveryFeeRwf == 0 -> "Free delivery"
                else -> "Delivery ${String.format(Locale.US, "%,d", restaurant.deliveryFeeRwf)}"
            }
            EatsRestaurantTile(
                restaurant = restaurant,
                rating = String.format(Locale.US, "%.1f (%d)", restaurant.rating, restaurant.reviews),
                eta = "$minutes min",
                deliveryLine = delivery,
                isFavorite = merchant.merchantId in favoriteIds,
                onOpen = { onOpen(merchant) },
                onToggleFavorite = { onToggleFavorite(merchant) },
                modifier = Modifier.weight(1f),
            )
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    }
}
