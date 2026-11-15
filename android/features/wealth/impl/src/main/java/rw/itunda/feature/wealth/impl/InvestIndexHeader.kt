package rw.itunda.feature.wealth.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

private val periods = listOf("1D", "1W", "1M", "3M", "1Y")

// Prototype demo curve for the RSE All Share Index until market data is wired to the backend.
private val demoCurve = listOf(
    58, 46, 52, 66, 60, 50, 54, 62, 70, 76, 74, 68, 62, 70, 66, 60, 72, 64, 52, 46, 44, 50, 58, 64, 72, 70,
    60, 48, 40, 34, 28, 36, 52, 66, 78, 86, 82, 90, 96, 88, 84, 92, 100,
).map { it.toFloat() }

/** Prototype Invest index block: label, level + today's change, area chart and a period selector (demo data). */
@Composable
fun InvestIndexHeader() {
    var period by rememberSaveable { mutableIntStateOf(0) }
    val green = Color(0xFF0A8A55)
    Column(Modifier.fillMaxWidth()) {
        Text("RSE All Share Index", color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("169.48", color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("  ▲ +0.6% today", color = green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(8.dp)).background(green.copy(alpha = 0.08f))) {
            val max = demoCurve.max(); val min = demoCurve.min()
            val step = size.width / (demoCurve.size - 1)
            val pts = demoCurve.mapIndexed { i, v -> Offset(i * step, size.height - 8.dp.toPx() - (v - min) / (max - min) * (size.height - 16.dp.toPx())) }
            val line = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(line, green, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            periods.forEachIndexed { i, label ->
                val on = i == period
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) Ids.colors.textPrimary else Color.Transparent)
                        .pressScaleClickable { period = i }.padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.background else Ids.colors.textTertiary) }
            }
        }
    }
}

/** Prototype "Start investing" empty state under the index chart. */
@Composable
fun InvestStartCard(onBrowse: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(width = 18.dp, height = 30.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFFFC53D)))
            Box(Modifier.size(width = 18.dp, height = 52.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF34B68D)))
            Box(Modifier.size(width = 18.dp, height = 76.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF8F89FF)))
        }
        Spacer(Modifier.height(16.dp))
        Text("Start investing", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Buy shares of Rwandan companies, lend to the Government with Treasury bills, or apply for an IPO. You can start with a small amount.",
            color = Ids.colors.textTertiary, fontSize = 15.sp, lineHeight = 22.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand).pressScaleClickable(onClick = onBrowse),
            contentAlignment = Alignment.Center,
        ) { Text("Browse stocks", color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
    }
}
