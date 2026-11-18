package rw.itunda.feature.pay.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable

private val Pink = Color(0xFFE6397D)
private val Orange = Color(0xFFF2994A)

/**
 * The 390x210 map from prototype `payhub` (SVG viewBox 390x210, drawn 1:1 in dp): two road
 * weights, a green park, the pink line through "Nyabugogo", four orange store pins and the
 * brand-coloured "you are here" dot, with the "128 earning stores nearby" pill on top.
 */
@Composable
internal fun PayHubMap(dark: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (dark) Color(0xFF16171D) else Color(0xFFE9EDF2)
    val major = if (dark) Color(0xFF23252D) else Color.White
    val minor = if (dark) Color(0xFF2C2E37) else Color(0xFFD5DBE3)
    val label = if (dark) Color(0xFFF9FAFB) else Color(0xFF191F28)
    val page = if (dark) Color(0xFF17171C) else Color.White
    Box(modifier.fillMaxWidth().height(210.dp).background(bg)) {
        Canvas(Modifier.fillMaxWidth().height(210.dp)) {
            fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, c: Color) =
                drawLine(c, Offset(x1.dp.toPx(), y1.dp.toPx()), Offset(x2.dp.toPx(), y2.dp.toPx()), w.dp.toPx(), StrokeCap.Round)
            line(-10f, 70f, 400f, 30f, 14f, major); line(-10f, 160f, 400f, 128f, 10f, major)
            line(95f, -10f, 135f, 220f, 12f, major); line(285f, -10f, 252f, 220f, 9f, major)
            line(-10f, 112f, 400f, 88f, 4f, minor); line(190f, -10f, 205f, 220f, 4f, minor)
            line(340f, -10f, 358f, 220f, 4f, minor); line(-10f, 195f, 400f, 176f, 4f, minor)
            val park = Path().apply {
                moveTo(300.dp.toPx(), -10.dp.toPx()); lineTo(400.dp.toPx(), -10.dp.toPx()); lineTo(400.dp.toPx(), 110.dp.toPx())
                lineTo(330.dp.toPx(), 120.dp.toPx()); lineTo(290.dp.toPx(), 80.dp.toPx()); close()
            }
            drawPath(park, Color(0xFF2DB873).copy(alpha = 0.22f))
            val route = Path().apply {
                moveTo(-10.dp.toPx(), 40.dp.toPx()); lineTo(120.dp.toPx(), 52.dp.toPx()); lineTo(160.dp.toPx(), 110.dp.toPx()); lineTo(160.dp.toPx(), 220.dp.toPx())
            }
            drawPath(route, Pink, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(page, 9.dp.toPx(), Offset(120.dp.toPx(), 52.dp.toPx()))
            drawCircle(Pink, 9.dp.toPx(), Offset(120.dp.toPx(), 52.dp.toPx()), style = Stroke(4.dp.toPx()))
            listOf(228f to 74f, 60f to 112f, 300f to 150f, 168f to 150f).forEach { (x, y) ->
                drawCircle(Orange, 10.dp.toPx(), Offset(x.dp.toPx(), y.dp.toPx())); drawCircle(Color.White, 3.5.dp.toPx(), Offset(x.dp.toPx(), y.dp.toPx()))
            }
            drawCircle(Color(0xFF5A51EC).copy(alpha = 0.2f), 16.dp.toPx(), Offset(215.dp.toPx(), 112.dp.toPx()))
            drawCircle(Color.White, 8.5.dp.toPx(), Offset(215.dp.toPx(), 112.dp.toPx()))
            drawCircle(Color(0xFF5A51EC), 7.dp.toPx(), Offset(215.dp.toPx(), 112.dp.toPx()))
            mapLabel("Nyabugogo", 120f, 38f, label); mapLabel("Heaven", 228f, 98f, label); mapLabel("Bourbon", 60f, 136f, label)
            mapLabel("Simba", 300f, 174f, label); mapLabel("Sawa Citi", 168f, 174f, label)
        }
        Row(
            Modifier.offset(x = 75.8.dp, y = 90.dp).width(238.4.dp).height(46.dp)
                .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.14f))
                .clip(RoundedCornerShape(24.dp)).background(if (dark) Color(0xFF191F28) else Color.White).pressScaleClickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.padding(start = 24.dp).size(18.dp)) {
                val u = size.width / 24f
                val p = Path().apply { moveTo(20.4f * u, 3.6f * u); lineTo(4f * u, 9.4f * u); lineTo(10.2f * u, 13.9f * u); lineTo(12.6f * u, 20.1f * u); close() }
                drawPath(p, if (dark) Color(0xFFF9FAFB) else Color(0xFF191F28))
            }
            Text("128 earning stores nearby", color = label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.18).sp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

private fun DrawScope.mapLabel(text: String, x: Float, y: Float, color: Color) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true; this.color = color.toArgb(); textSize = 11.dp.toPx(); textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawText(text, x.dp.toPx(), y.dp.toPx(), paint)
}
