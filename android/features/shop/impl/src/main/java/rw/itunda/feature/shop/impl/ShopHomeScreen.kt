package rw.itunda.feature.shop.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.PretendardFontFamily

/** Measured from prototype v1038 `shop` (Shop tab), light + dark. */
private class ShopPalette(val page: Color, val bar: Color, val soft: Color, val text: Color, val tt: Color)

private val Light = ShopPalette(Color.White, Color.White, Color(0xFFF2F4F6), Color(0xFF191F28), Color(0xFF6B7684))
private val Dark = ShopPalette(Color(0xFF17171C), Color(0xFF18181D), Color(0xFF2C2C35), Color(0xFFF9FAFB), Color(0xFF9EA4AE))
private val Danger = Color(0xFFF04452)
private val Brand = Color(0xFF8F89FF)

private class FirstCome(val count: Int, val name: String, val price: String, val art: String, val tint: Color)
private class Item(val name: String, val art: String, val price: Int, val old: Int, val arrives: String?, val rating: String, val reviews: Int)

private val firstCome = listOf(
    FirstCome(500, "Wireless earphones", "18,500", "celebrate", Color(0xFFFDECEF)),
    FirstCome(1000, "Power bank, 10,000 mAh", "16,000", "airtime", Color(0xFFE8F1FF)),
    FirstCome(1500, "Yoga mat", "14,000", "community", Color(0xFFFFF3DA)),
    FirstCome(2000, "LED desk lamp", "9,800", "bolt", Color(0xFFE6F6EC)),
)

// Prototype PRODS (names, prices, glyphs) with the card's arrival / rating lines as rendered.
private val items = listOf(
    Item("Mountain coffee, 500 g", "harvest", 6500, 8000, "Arrives today", "4.2", 6),
    Item("Umuganda tee, cotton", "bag", 9000, 10500, "Arrives tomorrow", "4.9", 43),
    Item("Agaseke basket, small", "community", 12000, 0, null, "4.8", 80),
    Item("Phone case, universal", "airtime", 3500, 0, "Arrives today", "4.7", 117),
    Item("Honey, 250 g", "gift", 4200, 4700, "Arrives tomorrow", "4.6", 154),
    Item("Kitenge fabric, 2 m", "voucher", 15000, 0, null, "4.5", 191),
)

private fun fmt(n: Int) = String.format(Locale.US, "%,d", n)

@Composable
private fun ShopText(content: @Composable () -> Unit) = ProvideTextStyle(
    TextStyle(
        fontFamily = PretendardFontFamily, letterSpacing = (-0.176).sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
    content,
)

/**
 * Prototype Shop home: search bar, tabs, first-come cards, quick actions and the Recommended grid.
 * Stateless and demo-backed so CI can render it; callers supply navigation.
 */
@Composable
fun ShopHomeContent(
    onBack: () -> Unit = {}, onOrders: () -> Unit = {}, onWishlist: () -> Unit = {}, onProduct: (Int) -> Unit = {}, onQuickAdd: (Int) -> Unit = {},
    dark: Boolean = isSystemInDarkTheme(),
) {
    val p = if (dark) Dark else Light
    ShopText {
        Column(Modifier.fillMaxSize().background(p.page).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().height(80.dp).background(p.bar).padding(start = 6.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) { ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = p.text) }
                Row(
                    Modifier.padding(start = 0.dp).width(214.dp).height(46.dp).clip(RoundedCornerShape(14.dp)).background(p.soft).padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ItundaArt("search", ItundaArtSet.Line, 20.dp, tint = p.tt)
                    Text("Search products", color = p.tt, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(start = 13.dp))
                }
                Spacer(Modifier.width(2.dp))
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { ItundaArt("user", ItundaArtSet.Line, 26.dp, tint = p.text) }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { ItundaArt("bag", ItundaArtSet.Line, 26.dp, tint = p.text) }
            }
            Row(Modifier.fillMaxWidth().height(56.dp).horizontalScroll(rememberScrollState()).padding(start = 16.dp, top = 12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Home", "Beauty", "Fresh", "Categories", "First-come deals").forEachIndexed { i, label ->
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (i == 0) p.soft else Color.Transparent).padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.SemiBold, color = if (i == 0) p.text else p.tt, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
            val pager = rememberLazyListState()
            Box(Modifier.padding(top = 16.dp)) {
                LazyRow(state = pager, contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(firstCome) { _, c -> FirstComeCard(c) }
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 15.dp).size(width = 46.dp, height = 26.dp).clip(RoundedCornerShape(13.dp)).background(Color(0x80191F28)),
                    contentAlignment = Alignment.Center,
                ) { Text("${pager.firstVisibleItemIndex + 1}|${firstCome.size}", color = Color.White, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 16.dp)) {
                listOf("Daily draw" to "celebrate", "Deals" to "money", "My orders" to "bag", "Rewards" to "gift", "Wishlist" to "heart").forEach { (label, art) ->
                    val go = when (label) { "My orders" -> onOrders; "Wishlist" -> onWishlist; else -> null }
                    Column(Modifier.width(70.dp).let { if (go != null) it.pressScaleClickable(onClick = go) else it }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(p.soft), contentAlignment = Alignment.Center) { ItundaArt(art, if (art == "heart") ItundaArtSet.Color else ItundaArtSet.Proto, if (art == "heart") 32.dp else 36.dp, fit = true) }
                        Text(label, color = p.tt, fontSize = 12.sp, lineHeight = 18.8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
            Text("Recommended for you", color = p.text, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 14.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                items.chunked(2).forEachIndexed { r, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEachIndexed { c, item -> Box(Modifier.weight(1f)) { ItemCard(item, p, onClick = { onProduct(r * 2 + c) }, onAdd = { onQuickAdd(r * 2 + c) }) } }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FirstComeCard(c: FirstCome) {
    val ink = Color(0xFF191F28)
    Box(Modifier.width(342.dp).height(300.dp).clip(RoundedCornerShape(28.dp)).background(c.tint)) {
        Text("${c.count} first-come", color = Danger, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp, top = 26.dp))
        Text(c.name, color = ink, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 53.dp).width(170.dp))
        Text("${c.price} RWF", color = ink, fontSize = 24.sp, lineHeight = 33.6.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = if (c.name.length > 17 && c.name != "Yoga mat") 121.dp else 89.dp))
        Text("Max 1 per day", color = Color(0xFF6B7684), fontSize = 13.sp, lineHeight = 18.2.sp, modifier = Modifier.padding(start = 24.dp, top = if (c.name.length > 17) 200.6.dp else 168.6.dp))
        Box(Modifier.offset(x = 202.dp, y = 64.dp).size(120.dp, 125.dp), contentAlignment = Alignment.Center) { if (c.art == "bolt") ItundaArt("bolt", ItundaArtSet.Line, 64.dp, tint = Color(0xFFF5A524)) else ItundaArt(c.art, ItundaArtSet.Proto, 120.dp, fit = true) }
    }
}

@Composable
private fun ItemCard(it: Item, p: ShopPalette, onClick: () -> Unit, onAdd: () -> Unit) {
    val pct = if (it.old > 0) Math.round((it.old - it.price).toFloat() / it.old * 100) else 0
    val earn = Math.round(it.price * 0.07f / 10f) * 10
    Column(Modifier.pressScaleClickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(169.dp).clip(RoundedCornerShape(16.dp)).background(p.soft), contentAlignment = Alignment.Center) {
            ItundaArt(it.art, ItundaArtSet.Proto, 64.dp, fit = true)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp).size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)).pressScaleClickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { ItundaArt("bag", ItundaArtSet.Proto, 26.dp, fit = true) }
        }
        Text(it.name, color = p.text, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.height(25.2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pct > 0) Text("$pct%  ", color = Danger, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text("${fmt(it.price)} RWF", color = p.text, fontSize = 18.sp, lineHeight = 25.2.sp, fontWeight = FontWeight.Bold)
        }
        if (it.arrives != null) Text(it.arrives, color = Brand, fontSize = 13.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(top = 6.dp).height(18.2.dp), verticalAlignment = Alignment.CenterVertically) {
            Star()
            Text(" ${it.rating} (${it.reviews})", color = p.tt, fontSize = 13.sp, lineHeight = 18.2.sp)
        }
        Text("Best seller", color = p.tt, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Text("Earn up to ${fmt(earn)} RWF", color = p.tt, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Star() {
    androidx.compose.foundation.Canvas(Modifier.size(12.3.dp)) {
        val path = androidx.compose.ui.graphics.Path()
        val c = center; val ro = size.minDimension / 2f; val ri = ro * 0.5f
        for (i in 0 until 10) {
            val a = Math.toRadians(-90.0 + i * 36.0); val r = if (i % 2 == 0) ro else ri
            val x = c.x + (r * Math.cos(a)).toFloat(); val y = c.y + (r * Math.sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, Color(0xFFFFB800))
    }
}
