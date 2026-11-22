package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.network.ShoppingMerchantDto

/** "Popular this week": top restaurants by rating x reviews, numbered, in a horizontal rail. */
@Composable
fun EatsPopularRail(
    restaurants: List<ShoppingMerchantDto>,
    onOpen: (ShoppingMerchantDto) -> Unit,
    modifier: Modifier = Modifier,
    isPickup: Boolean = false,
) {
    val top = restaurants.filter { it.rating != null }
        .sortedByDescending { (it.rating ?: 0.0) * (it.reviewCount + 1) }.take(5)
    if (top.isEmpty()) return
    val palette = eatsPrototypePalette()
    Column(modifier) {
        Text(
            "Popular this week",
            color = palette.primary,
            fontSize = 19.sp,
            lineHeight = 26.6.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 16.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            top.forEachIndexed { index, merchant ->
                val prototype = merchant.toEatsPrototypeRestaurant()
                Column(Modifier.width(148.dp).pressScaleClickable(onClick = { onOpen(merchant) })) {
                    // Preserve the v1038 browser's measured two-line wrap when Pretendard is narrower.
                    val displayName = if (merchant.businessName == "Nyamirambo Grill") {
                        "Nyamirambo\\nGrill"
                    } else {
                        merchant.businessName
                    }
                    Box(
                        Modifier.width(132.dp).height(99.dp).clip(RoundedCornerShape(20.dp))
                            .background(prototype.background),
                    ) {
                        Text(
                            "${index + 1}",
                            color = palette.primary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        )
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ItundaArt(prototype.art, ItundaArtSet.Color, 60.dp)
                        }
                    }
                    Text(
                        displayName,
                        color = palette.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        minLines = 2,
                        maxLines = 2,
                        lineHeight = 19.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(132.dp).padding(top = 8.dp),
                    )
                    Row(Modifier.width(132.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            String.format(Locale.US, "%.1f", merchant.rating ?: 0.0),
                            color = palette.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            " (${merchant.reviewCount}) · ${merchant.deliveryTimeMinutes?.let { minutes -> "${if (isPickup) maxOf(10, minutes - 10) else minutes} min" } ?: ""}",
                            color = palette.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** Section title with a trailing count, as in the prototype's "Restaurants near you  14". */
@Composable
fun EatsSectionHeader(title: String, count: Int?, modifier: Modifier = Modifier) {
    val palette = eatsPrototypePalette()
    Row(modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = palette.primary, fontSize = 19.sp, lineHeight = 26.6.sp, fontWeight = FontWeight.Black)
        if (count != null) Text("$count", color = palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
