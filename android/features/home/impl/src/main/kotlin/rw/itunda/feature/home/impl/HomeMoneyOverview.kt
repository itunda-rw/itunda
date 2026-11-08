package rw.itunda.feature.home.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Divider
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
import androidx.compose.ui.unit.dp
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.rememberCountUp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.core.network.Account
import rw.itunda.core.network.NetworkClient

private const val HIDDEN_AMOUNT = "••••••"

private fun rwf(amount: Double) = String.format(Locale.US, "%,.0f", amount)

/**
 * "My money" header of the Home tab, matching the clickable prototype's Home (v928):
 * one big total, the accounts it is made of, and what was spent. Built only from data
 * the backend really returns -- `getAccounts()` and `getSpendingInsight()`. The
 * prototype's "next bill" row is NOT here: there is no upcoming-bills endpoint (bills
 * are a separate mini-app), and a hard-coded due date would be invented data.
 *
 * Only active RWF accounts are summed; foreign-currency accounts are left out rather
 * than added at a made-up rate. Taps go to Bank / Pay (where the details live), never
 * straight into a ledger over Home -- see ItundaAppScreen's 2026-08-14 note on
 * `onOpenAccountDetail`.
 */
@Composable
internal fun HomeMoneyOverview(
    refreshKey: Boolean,
    onOpenBank: () -> Unit,
    onOpenPay: () -> Unit,
) {
    var accounts by remember { mutableStateOf<List<Account>?>(null) }
    var spent by remember { mutableStateOf<Double?>(null) }
    var failed by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    var week by remember { mutableStateOf<List<DaySpend>>(emptyList()) }

    // Re-runs when the pull-to-refresh indicator toggles, so a refresh updates the totals.
    LaunchedEffect(refreshKey) {
        try {
            accounts = NetworkClient.apiService.getAccounts().accounts
            failed = false
        } catch (e: Exception) {
            failed = accounts == null
        }
        try {
            spent = NetworkClient.apiService.getSpendingInsight().totalSpent.toDouble()
        } catch (e: Exception) {
            // Spending row simply stays hidden; balances are the important part.
        }
        try {
            val userId = accounts?.firstOrNull()?.userId
            if (userId != null) week = weekSpend(NetworkClient.apiService.getTransactionHistory().transactions, userId)
        } catch (e: Exception) {
            // Chart simply stays hidden.
        }
    }

    val rwfAccounts = accounts.orEmpty().filter { it.isActive && it.currency == "RWF" }
    if (accounts == null) {
        if (failed) {
            Text(
                "Couldn't load your money. Pull down to try again.",
                style = IdsTypography.Body2,
                color = Ids.colors.textSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        return
    }
    if (rwfAccounts.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My money", style = IdsTypography.Body1, color = Ids.colors.textSecondary)
            Icon(
                imageVector = if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (hidden) "Show amounts" else "Hide amounts",
                tint = Ids.colors.textTertiary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
                    .pressScaleClickable { hidden = !hidden },
            )
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                if (hidden) HIDDEN_AMOUNT else rwf(rememberCountUp(rwfAccounts.sumOf { it.balance })),
                style = IdsTypography.LargeAmount,
                color = Ids.colors.textPrimary,
            )
            Text(
                " RWF",
                style = IdsTypography.Body1,
                color = Ids.colors.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        WeekSpendChart(week, hidden)
        Spacer(modifier = Modifier.height(12.dp))
        rwfAccounts.sortedByDescending { it.balance }.take(3).forEach { account ->
            MoneyRow(
                label = account.nickname?.takeIf { it.isNotBlank() } ?: account.accountName,
                value = if (hidden) HIDDEN_AMOUNT else "${rwf(account.balance)} RWF",
                onClick = if (account.type == "PAY") onOpenPay else onOpenBank,
            )
        }
        spent?.takeIf { it > 0.0 }?.let { total ->
            MoneyRow(
                label = "Spent this period",
                value = if (hidden) HIDDEN_AMOUNT else "${rwf(total)} RWF",
                onClick = onOpenBank,
            )
        }
    }
}

@Composable
private fun MoneyRow(label: String, value: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = IdsTypography.Body1, color = Ids.colors.textSecondary, modifier = Modifier.weight(1f))
            Text(value, style = IdsTypography.Subtitle1, color = Ids.colors.textPrimary)
            Icon(
                IdsIcons.ChevronRight,
                contentDescription = null,
                tint = Ids.colors.textTertiary,
                modifier = Modifier.padding(start = 8.dp).size(16.dp),
            )
        }
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
    }
}
