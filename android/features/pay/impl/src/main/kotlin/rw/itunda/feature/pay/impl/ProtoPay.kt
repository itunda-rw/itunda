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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
    scanContent: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Ids.colors.surface).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(78.dp).padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IdsAppBarTitle(area = "pay", onClick = onOpenPay)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 40.dp, height = 32.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                Text("EN", color = Ids.colors.textTertiary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Outlined.Settings, "Settings", Modifier.size(28.dp).pressScaleClickable(onClick = onSettings), tint = Ids.colors.textTertiary)
        }
        Spacer(Modifier.height(8.dp))
        Segments(mode, onMode)
        Spacer(Modifier.height(16.dp))
        when (mode) {
            ProtoPayMode.MY_CODE -> MyCode(qrContent, secondsLeft, fundingName, fundingBalance, onReceive)
            else -> Box(Modifier.weight(1f).fillMaxWidth()) { scanContent() }
        }
        if (mode == ProtoPayMode.MY_CODE) Spacer(Modifier.weight(1f))
        FundingCard(fundingName, fundingBalance, onChangeFunding)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Segments(mode: ProtoPayMode, onMode: (ProtoPayMode) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(4.dp)) {
        ProtoPayMode.values().forEach { m ->
            val on = m == mode
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) Ids.colors.brand else Color.Transparent)
                    .pressScaleClickable { onMode(m) },
                contentAlignment = Alignment.Center,
            ) {
                Text(m.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.textPrimary else Ids.colors.textTertiary)
            }
        }
    }
}

@Composable
private fun MyCode(qrContent: String?, secondsLeft: Int, fundingName: String, fundingBalance: String, onReceive: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Ids.colors.surfaceSoft).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(204.dp).clip(RoundedCornerShape(24.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            if (qrContent != null) {
                val qr = remember(qrContent) { generatePayQrBitmap(qrContent) }
                Image(qr, "Your payment code", Modifier.size(196.dp), contentScale = ContentScale.Fit)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(Ids.colors.brand), contentAlignment = Alignment.Center) {
                Text("i", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        CountdownRing(secondsLeft)
        Spacer(Modifier.height(14.dp))
        Text("Pays from", color = Ids.colors.textTertiary, fontSize = 15.sp)
        Text(
            "$fundingName · $fundingBalance RWF", color = Ids.colors.textPrimary, fontSize = 17.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Receive money instead", color = Ids.colors.textBrand, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.pressScaleClickable(onClick = onReceive).padding(vertical = 8.dp),
    )
}

@Composable
private fun CountdownRing(secondsLeft: Int) {
    val brand = Ids.colors.brand
    val track = Ids.colors.brand.copy(alpha = 0.25f)
    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 3.dp.toPx()
            val inset = w / 2
            val arc = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            val frac = (secondsLeft / 60f).coerceIn(0f, 1f)
            drawArc(brand, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("$secondsLeft", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FundingCard(name: String, balance: String, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 16.dp).height(70.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.brand.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            ItundaArt("wallet", ItundaArtSet.Color, 28.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(name, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("$balance RWF · 3% back", color = Ids.colors.textTertiary, fontSize = 14.sp)
        }
        Text("Change", color = Ids.colors.textBrand, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressScaleClickable(onClick = onChange).padding(8.dp))
    }
}
