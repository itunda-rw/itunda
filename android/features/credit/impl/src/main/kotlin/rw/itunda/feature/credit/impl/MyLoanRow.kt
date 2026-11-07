package rw.itunda.feature.credit.impl

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.formatMoney
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.LoanAccountDto

/**
 * "My loan" in the prototype's v947 shape: the amount left is the hero over a thick
 * progress bar, the details are flat 56dp lines (no card), then the repay field and
 * the two actions. Same data and callbacks as the old card; no new backend fields
 * (the prototype's per-payment timeline needs a schedule endpoint itunda does not have).
 */
@Composable
internal fun MyLoanRow(
    loan: LoanAccountDto,
    busy: Boolean,
    repayText: String,
    onRepayTextChanged: (String) -> Unit,
    onRepay: () -> Unit,
    onRefinance: () -> Unit,
) {
    val paidShare = if (loan.principal.signum() > 0) {
        (1.0 - loan.outstanding.toDouble() / loan.principal.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else 0f
    Column(Modifier.fillMaxWidth()) {
        Text("Left to pay", color = Ids.colors.textSecondary, fontSize = 14.sp)
        Text(
            "${formatMoney(loan.outstanding)} RWF",
            color = Ids.colors.textPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Box(
            Modifier.padding(top = 16.dp).fillMaxWidth().height(10.dp)
                .clip(RoundedCornerShape(5.dp)).background(Ids.colors.surfaceSoft),
        ) {
            Box(Modifier.fillMaxWidth(paidShare).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Ids.colors.brand))
        }
        Spacer(Modifier.height(12.dp))
        LoanLine("Loan amount", "${formatMoney(loan.principal)} RWF")
        LoanLine("Interest rate", "${loan.interestRate}%")
        LoanLine("Status", loan.status.lowercase().replaceFirstChar { it.uppercase() })
        if (loan.status == "ACTIVE") {
            Spacer(Modifier.height(12.dp))
            IdsTextField(value = repayText, onValueChange = onRepayTextChanged, label = "Repay amount (RWF)", isAmount = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            IdsButton(text = if (busy) "Repaying…" else "Repay", enabled = !busy, onClick = onRepay)
            Spacer(Modifier.height(8.dp))
            IdsButton(
                text = if (busy) "Checking…" else "Refinance to a lower rate",
                enabled = !busy,
                onClick = onRefinance,
                variant = IdsButtonVariant.Tinted,
            )
        }
        Spacer(Modifier.height(16.dp))
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
    }
}

@Composable
private fun LoanLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ids.colors.textSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
