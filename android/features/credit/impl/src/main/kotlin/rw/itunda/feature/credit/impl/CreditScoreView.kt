package rw.itunda.feature.credit.impl

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

/** One bar under "What shapes your score". [fraction] is 0..1. */
data class ScoreFactor(val label: String, val fraction: Float)

/** Demo factors matching the prototype until the backend returns percentage-style factors. */
val demoScoreFactors = listOf(
    ScoreFactor("Payment history", 0.97f),
    ScoreFactor("Mobile money activity", 0.78f),
    ScoreFactor("Savings habits", 0.64f),
    ScoreFactor("Account age", 0.40f),
)

private fun band(score: Int) = when {
    score >= 740 -> "Excellent"
    score >= 670 -> "Good"
    score >= 580 -> "Fair"
    else -> "Building"
}

/** Prototype credit score body: arc gauge, band pill, factor bars, tip card. Scrolls with its parent. */
@Composable
fun CreditScoreBody(score: Int, factors: List<ScoreFactor>) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        ScoreGauge(score)
        Spacer(Modifier.height(8.dp))
        Text(
            band(score), color = Ids.colors.success, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Ids.colors.surfaceSoft).padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text("Range 300 to 850", color = Ids.colors.textTertiary, fontSize = 13.sp)
    }
    Spacer(Modifier.height(28.dp))
    Text("What shapes your score", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    factors.forEach { FactorBar(it) }
    Spacer(Modifier.height(28.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(20.dp)) {
        Text("Tip", color = Ids.colors.textTertiary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Paying bills on time through itunda lifts your score. Starting a savings goal helps too.",
            color = Ids.colors.textPrimary, fontSize = 16.sp, lineHeight = 24.sp,
        )
    }
    Spacer(Modifier.height(20.dp))
    Text("Free check using alternative data.", color = Ids.colors.textTertiary, fontSize = 14.sp)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ScoreGauge(score: Int) {
    val brand = Ids.colors.brand
    val track = Ids.colors.divider
    val fraction = ((score - 300) / 550f).coerceIn(0f, 1f)
    Box(Modifier.size(width = 220.dp, height = 130.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.size(width = 220.dp, height = 220.dp).align(Alignment.TopCenter)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 180f, 180f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(brand, 180f, 180f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(score.toString(), color = Ids.colors.textPrimary, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun FactorBar(factor: ScoreFactor) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(factor.label, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("${(factor.fraction * 100).toInt()}%", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ids.colors.divider)) {
            Box(Modifier.fillMaxWidth(factor.fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ids.colors.brand))
        }
    }
}
