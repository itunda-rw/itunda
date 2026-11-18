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
import rw.itunda.core.designsystem.components.pressScaleClickable

private data class HubRecent(val name: String, val time: String, val amount: String, val earned: String)

private val Recents = listOf(
    HubRecent("Kigali Bus Services", "Tuesday", "300 RWF", "9 RWF earned"),
    HubRecent("Kigali Bakery", "08:12", "4,500 RWF", "135 RWF earned"),
    HubRecent("itunda Shop", "14:05", "18,000 RWF", "540 RWF earned"),
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
    Box(Modifier.fillMaxWidth().height(78.dp).padding(top = 24.dp)) {
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
}

@Composable
private fun EarningCard(p: PayPalette, dark: Boolean, onFindStores: () -> Unit) {
    Column(Modifier.fillMaxWidth().height(227.dp).clip(RoundedCornerShape(24.dp)).background(p.soft)) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp).fillMaxWidth().height(60.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    WalletGlyph(Modifier.size(width = 14.5.dp, height = 13.9.dp).offset(x = 4.7.dp))
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
            }, contentAlignment = Alignment.Center) { WalletGlyph(Modifier.size(24.dp)) }
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
private fun WalletGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRoundRect(Color(0xFFFFC837), Offset(w * 0.18f, 0f), Size(w * 0.6f, h * 0.45f), CornerRadius(w * 0.1f))
        drawRoundRect(Color(0xFF8F89FF), Offset(0f, h * 0.28f), Size(w, h * 0.72f), CornerRadius(w * 0.18f))
        drawRoundRect(Color(0xFF6C63F0), Offset(w * 0.58f, h * 0.52f), Size(w * 0.42f, h * 0.26f), CornerRadius(w * 0.13f))
        drawCircle(Color(0xFFFFD24D), w * 0.05f, Offset(w * 0.73f, h * 0.65f))
    }
}

@Composable
private fun HubCard(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().padding(horizontal = 15.dp)) { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) { content() } }
}

@Composable
private fun IconChip(tint: Color, p: PayPalette, size: Dp = 30.dp, content: @Composable () -> Unit = {}) {
    Box(Modifier.size(size).clip(RoundedCornerShape(9.dp)).background(p.soft), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun OnlineRow(p: PayPalette) {
    Row(Modifier.height(70.dp), verticalAlignment = Alignment.CenterVertically) {
        IconChip(Color(0xFF5B8CFF), p) {
            Canvas(Modifier.size(16.3.dp)) { drawCircle(Color(0xFF5B8CFF), size.width / 2); drawCircle(Color(0xFF2DB873), size.width * 0.2f, Offset(size.width * 0.35f, size.height * 0.62f)) }
        }
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
    BenefitRow(p, Color(0xFFF5C518), "Points · Pay Money", "Total 6,400 RWF", onOpenPoints)
    BenefitRow(p, Color(0xFFF0535F), "Coupons received", "0", null)
}

@Composable
private fun Chevron(c: Color) {
    Canvas(Modifier.padding(start = 6.dp).size(width = 8.dp, height = 14.dp)) {
        val path = Path().apply { moveTo(1.5.dp.toPx(), 1.5.dp.toPx()); lineTo(6.5.dp.toPx(), 7.dp.toPx()); lineTo(1.5.dp.toPx(), 12.5.dp.toPx()) }
        drawPath(path, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun BenefitRow(p: PayPalette, tint: Color, label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).then(if (onClick != null) Modifier.pressScaleClickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(tint, p) { Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(tint)) }
        Text(label, color = p.text, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Text(value, color = p.tt, fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp)
    }
}

@Composable
private fun MoreBenefits(p: PayPalette) {
    Text("Get more benefits", color = p.tt, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.height(30.dp))
    listOf(
        Color(0xFFF04452) to "Discount and cashback coupons", Color(0xFF8B5CF6) to "Airtime discounts", Color(0xFF14B8A6) to "Card with up to 5% back",
        Color(0xFFF5A623) to "Invite friends, get 10,000 RWF", Color(0xFF3B6FF6) to "Point missions",
    ).forEach { (c, t) ->
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconChip(c, p) { Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(c)) }
            Text(t, color = p.text, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, modifier = Modifier.padding(start = 14.dp))
        }
    }
}

@Composable
private fun HistoryBlock(p: PayPalette, dark: Boolean, onOpenHistory: () -> Unit) {
    Text("History", color = p.tt, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.height(30.dp))
    Recents.forEach { r ->
        Row(Modifier.fillMaxWidth().height(55.dp).pressScaleClickable(onClick = onOpenHistory), verticalAlignment = Alignment.CenterVertically) {
            IconChip(p.text, p) { Box(Modifier.size(13.dp).clip(RoundedCornerShape(4.dp)).background(p.text)) }
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
    Box(
        Modifier.padding(top = 8.dp).fillMaxWidth().height(60.dp).requiredWidth(360.dp).offset(x = (-20).dp).pressScaleClickable(onClick = onOpenHistory),
        contentAlignment = Alignment.Center,
    ) { Text("See more", color = if (dark) Color(0xFF9EA4AE) else Color(0xFF4E5968), fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Links(p: PayPalette) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
        listOf("FAQ", "Send feedback").forEach {
            Text(it, color = p.tt, fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp).wrapContentHeight(Alignment.CenterVertically))
        }
    }
}
