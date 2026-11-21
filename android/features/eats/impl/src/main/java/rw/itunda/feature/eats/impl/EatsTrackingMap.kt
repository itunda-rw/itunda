package rw.itunda.feature.eats.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EatsTrackingMap(
    statusText: String,
    step: Int,
    pickup: Boolean,
    complete: Boolean,
    cancelled: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = eatsPrototypePalette()
    val liveDeliveryMap = !pickup && !cancelled
    val viewBoxHeight = if (liveDeliveryMap) 176f else 190f
    val pulse = rememberInfiniteTransition(label = "eats-tracking-pulse")
    val pulseRadius by pulse.animateFloat(
        initialValue = 12f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(tween(1400), repeatMode = RepeatMode.Restart),
        label = "eats-tracking-pulse-radius",
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = .8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400), repeatMode = RepeatMode.Restart),
        label = "eats-tracking-pulse-alpha",
    )
    val riderProgress = remember { Animatable(0f) }
    LaunchedEffect(step, liveDeliveryMap) {
        if (liveDeliveryMap && step == 2) {
            riderProgress.snapTo(0f)
            riderProgress.animateTo(1f, animationSpec = tween(5000))
        } else {
            riderProgress.snapTo(0f)
        }
    }

    Box(
        modifier.fillMaxWidth()
            .height(if (liveDeliveryMap) 230.dp else 190.dp)
            .clip(RoundedCornerShape(if (liveDeliveryMap) 28.dp else 20.dp))
            .background(palette.surfaceSoft),
    ) {
        Canvas(
            Modifier.padding(if (liveDeliveryMap) 1.dp else 0.dp)
                .fillMaxWidth()
                .aspectRatio(342f / viewBoxHeight)
                .clip(RoundedCornerShape(20.dp))
                .align(Alignment.TopStart),
        ) {
            val scaleX = size.width / 342f
            val scaleY = size.height / viewBoxHeight
            scale(scaleX, scaleY, pivot = Offset.Zero) {
                if (liveDeliveryMap) {
                    drawLiveDeliveryMap(
                        palette = palette,
                        step = step,
                        pulseRadius = pulseRadius,
                        pulseAlpha = pulseAlpha,
                        riderProgress = riderProgress.value,
                    )
                } else {
                    drawStaticTrackingMap(palette = palette, pickup = pickup)
                }
            }
        }
        if (liveDeliveryMap) {
            Row(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = 1.dp, end = 1.dp, bottom = 1.dp)
                    .fillMaxWidth()
                    .height(40.6.dp)
                    .background(palette.surfaceSoft)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (complete) Color(0xFF0B8F62) else palette.brandFill),
                )
                Text(
                    statusText,
                    color = palette.primary,
                    fontSize = 14.sp,
                    lineHeight = 19.6.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private fun DrawScope.drawLiveDeliveryMap(
    palette: EatsPrototypePalette,
    step: Int,
    pulseRadius: Float,
    pulseAlpha: Float,
    riderProgress: Float,
) {
    drawRect(palette.surfaceSoft, size = Size(342f, 176f))

    val river = Path().apply {
        moveTo(-10f, 78f)
        cubicTo(60f, 60f, 120f, 96f, 200f, 70f)
        cubicTo(280f, 44f, 320f, 40f, 360f, 60f)
    }
    drawPath(river, Color(0xFFBFEAD7).copy(alpha = .55f), style = Stroke(width = 22f))

    val roads = Path().apply {
        moveTo(0f, 20f); lineTo(342f, 20f)
        moveTo(0f, 150f); lineTo(342f, 150f)
        moveTo(30f, 0f); lineTo(30f, 176f)
        moveTo(118f, 0f); lineTo(118f, 176f)
        moveTo(226f, 0f); lineTo(226f, 176f)
        moveTo(318f, 0f); lineTo(318f, 176f)
    }
    drawPath(roads, palette.background, style = Stroke(width = 9f, cap = StrokeCap.Round))

    val route = liveRoutePath()
    drawPath(
        route,
        palette.accent,
        style = Stroke(
            width = 4f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 8f)),
        ),
    )

    drawCircle(Color(0xFF17171C), 12f, Offset(52f, 42f))
    val bag = Path().apply {
        moveTo(47f, 40f); lineTo(57f, 40f); lineTo(56f, 48f); lineTo(48f, 48f); close()
        moveTo(48f, 40f); lineTo(49f, 36f); lineTo(57f, 36f); lineTo(58f, 40f)
    }
    drawPath(bag, Color.White, style = Stroke(width = 1.8f, join = StrokeJoin.Round))

    if (step == 1) {
        drawCircle(
            palette.accent.copy(alpha = pulseAlpha),
            pulseRadius,
            Offset(52f, 42f),
            style = Stroke(width = 3f),
        )
    }

    drawCircle(palette.accent, 12f, Offset(292f, 134f))
    val home = Path().apply {
        moveTo(286f, 134f); lineTo(292f, 128f); lineTo(298f, 134f)
        lineTo(298f, 140f); lineTo(286f, 140f); close()
    }
    drawPath(home, Color(0xFF17171C), style = Stroke(width = 2f, join = StrokeJoin.Round))

    when {
        step == 2 -> {
            val point = liveRoutePoint(riderProgress)
            drawCircle(Color(0xFF17171C), 10f, point)
            drawCircle(Color.White, 10f, point, style = Stroke(width = 3f))
            drawCircle(Color(0xFFFFCC4D), 3.5f, point)
        }
        step >= 3 -> {
            val destination = Offset(292f, 134f)
            drawCircle(Color(0xFF17171C), 10f, destination)
            drawCircle(Color.White, 10f, destination, style = Stroke(width = 3f))
            drawCircle(Color(0xFFFFCC4D), 3.5f, destination)
        }
    }
}

private fun DrawScope.drawStaticTrackingMap(palette: EatsPrototypePalette, pickup: Boolean) {
    drawRect(palette.surfaceSoft, size = Size(342f, 190f))
    val roads = Path().apply {
        moveTo(0f, 62f); lineTo(342f, 40f)
        moveTo(0f, 132f); lineTo(342f, 152f)
        moveTo(92f, 0f); lineTo(112f, 190f)
        moveTo(232f, 0f); lineTo(212f, 190f)
    }
    drawPath(roads, palette.background.copy(alpha = .85f), style = Stroke(width = 12f))

    val route = Path().apply {
        moveTo(60f, 150f)
        cubicTo(120f, 150f, 130f, 76f, 190f, 76f)
        cubicTo(250f, 76f, 270f, 62f, 290f, 40f)
    }
    drawPath(
        route,
        palette.accent,
        style = Stroke(
            width = 5f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 9f)),
        ),
    )

    drawCircle(palette.accent, 15f, Offset(60f, 150f))
    val bag = Path().apply {
        moveTo(53f, 146f); lineTo(67f, 146f); lineTo(66f, 162f); lineTo(54f, 162f); close()
        moveTo(55f, 146f); cubicTo(55f, 138f, 65f, 138f, 65f, 146f)
    }
    drawPath(bag, Color(0xFF17171C), style = Stroke(width = 2f, join = StrokeJoin.Round))

    drawCircle(palette.primary, 15f, Offset(290f, 40f))
    val home = Path().apply {
        moveTo(280f, 40f); lineTo(290f, 30f); lineTo(300f, 40f)
        lineTo(300f, 50f); lineTo(280f, 50f); close()
    }
    drawPath(home, palette.background, style = Stroke(width = 2f, join = StrokeJoin.Round))

    if (!pickup) {
        val rider = Offset(60f, 150f)
        drawCircle(Color.White, 9f, rider)
        drawCircle(palette.primary, 9f, rider, style = Stroke(width = 3f))
    }
}

private fun liveRoutePath() = Path().apply {
    moveTo(52f, 42f)
    cubicTo(118f, 42f, 104f, 124f, 178f, 118f)
    cubicTo(252f, 112f, 292f, 96f, 292f, 134f)
}

private fun liveRoutePoint(progress: Float): Offset {
    val t = progress.coerceIn(0f, 1f)
    return if (t <= .5f) {
        cubicPoint(Offset(52f, 42f), Offset(118f, 42f), Offset(104f, 124f), Offset(178f, 118f), t * 2f)
    } else {
        cubicPoint(Offset(178f, 118f), Offset(252f, 112f), Offset(292f, 96f), Offset(292f, 134f), (t - .5f) * 2f)
    }
}

private fun cubicPoint(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val u = 1f - t
    val a = u * u * u
    val b = 3f * u * u * t
    val c = 3f * u * t * t
    val d = t * t * t
    return Offset(
        a * p0.x + b * p1.x + c * p2.x + d * p3.x,
        a * p0.y + b * p1.y + c * p2.y + d * p3.y,
    )
}

@Composable
internal fun EatsTrackingStepper(labels: List<String>, activeStep: Int, modifier: Modifier = Modifier) {
    val palette = eatsPrototypePalette()
    Column(modifier.fillMaxWidth().padding(bottom = 15.dp)) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, _ ->
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (index <= activeStep) palette.brandFill else palette.surfaceSoft))
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, label ->
                Text(
                    label,
                    color = if (index <= activeStep) palette.primary else palette.secondary,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (index == activeStep) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
