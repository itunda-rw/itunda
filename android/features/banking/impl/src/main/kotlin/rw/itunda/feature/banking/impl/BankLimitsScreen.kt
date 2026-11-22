package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

private const val SENT_TODAY = 0L
private const val MAX_LIMIT = 5_000_000L

/** Prototype "Limits & alerts": today's usage bar, daily limit input with quick picks, large-transfer alert. */
@Composable
internal fun BankLimitsScreen(onBack: () -> Unit) {
    var limit by remember { mutableStateOf(1_000_000L) }
    var alert by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf(false) }
    if (done) { BankDone("Limit saved", "Daily limit ${rwf(limit)} RWF. Alerts ${if (alert) "on" else "off"}.", onBack); return }
    val tooLow = limit in 1 until SENT_TODAY
    val valid = limit > 0 && !tooLow && limit <= MAX_LIMIT
    val used = if (limit > 0) (SENT_TODAY.toFloat() / limit).coerceIn(0f, 1f) else 1f
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Limits & alerts", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(18.dp)) {
                Text("Sent today", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${rwf(SENT_TODAY)} RWF", color = Ids.colors.textPrimary, fontSize = 26.sp, lineHeight = 36.4.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.Bold)
                    Text("of ${rwf(limit)}", color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White)) {
                    Box(Modifier.fillMaxWidth(used).height(8.dp).background(if (used >= 0.9f) Ids.colors.danger else Ids.colors.brand))
                }
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append("Left today: ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold))
                        append("${rwf((limit - SENT_TODAY).coerceAtLeast(0))} RWF")
                        pop()
                    },
                    color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp),
                )
            }
            Text("Daily transfer limit", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 28.dp, bottom = 6.dp))
            Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = if (limit == 0L) "" else rwf(limit), onValueChange = { limit = it.filter(Char::isDigit).take(8).toLongOrNull() ?: 0L },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 40.sp, lineHeight = 56.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Text("RWF", color = Ids.colors.textTertiary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
            }
            Text(
                when {
                    tooLow -> "Must be at least what you sent today."
                    limit > MAX_LIMIT -> "The most you can set is ${rwf(MAX_LIMIT)} RWF."
                    else -> "Between 10,000 and ${rwf(MAX_LIMIT)} RWF."
                },
                color = if (tooLow || limit > MAX_LIMIT) Ids.colors.danger else Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(200_000L, 500_000L, 1_000_000L, 2_000_000L).forEach { BankChip(rwf(it), limit == it, { limit = it }, Modifier.weight(1f), compact = true, largeText = true) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 22.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alerts for large transfers", color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp, fontWeight = FontWeight.SemiBold)
                    Text("Notify me above 100,000 RWF", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp, modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (alert) Ids.colors.brand else Ids.colors.divider).pressScaleClickable { alert = !alert },
                    contentAlignment = Alignment.Center,
                ) { if (alert) Icon(Icons.Filled.Check, contentDescription = "Alerts on", modifier = Modifier.size(16.dp), tint = Color.White) }
            }
        }
        val changed = limit != 1_000_000L || !alert
        BankSubCta(if (changed) "Save limit" else "No changes", enabled = valid && changed) { done = true }
    }
}
