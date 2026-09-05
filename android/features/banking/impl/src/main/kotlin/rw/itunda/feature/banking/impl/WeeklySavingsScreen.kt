package rw.itunda.feature.banking.impl

import java.util.Locale
import rw.itunda.core.designsystem.components.formatMoney
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.BucketTransactionRow
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import rw.itunda.core.designsystem.components.SkeletonBlock
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.WeeklySavingsPlanDetailResponse
import rw.itunda.core.network.WeeklySavingsPlanDto
import rw.itunda.core.network.superAppErrorMessage
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import java.io.IOException

// Real Toss/KakaoBank 26주적금-style escalating savings plan UI (2026-07-21) -- the
// first mobile UI this feature has ever had; the backend (WeeklySavingsController.kt/
// WeeklySavingsService.kt) has been real and ledger-backed since it was built. Direct
// sibling of InvestScreen.kt: same no-ViewModel, NetworkClient-direct-from-Composable
// shape, same Toss* color aliases/Ids.layout tokens, same BackTopBar/error-card pattern.
//
// TERM_WEEKS/ESCALATION_STEP_WEEKS are display-only constants mirrored from
// WeeklySavingsService.kt -- there's no endpoint that exposes them since they never
// change for any plan.
internal const val TERM_WEEKS = 26
internal const val ESCALATION_STEP_WEEKS = 4

private enum class WeeklySavingsMode { LIST, INTRO, NEW }

@Composable
fun WeeklySavingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(WeeklySavingsMode.LIST) }
    var selectedPlanId by remember { mutableStateOf<String?>(null) }
    var plans by remember { mutableStateOf<List<WeeklySavingsPlanDto>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    fun loadPlans() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getWeeklySavingsPlans()
                if (res.success) plans = res.plans
                listError = null
            } catch (e: HttpException) {
                listError = superAppErrorMessage(e)
            } catch (e: IOException) {
                listError = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(refreshKey) { loadPlans() }

    Column(modifier = Modifier.fillMaxSize()) {
        val title = when {
            selectedPlanId != null -> "Plan detail"
            mode == WeeklySavingsMode.INTRO -> "26-week plan"
            mode == WeeklySavingsMode.NEW -> "New 26-week plan"
            else -> "26-Week Savings"
        }
        val backAction: () -> Unit = when {
            selectedPlanId != null -> { { selectedPlanId = null; refreshKey++ } }
            mode == WeeklySavingsMode.NEW -> { { mode = WeeklySavingsMode.INTRO } }
            mode == WeeklySavingsMode.INTRO -> { { mode = WeeklySavingsMode.LIST } }
            else -> onBack
        }
        BackTopBar(title = title, onBack = backAction)

        when {
            selectedPlanId != null -> WeeklySavingsDetailContent(planId = selectedPlanId!!, onChanged = { refreshKey++ })
            mode == WeeklySavingsMode.NEW -> WeeklySavingsCreateContent(onCreated = { mode = WeeklySavingsMode.LIST; refreshKey++ })
            mode == WeeklySavingsMode.INTRO -> WeeklySavingsIntroContent(onContinue = { mode = WeeklySavingsMode.NEW })
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Ids.colors.brand).pressScaleClickable { mode = WeeklySavingsMode.INTRO }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ New 26-week plan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                when {
                    listError != null -> item { ErrorCard(listError!!, onRetry = ::loadPlans) }
                    plans == null -> item {
                        SkeletonBlock(height = 120.dp)
                    }
                    plans!!.isEmpty() -> item {
                        EmptyState(
                            "No 26-week plans yet — start one below. The weekly amount steps up automatically every " +
                                "$ESCALATION_STEP_WEEKS weeks, and keeping an unbroken streak all the way to week " +
                                "$TERM_WEEKS earns a bonus interest rate on top.",
                        )
                    }
                    else -> items(plans!!, key = { it.id }) { plan -> WeeklySavingsPlanRow(plan, onClick = { selectedPlanId = plan.id }) }
                }
            }
        }
    }
}

// Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a history/
// progress log of savings plans, kept the per-row Divider convention
// (docs/DESIGN_REFERENCES.md §274).
@Composable
private fun WeeklySavingsPlanRow(plan: WeeklySavingsPlanDto, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(plan.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(planStatusLabel(plan), color = planStatusColor(plan), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text("${formatMoney(plan.currentAmount)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("Week ${plan.weeksElapsed.coerceAtMost(TERM_WEEKS)}/$TERM_WEEKS", color = Ids.colors.textSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        WeeklyProgressBar(progress = plan.weeksElapsed.toFloat() / TERM_WEEKS.toFloat())
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (plan.streakBroken) "Streak broken -- bonus forfeited" else "On streak -- bonus rate on track",
            color = if (plan.streakBroken) Ids.colors.danger else Ids.colors.success,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
    }
    Divider(color = Ids.colors.divider, thickness = 0.5.dp)
}

private fun planStatusLabel(plan: WeeklySavingsPlanDto): String = when (plan.status) {
    "ACTIVE" -> "Active"
    "MATURED" -> if (plan.withdrawnAt != null) "Withdrawn" else "Matured"
    "CANCELLED" -> "Cancelled"
    else -> plan.status
}

@Composable
private fun planStatusColor(plan: WeeklySavingsPlanDto): Color = when (plan.status) {
    "ACTIVE" -> Ids.colors.brand
    "MATURED" -> if (plan.withdrawnAt != null) Ids.colors.textSecondary else Ids.colors.success
    "CANCELLED" -> Ids.colors.danger
    else -> Ids.colors.textSecondary
}

@Composable
private fun WeeklyProgressBar(progress: Float) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ids.colors.surfaceSoft)) {
        Box(
            modifier = Modifier.fillMaxWidth(clamped).fillMaxHeight()
                .clip(RoundedCornerShape(4.dp)).background(Ids.colors.brand),
        )
    }
}

@Composable
private fun WeeklySavingsDetailContent(planId: String, onChanged: () -> Unit) {
    var detail by remember { mutableStateOf<WeeklySavingsPlanDetailResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var confirmingCancel by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    // Real Toss motion research (2026-08-12), same reasoning as Grow31SavingsScreen.kt's
    // own withdrawSuccess: completing a real 26-week savings challenge is a genuine
    // earned milestone with zero success acknowledgment before this fix.
    var withdrawSuccess by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // Real per-bucket ledger (2026-08-31) -- see BucketTransactionList's own doc
    // comment. Replaces the old "Installments" list below with the real transaction
    // ledger this plan's own dedicated account always had, just never exposed.
    var transactions by remember { mutableStateOf<List<rw.itunda.core.network.BucketTransactionDto>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getWeeklySavingsPlan(planId)
                detail = res
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
            try {
                transactions = NetworkClient.apiService.getWeeklySavingsPlanTransactions(planId).transactions
            } catch (e: Exception) {
                transactions = emptyList()
            }
        }
    }
    LaunchedEffect(planId) { load() }

    fun cancelPlan() {
        submitting = true
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.cancelWeeklySavingsPlan(planId)
                detail = WeeklySavingsPlanDetailResponse(res.success, res.plan, res.accountBalance, res.installments)
                actionError = null
                confirmingCancel = false
                onChanged()
            } catch (e: HttpException) {
                actionError = superAppErrorMessage(e)
            } catch (e: IOException) {
                actionError = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    fun withdraw() {
        submitting = true
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.withdrawWeeklySavingsPlan(planId)
                detail = WeeklySavingsPlanDetailResponse(res.success, res.plan, res.accountBalance, res.installments)
                actionError = null
                withdrawSuccess = res.plan.currentAmount to (res.plan.totalInterestPaid ?: 0.0)
                onChanged()
            } catch (e: HttpException) {
                actionError = superAppErrorMessage(e)
            } catch (e: IOException) {
                actionError = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    withdrawSuccess?.let { (totalSaved, interestPaid) ->
        rw.itunda.core.designsystem.components.IdsCelebrationScreen(
            headline = "${formatMoney(totalSaved)} RWF saved",
            message = if (interestPaid > 0.0) {
                "26-week challenge complete -- ${formatMoney(interestPaid)} RWF bonus interest is in your account."
            } else {
                "26-week challenge complete -- moved to your main account."
            },
            celebratory = true,
            onDone = { withdrawSuccess = null },
        )
        return
    }

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        detail == null -> SkeletonBlock(height = 220.dp)
        else -> {
            val current = detail!!
            val plan = current.plan
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                    // this detail screen's own main content (docs/UI_UX_GUIDELINES.md §10).
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(plan.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${formatMoney(current.accountBalance)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Text("Account balance", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        WeeklyProgressBar(progress = plan.weeksElapsed.toFloat() / TERM_WEEKS.toFloat())
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Week ${plan.weeksElapsed.coerceAtMost(TERM_WEEKS)} of $TERM_WEEKS -- amount steps up every $ESCALATION_STEP_WEEKS weeks",
                            color = Ids.colors.textSecondary, fontSize = 12.sp,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            if (plan.streakBroken) {
                                "Streak broken -- bonus rate (+${String.format(Locale.US, "%.1f", plan.bonusRate)}%) forfeited, " +
                                    "base rate ${String.format(Locale.US, "%.1f", plan.baseRate)}% still applies"
                            } else {
                                "On streak -- an unbroken run to maturity earns +${String.format(Locale.US, "%.1f", plan.bonusRate)}% bonus " +
                                    "on top of the ${String.format(Locale.US, "%.1f", plan.baseRate)}% base rate"
                            },
                            color = if (plan.streakBroken) Ids.colors.danger else Ids.colors.success,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                        if (plan.status == "ACTIVE") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Next installment due ${formatWeeklyDate(plan.nextInstallmentDueAt)}", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                        plan.totalInterestPaid?.let {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Total interest paid: ${formatMoney(it)} RWF", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
                actionError?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
                item {
                    when {
                        plan.status == "ACTIVE" && !confirmingCancel -> ActionButtonWeekly(
                            "Cancel plan (early withdrawal)", color = Ids.colors.danger, enabled = !submitting,
                        ) { confirmingCancel = true }
                        plan.status == "ACTIVE" && confirmingCancel -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Cancelling now forfeits your streak bonus -- you'll only get principal plus " +
                                    "base-rate interest, paid out immediately. This can't be undone.",
                                color = Ids.colors.textSecondary, fontSize = 13.sp,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                        .pressScaleClickable(enabled = !submitting) { confirmingCancel = false }.padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Keep plan", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.danger)
                                        .pressScaleClickable(enabled = !submitting) { cancelPlan() }.padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(if (submitting) "Working…" else "Confirm cancel", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        plan.status == "MATURED" && plan.withdrawnAt == null -> ActionButtonWeekly(
                            "Withdraw to main account", color = Ids.colors.brand, enabled = !submitting,
                        ) { withdraw() }
                        else -> Text(
                            if (plan.status == "CANCELLED") "This plan was cancelled." else "This plan has been withdrawn to your main account.",
                            color = Ids.colors.textSecondary, fontSize = 13.sp,
                        )
                    }
                }
                item { Text("Transactions", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                if (transactions == null) {
                    item { Text("Loading…", color = Ids.colors.textSecondary, fontSize = 13.sp) }
                } else if (transactions!!.isEmpty()) {
                    item { EmptyState("No transactions to show yet.") }
                } else {
                    items(transactions!!.sortedByDescending { it.createdAt }, key = { it.id }) { tx ->
                        BucketTransactionRow(tx)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtonWeekly(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(color)
            .pressScaleClickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}


private fun formatWeeklyDate(iso: String): String = try {
    java.time.Instant.parse(iso).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
} catch (_: Exception) {
    iso.take(10)
}

