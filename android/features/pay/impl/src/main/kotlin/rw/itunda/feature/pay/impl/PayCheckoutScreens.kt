package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable

@Composable
internal fun PayPrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val p = rememberPayPalette()
    PayTextScope { PayBtn(label, p, enabled, onClick = onClick) }
}

class PayMerchantRef(val name: String, val till: String)

/** Prototype "Enter the merchant's till number": a 6-digit field (system number keyboard), saved tills as quick picks. */
@Composable
fun PayTillScreen(onBack: () -> Unit, onNext: (PayMerchantRef) -> Unit) {
    var till by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    PaySubScreen(
        onBack = onBack,
        cta = {
            val p = rememberPayPalette()
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp)) {
                PayBtn("Next", p, enabled = till.length == 6) { onNext(PayMerchantRef(payMerchants.firstOrNull { it.till == till }?.name ?: "Till $till", till)) }
                Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.Center) {
                    Text("Enter the full till number to continue.", color = p.tt, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.32).sp)
                }
            }
        },
    ) { p ->
        Column(Modifier.weight(1f).padding(horizontal = 24.dp).padding(top = 20.dp)) {
            Text("Enter the merchant’s till number", color = p.text, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            Spacer(Modifier.height(20.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = till, onValueChange = { v -> till = v.filter(Char::isDigit).take(6) }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = p.text, fontSize = 26.sp, lineHeight = 36.4.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(p.brandText),
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(22.dp)).background(p.soft).focusRequester(focus),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (till.isEmpty()) Text("000000", color = p.tt.copy(alpha = 0.7f), fontSize = 26.sp, lineHeight = 36.4.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text("${till.length}/6", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(18.dp))
            Text("Tills", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                payMerchants.sortedBy { it.till }.forEach { m ->
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(20.dp)).background(p.soft).pressScaleClickable { till = m.till },
                        contentAlignment = Alignment.Center,
                    ) { Text(m.till, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Prototype order review for a merchant's counter order (dummy lines). */
@Composable
fun PayOrderScreen(merchantName: String, onBack: () -> Unit, onPay: () -> Unit) {
    PaySubScreen(
        onBack = onBack, title = merchantName,
        cta = { Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp)) { PayBtn("Pay 7,000 RWF", rememberPayPalette(), onClick = onPay) } },
    ) { p ->
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("Order at the counter", color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text("7,000 RWF", color = p.text, fontSize = 34.sp, lineHeight = 47.6.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.padding(vertical = 4.dp)) {
                listOf("2 × Cappuccino" to "5,000", "1 × Croissant" to "2,000").forEach { OrderLine(p, it.first, it.second, false) }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(vertical = 4.dp)) {
                OrderLine(p, "Pay from", "itunda Pay", true)
                OrderLine(p, "You earn", "70 points", true)
            }
        }
    }
}

@Composable
private fun OrderLine(p: PayPalette, label: String, value: String, muted: Boolean) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (muted) p.tt else p.text, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
        Text(value, color = p.text, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
    }
}

/** Demo completion state: the prototype's Pay button ends here; no money moves until payments are wired. */
@Composable
fun PayDoneScreen(merchantName: String, onDone: () -> Unit) {
    PaySubScreen(
        onBack = onDone,
        cta = { Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp)) { PayBtn("Done", rememberPayPalette(), onClick = onDone) } },
    ) { p ->
        Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Text("Sent to $merchantName", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("Demo only \u2014 no money was moved.", color = p.tt, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Prototype amount entry for paying a friend: amount, funding line, "Pay from" chip, Pay and an in-screen keypad. */
@Composable
fun PayAmountScreen(recipient: String, onBack: () -> Unit, onPay: (Long) -> Unit) {
    var digits by remember { mutableStateOf("") }
    val amount = digits.toLongOrNull() ?: 0L
    PaySubScreen(onBack = onBack) { p ->
        Column(Modifier.fillMaxWidth().weight(1f)) {
            Spacer(Modifier.height(56.dp))
            Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(78.4.dp)) {
                    Text(
                        String.format(java.util.Locale.US, "%,d", amount), color = p.text, fontSize = 56.sp, lineHeight = 78.4.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-2).sp,
                    )
                    Text("RWF", color = p.text, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, bottom = 14.dp))
                }
                Text("From itunda Pay · 120,500 RWF", color = p.tt, fontSize = 14.sp, lineHeight = 19.6.sp, modifier = Modifier.padding(top = 10.dp))
                Row(
                    Modifier.padding(top = 12.dp).size(width = 208.9.dp, height = 40.dp).clip(RoundedCornerShape(50)).background(p.soft),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Pay from", color = p.tt, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp)
                    Text("itunda Pay", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.15).sp, modifier = Modifier.padding(start = 6.dp))
                    androidx.compose.foundation.Canvas(Modifier.padding(start = 6.dp).size(18.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply { moveTo(4.5.dp.toPx(), 7.dp.toPx()); lineTo(9.dp.toPx(), 11.5.dp.toPx()); lineTo(13.5.dp.toPx(), 7.dp.toPx()) }
                        drawPath(path, p.tt, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(horizontal = 16.dp).height(68.dp), contentAlignment = Alignment.Center) {
                PayBtn("Pay", p, enabled = amount > 0, height = 56.dp, radius = 16.dp, size = 17) { onPay(amount) }
            }
            PayKeypad(p) { k -> digits = if (k == "⌫") digits.dropLast(1) else (digits + k).trimStart('0').take(9) }
            Spacer(Modifier.height(46.dp))
        }
    }
}
