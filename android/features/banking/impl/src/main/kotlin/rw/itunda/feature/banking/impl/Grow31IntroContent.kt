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
import rw.itunda.core.designsystem.components.FixedBottomCta
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import java.util.Locale

private val dailyChoices = listOf(200L, 500L, 1_000L, 2_000L)

// Prototype v1038 "31-day savings" landing: headline, bonus-ladder illustration (coffee bean
// dropping toward the cup) and a daily-amount chip row. The ladder shows the backend's real tiers
// (Grow31SavingsService.bonusRateForStreak) via grow31BonusRateForStreak, not invented numbers.
@Composable
fun Grow31IntroContent(onContinue: (dailyAmount: Long) -> Unit) {
    var daily by rememberSaveable { mutableLongStateOf(500L) }
    FixedBottomCta(
        content = {
            Text("31-day savings", color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Save a little each day, earn up to 10%", color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
            Text("Your bonus grows at day 7, 14, 21 and 31.", color = Ids.colors.textTertiary, fontSize = 15.sp)
            BonusIllustration()
            Text("How much each day?", color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                dailyChoices.forEach { amount ->
                    val on = amount == daily
                    Box(
                        Modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
                            .pressScaleClickable { daily = amount }.padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(String.format(Locale.US, "%,d", amount), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.background else Ids.colors.textPrimary)
                    }
                }
            }
            Text(
                "$TERM_DAYS days × ${String.format(Locale.US, "%,d", daily)} = ${String.format(Locale.US, "%,d", daily * TERM_DAYS)} RWF",
                color = Ids.colors.textTertiary, fontSize = 14.sp,
            )
        },
        cta = { IdsButton(text = "Next", onClick = { onContinue(daily) }) },
    )
}

@Composable
private fun BonusIllustration() {
    val brandSoft = Ids.colors.brand.copy(alpha = 0.18f)
    Box(Modifier.fillMaxWidth().height(224.dp).clip(RoundedCornerShape(28.dp)).background(Ids.colors.brand.copy(alpha = 0.12f))) {
        Box(
            Modifier.padding(start = 14.dp, top = 14.dp).width(128.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(12.dp),
        ) {
            Text("Reach the cup for the full 10% bonus", color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 17.sp)
        }
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val stringX = w * 0.54f
            drawLine(Color(0xFFD9A441), Offset(stringX, 0f), Offset(stringX, 34.dp.toPx()), strokeWidth = 3.dp.toPx())
            drawOval(Color(0xFF6B4A2F), Offset(stringX - 14.dp.toPx(), 34.dp.toPx()), Size(28.dp.toPx(), 40.dp.toPx()))
            drawArc(Color(0xFFD9A441), 100f, 160f, false, Offset(stringX - 7.dp.toPx(), 42.dp.toPx()), Size(14.dp.toPx(), 24.dp.toPx()), style = Stroke(2.5.dp.toPx()))
            val cupTop = size.height - 66.dp.toPx()
            drawRoundRect(brandSoft, Offset(w * 0.30f, cupTop - 6.dp.toPx()), Size(w * 0.30f, 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawArc(brandSoft, 0f, 180f, false, Offset(w * 0.32f, cupTop - w * 0.14f), Size(w * 0.26f, w * 0.28f), style = Stroke(5.dp.toPx()))
            drawArc(brandSoft, -90f, 180f, false, Offset(w * 0.55f, cupTop + 4.dp.toPx()), Size(w * 0.09f, w * 0.09f), style = Stroke(5.dp.toPx()))
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp, top = 18.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(7, 14, 21, 31).forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Day $day · ${grow31BonusRateForStreak(day).toInt()}%", color = Ids.colors.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
}

