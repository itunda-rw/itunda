package rw.itunda.feature.banking.impl

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Agriculture
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.UPFRONT_DEPOSIT_ANNUAL_RATE
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.rememberCountUp
import rw.itunda.core.designsystem.theme.AccentIndigo
import rw.itunda.core.designsystem.theme.AccentOrange
import rw.itunda.core.designsystem.theme.AccentPurple
import rw.itunda.core.designsystem.theme.AccentTeal
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.BucketDetailTarget
import rw.itunda.core.network.MoneyActionResult

// Real, display-only mirrors of each product's own real backend rate constant --
// same convention Grow31SavingsScreen's own grow31BonusRateForStreak() already
// establishes, just file-scoped here since BankHubScreen's hub-level teaser rows
// (below) need them before any specific plan exists to read a real per-plan rate
// off of. Keep in sync with the real source: WeeklySavingsService.BASE_RATE/
// BONUS_RATE, Grow31SavingsService's real streak-bonus tier table (10.0 at the max
// 31-day streak).
private const val BANK_HUB_WEEKLY_SAVINGS_BASE_RATE = 5.0
private const val BANK_HUB_GROW31_MAX_BONUS_RATE = 10.0
// Consolidated 2026-09-06 into core/designsystem's UpfrontDepositPolicy.kt -- see its
// own doc comment. Unlike the two rates above, this one now shares the exact same
// constant UpfrontDepositScreen.kt itself uses, not just a manually-kept-in-sync copy.
private const val BANK_HUB_UPFRONT_DEPOSIT_ANNUAL_RATE = UPFRONT_DEPOSIT_ANNUAL_RATE

// Real itunda Bank product hub (2026-08-11) -- see the doc comment on the showBank
// state var in :app's ItundaAppScreen for the full "itunda Bank vs itunda Pay/account"
// naming research this came out of. Every row here is a real, already-built screen
// (see LoansScreen.kt/InvestScreen.kt/SaccoScreen.kt/IkiminaScreen.kt/etc.'s own doc
// comments) -- this just gives them a shared front door with real aggregate data,
// instead of each living as an unconnected flat row.
//
// Moved into :features:banking:impl (2026-09-02, Banking Feature-module decomposition
// slice 5, following the user's explicit "Full ItundaAppScreen decomposition" scope
// choice) -- the last remaining blocker after slices 1-4 (ShellRow/ShellSection,
// RoundUpSettingsDialog, NewSavingsGoalDialog, and 45 R.string.* keys migrated into
// this module's own strings.xml) was resolved the same slice. See
// [[project_itunda_feature_isolation]] for the full account.
@Composable
fun BankHubScreen(
    // Real decoupling (2026-09-02, slice 2) -- was `viewModel: MainViewModel` (the
    // whole 674-line, :app-only ViewModel); narrowed to just the 5 StateFlow values
    // this screen actually reads, hoisted to plain params so this composable has zero
    // MainViewModel/:app dependency.
    primaryAccount: rw.itunda.core.network.Account?,
    savingsGoals: List<rw.itunda.core.network.SavingsGoal>,
    interestJar: rw.itunda.core.network.InterestJar?,
    roundUpSettings: rw.itunda.core.network.RoundUpSettingsDto?,
    spendingInsight: rw.itunda.core.network.SpendingInsightResponse?,
    onSetRoundUpSettings: suspend (enabled: Boolean, roundToNearest: Long, targetGoalId: String?, targetStockId: String?) -> MoneyActionResult,
    onCreateSavingsGoal: suspend (name: String, targetAmountRwf: Long, monthlyContributionRwf: Long?, targetDate: String?) -> MoneyActionResult,
    onBack: () -> Unit,
    onDepositToGoal: (goalId: String, goalName: String) -> Unit,
    onWithdrawFromGoal: (goalId: String, goalName: String, currentAmount: Double) -> Unit,
    onClaimInterest: () -> Unit,
    onOpenBucketDetail: (BucketDetailTarget) -> Unit = {},
    onOpenSacco: () -> Unit,
    onOpenIkimina: () -> Unit,
    onOpenMotoOwnership: () -> Unit,
    onOpenHarvestAdvance: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenInvest: () -> Unit,
    onOpenWeeklySavings: () -> Unit,
    onOpenGrow31Savings: () -> Unit,
    onOpenUpfrontDeposit: () -> Unit,
    onOpenVupLoan: () -> Unit,
    onOpenStudentLoan: () -> Unit,
    autoTransferCount: Int = 0,
    onOpenAutoTransfers: () -> Unit = {},
    onOpenCreditScore: () -> Unit = {},
    onOpenSpendingInsight: () -> Unit = {},
    onOpenAccountDetail: () -> Unit = {},
    onOpenPay: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var showRoundUpDialog by remember { mutableStateOf(false) }
    var showNewGoalDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    // Real Deposit Protection Fund status (2026-08-11) -- own-screen fetch, same
    // "each screen fetches its own minimal real data" precedent OverviewScreen's own
    // getProfile() call already establishes, rather than growing MainViewModel's
    // Home-load path with a fetch only this screen needs.
    var depositProtection by remember { mutableStateOf<rw.itunda.core.network.DepositProtectionStatus?>(null) }
    LaunchedEffect(Unit) {
        try {
            depositProtection = rw.itunda.core.network.NetworkClient.apiService.getDepositProtectionStatus().status
        } catch (_: Exception) {
            // Non-critical -- the disclosure copy below still renders without it.
        }
    }
    // Dual-balance UI (2026-08-29) -- same "each screen fetches its own minimal real
    // data" precedent as depositProtection/creditScore above, not a MainViewModel
    // field, matching bank-mfe's identical AccountSummaryRow.tsx fix.
    var payAccount by remember { mutableStateOf<rw.itunda.core.network.Account?>(null) }
    LaunchedEffect(Unit) {
        try {
            payAccount = rw.itunda.core.network.NetworkClient.apiService.getAccounts().accounts.firstOrNull { it.type == "PAY" }
        } catch (_: Exception) {
            // Non-critical -- the secondary line below just won't render without it.
        }
    }
    // Real architectural fix (2026-08-13, direct user directive): credit score and
    // spending insight used to be fetched by HomeTab -- moved here, same "each
    // screen fetches its own minimal real data" precedent as depositProtection
    // above, now that Bank (not Home) is their real home. spendingInsight itself is
    // now a plain param (see this function's own signature comment).
    val spendingTopCategory = spendingInsight?.categories?.maxByOrNull { it.amount.toDouble() }
    var creditScore by remember { mutableStateOf<rw.itunda.core.network.CreditScoreResponse?>(null) }
    LaunchedEffect(Unit) {
        try {
            creditScore = rw.itunda.core.network.NetworkClient.apiService.getCreditScore()
        } catch (_: Exception) {
            // Non-critical -- the row just won't render if this fails.
        }
    }
    // Real stock-destination option for round-up (Wealth product-completeness
    // pass, 2026-09-06) -- same "each screen fetches its own minimal real data"
    // precedent as depositProtection/creditScore above. Fetched once the dialog
    // is actually opened, not on every BankHubScreen composition -- unlike the
    // three fetches above, a stock list is real Invest-market data this screen
    // otherwise has no use for.
    var roundUpStocks by remember { mutableStateOf<List<rw.itunda.core.network.StockDto>>(emptyList()) }
    LaunchedEffect(showRoundUpDialog) {
        if (showRoundUpDialog) {
            try {
                roundUpStocks = rw.itunda.core.network.NetworkClient.apiService.getStocks().stocks
            } catch (_: Exception) {
                // Non-critical -- the stock-destination radio list just stays empty.
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = stringResource(R.string.bank_title), onBack = onBack)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
        ) {
            if (primaryAccount != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenAccountDetail).padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(stringResource(R.string.bank_account_account), fontSize = 13.sp, color = Ids.colors.textSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            val animatedBalance = rememberCountUp(primaryAccount!!.balance)
                            Text(
                                String.format(Locale.US, "%,.0f ${primaryAccount!!.currency}", animatedBalance),
                                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary,
                            )
                        }
                        Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary)
                    }
                }
            }
            // Dual-balance UI (2026-08-29): a small flat secondary "itunda Pay" line,
            // matching bank-mfe's identical AccountSummaryRow.tsx fix.
            if (payAccount != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenPay).padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.pay_title), fontSize = 14.sp, color = Ids.colors.textSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                String.format(Locale.US, "%,.0f ${payAccount!!.currency}", payAccount!!.balance),
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textSecondary,
                            )
                            Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenAutoTransfers).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Ids.colors.chip),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = Ids.colors.textPrimary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.home_auto_transfer_title), color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (autoTransferCount > 0) stringResource(R.string.home_auto_transfer_active, autoTransferCount) else stringResource(R.string.home_auto_transfer_setup),
                            color = Ids.colors.textSecondary,
                            fontSize = 13.sp,
                        )
                        Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(18.dp))
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = Ids.colors.divider, thickness = 0.5.dp)
            }
            item {
                val roundUpOff = stringResource(R.string.home_round_up_off)
                val roundUpOn = stringResource(R.string.home_round_up_on)
                val roundUpSetUp = stringResource(R.string.home_round_up_set_up)
                val interestJarLabel = stringResource(R.string.home_interest_jar)
                val roundUpTitle = stringResource(R.string.home_round_up_title)
                val roundUpRoundingText = roundUpSettings?.roundToNearest?.let { stringResource(R.string.home_round_up_rounding, String.format(Locale.US, "%,.0f", it)) }
                val savingsProgressPattern = stringResource(R.string.home_savings_progress)
                val savingsProgressByDatePattern = stringResource(R.string.home_savings_progress_by_date)
                ShellSection(
                    title = stringResource(R.string.bank_save_grow),
                    rows = buildList {
                        interestJar?.let { jar ->
                            add(
                                ShellRow(
                                    interestJarLabel,
                                    if (jar.earnedTotal > 0.0) {
                                        stringResource(
                                            R.string.home_interest_jar_rate_subtitle_total,
                                            String.format(Locale.US, "%.1f", jar.rate),
                                            String.format(Locale.US, "%,.0f", jar.earnedTotal),
                                        )
                                    } else {
                                        stringResource(R.string.home_interest_jar_rate_subtitle, String.format(Locale.US, "%.1f", jar.rate))
                                    },
                                    String.format(Locale.US, "%,.0f RWF", jar.earnedThisMonth),
                                    Icons.Outlined.Savings,
                                    AccentOrange,
                                    onClick = { onOpenBucketDetail(BucketDetailTarget.InterestJar) },
                                )
                            )
                        }
                        savingsGoals.forEach { goal ->
                            val progressPercent = if (goal.targetAmount > 0) {
                                (goal.currentAmount / goal.targetAmount * 100).toInt()
                            } else 0
                            val completed = goal.status == "completed"
                            add(
                                ShellRow(
                                    goal.name,
                                    (
                                        goal.targetDate?.takeIf { it.isNotBlank() }?.let { date ->
                                            savingsProgressByDatePattern.format(
                                                String.format(Locale.US, "%,.0f", goal.currentAmount),
                                                String.format(Locale.US, "%,.0f", goal.targetAmount),
                                                date.take(10),
                                            )
                                        } ?: savingsProgressPattern.format(String.format(Locale.US, "%,.0f", goal.currentAmount), String.format(Locale.US, "%,.0f", goal.targetAmount))
                                    ) + if (completed) " · Completed 🎉" else "",
                                    "$progressPercent%",
                                    Icons.Outlined.Savings,
                                    AccentIndigo,
                                    onClick = { onOpenBucketDetail(BucketDetailTarget.Goal(goal.id, goal.name, goal.currentAmount, goal.targetAmount)) },
                                    secondaryAction = if (goal.currentAmount > 0) "Withdraw" else null,
                                    onSecondaryClick = if (goal.currentAmount > 0) {
                                        { onWithdrawFromGoal(goal.id, goal.name, goal.currentAmount) }
                                    } else null,
                                )
                            )
                        }
                        add(
                            ShellRow(
                                stringResource(R.string.savings_new_goal_title),
                                stringResource(R.string.savings_new_goal_subtitle),
                                "+",
                                Icons.Outlined.Savings,
                                AccentTeal,
                                onClick = { showNewGoalDialog = true },
                            )
                        )
                        if (savingsGoals.isNotEmpty()) {
                            add(
                                ShellRow(
                                    roundUpTitle,
                                    if (roundUpSettings?.enabled == true) {
                                        roundUpRoundingText ?: roundUpOff
                                    } else roundUpOff,
                                    if (roundUpSettings?.enabled == true) roundUpOn else roundUpSetUp,
                                    Icons.Outlined.CurrencyExchange,
                                    AccentPurple,
                                    onClick = { showRoundUpDialog = true },
                                )
                            )
                        }
                        add(ShellRow("26-week savings", "$BANK_HUB_WEEKLY_SAVINGS_BASE_RATE% base rate, escalates weekly", ">", Icons.Outlined.CalendarMonth, AccentIndigo, onClick = onOpenWeeklySavings))
                        add(ShellRow("31-day savings", "Daily streak, up to $BANK_HUB_GROW31_MAX_BONUS_RATE% bonus rate", ">", Icons.Outlined.Bolt, AccentOrange, onClick = onOpenGrow31Savings))
                        add(ShellRow("12-month deposit", "$BANK_HUB_UPFRONT_DEPOSIT_ANNUAL_RATE%/yr interest paid upfront, principal locked", ">", Icons.Outlined.Lock, AccentPurple, onClick = onOpenUpfrontDeposit))
                        add(ShellRow(stringResource(R.string.home_coop_rail_ikimina_title), stringResource(R.string.home_coop_rail_ikimina_subtitle), ">", Icons.Outlined.Groups, AccentTeal, onClick = onOpenIkimina))
                        add(ShellRow(stringResource(R.string.home_coop_rail_sacco_title), stringResource(R.string.home_coop_rail_sacco_subtitle), ">", Icons.Outlined.AccountBalance, AccentPurple, onClick = onOpenSacco))
                        add(ShellRow("Investments", "RSE stocks, bonds & fixed income, IPOs", ">", Icons.Outlined.TrendingUp, AccentTeal, onClick = onOpenInvest))
                    }
                )
            }
            item {
                ShellSection(
                    title = stringResource(R.string.bank_borrow),
                    rows = listOf(
                        ShellRow("Get a loan", "Personal, salary-backed, SME working capital", ">", Icons.Outlined.AccountBalanceWallet, AccentIndigo, onClick = onOpenLoans),
                        ShellRow(stringResource(R.string.home_coop_rail_harvest_title), stringResource(R.string.home_coop_rail_harvest_subtitle), ">", Icons.Outlined.Agriculture, AccentTeal, onClick = onOpenHarvestAdvance),
                        ShellRow("VUP Financial Services", "Means-tested government microloan for farming, livestock, business", ">", IdsIcons.ShieldCheck, AccentIndigo, onClick = onOpenVupLoan),
                        ShellRow("Student loan", "BRD higher-education loan -- 11% undergraduate, 12% postgraduate", ">", Icons.Outlined.School, AccentPurple, onClick = onOpenStudentLoan),
                        ShellRow(stringResource(R.string.home_coop_rail_moto_title), "Save a 30% down payment, then convert to a loan for your own bike", ">", Icons.Outlined.DirectionsBike, AccentTeal, onClick = onOpenMotoOwnership),
                    )
                )
            }
            item {
                ShellSection(
                    title = stringResource(R.string.bank_insights),
                    rows = buildList {
                        if ((spendingInsight?.totalSpent?.toDouble() ?: 0.0) > 0.0) {
                            add(
                                ShellRow(
                                    String.format(Locale.US, "%,.0f RWF", spendingInsight?.totalSpent?.toDouble() ?: 0.0),
                                    if (spendingTopCategory != null) stringResource(R.string.home_spent_period_category, spendingTopCategory.name) else stringResource(R.string.home_spent_period),
                                    ">",
                                    Icons.Outlined.PieChart,
                                    AccentPurple,
                                    onClick = onOpenSpendingInsight,
                                ),
                            )
                        }
                        add(
                            ShellRow(
                                stringResource(R.string.home_credit_score_title),
                                creditScore?.let { stringResource(R.string.home_credit_score_value, it.score) } ?: stringResource(R.string.home_credit_score_subtitle),
                                stringResource(R.string.home_credit_score_action),
                                Icons.Outlined.TrendingUp,
                                AccentPurple,
                                onClick = onOpenCreditScore,
                            ),
                        )
                    },
                )
            }
            // Real licensed-bank/deposit-insurance disclosure (2026-08-11) -- see
            // docs/TOSS_PARITY_MATRIX.md's own confirmation of "zero real banking-
            // license implementation anywhere". Real Deposit Protection Fund card
            // (2026-08-11) -- see DepositProtectionFund.kt's own doc comment: rather
            // than just disclosing an absence, this shows the real, working,
            // ledger-backed reserve itunda maintains as its own internal simulation.
            depositProtection?.let { dp ->
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(stringResource(R.string.bank_deposit_protection_title), color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.bank_deposit_protection_covered), color = Ids.colors.textSecondary, fontSize = 13.sp)
                            Text(String.format(Locale.US, "%,.0f RWF", dp.yourCoveredBalance), color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.bank_deposit_protection_cap, String.format(Locale.US, "%,.0f", dp.coverageCapPerUser)), color = Ids.colors.textTertiary, fontSize = 11.sp)
                        Text(stringResource(R.string.bank_deposit_protection_reserve, String.format(Locale.US, "%,.0f", dp.fundReserveBalance)), color = Ids.colors.textTertiary, fontSize = 11.sp)
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.bank_status_disclosure),
                    color = Ids.colors.textTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
        if (showRoundUpDialog) {
            RoundUpSettingsDialog(
                settings = roundUpSettings,
                goals = savingsGoals,
                stocks = roundUpStocks,
                onDismiss = { showRoundUpDialog = false },
                onSave = { enabled, increment, goalId, stockId ->
                    coroutineScope.launch {
                        onSetRoundUpSettings(enabled, increment, goalId, stockId)
                        showRoundUpDialog = false
                    }
                },
            )
        }
        if (showNewGoalDialog) {
            NewSavingsGoalDialog(
                onDismiss = { showNewGoalDialog = false },
                onCreate = { name, target, monthly, date ->
                    onCreateSavingsGoal(name, target, monthly, date)
                },
                onCreated = { showNewGoalDialog = false },
            )
        }
    }
}
