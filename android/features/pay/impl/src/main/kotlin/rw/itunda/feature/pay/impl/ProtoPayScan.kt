package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable

private val ScanBg = Color(0xFF0A0A0E)
private val ScanSub = Color(0xFFA0A4AE)

/**
 * Prototype v1038 Scan view (always dark), positions measured in dp from the 628dp camera
 * area: heading, 280dp viewfinder with 46dp corner brackets and a scan line, the
 * Flash / Till number / Photos pills, and the recents row. [scanContent] (the real
 * pay-a-merchant section) lives inside the viewfinder so nothing real is lost.
 */
@Composable
internal fun ProtoPayScan(
    p: PayPalette, onTill: () -> Unit, onMerchant: (String) -> Unit, scanContent: @Composable (Boolean, ((() -> Unit) -> Unit), (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var flashEnabled by remember { mutableStateOf(false) }
    var launchPhoto by remember { mutableStateOf<(() -> Unit)?>(null) }
    var flashAvailable by remember { mutableStateOf<Boolean?>(null) }
    Box(modifier.fillMaxSize().background(ScanBg)) {
        Column(Modifier.fillMaxWidth().offset(y = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Scan a QR code to pay", color = Color.White, fontSize = 24.sp, lineHeight = 31.2.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            Text("Shops and friends both work.", color = ScanSub, fontSize = 14.sp, lineHeight = 19.6.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Box(Modifier.offset(x = 55.dp, y = 123.2.dp).size(280.dp).drawBehind { drawFrame(p) }) { scanContent(flashEnabled, { launchPhoto = it }, { flashAvailable = it }) }
        Row(
            Modifier.align(Alignment.TopCenter).offset(y = 423.2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScanPill(if (flashAvailable == false) "No flash" else if (flashEnabled) "Flash on" else "Flash", 85.5.dp, p, if (flashAvailable == false) null else { { flashEnabled = !flashEnabled } })
            ScanPill("Till number", 124.8.dp, p, onTill)
            ScanPill("Photos", 96.3.dp, p, { launchPhoto?.invoke() })
        }
        Row(
            Modifier.offset(y = 568.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScanChip("Amina K.", 91.dp, true, p, onMerchant)
            ScanChip("Kigali Bakery", 88.6.dp, false, p, onMerchant)
            ScanChip("Nyamirambo Coffee House", 167.dp, false, p, onMerchant)
            ScanChip("Simba Supermarket", 126.1.dp, false, p, onMerchant)
        }
    }
}

/** Line glyphs for the three pills, 22dp box, 1.8dp round strokes (bolt / pencil / photo). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPillGlyph(label: String) {
    val u = size.width / 22f
    val st = Stroke(1.8f * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    val path = Path()
    when (label) {
        "Flash" -> { path.moveTo(12.5f * u, 3f * u); path.lineTo(5f * u, 12.5f * u); path.lineTo(10.5f * u, 12.5f * u); path.lineTo(9.5f * u, 19f * u); path.lineTo(17f * u, 9.5f * u); path.lineTo(11.5f * u, 9.5f * u); path.close() }
        "Till number" -> { path.moveTo(4f * u, 18f * u); path.lineTo(4.8f * u, 14f * u); path.lineTo(14.5f * u, 4.3f * u); path.lineTo(17.7f * u, 7.5f * u); path.lineTo(8f * u, 17.2f * u); path.close(); path.moveTo(12.6f * u, 6.2f * u); path.lineTo(15.8f * u, 9.4f * u) }
        else -> {
            path.addRoundRect(androidx.compose.ui.geometry.RoundRect(3f * u, 4f * u, 19f * u, 18f * u, CornerRadius(3f * u)))
            path.moveTo(3.5f * u, 15f * u); path.lineTo(8.5f * u, 10.5f * u); path.lineTo(13f * u, 14.5f * u); path.lineTo(15.5f * u, 12.5f * u); path.lineTo(18.5f * u, 15.5f * u)
            drawCircle(Color.White, 1.4f * u, Offset(14.5f * u, 8.5f * u), style = st)
        }
    }
    drawPath(path, Color.White, style = st)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrame(p: PayPalette) {
    val w = 280.dp.toPx(); val r = 18.dp.toPx(); val len = 46.dp.toPx(); val t = 4.dp.toPx()
    val stroke = Stroke(t, cap = StrokeCap.Butt)
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        val path = Path().apply {
            moveTo(x, y + dy * len); lineTo(x, y + dy * r)
            quadraticBezierTo(x, y, x + dx * r, y); lineTo(x + dx * len, y)
        }
        drawPath(path, Color.White, style = stroke)
    }
    val h = t / 2
    corner(h, h, 1f, 1f); corner(w - h, h, -1f, 1f); corner(h, w - h, 1f, -1f); corner(w - h, w - h, -1f, -1f)
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, p.brand.copy(alpha = 0.9f), Color.Transparent)),
        topLeft = Offset(0f, w / 2 - 1.dp.toPx()), size = Size(w, 2.dp.toPx()),
    )
}

@Composable
private fun ScanPill(label: String, width: Dp, p: PayPalette, onClick: (() -> Unit)?) {
    Row(
        Modifier.width(width).height(44.dp).clip(RoundedCornerShape(22.dp)).background(p.scanChip)
            .then(if (onClick != null) Modifier.pressScaleClickable(onClick = onClick) else Modifier)
            .padding(start = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).drawBehind { drawPillGlyph(label) })
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.14).sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun ScanChip(name: String, width: Dp, strong: Boolean, p: PayPalette, onMerchant: (String) -> Unit) {
    Box(
        Modifier.width(width).height(44.dp).clip(RoundedCornerShape(22.dp)).background(p.scanChip).pressScaleClickable { onMerchant(name) },
        contentAlignment = Alignment.Center,
    ) {
        if (!strong) Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(3.3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
        Text(
            name, maxLines = 1, textAlign = TextAlign.Center,
            color = if (strong) Color.White else Color.White.copy(alpha = 0.62f),
            fontSize = if (strong) 14.sp else 12.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
