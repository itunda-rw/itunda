package rw.itunda.feature.shop.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Prototype scan header: title + subtitle above the viewfinder. */
@Composable
internal fun ScanHeading() {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Scan a QR code to pay", color = Ids.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text("Shops and friends both work.", color = Ids.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Real camera (passed in) with the prototype's white corner brackets and brand scan line. */
@Composable
internal fun ScanFrame(camera: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) { camera() }
        val brand = Ids.colors.brand
        Canvas(Modifier.fillMaxSize()) {
            val arm = 46.dp.toPx(); val r = 18.dp.toPx(); val w = 4.dp.toPx(); val inset = 2.dp.toPx()
            val l = inset; val t = inset; val rt = size.width - inset; val b = size.height - inset
            fun corner(cx: Float, cy: Float, sx: Float, sy: Float) {
                val path = Path().apply {
                    moveTo(cx + sx * arm, cy); lineTo(cx + sx * r, cy)
                    quadraticBezierTo(cx, cy, cx, cy + sy * r); lineTo(cx, cy + sy * arm)
                }
                drawPath(path, Color.White, style = Stroke(w, cap = StrokeCap.Round))
            }
            corner(l, t, 1f, 1f); corner(rt, t, -1f, 1f); corner(l, b, 1f, -1f); corner(rt, b, -1f, -1f)
            val y = size.height / 2
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, brand, Color.Transparent)), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        }
    }
}

/** Dark rounded pills under the viewfinder; each one is a real alternate pay flow. */
@Composable
internal fun ScanPills(items: List<Pair<String, () -> Unit>>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        items.forEach { (label, onClick) ->
            Text(
                label, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Ids.colors.surfaceSoft).pressScaleClickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}
