package rw.itunda.feature.pay.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable

private data class HubRecent(val name: String, val time: String, val amount: String, val earned: String, val icon: HubIcon)

private val Recents = listOf(
    HubRecent("Kigali Bus Services", "Tuesday", "300 RWF", "9 RWF earned", HubIcon.Bus),
    HubRecent("Kigali Bakery", "08:12", "4,500 RWF", "135 RWF earned", HubIcon.Food),
    HubRecent("itunda Shop", "14:05", "18,000 RWF", "540 RWF earned", HubIcon.Bag),
)

/**
 * Prototype `payhub`, measured at 390dp: centred title bar, map hero, overlapping earning card with
 * the Pay Money timeline, online-pay row, benefits, "Get more benefits", history and links.
 * All numbers are dummy until a rewards summary endpoint exists.
 */
@Composable
fun PayHub(
    onBack: () -> Unit, onFindStores: () -> Unit, onOpenPoints: () -> Unit = {}, onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val p = payPalette(false, dark)
    PayTextScope {
        Column(Modifier.fillMaxSize().background(p.page).verticalScroll(rememberScrollState())) {
            HubTopBar(p, onBack, onOpenSettings)
            Box(Modifier.fillMaxWidth().height(385.dp)) {
                PayHubMap(dark, onFindStores)
                Box(Modifier.offset(y = 158.dp).padding(horizontal = 15.dp)) { EarningCard(p, dark, onFindStores) }
            }
            Spacer(Modifier.height(16.dp))
            HubCard(Modifier.height(96.dp)) { OnlineRow(p) }
            Spacer(Modifier.height(12.dp))
            HubCard(Modifier.height(174.dp)) { BenefitsReceived(p, onOpenPoints) }
            Spacer(Modifier.height(12.dp))
            HubCard(Modifier.height(276.dp)) { MoreBenefits(p) }
            Spacer(Modifier.height(12.dp))
            HubCard(Modifier.height(283.dp)) { HistoryBlock(p, dark, onOpenHistory) }
            Spacer(Modifier.height(12.dp))
            Links(p)
        }
    }
}

@Composable
private fun HubTopBar(p: PayPalette, onBack: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp).height(44.dp)) {
        Box(Modifier.padding(start = 24.dp).size(44.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", Modifier.size(24.dp), tint = p.gear)
        }
        Row(Modifier.align(Alignment.Center).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(rw.itunda.core.designsystem.R.drawable.itunda_mark), null, Modifier.size(24.dp), tint = p.title)
            Spacer(Modifier.width(2.dp))
            Text("itunda pay", color = p.title, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(44.dp).pressScaleClickable(onClick = onSettings), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Settings, "Settings", Modifier.size(30.dp), tint = p.gear)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun EarningCard(p: PayPalette, dark: Boolean, onFindStores: () -> Unit) {
    Column(Modifier.fillMaxWidth().height(227.dp).clip(RoundedCornerShape(24.dp)).background(p.soft)) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp).fillMaxWidth().height(60.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItundaArt("wallet", ItundaArtSet.Proto, 12.dp, modifier = Modifier.offset(x = 4.7.dp))
                    Text("itunda Pay", color = p.tt, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 22.dp))
                }
                Text("Earning 3%", color = p.brandText, fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, modifier = Modifier.padding(top = 2.dp))
            }
            Box(
                Modifier.padding(top = 8.dp).size(width = 127.9.dp, height = 44.dp).clip(RoundedCornerShape(14.dp)).background(p.brand).pressScaleClickable(onClick = onFindStores),
                contentAlignment = Alignment.Center,
            ) { Text("Find stores", color = Color(0xFF17171C), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp) }
        }
        Spacer(Modifier.height(12.dp))
        val shade = if (dark) Color.White else Color(0xFF191F28)
        Box(
            Modifier.fillMaxWidth().height(135.dp).clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Brush.linearGradient(listOf(shade.copy(alpha = 0f), shade.copy(alpha = if (dark) 0.016f else 0.01f), shade.copy(alpha = if (dark) 0.067f else 0.05f)), Offset(0f, 0f), Offset(100f, 600f)))
                .drawBehind { drawRect(shade.copy(alpha = 0.06f), Offset.Zero, Size(size.width, 1.dp.toPx())) },
        ) {
            Box(Modifier.offset(x = 20.dp, y = 17.dp).size(36.dp).clip(RoundedCornerShape(11.dp)).background(if (dark) Color(0xFF17171C) else Color.White).drawBehind {
                drawRoundRect(p.brandText, cornerRadius = CornerRadius(11.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }, contentAlignment = Alignment.Center) { ItundaArt("wallet", ItundaArtSet.Proto, 17.dp) }
            Box(Modifier.offset(x = 36.5.dp, y = 53.dp).size(1.5.dp, 24.dp).background(p.brandText.copy(alpha = 0.9f)))
            Box(Modifier.offset(x = 24.dp, y = 77.dp).size(28.dp).clip(CircleShape).background(p.tint), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (dark) Color(0xFF17171C).let { p.brandText } else p.brandText))
            }
            Text("itunda Pay Money", color = p.tt, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp, modifier = Modifier.offset(x = 70.dp, y = 28.dp))
            Column(Modifier.offset(x = 70.dp, y = 69.dp)) {
                Text("itunda card benefits", color = p.brandText, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                Text("Get 2% extra back", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun HubCard(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().padding(horizontal = 15.dp)) { Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)) { content() } }
}

@Composable
private fun IconChip(p: PayPalette, size: Dp = 30.dp, content: @Composable () -> Unit = {}) {
    Box(Modifier.size(size).clip(RoundedCornerShape(9.dp)).background(p.soft), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun OnlineRow(p: PayPalette) {
    Row(Modifier.height(70.dp), verticalAlignment = Alignment.CenterVertically) {
        IconChip(p) { ItundaArt("globe", ItundaArtSet.Proto, 16.3.dp) }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text("How to pay online", color = p.text, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
            Text("Jumia, Yego and more", color = p.tt, fontSize = 14.sp, lineHeight = 18.sp)
        }
        Box(Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF5A51EC).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text("View", color = p.brandText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BenefitsReceived(p: PayPalette, onOpenPoints: () -> Unit) {
    Text("Benefits received in November", color = p.tt, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.height(22.dp))
    Row(Modifier.height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("6,400 RWF received", color = p.text, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
        Chevron(p.tt.copy(alpha = 0.6f))
    }
    BenefitRow(p, HubIcon.Star, "Points · Pay Money", "Total 6,400 RWF", onOpenPoints)
    BenefitRow(p, HubIcon.Gift, "Coupons received", "0", null)
}

@Composable
private fun Chevron(c: Color) {
    Canvas(Modifier.padding(start = 6.dp).size(width = 8.dp, height = 14.dp)) {
        val path = Path().apply { moveTo(1.5.dp.toPx(), 1.5.dp.toPx()); lineTo(6.5.dp.toPx(), 7.dp.toPx()); lineTo(1.5.dp.toPx(), 12.5.dp.toPx()) }
        drawPath(path, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun BenefitRow(p: PayPalette, icon: HubIcon, label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).then(if (onClick != null) Modifier.pressScaleClickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(p) { HubGlyph(icon) }
        Text(label, color = p.text, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Text(value, color = p.tt, fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp)
    }
}

@Composable
private fun MoreBenefits(p: PayPalette) {
    Text("Get more benefits", color = p.tt, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.height(30.dp))
    listOf(
        HubIcon.Gift to "Discount and cashback coupons", HubIcon.Bolt to "Airtime discounts", HubIcon.Card to "Card with up to 5% back",
        HubIcon.People to "Invite friends, get 10,000 RWF", HubIcon.Star to "Point missions",
    ).forEach { (c, t) ->
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconChip(p) { HubGlyph(c) }
            Text(t, color = p.text, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, modifier = Modifier.padding(start = 14.dp))
        }
    }
}

@Composable
private fun HistoryBlock(p: PayPalette, dark: Boolean, onOpenHistory: () -> Unit) {
    Text("History", color = p.tt, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.height(30.dp))
    Recents.forEach { r ->
        Row(Modifier.fillMaxWidth().height(55.dp).pressScaleClickable(onClick = onOpenHistory), verticalAlignment = Alignment.CenterVertically) {
            IconChip(p) { HubGlyph(r.icon) }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(r.name, color = p.text, fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
                Text(r.time, color = p.tt, fontSize = 14.sp, lineHeight = 18.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(r.amount, color = p.text, fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                Text(r.earned, color = p.brandText, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    val shade = if (dark) Color.White else Color(0xFF191F28)
    Box(
        Modifier.padding(top = 8.dp).requiredWidth(360.dp).offset(x = (-20).dp).height(60.dp)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(Brush.linearGradient(listOf(shade.copy(alpha = 0f), shade.copy(alpha = if (dark) 0.016f else 0.01f), shade.copy(alpha = if (dark) 0.067f else 0.05f)), Offset(0f, 0f), Offset(100f, 400f)))
            .drawBehind { drawRect(shade.copy(alpha = 0.06f), Offset.Zero, Size(size.width, 1.dp.toPx())) }
            .pressScaleClickable(onClick = onOpenHistory),
        contentAlignment = Alignment.Center,
    ) { Text("See more", color = if (dark) Color(0xFF9EA4AE) else Color(0xFF4E5968), fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Links(p: PayPalette) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
        listOf("FAQ", "Send feedback").forEach {
            Text(it, color = p.tt, fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 24.dp).wrapContentHeight(Alignment.CenterVertically))
        }
    }
}

private enum class HubIcon { Star, Gift, Bolt, Card, People, Bus, Food, Bag }

/** Prototype row glyphs: the shared Proto art where it exists, the two flat shapes (star, bolt) drawn directly. */
@Composable
private fun HubGlyph(icon: HubIcon) {
    val gold = Color(0xFFFFC837)
    when (icon) {
        HubIcon.Star -> Canvas(Modifier.size(19.dp)) {
            val u = size.width / 80f
            val path = Path().apply { moveTo(40f * u, 9f * u); lineTo(49f * u, 28f * u); lineTo(70f * u, 31f * u); lineTo(55f * u, 46f * u); lineTo(58.5f * u, 67f * u); lineTo(40f * u, 57f * u); lineTo(21.5f * u, 67f * u); lineTo(25f * u, 46f * u); lineTo(10f * u, 31f * u); lineTo(31f * u, 28f * u); close() }
            drawPath(path, gold); drawPath(path, gold, style = androidx.compose.ui.graphics.drawscope.Stroke(6f * u, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        HubIcon.Bolt -> Canvas(Modifier.size(19.dp)) {
            val u = size.width / 80f
            val path = Path().apply { moveTo(46f * u, 8f * u); lineTo(16f * u, 46f * u); lineTo(36f * u, 46f * u); lineTo(32f * u, 72f * u); lineTo(64f * u, 32f * u); lineTo(44f * u, 32f * u); close() }
            drawPath(path, gold); drawPath(path, gold, style = androidx.compose.ui.graphics.drawscope.Stroke(6f * u, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        HubIcon.Gift -> ItundaArt("gift", ItundaArtSet.Proto, 16.dp)
        HubIcon.Card -> ItundaArt("card", ItundaArtSet.Proto, 16.dp)
        HubIcon.People -> ItundaArt("community", ItundaArtSet.Proto, 16.dp)
        HubIcon.Bus -> ItundaArt("busstop", ItundaArtSet.Proto, 16.dp)
        HubIcon.Food -> ItundaArt("restaurant", ItundaArtSet.Proto, 16.dp)
        HubIcon.Bag -> ItundaArt("bag", ItundaArtSet.Proto, 16.dp)
    }
}
