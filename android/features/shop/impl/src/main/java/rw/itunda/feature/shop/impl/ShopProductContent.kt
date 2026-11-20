package rw.itunda.feature.shop.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable

private val GalleryTints = listOf(Color(0xFFD5ECF6), Color(0xFFFDECEF), Color(0xFFFFF3DA), Color(0xFFE6F6EC))
private val Link = Color(0xFF5A51EC)

/**
 * Prototype `product` (above the reviews): 4-page gallery with back / search / bag, Similar chip and counter,
 * seller + name, discount and price block, shipping line, the Earn / Rating / Delivery / Seller / Draw rows
 * and the pinned heart / bag / Buy now bar. Stateless and demo-backed.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ShopProductContent(
    index: Int, onBack: () -> Unit = {}, onBuy: () -> Unit = {}, onAddToBag: () -> Unit = {}, onFavourite: () -> Unit = {},
    dark: Boolean = isSystemInDarkTheme(),
) {
    val p = if (dark) Dark else Light
    val it = items[index]
    val pct = if (it.old > 0) Math.round((it.old - it.price).toFloat() / it.old * 100) else 0
    val earn = Math.round(it.price * 0.07f / 10f) * 10
    ShopText {
        Box(Modifier.fillMaxSize().background(p.page)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
                val gallery = rememberLazyListState()
                Box(Modifier.fillMaxWidth().height(380.dp)) {
                    LazyRow(state = gallery, flingBehavior = rememberSnapFlingBehavior(gallery)) {
                        itemsIndexed(GalleryTints) { _, tint ->
                            Box(Modifier.width(390.dp).height(380.dp).background(tint), contentAlignment = Alignment.Center) {
                                ItundaArt(it.art, ItundaArtSet.Proto, 142.dp, fit = true)
                            }
                        }
                    }
                    GlassButton(Modifier.offset(12.dp, 12.dp), onBack) { ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Color.White) }
                    GlassButton(Modifier.offset(284.dp, 12.dp)) { ItundaArt("search", ItundaArtSet.Line, 24.dp, tint = Color.White) }
                    GlassButton(Modifier.offset(334.dp, 12.dp)) { ItundaArt("bag", ItundaArtSet.Proto, 28.dp, fit = true) }
                    Row(
                        Modifier.offset(12.dp, 328.dp).size(121.2.dp, 40.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.9f)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.padding(start = 8.dp).size(44.dp, 24.dp)) {
                            Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFDECEF)), contentAlignment = Alignment.Center) { ItundaArt("bag", ItundaArtSet.Proto, 16.dp, fit = true) }
                            Box(Modifier.offset(20.dp).size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFF3DA)), contentAlignment = Alignment.Center) { ItundaArt("community", ItundaArtSet.Proto, 16.dp, fit = true) }
                        }
                        Text("Similar", color = Color(0xFF191F28), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                    Box(
                        Modifier.offset(331.dp, 339.8.dp).size(45.dp, 26.2.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("${gallery.firstVisibleItemIndex + 1}|${GalleryTints.size}", color = Color(0xFF191F28), fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.SemiBold) }
                }
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp)) {
                    Text(it.seller, color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
                    Text(it.name, color = p.text, fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    Text("Origin: see product details", color = p.tt, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 3.dp))
                    if (pct > 0) Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = p.text)) { append("$pct%") }
                            append("  ")
                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append("${fmt(it.old)} RWF") }
                        },
                        color = p.tt, fontSize = 18.sp, lineHeight = 25.2.sp, modifier = Modifier.padding(top = 15.dp),
                    )
                    Row(Modifier.padding(top = 2.dp).fillMaxWidth().height(67.2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = p.text)) { append("${fmt(it.price)} RWF") }
                                withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Danger)) { append(" Best price") }
                            },
                            lineHeight = 39.sp, modifier = Modifier.width(203.2.dp).wrapContentHeight(Alignment.Top, unbounded = true),
                        )
                        Box(
                            Modifier.padding(top = 9.6.dp).size(138.8.dp, 48.dp).clip(RoundedCornerShape(16.dp)).background(p.soft).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Compare prices", color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                    Text("Shipping 1,500 RWF · free over 8,000", color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp)) {
                    InfoRow("Earn", p, chevron = true) {
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = if (dark) Brand else Link)) { append("Up to ${fmt(earn)} RWF") }
                            append(" back with itunda Pay")
                        }, color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp)
                    }
                    InfoRow("Rating", p, chevron = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StarIcon(16.dp, Gold)
                            Text(" ${it.rating} (${it.reviews})", color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp)
                        }
                    }
                    InfoRow("Delivery", p) { Text(it.arrives ?: "Ships in 2-3 days", color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp) }
                    InfoRow("Seller", p) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Chip("Official", p) }
                            Chip("Best seller", p)
                        }
                    }
                    InfoRow("Draw", p, chevron = true) {
                        Text("Win 500 points", color = if (dark) Brand else Link, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(12.dp).background(p.soft))
                ShopReviewsSection(p, it.art, it.seller, it.name)
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(p.page).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp, 52.dp).pressScaleClickable(onClick = onFavourite), contentAlignment = Alignment.Center) { ItundaArt("heart", ItundaArtSet.Line, 24.dp, tint = p.tt) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(p.soft).pressScaleClickable(onClick = onAddToBag), contentAlignment = Alignment.Center) {
                    ItundaArt("bag", ItundaArtSet.Proto, 28.dp, fit = true)
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(18.dp)).background(Brand).pressScaleClickable(onClick = onBuy), contentAlignment = Alignment.Center) {
                    Text("Buy now", color = Color(0xFF17171C), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.32).sp)
                }
            }
        }
    }
}

@Composable
private fun GlassButton(modifier: Modifier, onClick: (() -> Unit)? = null, icon: @Composable () -> Unit) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)).let { m -> if (onClick != null) m.pressScaleClickable(onClick = onClick) else m },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun Chip(label: String, p: ShopPalette) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(p.soft).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, color = p.text, fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(label: String, p: ShopPalette, chevron: Boolean = false, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.tt, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.width(80.dp))
        Box(Modifier.width(210.dp)) { value() }
        Spacer(Modifier.weight(1f))
        if (chevron) Canvas(Modifier.padding(end = 4.dp).size(8.dp, 14.dp)) {
            val w = 1.8.dp.toPx()
            drawLine(p.tt, Offset(1.dp.toPx(), 1.dp.toPx()), Offset(7.dp.toPx(), 7.dp.toPx()), w, StrokeCap.Round)
            drawLine(p.tt, Offset(7.dp.toPx(), 7.dp.toPx()), Offset(1.dp.toPx(), 13.dp.toPx()), w, StrokeCap.Round)
        }
    }
}
