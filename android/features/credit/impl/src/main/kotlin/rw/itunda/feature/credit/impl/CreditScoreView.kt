package rw.itunda.feature.credit.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/** Prototype credit score body: arc gauge counting up from 300, band pill, factor bars, tip card. Scrolls with its parent. */
@Composable
fun CreditScoreBody(score: Int, factors: List<ScoreFactor>) {
    val shown = remember { Animatable(300f) }
    LaunchedEffect(score) { shown.animateTo(score.toFloat(), tween(1100)) }
    val green = Color(0xFF08794D)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(width = 240.dp, height = 139.2.dp)) {
            ScoreArc(((shown.value - 300f) / 550f).coerceIn(0f, 1f))
            Text(
                shown.value.toInt().toString(), color = Ids.colors.textPrimary, fontSize = 56.sp, lineHeight = 60.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 86.6.dp),
            )
        }
        Text(
            band(score), color = green, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp,
            modifier = Modifier.padding(top = 17.4.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE1EFE9)).padding(horizontal = 16.dp, vertical = 5.dp),
        )
        Text("Range 300 to 850", color = Ids.colors.textTertiary, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
    }
    Text("What shapes your score", color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(top = 30.dp, bottom = 12.dp))
    factors.forEach { FactorBar(it) }
    Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFEEEDFF)).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text("Tip", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Paying bills on time through itunda lifts your score. Starting a savings goal helps too.",
            color = Ids.colors.textPrimary, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp,
        )
    }
    Text("Free check using alternative data.", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
}

/** 240x139.2 gauge: 15dp round-cap semicircle centred at (120,120), radius 96, like the prototype SVG. */
@Composable
private fun ScoreArc(fraction: Float) {
    val brand = Ids.colors.brand
    val track = Ids.colors.surfaceSoft
    Canvas(Modifier.size(width = 240.dp, height = 139.2.dp)) {
        val stroke = 15.dp.toPx()
        val r = 96.dp.toPx()
        val tl = Offset(120.dp.toPx() - r, 120.dp.toPx() - r)
        val sz = Size(r * 2, r * 2)
        drawArc(track, 180f, 180f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(brand, 180f, 180f * fraction, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun FactorBar(factor: ScoreFactor) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(factor.label, color = Ids.colors.textPrimary, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
            Text("${(factor.fraction * 100).toInt()}%", color = Ids.colors.textPrimary, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)).background(Ids.colors.surfaceSoft)) {
            Box(Modifier.fillMaxWidth(factor.fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(4.dp)).background(Ids.colors.brand))
        }
    }
}
