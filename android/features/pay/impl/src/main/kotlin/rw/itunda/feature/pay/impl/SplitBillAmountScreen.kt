package rw.itunda.feature.pay.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Prototype "Split a bill" opener: enter the total, see an even two-way share, then continue into
 * Talk, where the real split request (friends, payments, receipts) lives.
 */
@Composable
fun SplitBillAmountScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    var digits by remember { mutableStateOf("") }
    val total = digits.toLongOrNull() ?: 0L
    val half = total / 2
    fun rwf(v: Long) = String.format(Locale.US, "%,d RWF", v)
    PaySubScreen(onBack = onBack) { p ->
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Text("Split a bill", color = p.text, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, modifier = Modifier.height(46.dp).padding(top = 8.dp))
            Spacer(Modifier.height(12.dp))
            Text("How much was the bill?", color = p.tt, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
            Text(rwf(total), color = p.text, fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(24.dp))
            listOf("You" to total - half, "Amina K." to half).forEach { (who, share) ->
                Row(Modifier.fillMaxWidth().height(30.dp).padding(top = 4.5.dp)) {
                    Text(who, color = p.tt, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                    Text(rwf(share), color = p.tt, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
            Spacer(Modifier.weight(1f))
        }
        Box(Modifier.padding(horizontal = 16.dp).height(68.dp)) { PayBtn("Send split request", p, enabled = total > 0, height = 56.dp, radius = 16.dp, size = 17, modifier = Modifier.padding(top = 8.dp), onClick = onContinue) }
        PayKeypad(p) { k -> digits = if (k == "⌫") digits.dropLast(1) else (digits + k).trimStart('0').take(9) }
        Spacer(Modifier.height(46.dp))
    }
}
