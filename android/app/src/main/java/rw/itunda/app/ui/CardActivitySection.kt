package rw.itunda.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.app.R
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.formatMoney
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CardDto
import rw.itunda.core.network.CardTransactionDto
import rw.itunda.core.network.CreditScoreFactorDto
import rw.itunda.core.network.CreditScoreSuggestionDto

// Extracted out of CardScreen.kt (2026-09-07, Card product-completeness pass) once
// that file crossed the file-size-lint 500-line guideline adding the new "Card
// benefits" section -- same real "code that changes together lives together"
// extraction this sweep has used repeatedly. LazyListScope extension functions,
// not plain composables, since both sections need to emit their own item{}/items{}
// calls directly into CardScreen's own LazyColumn.

// Extracted out of CardScreen.kt (2026-09-07, Card product-completeness pass) once
// the new i18n string-prefetch vals pushed that file back past the file-size-lint
// 500-line guideline right after the previous split -- explicit params/callbacks,
// same "code that changes together lives together" convention this file's own
// cardRecentActivitySection/cardBenefitsSection already established.
@Composable
fun CardPaySection(
    card: CardDto,
    merchantName: String,
    onMerchantNameChange: (String) -> Unit,
    chargeAmount: String,
    onChargeAmountChange: (String) -> Unit,
    chargeMessage: String?,
    chargeSucceeded: Boolean,
    busy: Boolean,
    onCharge: () -> Unit,
) {
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.card_pay_title), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                stringResource(R.string.card_pay_disclaimer),
                color = Ids.colors.textTertiary, fontSize = 11.sp,
            )
            chargeMessage?.let { Text(it, color = if (chargeSucceeded) Ids.colors.success else Ids.colors.danger, fontSize = 12.sp) }
            IdsTextField(value = merchantName, onValueChange = onMerchantNameChange, label = stringResource(R.string.card_merchant_label), modifier = Modifier.fillMaxWidth())
            IdsTextField(value = chargeAmount, onValueChange = onChargeAmountChange, label = stringResource(R.string.card_amount_label), isAmount = true, modifier = Modifier.fillMaxWidth())
            val payLabel = when {
                card.closedAt != null -> stringResource(R.string.card_pay_state_closed)
                card.lost -> stringResource(R.string.card_pay_state_lost)
                card.frozen -> stringResource(R.string.card_pay_state_frozen)
                busy -> stringResource(R.string.card_paying)
                else -> stringResource(R.string.card_pay)
            }
            CardActionButton(payLabel, enabled = !busy && !card.frozen, onClick = onCharge)
        }
    }
}

fun LazyListScope.cardRecentActivitySection(transactions: List<CardTransactionDto>) {
    item {
        Text(stringResource(R.string.card_recent_activity), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
    if (transactions.isEmpty()) {
        item { EmptyState(stringResource(R.string.card_no_purchases_yet)) }
    } else {
        items(transactions, key = { it.id }) { t ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t.merchantName, color = Ids.colors.textPrimary, fontSize = 13.sp)
                Text("${formatMoney(t.amount)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// Real gap closed 2026-09-07 (Card product-completeness pass): bank-mfe's
// BankDashboard.tsx has had a "Card benefits" section (live credit-score
// card-usage factor + suggestion) since it was built; this is Android's own port.
fun LazyListScope.cardBenefitsSection(factor: CreditScoreFactorDto?, suggestion: CreditScoreSuggestionDto?) {
    if (factor == null && suggestion == null) return
    item {
        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.card_benefits_title), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                factor?.let {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(it.description, color = Ids.colors.textTertiary, fontSize = 12.sp)
                        Text(stringResource(R.string.card_credit_score_points, it.points), color = Ids.colors.success, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                suggestion?.let {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(it.description, color = Ids.colors.textTertiary, fontSize = 12.sp)
                        Text(stringResource(R.string.card_more_points, it.pointsGain), color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
