package rw.itunda.feature.home.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import rw.itunda.core.network.NetworkClient

/**
 * Live data for [ProtoHomeContent]: accounts -> the two balances, the transaction
 * history -> the 7-day bars and month total, rewards -> "earned". Anything that fails
 * just keeps its zero/empty default, so Home never blocks on one endpoint.
 *
 * The upcoming-bill row has no real source yet (no per-user bills endpoint), so it is
 * shown only in demo builds, with the prototype's sample bill, and hidden otherwise.
 */
@Composable
internal fun ProtoHomeTab(
    refreshKey: Any?,
    unreadNotificationCount: Int,
    onOpenBank: () -> Unit,
    onOpenPay: () -> Unit,
    onOpenSend: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSavings: () -> Unit,
    onOpenCard: () -> Unit,
    onOpenSeeAll: () -> Unit,
    onOpenRewards: () -> Unit,
) {
    val today = LocalDate.now()
    var state by remember {
        mutableStateOf(
            ProtoHomeState(
                bankBalance = 0.0, payBalance = 0.0, interestDays = null, spentThisMonth = 0.0,
                monthName = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                week = weekSpend(emptyList(), "", today), bill = null, earned = 0.0,
            ),
        )
    }
    LaunchedEffect(refreshKey) {
        try {
            val accounts = NetworkClient.apiService.getAccounts().accounts.filter { it.isActive && it.currency == "RWF" }
            val bank = accounts.filter { it.type != "PAY" }.sumOf { it.balance }
            val pay = accounts.filter { it.type == "PAY" }.sumOf { it.balance }
            state = state.copy(bankBalance = bank, payBalance = pay)
            val userId = accounts.firstOrNull()?.userId
            if (userId != null) {
                val txns = NetworkClient.apiService.getTransactionHistory().transactions
                val week = weekSpend(txns, userId, today)
                val month = txns.filter { it.senderId == userId && it.status == "COMPLETED" }
                    .filter { runCatching { java.time.Instant.parse(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().month == today.month }.getOrDefault(false) }
                    .sumOf { it.amount }
                state = state.copy(week = week, spentThisMonth = month)
            }
        } catch (_: Exception) {
        }
        try {
            state = state.copy(earned = NetworkClient.apiService.getRewardTasks().rewardsTotal)
        } catch (_: Exception) {
        }
        if (NetworkClient.demoMode) {
            state = state.copy(interestDays = 3, bill = ProtoBill("REG electricity", "Fri 13 Nov", 18_500.0))
        }
    }
    ProtoHomeContent(
        state = state,
        hasUnread = unreadNotificationCount > 0,
        onOpenBank = onOpenBank, onOpenPay = onOpenPay, onOpenNotifications = onOpenNotifications,
        onSend = onOpenSend, onAdd = onOpenPay, onBills = onOpenBills, onCard = onOpenCard, onSavings = onOpenSavings,
        onSeeAll = onOpenSeeAll, onInvite = {}, onOpenSpending = onOpenBank, onPayBill = onOpenBills, onOpenRewards = onOpenRewards,
    )
}
