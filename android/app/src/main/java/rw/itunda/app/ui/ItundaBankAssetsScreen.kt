package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import java.util.Locale
import rw.itunda.app.R
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.Account
import rw.itunda.core.network.BucketTransactionDto
import rw.itunda.core.network.Grow31SavingsPlanDto
import rw.itunda.core.network.InterestJar
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SavingsGoal
import rw.itunda.core.network.UpfrontDepositDto
import rw.itunda.core.network.WeeklySavingsPlanDto

// Real "itunda Bank assets" hub (2026-08-31, direct user-supplied Toss Bank
// screenshots + explicit correction: "my asset screen is hub of all assets, itunda
// bank assets only itunda bank assets" -- scoped ONLY to itunda Bank's own money
// buckets, deliberately NOT the app-wide net-worth hub. A flat balance-summary list
// matching the real "My Toss Bank assets" screen, each row opening its own
// BucketDetailScreen. Same no-ViewModel, NetworkClient-direct-from-Composable shape
// as WeeklySavingsScreen.kt.
private sealed class BankAssetRow {
    data class InterestJarRow(val jar: InterestJar) : BankAssetRow()
    data class GoalRow(val goal: SavingsGoal) : BankAssetRow()
    data class WeeklyRow(val plan: WeeklySavingsPlanDto) : BankAssetRow()
    data class Grow31Row(val plan: Grow31SavingsPlanDto) : BankAssetRow()
    data class UpfrontRow(val deposit: UpfrontDepositDto) : BankAssetRow()
    data class YouthRow(val account: Account) : BankAssetRow()
}

private fun BankAssetRow.label(): String = when (this) {
    is BankAssetRow.InterestJarRow -> "Interest Jar"
    is BankAssetRow.GoalRow -> goal.name
    is BankAssetRow.WeeklyRow -> plan.name
    is BankAssetRow.Grow31Row -> plan.name
    is BankAssetRow.UpfrontRow -> "12-Month Deposit"
    is BankAssetRow.YouthRow -> "Youth Account"
}

private fun BankAssetRow.balance(): Double = when (this) {
    is BankAssetRow.InterestJarRow -> jar.balance
    is BankAssetRow.GoalRow -> goal.currentAmount
    is BankAssetRow.WeeklyRow -> plan.currentAmount
    is BankAssetRow.Grow31Row -> plan.totalSaved
    is BankAssetRow.UpfrontRow -> deposit.principal
    is BankAssetRow.YouthRow -> account.balance
}

@Composable
fun ItundaBankAssetsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var rows by remember { mutableStateOf<List<BankAssetRow>?>(null) }
    var openRow by remember { mutableStateOf<BankAssetRow?>(null) }

    LaunchedEffect(Unit) {
        val jar = try { NetworkClient.apiService.getInterestJar().jar } catch (e: Exception) { null }
        val goals = try { NetworkClient.apiService.getSavingsGoals().goals.filter { it.status == "active" } } catch (e: Exception) { emptyList() }
        val weekly = try { NetworkClient.apiService.getWeeklySavingsPlans().plans.filter { it.status == "ACTIVE" } } catch (e: Exception) { emptyList() }
        val grow31 = try { NetworkClient.apiService.getGrow31SavingsPlans().plans.filter { it.status == "ACTIVE" } } catch (e: Exception) { emptyList() }
        val upfront = try { NetworkClient.apiService.getUpfrontDeposits().deposits.filter { it.withdrawnAt == null } } catch (e: Exception) { emptyList() }
        val youth = try { NetworkClient.apiService.getAccounts().accounts.find { it.type == "MINI" } } catch (e: Exception) { null }
        rows = buildList {
            jar?.let { add(BankAssetRow.InterestJarRow(it)) }
            goals.forEach { add(BankAssetRow.GoalRow(it)) }
            weekly.forEach { add(BankAssetRow.WeeklyRow(it)) }
            grow31.forEach { add(BankAssetRow.Grow31Row(it)) }
            upfront.forEach { add(BankAssetRow.UpfrontRow(it)) }
            youth?.let { add(BankAssetRow.YouthRow(it)) }
        }
    }

    openRow?.let { row ->
        val backToList = { openRow = null }
        when (row) {
            is BankAssetRow.InterestJarRow -> BucketDetailScreen(
                title = "Interest Jar", subtitle = "Safe Box", balanceText = String.format(Locale.US, "%,.0f RWF", row.jar.balance),
                fetchTransactions = { NetworkClient.apiService.getInterestJarTransactions().transactions },
                onBack = backToList,
            )
            is BankAssetRow.GoalRow -> BucketDetailScreen(
                title = row.goal.name, subtitle = "Savings Goal", balanceText = String.format(Locale.US, "%,.0f RWF", row.goal.currentAmount),
                fetchTransactions = { NetworkClient.apiService.getSavingsGoalTransactions(row.goal.id).transactions },
                onBack = backToList,
            )
            is BankAssetRow.WeeklyRow -> BucketDetailScreen(
                title = row.plan.name, subtitle = "26-Week Savings", balanceText = String.format(Locale.US, "%,.0f RWF", row.plan.currentAmount),
                fetchTransactions = { NetworkClient.apiService.getWeeklySavingsPlanTransactions(row.plan.id).transactions },
                onBack = backToList,
            )
            is BankAssetRow.Grow31Row -> BucketDetailScreen(
                title = row.plan.name, subtitle = "31-Day Savings", balanceText = String.format(Locale.US, "%,.0f RWF", row.plan.totalSaved),
                fetchTransactions = { NetworkClient.apiService.getGrow31SavingsPlanTransactions(row.plan.id).transactions },
                onBack = backToList,
            )
            is BankAssetRow.UpfrontRow -> BucketDetailScreen(
                title = "12-Month Deposit", subtitle = "Upfront Interest Deposit", balanceText = String.format(Locale.US, "%,.0f RWF", row.deposit.principal),
                fetchTransactions = { NetworkClient.apiService.getUpfrontDepositTransactions(row.deposit.id).transactions },
                onBack = backToList,
            )
            is BankAssetRow.YouthRow -> BucketDetailScreen(
                title = "Youth Account", subtitle = row.account.accountNumber, balanceText = String.format(Locale.US, "%,.0f RWF", row.account.balance),
                fetchTransactions = {
                    val txs = NetworkClient.apiService.getAccountTransactionHistory(row.account.id).transactions.sortedByDescending { it.createdAt }
                    var runningBalance = row.account.balance
                    txs.map { tx ->
                        val balanceAfter = runningBalance
                        runningBalance -= tx.amount
                        BucketTransactionDto(id = tx.id, description = tx.description, amount = tx.amount, isCredit = true, balanceAfter = balanceAfter, createdAt = tx.createdAt)
                    }
                },
                onBack = backToList,
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp)) {
            Box(
                modifier = Modifier.size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IdsIcons.Back, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
        }
        Text(
            stringResource(R.string.itunda_bank_assets_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary,
            modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 4.dp),
        )
        when {
            rows == null -> Text(
                stringResource(R.string.loading), color = Ids.colors.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 16.dp),
            )
            rows!!.isEmpty() -> Text(
                stringResource(R.string.itunda_bank_assets_empty), color = Ids.colors.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 24.dp),
            )
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)) {
                items(rows!!) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().pressScaleClickable { openRow = row }.padding(vertical = 12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    ) {
                        Text(row.label(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                        Text(String.format(Locale.US, "%,.0f RWF", row.balance()), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
                    }
                }
            }
        }
    }
}
