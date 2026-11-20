package rw.itunda.feature.shop.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val Amber = Color(0xFFF5A524)
internal val Gold = Color(0xFFFFB800)

private class Review(val who: String, val when_: String, val stars: Int, val text: String, val photo: Boolean, val helpful: Int)

private val reviews = listOf(
    Review("K*m", "Yesterday", 5, "Arrived on time and well packed. Will order again.", true, 12),
    Review("S*n", "3 days ago", 4, "Good value for the price. Quality is as described.", false, 3),
    Review("J*a", "1 week ago", 5, "Bought it for the family and everyone liked it.", true, 7),
)

/** Prototype `product` below the divider: tabs, rating summary, photo reviews, filter chips and review items. */
@Composable
internal fun ShopReviewsSection(p: ShopPalette, art: String, seller: String, name: String) {
    Row(Modifier.fillMaxWidth().height(55.dp).border(Dp.Hairline, p.soft)) {
        Box(Modifier.weight(1f).height(54.dp), Alignment.Center) {
            Text("Reviews (6)", color = p.text, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.67.dp).background(p.text))
        }
        Box(Modifier.weight(1f).height(54.dp), Alignment.Center) { Text("Product info", color = p.tt, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.SemiBold) }
    }
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text("Reviews from all shops", color = p.tt, fontSize = 14.sp, lineHeight = 19.6.sp, modifier = Modifier.padding(top = 22.dp))
        Row(Modifier.padding(top = 9.dp).height(110.dp)) {
            Column(Modifier.width(100.6.dp).padding(top = 9.8.dp)) {
                Text("4.2", color = p.text, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Row(Modifier.padding(top = 1.dp).height(22.4.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { i -> StarIcon(15.2.dp, if (i < 4) Gold else p.soft); if (i < 4) Spacer(Modifier.width(1.dp)) }
                }
                Text("6 reviews", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 0.dp))
            }
            Column(Modifier.width(241.4.dp)) {
                listOf(5 to 0.59f, 4 to 0.28f, 3 to 0.08f, 2 to 0.03f, 1 to 0.02f).forEachIndexed { i, (n, f) ->
                    Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$n", color = p.tt, fontSize = 13.sp, lineHeight = 18.2.sp, modifier = Modifier.width(24.dp))
                        Box(Modifier.width(171.4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.soft)) {
                            Box(Modifier.width((171.4f * f).dp).height(8.dp).background(Amber))
                        }
                        Text("${Math.round(f * 100)}%", color = p.tt, fontSize = 13.sp, lineHeight = 18.2.sp, textAlign = TextAlign.End, modifier = Modifier.width(46.dp))
                    }
                }
            }
        }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Photo reviews", color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold)
            Text("  2", color = p.tt, fontSize = 16.sp, lineHeight = 22.4.sp)
        }
        Row(Modifier.padding(top = 9.7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Color(0xFFD5ECF6), Color(0xFFFDECEF)).forEach { tint ->
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(tint), Alignment.Center) { ItundaArt(art, ItundaArtSet.Proto, 44.dp, fit = true) }
            }
        }
    }
    Row(Modifier.padding(start = 20.dp, top = 18.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Latest" to 73.3f, "Most helpful" to 116f, "Photo only" to 103.6f).forEachIndexed { i, (l, w) ->
            Box(
                Modifier.padding(end = if (i == 2) 0.dp else 28.5.dp).size(w.dp, 38.dp).clip(RoundedCornerShape(19.dp)).background(if (i == 0) p.text else p.soft),
                Alignment.Center,
            ) { Text(l, color = if (i == 0) p.page else p.text, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Bold) }
        }
    }
    reviews.forEach { r ->
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(Modifier.padding(top = 20.dp).height(22.6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(90.dp)) { repeat(5) { i -> StarIcon(15.2.dp, if (i < r.stars) Gold else p.soft); Spacer(Modifier.width(1.dp)) } }
                Text(r.who, color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 0.dp))
                Text(r.when_, color = p.tt, fontSize = 14.sp, lineHeight = 19.6.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Text("$name · Seller: $seller", color = p.tt, fontSize = 14.sp, lineHeight = 19.6.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
            Row(Modifier.padding(top = 10.dp)) {
                if (r.photo) Box(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFD5ECF6)), Alignment.Center) { ItundaArt(art, ItundaArtSet.Proto, 44.dp, fit = true) }
                Text(r.text, color = p.text, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(start = if (r.photo) 12.dp else 0.dp))
            }
            Row(Modifier.fillMaxWidth().height(40.dp).padding(top = 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text("Helpful · ${r.helpful}", color = p.tt, fontSize = 14.sp, lineHeight = 19.6.sp)
                Box(Modifier.padding(start = 10.dp).size(44.dp, 40.dp).clip(RoundedCornerShape(12.dp)).background(p.soft), Alignment.Center) { Text("👍", fontSize = 18.sp) }
            }
        }
    }
}

@Composable
internal fun StarIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val path = androidx.compose.ui.graphics.Path()
        val c = center; val ro = this.size.minDimension / 2f; val ri = ro * 0.5f
        for (i in 0 until 10) {
            val a = Math.toRadians(-90.0 + i * 36.0); val r = if (i % 2 == 0) ro else ri
            val x = c.x + (r * Math.cos(a)).toFloat(); val y = c.y + (r * Math.sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}
