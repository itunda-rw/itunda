package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ShoppingMerchantDto

private val railTints = listOf(Color(0xFFFFDDD4), Color(0xFFFFF0B8), Color(0xFFFFE6C7), Color(0xFFDDF3E4), Color(0xFFE6DDF7))

/** "Popular this week": top restaurants by rating x reviews, numbered, in a horizontal rail. Hidden when nothing is rated. */
@Composable
fun EatsPopularRail(restaurants: List<ShoppingMerchantDto>, onOpen: (ShoppingMerchantDto) -> Unit, modifier: Modifier = Modifier, isPickup: Boolean = false) {
    val top = restaurants.filter { it.rating != null }.sortedByDescending { (it.rating ?: 0.0) * (it.reviewCount + 1) }.take(5)
    if (top.isEmpty()) return
    Column(modifier) {
        Text("Popular this week", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            top.forEachIndexed { i, m ->
                Column(Modifier.width(150.dp).pressScaleClickable(onClick = { onOpen(m) })) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(22.dp)).background(railTints[i % railTints.size])) {
                        Text("${i + 1}", color = Color(0xFF191F28), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ItundaArt(eatsCategoryArt(m.category), ItundaArtSet.Color, 56.dp) }
                    }
                    Text(m.businessName, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        listOfNotNull(m.rating?.let { String.format(Locale.US, "★ %.1f (%d)", it, m.reviewCount) }, m.deliveryTimeMinutes?.let { minutes -> if (isPickup) "Ready in ${maxOf(10, minutes - 10)} min" else "$minutes min" }).joinToString(" · "),
                        color = Ids.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Section title with a trailing count, as in the prototype's "Restaurants near you  14". */
@Composable
fun EatsSectionHeader(title: String, count: Int?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        if (count != null) Text("$count", color = Ids.colors.textSecondary, fontSize = 14.sp)
    }
}
