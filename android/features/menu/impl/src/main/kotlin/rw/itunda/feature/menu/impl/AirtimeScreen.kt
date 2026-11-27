package rw.itunda.feature.menu.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.NumberFormat
import java.util.Locale
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.ProtoTopBar
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

private const val MIN = 100L
private const val MAX = 100_000L
private const val MINE = "0788000214"
private const val PAY_BALANCE = 120_500L

private fun fmt(n: Long) = NumberFormat.getIntegerInstance(Locale.US).format(n)
private fun group(p: String) = listOf(p.take(3), p.drop(3).take(3), p.drop(6).take(4)).filter { it.isNotEmpty() }.joinToString(" ")
private fun netOf(p: String): String? = if (Regex("^07[89].*").matches(p)) "MTN" else if (Regex("^07[23].*").matches(p)) "Airtel" else null

/** Prototype `airtime` (v276, with the v429 quieting): number, network, amount, quick chips, review sheet, receipt. */
@Composable
fun AirtimeScreen(onBack: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var op by remember { mutableStateOf("MTN") }
    var amount by remember { mutableStateOf(1000L) }
    var balance by remember { mutableStateOf(PAY_BALANCE) }
    var review by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Triple<String, String, Long>?>(null) }
    val history = remember { mutableStateListOf<String>() }

    done?.let { d -> AirtimeDone(d, balance) { phone = ""; done = null; onBack() }; return }

    val phoneOk = Regex("^07\\d{8}$").matches(phone)
    val amountOk = amount in MIN..MAX && amount <= balance
    val ok = phoneOk && amountOk
    val detected = netOf(phone)
    val recents = history.filter { it != MINE }.distinct().take(3)
    fun setPhone(p: String) { phone = p; netOf(p.take(3).padEnd(3, 'x'))?.let { op = it } }

    if (review) AirtimeReview(op, phone, amount, balance, detected?.takeIf { it != op }, { review = false }) {
        balance -= amount; history.add(0, phone); done = Triple(op, phone, amount); review = false
    }

    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        ProtoTopBar("Buy airtime", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
            Text("Phone number", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp)
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.chip).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    group(phone), { v -> setPhone(v.filter { it.isDigit() }.take(10)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 24.sp, lineHeight = 33.6.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Ids.colors.brand), modifier = Modifier.fillMaxWidth(),
                )
                if (phone.isEmpty()) Text("078 000 0000", color = Ids.colors.textTertiary.copy(alpha = 0.7f), fontSize = 24.sp, lineHeight = 33.6.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.padding(top = 6.dp).heightIn20(), verticalAlignment = Alignment.CenterVertically) {
                when {
                    phone.isEmpty() -> {}
                    phone.length < 10 -> Text("${phone.length} of 10 digits", color = Ids.colors.textTertiary, fontSize = 14.sp)
                    detected != null && detected != op -> {
                        Text("This looks like an $detected number. ", color = Ids.colors.danger, fontSize = 14.sp)
                        Text("Switch to $detected", color = Ids.colors.textBrand, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressScaleClickable { op = detected })
                    }
                    else -> Text("Looks good" + (detected?.let { " · $it" } ?: ""), color = Ids.colors.success, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AirChip("My number", Modifier.weight(1f)) { setPhone(MINE) }
                recents.forEach { n -> AirChip(group(n), Modifier.weight(1f)) { setPhone(n) } }
            }
            Text("Network", color = Ids.colors.textTertiary, fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 28.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MTN" to Color(0xFFFFCC00), "Airtel" to Color(0xFFE40000)).forEach { (name, dot) ->
                    val on = op == name
                    Row(
                        Modifier.height(44.dp).clip(RoundedCornerShape(20.dp)).background(if (on) Ids.colors.textPrimary else Ids.colors.chip).pressScaleClickable { op = name }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                        Text(name, color = if (on) Ids.colors.background else Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold)
                    }
                }
            }
            Text("Amount", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 28.dp))
            Row(Modifier.padding(top = 6.dp).fillMaxWidth().height(56.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        if (amount == 0L) "" else fmt(amount), { v -> amount = v.filter { it.isDigit() }.take(8).toLongOrNull() ?: 0L }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 40.sp, lineHeight = 56.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(Ids.colors.brand), modifier = Modifier.fillMaxWidth(),
                    )
                    if (amount == 0L) Text("0", color = Ids.colors.textTertiary, fontSize = 40.sp, lineHeight = 56.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Bold)
                }
                Text("RWF", color = Ids.colors.textTertiary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(500L, 1000L, 2000L, 5000L).forEach { n -> AirChip("+${fmt(n)}", Modifier.weight(1f)) { amount = minOf(amount + n, 99_999_999L) } }
            }
            Spacer(Modifier.height(24.dp))
        }
        val label = if (ok) "Buy ${fmt(amount)} RWF" else if (!phoneOk) "Enter a phone number" else "Enter an amount"
        Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp)) {
            AirCta(label, ok) { review = true }
        }
    }
}

private fun Modifier.heightIn20() = this.height(20.dp)

@Composable
private fun AirChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(44.dp).clip(RoundedCornerShape(20.dp)).background(Ids.colors.chip).pressScaleClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun AirCta(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp)).background(if (enabled) Ids.colors.brand else Ids.colors.brand.copy(alpha = 0.12f))
            .then(if (enabled) Modifier.pressScaleClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) Color.White else Ids.colors.textBrand, fontSize = 18.sp, letterSpacing = (-0.36).sp, fontWeight = FontWeight.Bold) }
}

/** Prototype airPay review sheet: network, number, amount, pay-with, balance after, and the wrong-network warning. */
@Composable
private fun AirtimeReview(op: String, phone: String, amount: Long, balance: Long, wrongNet: String?, onClose: () -> Unit, onBuy: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ids.colors.background).padding(24.dp)) {
            Text("Review airtime", color = Ids.colors.textPrimary, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
            Column(Modifier.padding(top = 12.dp, bottom = if (wrongNet != null) 8.dp else 22.dp)) {
                listOf("Network" to op, "Phone number" to group(phone), "Amount" to "${fmt(amount)} RWF", "Pay with" to "itunda Pay", "Balance after" to "${fmt(balance - amount)} RWF").forEach { (a, b) ->
                    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(a, color = Ids.colors.textTertiary, fontSize = 15.sp)
                        Text(b, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (wrongNet != null) Text("This number looks like an $wrongNet number, but you picked $op. Airtime sent to the wrong network may not arrive.", color = Ids.colors.danger, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 16.dp))
            AirCta("Buy ${fmt(amount)} RWF", true, onBuy)
        }
    }
}

/** Prototype receipt: check badge, "Airtime sent", amount and network, masked number, balance, Done. */
@Composable
private fun AirtimeDone(d: Triple<String, String, Long>, balance: Long, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        ProtoTopBar("", onDone)
        Column(Modifier.weight(1f).fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(Ids.colors.brand), contentAlignment = Alignment.Center) { ItundaArt("check", ItundaArtSet.Line, 44.dp, tint = Color(0xFF17171C)) }
            Text("Airtime sent", color = Ids.colors.textPrimary, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 22.dp))
            Text("${fmt(d.third)} RWF ${d.first}\nto ${group(d.second)}", color = Ids.colors.textTertiary, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Text("Balance: ${fmt(balance)} RWF", color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp))
        }
        Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp)) { AirCta("Done", true, onDone) }
    }
}
