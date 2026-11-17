package rw.itunda.feature.eats.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EatsTrackingMap(statusText: String, complete: Boolean, cancelled: Boolean, modifier: Modifier = Modifier) {
    val palette = eatsPrototypePalette()
    Box(modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(28.dp)).background(palette.surfaceSoft)) {
        Canvas(Modifier.fillMaxSize().padding(bottom = 40.dp)) {
            val w = size.width
            val h = size.height
            val river = Path().apply {
                moveTo(0f, h * .57f)
                cubicTo(w * .20f, h * .44f, w * .31f, h * .72f, w * .52f, h * .59f)
                cubicTo(w * .72f, h * .46f, w * .83f, h * .34f, w, h * .43f)
            }
            drawPath(river, Color(0xFFD8F1E8), style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round))
            listOf(h * .20f, h * .79f, h * .04f, h * .95f).forEach { y ->
                drawLine(Color.White, Offset(0f, y), Offset(w, y), 8.dp.toPx())
            }
            listOf(w * .10f, w * .38f, w * .70f, w * .92f).forEach { x ->
                drawLine(Color.White, Offset(x, 0f), Offset(x, h), 8.dp.toPx())
            }
            val route = Path().apply {
                moveTo(w * .16f, h * .25f)
                cubicTo(w * .27f, h * .26f, w * .27f, h * .65f, w * .49f, h * .70f)
                cubicTo(w * .68f, h * .75f, w * .86f, h * .58f, w * .82f, h * .78f)
            }
            if (!cancelled) drawPath(route, Color(0xFF8F89FF), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 10.dp.toPx()))))
        }
        Box(Modifier.align(Alignment.TopStart).padding(start = 42.dp, top = 30.dp).size(36.dp)
            .clip(CircleShape).background(Color(0xFF191F28)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 42.dp, bottom = 66.dp).size(40.dp)
            .clip(CircleShape).background(if (complete) Color.White else Color(0xFF8F89FF)), contentAlignment = Alignment.Center) {
            if (complete) Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFFFFB321)))
            else Icon(Icons.Outlined.Home, contentDescription = null, tint = Color(0xFF191F28), modifier = Modifier.size(22.dp))
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(40.dp).background(palette.surfaceSoft).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (complete) Color(0xFF0B8F62) else if (cancelled) Color(0xFF9EA4AE) else Color(0xFF8F89FF)))
            Text(statusText, color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp), maxLines = 1)
        }
    }
}

@Composable
internal fun EatsTrackingStepper(labels: List<String>, activeStep: Int, modifier: Modifier = Modifier) {
    val palette = eatsPrototypePalette()
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, _ ->
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (index <= activeStep) Color(0xFF8F89FF) else palette.surfaceSoft))
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, label ->
                Text(label, color = if (index <= activeStep) palette.primary else palette.secondary, fontSize = 14.sp, lineHeight = 18.sp,
                    fontWeight = if (index <= activeStep) FontWeight.Bold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }
    }
}
