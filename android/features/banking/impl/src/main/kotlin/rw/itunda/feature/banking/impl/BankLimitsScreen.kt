package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

private const val SENT_TODAY = 150_000L
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
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(20.dp)) {
                Text("Sent today", color = Ids.colors.textTertiary, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${rwf(SENT_TODAY)} RWF", color = Ids.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("  of ${rwf(limit)}", color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ids.colors.divider)) {
                    Box(Modifier.fillMaxWidth(used).height(8.dp).background(if (used >= 0.9f) Ids.colors.danger else Ids.colors.brand))
                }
                Text("Left today: ${rwf((limit - SENT_TODAY).coerceAtLeast(0))} RWF", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            BankSectionLabel("Daily transfer limit")
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = if (limit == 0L) "" else rwf(limit), onValueChange = { limit = it.filter(Char::isDigit).take(8).toLongOrNull() ?: 0L },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.weight(1f),
                )
                Text("RWF", color = Ids.colors.textTertiary, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                when {
                    tooLow -> "Must be at least what you sent today."
                    limit > MAX_LIMIT -> "The most you can set is ${rwf(MAX_LIMIT)} RWF."
                    else -> "You can change this at any time."
                },
                color = if (tooLow || limit > MAX_LIMIT) Ids.colors.danger else Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(200_000L, 500_000L, 1_000_000L, 2_000_000L).forEach { BankChip(rwf(it), limit == it, { limit = it }, Modifier.weight(1f)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alerts for large transfers", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Notify me above 100,000 RWF", color = Ids.colors.textTertiary, fontSize = 13.sp)
                }
                Switch(checked = alert, onCheckedChange = { alert = it })
            }
        }
        BankSubCta("Save limit", enabled = valid) { done = true }
    }
}
