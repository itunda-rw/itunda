package rw.itunda.feature.credit.impl

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
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
    var amountText by remember { mutableStateOf(offer.maxAmount.toPlainString()) }
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
        Text(offer.name, color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(offer.lenderName, color = Ids.colors.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        OfferLine("Up to", "${formatMoney(offer.maxAmount)} RWF")
        OfferLine("Interest rate", "${offer.interestRate}%")
        OfferLine("Repay over", offer.term)
        Text(offer.requirements, color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        IdsTextField(value = amountText, onValueChange = { amountText = it }, label = "Amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        IdsButton(
            text = "Apply",
            enabled = true,
            onClick = {
                val amount = amountText.toBigDecimalOrNull()
                if (amount != null && amount > BigDecimal.ZERO) onApply(amount)
            },
        )
        Spacer(Modifier.height(16.dp))
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
