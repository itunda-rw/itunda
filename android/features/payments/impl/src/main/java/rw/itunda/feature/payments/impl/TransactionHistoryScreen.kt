package rw.itunda.feature.payments.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ReceiptLong
import rw.itunda.core.designsystem.components.EmptyState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

/**
 * Plain display model, not the App target's TransactionDto directly -- this Feature
 * module can't depend back on :app (:app depends on :features:payments:impl, never
 * the reverse), same constraint as iOS's BankView/SavingsRowData. ItundaAppScreen.kt
 * maps the real TransactionDto list into this shape before calling this screen.
 */
data class TransactionDisplayItem(
    val id: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val status: String,
    val isOutgoing: Boolean,
)

/**
 * Real transaction history screen, matching the real Toss card-detail reference
 * screenshot (user-provided, 2026-07-12): spend-this-month total up top, then a
 * real list -- "아직 내역이 없어요" (no history yet) when empty, matching Toss's own
 * empty state exactly rather than inventing fake rows to fill the screen. Backed
 * by services/backend/wallet's real getTransactionHistory endpoint (previously a
 * dead repository method with no controller ever calling it) -- no card issuance
 * or card network exists, so this is framed as spend history, not a real card.
 */
@Composable
fun TransactionHistoryScreen(
    transactions: List<TransactionDisplayItem>,
    onBack: () -> Unit,
) {
    val spentThisMonth = transactions
        .filter { it.isOutgoing && it.status == "COMPLETED" }
        .sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ids.colors.background)
    ) {
        FlowTopBar(onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Spent this month", color = Ids.colors.textSecondary, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "RWF ${rwfFormatter.format(spentThisMonth.toLong())}",
                color = Ids.colors.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (transactions.isEmpty()) {
            EmptyState("No transactions yet — sends, receives, and payments will show up here.", icon = Icons.Outlined.ReceiptLong)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                items(transactions, key = { it.id }) { tx -> TransactionRow(tx) }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionDisplayItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (tx.isOutgoing) Ids.colors.dangerTint else Ids.colors.successTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (tx.isOutgoing) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (tx.isOutgoing) Ids.colors.danger else Ids.colors.success
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.description, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(tx.status.lowercase().replaceFirstChar { it.uppercase() }, color = Ids.colors.textTertiary, fontSize = 13.sp)
        }
        Text(
            "${if (tx.isOutgoing) "-" else "+"}${rwfFormatter.format(tx.amount.toLong())} ${tx.currency}",
            color = if (tx.isOutgoing) Ids.colors.textPrimary else Ids.colors.success,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
