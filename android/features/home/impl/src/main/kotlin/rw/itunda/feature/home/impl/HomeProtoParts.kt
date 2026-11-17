package rw.itunda.feature.home.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet

// Prototype v1038 Home pieces. Every dp / sp value below is measured from the rendered HTML
// (bounding boxes + computed styles at a 390dp viewport), not eyeballed.

@Composable
internal fun HText(
    text: String, color: Color, size: Int, weight: FontWeight, modifier: Modifier = Modifier, lh: Int? = null, ls: Float = 0f,
) = Text(text, modifier, color = color, fontSize = size.sp, fontWeight = weight, lineHeight = (lh ?: (size * 14 / 10)).sp, letterSpacing = ls.sp)

@Composable
internal fun HomeTile(size: Dp, content: @Composable () -> Unit) {
    val p = homePalette()
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).drawBehind {
            val s = this.size.width
            drawRect(Brush.linearGradient(listOf(p.tileA, p.tileB), Offset(.1f * s, -.07f * s), Offset(.9f * s, 1.07f * s)))
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun HomePill(label: String, onClick: () -> Unit) {
    val p = homePalette()
    Box(
        Modifier.defaultMinSize(minWidth = 64.dp).height(44.dp).clip(RoundedCornerShape(12.dp)).background(p.tint).clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { HText(label, p.tintFg, 14, FontWeight.Bold, lh = 17) }
}

@Composable
internal fun HomeProtoHeader(hasUnread: Boolean, onBank: () -> Unit, onPay: () -> Unit, onBell: () -> Unit, bellLabel: String) {
    val p = homePalette()
    Row(Modifier.fillMaxWidth().height(78.dp).padding(start = 24.dp, end = 16.dp, top = 24.dp), verticalAlignment = Alignment.Top) {
        Row(Modifier.height(44.dp).offset(y = 2.dp).clickable(onClick = onBank), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(rw.itunda.core.designsystem.R.drawable.itunda_mark), null, Modifier.size(24.dp), tint = p.logo)
            Spacer(Modifier.width(2.dp))
            HText("Bank", p.bankTitle, 24, FontWeight.ExtraBold, lh = 28, ls = -0.3f)
            ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = p.tt)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(44.dp).clickable(onClick = onPay), contentAlignment = Alignment.Center) { PayScanGlyph(30.dp, p.text) }
        Box(Modifier.size(44.dp).clickable(onClick = onBell).semantics { contentDescription = bellLabel }, contentAlignment = Alignment.Center) {
            ItundaArt("bell", ItundaArtSet.Line, 30.dp, tint = p.text)
            if (hasUnread) Box(Modifier.align(Alignment.TopStart).padding(start = 25.dp, top = 10.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(p.unread))
        }
    }
}

/** 20sp amount (24dp line) with the 14sp unit hanging 10dp below the top, inside the prototype's 35dp line box. */
@Composable
private fun HomeAmountLine(amount: String) {
    val p = homePalette()
    Row(Modifier.height(35.dp)) {
        Text(amount, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp, letterSpacing = (-0.5).sp)
        Text("RWF", color = p.mu, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, modifier = Modifier.padding(start = 5.dp, top = 10.dp))
    }
}

@Composable
private fun HeroRow(glyph: @Composable () -> Unit, amount: Double, label: String, extra: String?, action: String, onOpen: () -> Unit, onAction: () -> Unit) {
    val p = homePalette()
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeTile(36.dp, glyph)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            HomeAmountLine(homeRwf(amount))
            Text(
                buildAnnotatedString { append(label); if (extra != null) withStyle(SpanStyle(color = p.lk)) { append(" $extra") } },
                color = p.mu, fontSize = 14.sp, lineHeight = 21.sp,
            )
        }
        HomePill(action, onAction)
    }
}

/** Hero card: 358x245, top-right lavender glow, bottom-left lavender corner, grey-shaded footer. */
@Composable
internal fun HomeAccountsCard(
    bank: Double, pay: Double, interestDays: Int?, onBank: () -> Unit, onPay: () -> Unit, onSend: () -> Unit, onAdd: () -> Unit,
    onBills: () -> Unit, onCard: () -> Unit, onSavings: () -> Unit, onSeeAll: () -> Unit,
) {
    val p = homePalette()
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).drawBehind {
            val w = size.width
            scale(1f, 0.55f, pivot = Offset(w, 0f)) {
                drawRect(Brush.radialGradient(listOf(p.glow, p.glow.copy(alpha = 0.55f), Color.Transparent), Offset(w, 0f), 250.dp.toPx()), size = Size(w, size.height / 0.55f))
            }
            drawRect(Brush.radialGradient(listOf(p.tint.copy(alpha = 0.6f), Color.Transparent), Offset(0f, size.height), 120.dp.toPx()))
        }.padding(top = 16.dp),
    ) {
        HeroRow({ Icon(painterResource(rw.itunda.core.designsystem.R.drawable.itunda_mark), null, Modifier.size(24.dp), tint = p.barOn) }, bank, "itunda Bank", interestDays?.let { "+interest $it" }, "Send", onBank, onSend)
        HeroRow({ ItundaArt("wallet", ItundaArtSet.Proto, 25.dp) }, pay, "itunda Pay", null, "Add", onPay, onAdd)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(49.dp).drawBehind {
                val w = size.width; val h = size.height
                val dx = 0.208f; val dy = 0.978f; val half = (w * dx + h * dy) / 2f
                drawRect(Brush.linearGradient(listOf(Color.Transparent, p.footShade.copy(alpha = p.footShade.alpha * 0.2f), p.footShade), Offset(w / 2 - dx * half, h / 2 - dy * half), Offset(w / 2 + dx * half, h / 2 + dy * half)))
            },
        ) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(0f to Color.Transparent, 0.2f to p.hair, 0.8f to p.hair, 1f to Color.Transparent)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(49.dp), verticalAlignment = Alignment.CenterVertically) {
                HText("Bills", p.mu, 15, FontWeight.Medium, Modifier.clickable(onClick = onBills), lh = 22)
                Spacer(Modifier.width(18.dp))
                HText("Card", p.mu, 15, FontWeight.Medium, Modifier.clickable(onClick = onCard), lh = 22)
                Spacer(Modifier.width(18.dp))
                HText("Savings", p.mu, 15, FontWeight.Medium, Modifier.clickable(onClick = onSavings), lh = 22)
                Spacer(Modifier.weight(1f))
                HText("See all", p.mu, 15, FontWeight.Medium, Modifier.clickable(onClick = onSeeAll), lh = 22)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
internal fun HomeInviteBanner(onInvite: () -> Unit) {
    val p = homePalette()
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp).clickable(onClick = onInvite), verticalAlignment = Alignment.CenterVertically) {
        HomeTile(48.dp) { ItundaArt("gift", ItundaArtSet.Proto, 20.5.dp) }
        Spacer(Modifier.width(14.dp))
        Text(
            buildAnnotatedString { append("Invite friends, get "); withStyle(SpanStyle(color = p.lk)) { append("10,000 RWF") } },
            color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, letterSpacing = (-0.3).sp,
        )
    }
}

/** Spending summary: 20sp amount + unit, caption, and the 8dp rotated chevron at the right. */
@Composable
internal fun HomeSpendingRow(amount: Double, month: String, onClick: () -> Unit) {
    val p = homePalette()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            HomeAmountLine(homeRwf(amount))
            Text("Spent in $month", color = p.mu, fontSize = 14.sp, lineHeight = 21.sp)
        }
        Canvas(Modifier.padding(end = 6.dp).size(8.dp)) {
            val s = 2.dp.toPx()
            drawLine(p.tt.copy(alpha = 0.6f), Offset(0f, 0f), Offset(size.width, size.height / 2), s, StrokeCap.Butt)
            drawLine(p.tt.copy(alpha = 0.6f), Offset(size.width, size.height / 2), Offset(0f, size.height), s, StrokeCap.Butt)
        }
    }
}

/** Standard card row (bill / points): 36dp tile, 17sp title, 14sp caption (wraps), pill. */
@Composable
internal fun HomeRow(glyph: @Composable () -> Unit, title: String, subtitle: String, action: String, onAction: () -> Unit) {
    val p = homePalette()
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeTile(36.dp, glyph)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            HText(title, p.text, 17, FontWeight.SemiBold, lh = 26, ls = -0.2f)
            Text(subtitle, color = p.mu, fontSize = 14.sp, lineHeight = 21.sp)
        }
        Spacer(Modifier.width(12.dp))
        HomePill(action, onAction)
    }
}

@Composable
internal fun HomeSectionHeader(title: String, action: String?, onAction: () -> Unit) {
    val p = homePalette()
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = if (action != null) 7.dp else 4.dp)) {
        Text(title, Modifier.weight(1f).alignByBaseline(), color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp, letterSpacing = (-0.4).sp)
        if (action != null) Text(action, Modifier.clickable(onClick = onAction).alignByBaseline(), color = p.pk, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp)
    }
}

/** A pocket: 44dp circle icon (optional goal ring), small name above a 17sp amount, "Top up" pill. */
@Composable
internal fun HomePocketRow(icon: @Composable () -> Unit, progress: Float?, name: String, amount: String, onTopUp: () -> Unit) {
    val p = homePalette()
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(p.tint).drawBehind {
            if (progress != null) {
                val sw = 2.5.dp.toPx()
                drawCircle(p.ring, radius = size.width / 2 - sw / 2, style = Stroke(sw))
                drawArc(p.barOn, -90f, 360f * progress, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
            }
        }, contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            HText(name, p.pk, 14, FontWeight.Normal, lh = 20, ls = -0.3f)
            Spacer(Modifier.height(2.dp))
            Row(Modifier.height(27.dp)) {
                Text(amount.substringBefore(" RWF"), color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp, letterSpacing = (-0.2).sp, modifier = Modifier.alignByBaseline())
                Text(" RWF", color = p.mu, fontSize = 13.94.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, modifier = Modifier.padding(start = 3.dp).alignByBaseline())
            }
        }
        HomePill("Top up", onTopUp)
    }
}

/** 64dp list row: 44dp chip tile (16dp radius, 31dp art), 17sp title, 14sp caption, optional chevron. */
@Composable
internal fun HomeNavRow(art: String, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    val p = homePalette()
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(p.chip), contentAlignment = Alignment.Center) { ItundaArt(art, ItundaArtSet.Proto, 27.dp) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            HText(title, p.text, 17, FontWeight.SemiBold, lh = 24, ls = -0.2f)
            Spacer(Modifier.height(3.dp))
            HText(subtitle, p.mu, 14, FontWeight.Normal, lh = 21, ls = -0.2f)
        }
        if (onClick != null) ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = p.tt)
    }
}

/** Activity row; the earned note sits inline after the amount, exactly as in the prototype. */
@Composable
internal fun HomeActivityRow(art: String, title: String, subtitle: String, amount: String, earned: String?) {
    val p = homePalette()
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(p.chip), contentAlignment = Alignment.Center) { ItundaArt(art, ItundaArtSet.Proto, 27.dp) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            HText(title, p.text, 17, FontWeight.SemiBold, lh = 24, ls = -0.2f)
            Spacer(Modifier.height(3.dp))
            HText(subtitle, p.pk, 14, FontWeight.Normal, lh = 21, ls = -0.2f)
        }
        Spacer(Modifier.width(16.dp))
        Text(
            buildAnnotatedString {
                append(amount.substringBefore(" RWF"))
                withStyle(SpanStyle(color = p.mu, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) { append(" RWF") }
            },
            color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 19.6.sp,
        )
        if (earned != null) { Spacer(Modifier.width(4.dp)); HText(earned, p.text, 13, FontWeight.Bold, lh = 18) }
    }
}

@Composable
internal fun HomeSettingsButton(onClick: () -> Unit) {
    val p = homePalette()
    Box(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(p.setBg).clickable(onClick = onClick).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val u = size.width / 24f
                val st = Stroke(2.25f * u, cap = StrokeCap.Round)
                fun seg(x1: Float, y: Float, x2: Float) = drawLine(p.setFg, Offset(x1 * u, y * u), Offset(x2 * u, y * u), st.width, StrokeCap.Round)
                seg(4f, 7f, 13f); seg(17f, 7f, 20f); seg(4f, 17f, 7f); seg(11f, 17f, 20f)
                drawCircle(p.setFg, 2f * u, Offset(15f * u, 7f * u), style = st)
                drawCircle(p.setFg, 2f * u, Offset(9f * u, 17f * u), style = st)
            }
            Spacer(Modifier.width(8.dp))
            HText("Home settings", p.setFg, 16, FontWeight.Medium, lh = 22)
        }
    }
}
