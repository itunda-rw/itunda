package rw.itunda.feature.pay.impl

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

@Composable
internal fun PayPrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Ids.colors.brand else Ids.colors.brand.copy(alpha = 0.25f))
            .pressScaleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
}

/** Prototype "Enter the merchant's till number": 6 digits, saved tills as quick picks. */
@Composable
fun PayTillScreen(onBack: () -> Unit, onNext: (PayMerchantRef) -> Unit) {
    var till by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(horizontal = 24.dp)) {
        BackTopBar("", onBack)
        Text("Enter the merchant’s till number", color = Ids.colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
        Box(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                if (till.isEmpty()) "0 0 0 0 0 0" else till.toCharArray().joinToString(" "),
                color = if (till.isEmpty()) Ids.colors.textTertiary else Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            )
        }
        Text("${till.length}/6", color = Ids.colors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Text("Tills", color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            payMerchants.forEach { m ->
                Text(m.till, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).pressScaleClickable { till = m.till }.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        PayKeypadRow(till, onChange = { till = it.take(6) })
        Spacer(Modifier.height(10.dp))
        PayPrimaryButton("Next", enabled = till.length == 6) { onNext(PayMerchantRef(payMerchants.firstOrNull { it.till == till }?.name ?: "Till $till", till)) }
        Text("Enter the full till number to continue.", color = Ids.colors.textTertiary, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp))
    }
}

class PayMerchantRef(val name: String, val till: String)

@Composable
private fun PayKeypadRow(value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫")).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { k ->
                    Box(Modifier.weight(1f).height(44.dp).pressScaleClickable(enabled = k.isNotEmpty()) { onChange(if (k == "⌫") value.dropLast(1) else value + k) }, contentAlignment = Alignment.Center) {
                        Text(k, color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Prototype order review for a merchant's counter order (dummy lines). */
@Composable
fun PayOrderScreen(merchantName: String, onBack: () -> Unit, onPay: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(horizontal = 24.dp)) {
        BackTopBar(merchantName, onBack)
        Text("Order at the counter", color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        Text("7,000 RWF", color = Ids.colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
        listOf("2 × Cappuccino" to "5,000", "1 × Croissant" to "2,000").forEach { OrderLine(it.first, it.second) }
        Spacer(Modifier.height(24.dp))
        OrderLine("Pay from", "itunda Pay", bold = true)
        Spacer(Modifier.height(12.dp))
        OrderLine("You earn", "70 points", bold = true)
        Spacer(Modifier.weight(1f))
        PayPrimaryButton("Pay 7,000 RWF", onClick = onPay)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OrderLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = if (bold) Ids.colors.textTertiary else Ids.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Demo completion state: the prototype's Pay button ends here; no money moves until payments are wired. */
@Composable
fun PayDoneScreen(merchantName: String, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text("\u2705", fontSize = 56.sp)
        Text("7,000 RWF to $merchantName", color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp))
        Text("Demo only \u2014 no money was moved.", color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.weight(1f))
        PayPrimaryButton("Done", onClick = onDone)
    }
}
