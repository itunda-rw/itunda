package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.app.R
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.ledgerDateHeader
import rw.itunda.core.designsystem.components.ledgerFullDateTime
import rw.itunda.core.designsystem.theme.AccentIndigo
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.core.network.BucketTransactionDto

// Real per-bucket detail screen (2026-08-31, direct user-supplied Toss Bank
// screenshots: 보관하기/매일모으기 each get their own full-screen ledger, not just a
// compact row on BankHubScreen). Generalizes AccountDetailScreen's own balance-hero/
// date-grouped-ledger shape to work for ANY savings bucket (Interest Jar, a Savings
// Goal, a Weekly/Grow31/Upfront plan, the Youth account) -- takes plain primitives +
// a suspend fetchTransactions() instead of assuming the primary account, matching
// web's identical BucketDetailScreen.tsx generalization of its own AccountDetailScreen.
@Composable
internal fun BucketDetailScreen(
    title: String,
    subtitle: String? = null,
    balanceText: String,
    secondaryStatLabel: String? = null,
    secondaryStatValue: String? = null,
    fetchTransactions: suspend () -> List<BucketTransactionDto>,
    fillLabel: String? = null,
    onFill: (() -> Unit)? = null,
    withdrawLabel: String? = null,
    onWithdraw: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var transactions by remember { mutableStateOf<List<BucketTransactionDto>?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            transactions = fetchTransactions()
        } catch (e: Exception) {
            error = true
        }
    }

    val grouped = remember(transactions) {
        (transactions ?: emptyList()).sortedByDescending { it.createdAt }.groupBy { ledgerDateHeader(it.createdAt) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IdsIcons.Back, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal, bottom = 16.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    if (subtitle != null) {
                        Text(subtitle, fontSize = 13.sp, color = Ids.colors.textSecondary)
                    }
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary, modifier = Modifier.padding(top = 2.dp))
                    Text(balanceText, style = IdsTypography.LargeAmount, color = Ids.colors.textPrimary, modifier = Modifier.padding(top = 4.dp))
                    if (secondaryStatLabel != null && secondaryStatValue != null) {
                        Text("$secondaryStatLabel: $secondaryStatValue", fontSize = 13.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (onFill != null || onWithdraw != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            if (onFill != null) {
                                IdsButton(fillLabel ?: stringResource(R.string.bucket_fill), onClick = onFill, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Medium)
                            }
                            if (onWithdraw != null) {
                                IdsButton(withdrawLabel ?: stringResource(R.string.bucket_withdraw), onClick = onWithdraw, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Medium)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            if (error) {
                item { Text(stringResource(R.string.bucket_load_error), color = Ids.colors.danger, fontSize = 13.sp) }
            } else if (transactions == null) {
                item { Text(stringResource(R.string.loading), color = Ids.colors.textSecondary, fontSize = 13.sp) }
            } else if (transactions!!.isEmpty()) {
                // Real, honest empty state (2026-08-31) -- a pre-existing bucket
                // migrated to per-bucket ledger isolation on the day this shipped has
                // no itemized history before that point (see backend's
                // ensureGoalLedgerAccount doc comment for the full account).
                item { EmptyState(stringResource(R.string.bucket_no_transactions_yet)) }
            } else {
                grouped.forEach { (dateHeader, rows) ->
                    item {
                        Text(
                            dateHeader, color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(rows, key = { it.id }) { tx -> BucketTransactionRow(tx) }
                }
            }
        }
    }
}

@Composable
internal fun BucketTransactionRow(tx: BucketTransactionDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(if (tx.isCredit) AccentIndigo else Ids.colors.textTertiary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (tx.isCredit) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                contentDescription = null, modifier = Modifier.size(18.dp), tint = androidx.compose.ui.graphics.Color.White,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.description, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
            Text(ledgerFullDateTime(tx.createdAt), fontSize = 12.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${if (tx.isCredit) "+" else "-"}%,.0f".format(tx.amount),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (tx.isCredit) AccentIndigo else Ids.colors.textPrimary,
            )
            Text("%,.0f".format(tx.balanceAfter), fontSize = 11.sp, color = Ids.colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
