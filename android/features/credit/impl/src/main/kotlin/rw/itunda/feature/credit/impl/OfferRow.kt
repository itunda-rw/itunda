package rw.itunda.feature.credit.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
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
import java.math.BigDecimal
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.formatMoney
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.LoanOfferDto

/**
 * One loan offer as a flat block (prototype v942/v945 shape): name and lender, the
 * terms as flat 56dp lines, the requirement as one quiet line, then amount + Apply.
 * Replaces the Material card; same data, same `onApply`. The apply-progress state
 * (`LoanApplyProgress`) still replaces the block while `busy`.
 */
@Composable
internal fun OfferRow(offer: LoanOfferDto, busy: Boolean, isBestRate: Boolean = false, onApply: (BigDecimal) -> Unit) {
    // Prototype v1038 loan form: quick amount chips (shares of the real maxAmount), a tinted
    // summary block, one CTA. The backend fixes the term, so there is no term picker.
    val max = offer.maxAmount
    val choices = remember(max) {
        listOf(10, 25, 50, 100).map { max.multiply(BigDecimal(it)).divide(BigDecimal(100)).setScale(-3, java.math.RoundingMode.DOWN) }
            .filter { it > BigDecimal.ZERO }.distinct()
    }
    var amount by remember(max) { mutableStateOf(choices.getOrNull(1) ?: max) }
    if (busy) {
        LoanApplyProgress()
        return
    }
    Column(Modifier.fillMaxWidth()) {
        if (isBestRate) {
            Text(
                "Best rate",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textBrand,
                modifier = Modifier
                    .background(Ids.colors.brand.copy(alpha = 0.1f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(offer.name, color = Ids.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(offer.lenderName, color = Ids.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(20.dp))
        Text("How much do you need?", color = Ids.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("You can borrow up to ${formatMoney(max)} RWF.", color = Ids.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(14.dp))
        choices.chunked(2).forEach { rowChoices ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowChoices.forEach { c ->
                    val on = c == amount
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (on) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .pressScaleClickable { amount = c },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${formatMoney(c)} RWF", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.textPrimary else Ids.colors.textTertiary)
                    }
                }
                if (rowChoices.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(20.dp)).background(Ids.colors.brand.copy(alpha = 0.08f)).padding(horizontal = 20.dp, vertical = 4.dp)) {
            OfferLine("Interest rate", "${offer.interestRate}%")
            OfferLine("Repay over", offer.term)
        }
        Text(offer.requirements, color = Ids.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(16.dp))
        IdsButton(text = "Apply for ${formatMoney(amount)} RWF", enabled = true, onClick = { onApply(amount) })
        Spacer(Modifier.height(20.dp))
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
    }
}

@Composable
private fun OfferLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ids.colors.textSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
