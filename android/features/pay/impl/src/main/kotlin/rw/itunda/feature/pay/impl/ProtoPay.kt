package rw.itunda.feature.pay.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsAppBarTitle
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.generatePayQrBitmap
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTheme

enum class ProtoPayMode(val label: String) { SCAN("Scan"), MY_CODE("My code"), TAP("Tap") }

/** Stateless Pay tab: header, Scan | My code | Tap segment, QR card, funding-source card. */
@Composable
fun ProtoPayContent(
    mode: ProtoPayMode,
    onMode: (ProtoPayMode) -> Unit,
    qrContent: String?,
    secondsLeft: Int,
    fundingName: String,
    fundingBalance: String,
    onOpenPay: () -> Unit,
    onSettings: () -> Unit,
    onChangeFunding: () -> Unit,
    onReceive: () -> Unit,
    scanContent: @Composable (Boolean, ((() -> Unit) -> Unit), (Boolean) -> Unit) -> Unit,
    onTill: () -> Unit = {},
    onMerchant: (String) -> Unit = {},
    onTopUp: () -> Unit = {},
) {
    val scan = mode == ProtoPayMode.SCAN
    val p = payPalette(scan, MaterialTheme.colorScheme.background.luminance() < 0.5f)
    // Scan is the prototype's one dark screen: force the dark token set for it only.
    IdsTheme(darkTheme = scan || MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        androidx.compose.material3.ProvideTextStyle(
            androidx.compose.ui.text.TextStyle(
                fontFamily = rw.itunda.core.designsystem.theme.PretendardFontFamily,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None),
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
            ),
        ) {
            Column(Modifier.fillMaxSize().background(p.page)) {
                PayHeader(p, onOpenPay, onSettings)
                Spacer(Modifier.height(20.dp))
                Segments(p, mode, onMode)
                Spacer(Modifier.height(if (scan) 0.dp else if (mode == ProtoPayMode.TAP) 21.dp else 16.dp))
                when (mode) {
                    ProtoPayMode.MY_CODE -> Column(Modifier.padding(horizontal = 20.dp)) { MyCode(p, qrContent, secondsLeft, fundingName, fundingBalance, onReceive) }
                    ProtoPayMode.TAP -> Column(Modifier.padding(horizontal = 20.dp)) { ProtoPayTap(p, secondsLeft, fundingName, fundingBalance, onTopUp) }
                    ProtoPayMode.SCAN -> Unit
                }
                if (scan) {
                    Spacer(Modifier.height(0.dp))
                    ProtoPayScan(p, onTill, onMerchant, scanContent, Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                    FundingCard(p, fundingName, fundingBalance, onChangeFunding)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

/** Title button (24,24 · 167x44), EN pill (40x32) and 44dp settings button, measured at 390dp. */
@Composable
private fun PayHeader(p: PayPalette, onOpenPay: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.padding(start = 24.dp).height(44.dp).pressScaleClickable(onClick = onOpenPay),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(rw.itunda.core.designsystem.R.drawable.itunda_mark), null, Modifier.size(24.dp), tint = p.title)
            Spacer(Modifier.width(2.dp))
            Text("itunda pay", color = p.title, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
            Icon(rw.itunda.core.designsystem.theme.IdsIcons.ChevronRight, null, Modifier.size(18.dp), tint = p.title)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(width = 40.dp, height = 32.dp).clip(RoundedCornerShape(16.dp)).background(if (p.page == Color.Black) p.soft else p.soft), contentAlignment = Alignment.Center) {
            Text("EN", color = p.en, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp)
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.padding(end = 18.dp).size(44.dp).pressScaleClickable(onClick = onSettings), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Settings, "Settings", Modifier.size(30.dp), tint = p.gear)
        }
    }
}

@Composable
private fun Segments(p: PayPalette, mode: ProtoPayMode, onMode: (ProtoPayMode) -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(p.soft).padding(4.dp)) {
        ProtoPayMode.values().forEach { m ->
            val on = m == mode
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (on) p.brand else Color.Transparent)
                    .pressScaleClickable { onMode(m) },
                contentAlignment = Alignment.Center,
            ) {
                Text(m.label, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = if (on) Color(0xFF17171C) else p.segOff)
            }
        }
    }
}

@Composable
private fun MyCode(p: PayPalette, qrContent: String?, secondsLeft: Int, fundingName: String, fundingBalance: String, onReceive: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.soft).padding(top = 20.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(204.dp).clip(RoundedCornerShape(20.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            if (qrContent != null) {
                val qr = remember(qrContent) { generatePayQrBitmap(qrContent, margin = 0) }
                Image(qr, "Your payment code", Modifier.size(176.dp), contentScale = ContentScale.Fit)
            }
            Box(Modifier.size(52.8.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF7472F4)), contentAlignment = Alignment.Center) {
                    Text("i", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        CountdownRing(p, secondsLeft)
        Spacer(Modifier.height(14.dp))
        Text("Pays from", color = p.tt, fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.Medium)
        Text(
            "$fundingName · $fundingBalance RWF", color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp,
            fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
        )
    }
    Box(Modifier.heightIn(min = 44.dp).pressScaleClickable(onClick = onReceive).padding(top = 12.dp), contentAlignment = Alignment.TopStart) {
        Text("Receive money instead", color = p.brandText, fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CountdownRing(p: PayPalette, secondsLeft: Int) {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(38.dp)) {
            val w = 3.dp.toPx()
            val inset = w / 2
            val arc = Size(size.width - w, size.height - w)
            drawArc(p.ring, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            val frac = (secondsLeft / 45f).coerceIn(0f, 1f)
            drawArc(p.brand, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("$secondsLeft", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun FundingCard(p: PayPalette, name: String, balance: String, onChange: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(70.6.dp).clip(RoundedCornerShape(20.dp)).background(p.soft).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(p.tint), contentAlignment = Alignment.Center) {
            Icon(painterResource(rw.itunda.core.designsystem.R.drawable.itunda_mark), null, Modifier.size(24.dp), tint = p.tintFg)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(name, color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold)
            Text("$balance RWF · 3% back", color = p.tt, fontSize = 13.sp, lineHeight = 18.2.sp)
        }
        Text("Change", color = p.brandText, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressScaleClickable(onClick = onChange).padding(horizontal = 8.dp, vertical = 12.dp))
    }
}
