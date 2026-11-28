package rw.itunda.feature.home.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The prototype's header "Pay" scan glyph: four corner brackets around the word Pay (24-unit viewBox). */
@Composable
internal fun PayScanGlyph(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val u = this.size.width / 24f
            fun o(x: Float, y: Float) = Offset(x * u, y * u)
            val p = Path().apply {
                moveTo(3.5f * u, 8f * u); lineTo(3.5f * u, 6.5f * u)
                arcTo(androidx.compose.ui.geometry.Rect(o(3.5f, 3.5f), o(9.5f, 9.5f)), 180f, 90f, false); lineTo(8f * u, 3.5f * u)
                moveTo(16f * u, 3.5f * u); lineTo(17.5f * u, 3.5f * u)
                arcTo(androidx.compose.ui.geometry.Rect(o(14.5f, 3.5f), o(20.5f, 9.5f)), 270f, 90f, false); lineTo(20.5f * u, 8f * u)
                moveTo(20.5f * u, 16f * u); lineTo(20.5f * u, 17.5f * u)
                arcTo(androidx.compose.ui.geometry.Rect(o(14.5f, 14.5f), o(20.5f, 20.5f)), 0f, 90f, false); lineTo(16f * u, 20.5f * u)
                moveTo(8f * u, 20.5f * u); lineTo(6.5f * u, 20.5f * u)
                arcTo(androidx.compose.ui.geometry.Rect(o(3.5f, 14.5f), o(9.5f, 20.5f)), 90f, 90f, false); lineTo(3.5f * u, 16f * u)
            }
            drawPath(p, tint, style = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Text("Pay", color = tint, fontSize = (size.value * 0.31f).sp, fontWeight = FontWeight.ExtraBold)
    }
}
