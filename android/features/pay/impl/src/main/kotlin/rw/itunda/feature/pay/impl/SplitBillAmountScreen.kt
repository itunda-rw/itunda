package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import rw.itunda.core.designsystem.components.AmountKeypadInput
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
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
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("Split a bill", onBack)
        Column(Modifier.weight(1f).padding(horizontal = Ids.layout.screenHorizontal)) {
            Text("How much was the bill?", color = Ids.colors.textTertiary, fontSize = 14.sp)
            AmountKeypadInput(digits = digits, onDigitsChange = { digits = it })
            listOf("You", "Friend").forEachIndexed { i, who ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(who, color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(String.format(Locale.US, "%,d RWF", if (i == 0) total - half else half), color = Ids.colors.textTertiary, fontSize = 14.sp)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp).height(56.dp).clip(RoundedCornerShape(16.dp))
                .background(if (total > 0) Ids.colors.brand else Ids.colors.brand.copy(alpha = 0.25f))
                .pressScaleClickable(enabled = total > 0, onClick = onContinue),
            contentAlignment = Alignment.Center,
        ) { Text("Choose who to split with", color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
    }
}
