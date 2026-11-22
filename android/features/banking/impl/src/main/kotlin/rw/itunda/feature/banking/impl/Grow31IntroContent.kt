package rw.itunda.feature.banking.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import java.util.Locale

private val dailyChoices = listOf(200L, 500L, 1_000L, 2_000L)

// Prototype v1038 "31-day savings" landing (measured: label y88, h1 30sp at y114, mu 15sp y198,
// 342x300 r28 illustration at y234, chips 40dp at y596, CTA 342x58 at y758). The ladder shows the
// backend's real tiers (Grow31SavingsService.bonusRateForStreak) via grow31BonusRateForStreak,
// not the prototype's demo numbers, so the screen never promises a bonus the backend won't pay.
@Composable
fun Grow31IntroContent(onBack: () -> Unit, onContinue: (dailyAmount: Long) -> Unit) {
    var daily by rememberSaveable { mutableLongStateOf(500L) }
    val ink = Ids.colors.textPrimary
    Column(Modifier.fillMaxSize()) {
        BankSubTopBar("", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(18.dp))
            Text("31-day savings", color = ink, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Save a little each\nday, earn up to 10%", color = ink, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text("Your bonus grows at day 7, 14, 21 and 31.", color = Ids.colors.textTertiary, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
            BonusIllustration()
            Text("How much each day?", color = ink, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 22.6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                dailyChoices.forEach { amount ->
                    val on = amount == daily
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(22.dp)).background(if (on) ink else Ids.colors.surfaceSoft)
                            .pressScaleClickable { daily = amount }.padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(String.format(Locale.US, "%,d", amount), fontSize = 15.sp, letterSpacing = (-0.15).sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.background else ink)
                    }
                }
            }
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append("$TERM_DAYS days × ${String.format(Locale.US, "%,d", daily)} = ")
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = ink, fontWeight = FontWeight.Bold))
                    append("${String.format(Locale.US, "%,d", daily * TERM_DAYS)} RWF")
                    pop()
                    append(" · itunda Bank balance 842,300 RWF")
                },
                color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 12.dp),
            )
        }
        BankSubCta("Next", top = 14.dp) { onContinue(daily) }
    }
}

@Composable
private fun BonusIllustration() {
    val cup = Color(0xFFB9B2F0)
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(28.dp)).background(Ids.colors.brand.copy(alpha = 0.16f))) {
        Canvas(Modifier.matchParentSize()) {
            val d = 1.dp.toPx()
            drawLine(Color(0xFFE0A63A), Offset(172 * d, 0f), Offset(172 * d, 40 * d), strokeWidth = 4 * d)
            drawOval(Color(0xFF6B4A2F), Offset(150 * d, 36 * d), Size(44 * d, 56 * d))
            val swirl = androidx.compose.ui.graphics.Path().apply {
                moveTo(172 * d, 44 * d); cubicTo(160 * d, 54 * d, 184 * d, 62 * d, 172 * d, 72 * d); cubicTo(166 * d, 77 * d, 172 * d, 82 * d, 172 * d, 86 * d)
            }
            drawPath(swirl, Color(0xFFE0A63A), style = Stroke(3 * d, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawLine(Color.White.copy(alpha = 0.5f), Offset(296 * d, 92 * d), Offset(296 * d, 186 * d), strokeWidth = 2 * d)
            drawRoundRect(cup, Offset(108 * d, 229 * d), Size(126 * d, 8 * d), androidx.compose.ui.geometry.CornerRadius(4 * d))
            drawArc(cup, 0f, 180f, false, Offset(124 * d, 237 * d - 2 * d), Size(92 * d, 100 * d), style = Stroke(4 * d))
            drawArc(cup, -90f, 180f, false, Offset(204 * d, 244 * d), Size(30 * d, 34 * d), style = Stroke(4 * d))
        }
        Box(Modifier.offset(14.dp, 14.dp).size(128.dp, 70.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(12.dp)) {
            Text("Reach the cup for the full 10% bonus", color = Ids.colors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
        }
        listOf(7 to 92, 14 to 119, 21 to 147, 31 to 186).forEach { (day, cy) ->
            Text(
                "Day $day · ${grow31BonusRateForStreak(day).toInt()}%", color = Ids.colors.textTertiary, fontSize = 11.sp, lineHeight = 15.4.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.align(Alignment.TopEnd).offset((-58).dp, (cy - 8).dp).width(80.dp),
            )
            Box(Modifier.offset(290.dp, (cy - 6).dp).size(12.dp).clip(CircleShape).background(Color.White))
        }
    }
}
