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
import androidx.compose.foundation.layout.offset
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
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Prototype Shop landing top: tabs, first-come card, quick actions and the recommended grid (demo data). */
private data class ProtoItem(val name: String, val discount: Int, val price: String, val art: String)

private val protoItems = listOf(
    ProtoItem("Mountain coffee, 500 g", 19, "6,500 RWF", "coffee"),
    ProtoItem("Umuganda tee, cotton", 14, "9,000 RWF", "bag"),
)

@Composable
fun ShopProtoHome(onOrders: () -> Unit = {}, onWishlist: () -> Unit = {}) {
    Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
        Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFFDECEF))) {
            Column(Modifier.padding(start = 24.dp, top = 26.dp, end = 24.dp)) {
                Text("500 first-come", color = Ids.colors.danger, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Wireless earphones", color = Color(0xFF191F28), fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(170.dp))
                Spacer(Modifier.height(4.dp))
                Text("18,500 RWF", color = Color(0xFF191F28), fontSize = 24.sp, lineHeight = 33.6.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.Bold)
            }
            ItundaArt("celebrate", ItundaArtSet.Color, 120.dp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp))
            Text("Max 1 per day", color = Color(0xFF6B7684), fontSize = 13.sp, lineHeight = 18.2.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 81.dp))
            Box(
                Modifier.align(Alignment.BottomEnd).padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) { Text("1|4", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth().offset(x = (-12).dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Daily draw" to "celebrate", "Deals" to "money", "My orders" to "bag", "Rewards" to "gift", "Wishlist" to "heart").forEach { (label, art) ->
                val go = when (label) { "My orders" -> onOrders; "Wishlist" -> onWishlist; else -> ({}) }
                Column(Modifier.width(70.dp).pressScaleClickable(enabled = label == "My orders" || label == "Wishlist", onClick = go), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        ItundaArt(art, ItundaArtSet.Color, 32.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(label, fontSize = 12.sp, lineHeight = 16.8.sp, letterSpacing = (-0.3).sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B7684), maxLines = 1)
                }
            }
        }
        Text("Recommended for you", color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            protoItems.forEach { item ->
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        ItundaArt(item.art, ItundaArtSet.Color, 53.dp)
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
