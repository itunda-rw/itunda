package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Real "My assets" teaser-card border (2026-08-27, Overview redesign, direct user
// reference: 3 real Toss "총자산" screenshots). Compose has no single built-in
// dashed-border parameter (unlike CSS's own `border: dashed` web already reuses
// as-is, or iOS's native StrokeStyle(dash:)) -- Modifier.border only takes a solid
// Brush, so a dashed look needs a manual drawBehind + PathEffect.dashPathEffect,
// same technique any other Compose codebase would reach for here. Shared here
// (not inlined into OverviewScreen.kt) since this is exactly the kind of reusable
// modifier this package already establishes the convention for -- see
// pressScaleClickable's own doc comment in IdsInteractions.kt.
fun Modifier.dashedBorder(color: Color, cornerRadius: Dp = 12.dp, strokeWidth: Dp = 1.dp, dashLength: Dp = 4.dp, gapLength: Dp = 3.dp): Modifier = composed {
    val pathEffect = remember(dashLength, gapLength) {
        PathEffect.dashPathEffect(floatArrayOf(dashLength.value, gapLength.value), 0f)
    }
    this
        .drawBehind {
            val strokeWidthPx = strokeWidth.toPx()
            drawRoundRect(
                color = color,
                cornerRadius = CornerRadius(cornerRadius.toPx()),
                style = Stroke(width = strokeWidthPx, pathEffect = pathEffect),
            )
        }
        .padding(1.dp)
}
