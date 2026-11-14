package rw.itunda.feature.shop.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.theme.Ids

/** Prototype Shop landing top: tabs, first-come card, quick actions and the recommended grid (demo data). */
private data class ProtoItem(val name: String, val discount: Int, val price: String, val art: String)

private val protoItems = listOf(
    ProtoItem("Mountain coffee, 500 g", 19, "6,500 RWF", "coffee"),
    ProtoItem("Umuganda tee, cotton", 14, "9,000 RWF", "bag"),
)

@Composable
fun ShopProtoHome() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItundaArt("search", ItundaArtSet.Line, 20.dp, tint = Ids.colors.textTertiary)
                Spacer(Modifier.width(12.dp))
                Text("Search products", color = Ids.colors.textTertiary, fontSize = 16.sp)
            }
            ItundaArt("user", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
            ItundaArt("bag", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Home", "Beauty", "Fresh", "Categories").forEachIndexed { i, label ->
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (i == 0) Ids.colors.surfaceSoft else Color.Transparent).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (i == 0) Ids.colors.textPrimary else Ids.colors.textTertiary)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFFCE4EC))) {
            Column(Modifier.padding(24.dp)) {
                Text("500 first-come", color = Ids.colors.danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Wireless\nearphones", color = Ids.colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("18,500 RWF", color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            ItundaArt("celebrate", ItundaArtSet.Color, 120.dp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp))
            Text("Max 1 per day", color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomStart).padding(24.dp))
            Box(
                Modifier.align(Alignment.BottomEnd).padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) { Text("1|4", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Daily draw" to "celebrate", "Deals" to "money", "My orders" to "bag", "Rewards" to "gift", "Wishlist" to "heart").forEach { (label, art) ->
                Column(Modifier.width(66.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        ItundaArt(art, ItundaArtSet.Color, 32.dp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(label, fontSize = 12.sp, color = Ids.colors.textTertiary, maxLines = 1)
                }
            }
        }
        Text("Recommended for you", color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            protoItems.forEach { item ->
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        ItundaArt(item.art, ItundaArtSet.Color, 64.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(item.name, color = Ids.colors.textPrimary, fontSize = 15.sp, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${item.discount}%", color = Ids.colors.danger, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(6.dp))
                        Text(item.price, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
