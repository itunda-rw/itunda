package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

private val tileTints = listOf(Color(0xFFE6DDF7), Color(0xFFDDF3E4), Color(0xFFFFE6C7), Color(0xFFFFF0B8), Color(0xFFFFDDD4))

/** Eats art keyed off the restaurant category; unknown categories get the generic plate. */
fun eatsCategoryArt(category: String?): String = when (category?.lowercase()) {
    "bakery" -> "bread"
    "cafe", "coffee" -> "coffee"
    "local" -> "brochette"
    "dessert" -> "juice"
    "fries", "fast food" -> "chips"
    else -> "restaurant"
}

/** Prototype 2-column restaurant tile: tinted art square, tag top-left, heart top-right, then name / rating / delivery. */
@Composable
fun EatsRestaurantTile(
    name: String,
    category: String?,
    tag: String?,
    rating: String?,
    meta: String?,
    deliveryLine: String?,
    freeDelivery: Boolean,
    isFavorite: Boolean,
    tintIndex: Int,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.pressScaleClickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(tileTints[tintIndex.mod(tileTints.size)])) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ItundaArt(eatsCategoryArt(category), ItundaArtSet.Proto, 72.dp) }
            if (tag != null) {
                Text(tag, color = Color(0xFF191F28), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 5.dp))
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp).clip(CircleShape).background(Color.White).pressScaleClickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) { ItundaArt(if (isFavorite) "heartred" else "heart", if (isFavorite) ItundaArtSet.Proto else ItundaArtSet.Line, 22.dp, tint = if (isFavorite) null else Color(0xFF8B95A1)) }
        }
        Text(name, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        if (rating != null || meta != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rating != null) Text("★ $rating", color = Ids.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (meta != null) Text((if (rating != null) " · " else "") + meta, color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
        }
        if (deliveryLine != null) Text(deliveryLine, color = if (freeDelivery) Ids.colors.textBrand else Ids.colors.textSecondary, fontSize = 12.sp, fontWeight = if (freeDelivery) FontWeight.Bold else FontWeight.Normal)
    }
}

/** One row of the 2-column restaurant grid; a lone trailing restaurant keeps its half-width slot. */
@Composable
fun EatsRestaurantGridRow(row: List<ShoppingMerchantDto>, favoriteIds: Set<String>, onOpen: (ShoppingMerchantDto) -> Unit, onToggleFavorite: (ShoppingMerchantDto) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        row.forEach { m ->
            val discount = m.maxDiscountPercent?.takeIf { it > 0 }
            EatsRestaurantTile(
                name = m.businessName, category = m.category,
                tag = if (discount != null) "$discount% off" else if (m.participatesInEatsMembership) "Member" else null,
                rating = m.rating?.let { String.format(Locale.US, "%.1f (%d)", it, m.reviewCount) },
                meta = m.deliveryTimeMinutes?.let { "$it min" },
                deliveryLine = if (m.participatesInEatsMembership) "Free delivery" else m.minOrderAmount?.let { "Min ${String.format(Locale.US, "%,.0f", it)}" },
                freeDelivery = m.participatesInEatsMembership,
                isFavorite = m.merchantId in favoriteIds, tintIndex = m.businessName.length,
                onOpen = { onOpen(m) }, onToggleFavorite = { onToggleFavorite(m) },
                modifier = Modifier.weight(1f),
            )
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    }
}
