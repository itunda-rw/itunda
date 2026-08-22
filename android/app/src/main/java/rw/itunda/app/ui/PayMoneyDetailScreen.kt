package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
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
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsColors
import rw.itunda.core.designsystem.theme.IdsTypography

// Real "Toss Pay Money" detail/statement screen (user screenshots, 2026-08-21):
// reached by drilling into the balance row on MyPaymentCodeCard (ItundaAppScreen.kt),
// structurally parallel to AccountDetailScreen (the real Toss Bank account-detail
// screen) but scoped to this one account's own transactions via the new
// getAccountTransactionHistory endpoint. Reuses AccountDetailScreen's own
// AccountLedgerRow/ledgerDateHeader/running-balance convention for consistency --
// both made internal (were private) so this file can share them. Deliberately flat
// throughout, matching both the real reference screenshot and a direct 2026-08-21
// user instruction to move itunda's designs toward flat over card-heavy --
// AccountDetailScreen itself was already built this way, so this screen follows its
// own established pattern rather than introducing a new one. Its own file (rather
// than inline in ItundaAppScreen.kt) per docs/ARCHITECTURE_GUIDELINES.md §2 --
// ItundaAppScreen.kt is already the tracked file-size-lint backlog's largest offender.
@Composable
internal fun PayMoneyDetailScreen(
    account: rw.itunda.core.network.Account,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onAddMoney: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var transactions by remember { mutableStateOf<List<rw.itunda.core.network.TransactionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var paymentsOnly by remember { mutableStateOf(false) }
    val currentUserId = account.userId

    LaunchedEffect(account.id) {
        try {
            transactions = rw.itunda.core.network.NetworkClient.apiService.getAccountTransactionHistory(account.id).transactions
        } catch (e: Exception) {
            error = "Could not load your transaction history."
        }
    }

    val visible = remember(transactions, paymentsOnly) {
        (transactions ?: emptyList()).filter { !paymentsOnly || it.type == "PAYMENT" }
    }
    val sorted = remember(visible) { visible.sortedByDescending { it.createdAt } }
    val withBalance = remember(sorted, account.balance, currentUserId) {
        var runningBalance = account.balance
        sorted.map { tx ->
            val afterBalance = runningBalance
            val delta = if (tx.senderId == currentUserId) -tx.amount else tx.amount
            runningBalance -= delta
            tx to afterBalance
        }
    }
    val grouped = remember(withBalance) { withBalance.groupBy { (tx, _) -> ledgerDateHeader(tx.createdAt) } }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        Box(
            modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp).size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal, bottom = 16.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)) {
                    Text(stringResource(R.string.pay_money_detail_title), fontSize = 13.sp, color = Ids.colors.textSecondary)
                    Text("${account.currency} %,.0f".format(account.balance), style = IdsTypography.LargeAmount, color = Ids.colors.textPrimary)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                    IdsButton(stringResource(R.string.home_send), onClick = onSend, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Tinted, size = IdsButtonSize.Medium)
                    IdsButton(stringResource(R.string.pay_money_add), onClick = onAddMoney, modifier = Modifier.weight(1f), variant = IdsButtonVariant.Filled, size = IdsButtonSize.Medium)
                }
            }
            item {
                // Real flat section break (matches the reference's own 8dp grey block
                // between the balance/buttons and the statement, no card wrapper).
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(Ids.colors.surfaceSoft))
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        java.time.Instant.now().atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("MMMM", java.util.Locale.ENGLISH)),
                        color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pay_money_history_only), color = Ids.colors.textSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(checked = paymentsOnly, onCheckedChange = { paymentsOnly = it })
                    }
                }
            }
            when {
                error != null -> item { Text(error!!, color = IdsColors.Red500, fontSize = 13.sp) }
                transactions == null -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = IdsColors.Blue500)
                    }
                }
                withBalance.isEmpty() -> item { EmptyState(stringResource(R.string.pay_money_empty)) }
                else -> grouped.forEach { (dateHeader, rows) ->
                    item {
                        Text(
                            dateHeader, color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(rows, key = { it.first.id }) { (tx, afterBalance) ->
                        AccountLedgerRow(transaction = tx, isOutgoing = tx.senderId == currentUserId, afterBalance = afterBalance, currency = account.currency)
                    }
                }
            }
        }
    }
}
