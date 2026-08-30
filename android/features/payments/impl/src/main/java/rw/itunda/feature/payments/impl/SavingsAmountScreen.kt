package rw.itunda.feature.payments.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

/**
 * Real savings deposit/withdraw amount screens, matching the two real Toss
 * reference screenshots (user-provided, 2026-07-12): "얼마나 채울까요?" (deposit)
 * and "얼마나 꺼낼까요?" (withdraw) -- same visual shape as TransferAmountScreen
 * (from/to summary, quick-amount chips, keypad, Next bar), reusing its shared
 * primitives directly (TransferFlow.kt's FlowTopBar/TransferPartyRow/
 * QuickAmountChip/FlowNextBar/NumericKeypad, widened from private to internal for
 * exactly this reuse) rather than re-implementing an identical layout.
 *
 * Real gap found live (2026-08-31, direct user re-reference of the same two
 * screenshots): a withdraw endpoint now exists (SavingsController.kt's real
 * POST /api/v1/savings/withdraw, closing SavingsService.depositToGoal's own
 * 2026-08-23 doc comment naming this exact gap) -- .withdraw is no longer faked
 * against a nonexistent endpoint, it's real.
 */
enum class SavingsAmountMode { deposit, withdraw, claimInterest }

@Composable
fun SavingsAmountScreen(
    goalName: String,
    mode: SavingsAmountMode,
    availableBalance: Double,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onConfirm: (amountRwf: Long) -> Unit,
) {
    var digits by rememberSaveable { mutableStateOf("") }
    val amount = digits.toLongOrNull() ?: 0L
    // For withdraw, `availableBalance` is the GOAL's own current balance (the real cap
    // on this action), not the destination account's balance -- see this screen's own
    // call site in ItundaAppScreen.kt.
    val availableBalanceLong = availableBalance.toLong()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ids.colors.background)
    ) {
        FlowTopBar(onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            if (mode == SavingsAmountMode.withdraw) {
                TransferPartyRow(
                    label = "From $goalName",
                    sublabel = "Available ${rwfFormatter.format(availableBalanceLong)} RWF",
                    icon = Icons.Outlined.Savings
                )
            } else {
                TransferPartyRow(
                    label = "From Itunda Account",
                    sublabel = "Available ${rwfFormatter.format(availableBalanceLong)} RWF",
                    icon = Icons.Outlined.AccountBalanceWallet
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .padding(start = 21.dp)
                    .width(2.dp)
                    .height(20.dp)
                    .background(Ids.colors.divider)
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (mode == SavingsAmountMode.withdraw) {
                TransferPartyRow(label = "To Itunda Account", sublabel = "", icon = Icons.Outlined.AccountBalanceWallet)
            } else {
                TransferPartyRow(
                    label = "To $goalName",
                    sublabel = if (mode == SavingsAmountMode.deposit) "Savings goal" else "Interest jar",
                    icon = Icons.Outlined.Savings
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                // Real fix (2026-08-11): interest now auto-credits to the account the
                // instant it accrues (see backend SavingsService.accrueInterest's own
                // doc comment, matching real Toss Bank passbook interest) -- this
                // screen no longer moves money, it just acknowledges what already
                // arrived. "Claim your interest" would overclaim a pending action
                // that doesn't exist anymore.
                when (mode) {
                    SavingsAmountMode.deposit -> "How much to save?"
                    SavingsAmountMode.withdraw -> "How much to withdraw?"
                    SavingsAmountMode.claimInterest -> "Interest already added to your balance"
                },
                color = Ids.colors.textSecondary,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (digits.isEmpty()) "0 RWF" else "${rwfFormatter.format(amount)} RWF",
                fontSize = if (digits.isEmpty()) 32.sp else 42.sp,
                fontWeight = FontWeight.Bold,
                color = if (digits.isEmpty()) Ids.colors.textTertiary else Ids.colors.textPrimary,
                textAlign = TextAlign.Center
            )
        }

        if (mode == SavingsAmountMode.deposit || mode == SavingsAmountMode.withdraw) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAmountChip("+10,000") { digits = (((digits.toLongOrNull() ?: 0L) + 10_000L).coerceAtMost(availableBalanceLong)).toString() }
                QuickAmountChip("+100,000") { digits = (((digits.toLongOrNull() ?: 0L) + 100_000L).coerceAtMost(availableBalanceLong)).toString() }
                QuickAmountChip("Max") { digits = availableBalanceLong.toString() }
            }
        }

        if (isSubmitting) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = Ids.colors.brand)
            }
        } else if (mode == SavingsAmountMode.claimInterest) {
            FlowNextBar(enabled = true, label = "OK") { onConfirm(0L) }
        } else {
            val label = if (mode == SavingsAmountMode.withdraw) "Withdraw" else "Deposit"
            FlowNextBar(enabled = digits.isNotEmpty() && amount > 0 && amount <= availableBalanceLong, label = label) { onConfirm(amount) }
            NumericKeypad(
                onDigit = { d -> if (digits.length < 9) digits += d },
                onDelete = { if (digits.isNotEmpty()) digits = digits.dropLast(1) }
            )
        }
    }
}
