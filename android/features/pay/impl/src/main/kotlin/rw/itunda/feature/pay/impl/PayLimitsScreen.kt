package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private const val MIN = 10_000
private const val ONE_MAX = 2_000_000
private const val DAY_MAX = 5_000_000

private fun fmt(n: Int) = String.format(Locale.US, "%,d", n)

/**
 * Prototype `paylim96` ("Pay limits"): what is left today, per-payment and per-day limits with the
 * prototype's validation messages, and a CTA that reads "Check your limits" / "No changes" / "Save limits".
 * Limits are owned by the caller; this screen only edits a draft.
 */
@Composable
fun PayLimitsScreen(perPayment: Int, perDay: Int, usedToday: Int, onBack: () -> Unit, onSave: (perPayment: Int, perDay: Int) -> Unit) {
    var one by remember { mutableIntStateOf(perPayment) }
    var day by remember { mutableIntStateOf(perDay) }
    val valid = one in MIN..ONE_MAX && day in MIN..DAY_MAX && one <= day
    val dirty = one != perPayment || day != perDay
    fun badOne() = one > ONE_MAX || (one in 1 until MIN) || (one > day && day >= MIN)
    fun badDay() = day > DAY_MAX || (day in 1 until MIN)
    val oneMsg = when {
        one > ONE_MAX -> "The most per payment is ${fmt(ONE_MAX)} RWF."
        one in 1 until MIN -> "The least is ${fmt(MIN)} RWF."
        one > day && day >= MIN -> "A payment can’t be more than your daily limit."
        else -> ""
    }
    val dayMsg = when {
        day > DAY_MAX -> "The most per day is ${fmt(DAY_MAX)} RWF."
        day in 1 until MIN -> "The least is ${fmt(MIN)} RWF."
        day in MIN until usedToday -> "You have already paid ${fmt(usedToday)} RWF today, so new payments wait until tomorrow."
        else -> ""
    }
    val left = maxOf(0, perDay - usedToday)
    val pct = minOf(100, Math.round(usedToday.toFloat() / maxOf(perDay, 1) * 100))
    PaySubScreen(
        onBack = onBack, title = "Pay limits",
        cta = {
            Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp)) {
                val label = when { !valid -> "Check your limits"; !dirty -> "No changes"; else -> "Save limits" }
                PayBtn(label, rememberPayPalette(), enabled = valid && dirty) { onSave(one, day) }
            }
        },
    ) { p ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 12.dp)) {
            Column(Modifier.padding(top = 4.dp, bottom = 20.dp)) {
                Text("Left today", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 0.dp))
                Text("${fmt(left)} RWF", color = p.text, fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Paid today", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
                Row {
                    Text(fmt(usedToday), color = p.text, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                    Text(" of ${fmt(perDay)} RWF", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.soft)) {
                Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (pct >= 90) Color(0xFFD92D20) else p.brand))
            }
            LimitField("Per payment", one, oneMsg, badOne(), p) { one = it }
            LimitField("Per day", day, dayMsg, badDay() || dayMsg.isNotEmpty(), p) { day = it }
            Text(
                "Per payment: 10,000 to 2,000,000 RWF. Per day: up to 5,000,000 RWF.\nApplies to every payment from itunda Pay or itunda Bank. Lower limits protect you if your phone is lost.",
                color = p.tt, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 28.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun LimitField(label: String, value: Int, message: String, bad: Boolean, p: PayPalette, onChange: (Int) -> Unit) {
    Text(label, color = p.text, fontSize = 16.sp, lineHeight = 22.4.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 32.dp))
    val style = TextStyle(color = p.text, fontSize = 22.sp, lineHeight = 30.8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(p.soft).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = if (value == 0) "" else fmt(value), onValueChange = { t -> onChange(minOf(t.filter(Char::isDigit).take(8).toIntOrNull() ?: 0, 99_999_999)) },
            singleLine = true, textStyle = style, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), cursorBrush = SolidColor(p.brandText),
            modifier = Modifier.weight(1f),
            decorationBox = { inner -> Box { if (value == 0) Text("0", color = p.tt, style = style); inner() } },
        )
        Text("RWF", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
    }
    if (message.isNotEmpty()) Text(message, color = if (bad) Color(0xFFD92D20) else p.tt, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
    else Spacer(Modifier.height(0.dp))
}
