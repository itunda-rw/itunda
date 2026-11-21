package rw.itunda.feature.shop.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable

private val CartLink = Color(0xFF5A51EC)
private val Green = Color(0xFF08794D)
private const val FREE_SHIPPING_OVER = 8_000
private const val SHIPPING_FEE = 1_500

/**
 * Prototype `scart`: Cart / Recent / Wishlist tabs, select-all row, line items with steppers, notes, promo field,
 * delivery address, free-shipping meter, delivery options, points toggle, totals and the pinned Pay bar.
 * [lines] is (demo item index, quantity). Stateless; the host owns quantities.
 */
@Composable
fun ShopCartContent(
    lines: List<Pair<Int, Int>>, payBalance: Int = 120_500, onBack: () -> Unit = {},
    onAdjust: (index: Int, delta: Int) -> Unit = { _, _ -> }, onPay: () -> Unit = {}, dark: Boolean = isSystemInDarkTheme(),
) {
    val p = if (dark) Dark else Light
    val link = if (dark) Brand else CartLink
    val sub = lines.sumOf { (i, q) -> items[i].price * q }
    val ship = if (sub >= FREE_SHIPPING_OVER || sub == 0) 0 else SHIPPING_FEE
    val total = sub + ship
    val count = lines.size
    ShopText {
        Box(Modifier.fillMaxSize().background(p.page)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
                Row(Modifier.fillMaxWidth().height(70.dp).padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(start = 6.dp).size(48.dp).pressScaleClickable(onClick = onBack), Alignment.Center) { ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = p.text) }
                    Text("Cart", color = p.text, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, modifier = Modifier.padding(start = 4.dp))
                }
                Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(52.dp)) {
                    listOf("Cart $count", "Recent", "Wishlist").forEachIndexed { i, l ->
                        Box(Modifier.width(114.dp).height(52.dp), Alignment.Center) {
                            Text(l, color = if (i == 0) p.text else p.tt, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.SemiBold)
                            if (i == 0) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.67.dp).background(p.text))
                        }
                    }
                }
                Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(0.5.dp).background(p.soft))
                Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(55.5.dp)) {
                    Box(Modifier.padding(top = 19.5.dp)) { Check(p) }
                    Text("Select all (0/${lines.size})", color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, top = 22.3.dp).weight(1f))
                    Soft(p, 143.3.dp, 40.dp, 12.dp, modifier = Modifier.padding(top = 13.5.dp)) { Text("Delete selected", color = p.tt, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold) }
                }
                Column(Modifier.padding(top = 12.dp)) {
                    lines.forEach { (i, q) ->
                        val it = items[i]
                        Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
                            Check(p)
                            Box(Modifier.padding(start = 12.dp).size(56.dp).clip(RoundedCornerShape(16.dp)).background(p.soft), Alignment.Center) { ItundaArt(it.art, ItundaArtSet.Proto, 34.dp, fit = true) }
                            Column(Modifier.padding(start = 12.dp).width(114.dp)) {
                                Text(it.name.substringBefore(" ·"), color = p.text, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text("${fmt(it.price)} RWF", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Soft(p, 36.dp, 36.dp, 12.dp, { onAdjust(i, -1) }) { Text("−", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Normal) }
                            Text("$q", color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                            Soft(p, 36.dp, 36.dp, 12.dp, { onAdjust(i, 1) }) { Text("+", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Normal) }
                        }
                    }
                }
                Text(
                    "* Up to 100 items fit in your cart.\n* Items added over 90 days ago are removed automatically.",
                    color = p.tt, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 5.dp, bottom = 1.dp),
                )
                Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 6.dp).fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(p.soft).padding(start = 14.dp), Alignment.CenterStart) {
                        Text("Promo code", color = p.tt, fontSize = 16.sp, lineHeight = 22.4.sp)
                    }
                    Box(Modifier.size(78.5.dp, 40.dp).clip(RoundedCornerShape(18.dp)).background(p.text), Alignment.Center) {
                        Text("Apply", color = p.page, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Card(p, Modifier.padding(top = 16.dp), 66.4.dp, 16.dp) {
                    Column(Modifier.weight(1f)) {
                        Text("Deliver to · Home", color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold)
                        Text("KG 11 Ave, Kimironko", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Text("Change", color = link, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.SemiBold)
                }
                Card(p, Modifier.padding(top = 16.dp), 71.dp, 20.dp, vertical = Alignment.Top) {
                    Column(Modifier.padding(top = 16.dp)) {
                        Text(if (sub >= FREE_SHIPPING_OVER) "You unlocked free shipping" else "Add ${fmt(FREE_SHIPPING_OVER - sub)} RWF for free shipping", color = p.text, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.padding(top = 10.4.dp).width(310.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.page.copy(alpha = 0.6f))) {
                            Box(Modifier.width((310f * (sub.toFloat() / FREE_SHIPPING_OVER).coerceAtMost(1f)).dp).height(8.dp).background(Green))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Option(p, "Rider delivery", "1 to 2 days", if (ship == 0) "Free" else "${fmt(ship)} RWF", selected = true, link)
                Spacer(Modifier.height(8.dp))
                Option(p, "Pickup point", "Kimironko, ready tomorrow", "Free", selected = false, link)
                Card(p, Modifier.padding(top = 20.dp), 70.4.dp, 20.dp) {
                    Column(Modifier.weight(1f)) {
                        Text("Use points", color = p.text, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                        Text("453 pts available", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Box(Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(p.soft).padding(3.dp)) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White))
                    }
                }
                Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp)) {
                    Sum(p, "Subtotal", "${fmt(sub)} RWF")
                    Sum(p, "Delivery", if (ship == 0) "Free" else "${fmt(ship)} RWF")
                    Row(Modifier.padding(top = 2.5.dp).fillMaxWidth().height(45.2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", color = p.text, fontSize = 18.sp, lineHeight = 25.2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${fmt(total)} RWF", color = p.text, fontSize = 28.sp, lineHeight = 39.2.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                    }
                    Row(Modifier.padding(top = 8.dp).fillMaxWidth()) {
                        Text("Pay from", color = p.tt, fontSize = 14.sp, lineHeight = 19.6.sp, modifier = Modifier.weight(1f))
                        Text("itunda Pay · ${fmt(payBalance)} RWF", color = p.text, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(p.page).padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp)) {
                Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp)).background(Brand).pressScaleClickable(onClick = onPay), Alignment.Center) {
                    Text("$count items · ${fmt(total)} RWF · Pay", color = Color(0xFF17171C), fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.36).sp)
                }
            }
        }
    }
}

@Composable private fun Check(p: ShopPalette) { Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).border(2.dp, p.soft, RoundedCornerShape(9.dp))) }

@Composable
private fun Soft(p: ShopPalette, w: Dp, h: Dp, r: Dp, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val m = modifier.size(w, h).clip(RoundedCornerShape(r)).background(p.soft)
    Box(if (onClick != null) m.pressScaleClickable(onClick = onClick) else m, Alignment.Center) { content() }
}

@Composable
private fun Card(p: ShopPalette, modifier: Modifier, h: Dp, r: Dp, vertical: Alignment.Vertical = Alignment.CenterVertically, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier.padding(horizontal = 24.dp).fillMaxWidth().height(h).clip(RoundedCornerShape(r)).background(p.soft).padding(horizontal = 14.dp), verticalAlignment = vertical, content = content)
}

@Composable
private fun Option(p: ShopPalette, title: String, caption: String, price: String, selected: Boolean, link: Color) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(69.dp).clip(shape).background(p.page).border(2.dp, if (selected) link else p.soft, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
            Text(caption, color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Text(price, color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Sum(p: ShopPalette, label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.tt, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
        Text(value, color = p.text, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
    }
}
