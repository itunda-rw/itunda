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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import rw.itunda.core.designsystem.components.IdsTextField
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
import rw.itunda.core.network.CreateGrow31SavingsPlanRequest
import rw.itunda.core.network.Grow31SavingsDepositDto
import rw.itunda.core.network.Grow31SavingsPlanDetailResponse
import rw.itunda.core.network.Grow31SavingsPlanDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

// Real Toss Bank 키워봐요 31일적금 (Grow-it 31-day savings) UI (2026-08-12) -- direct
// sibling of WeeklySavingsScreen.kt, same no-ViewModel NetworkClient-direct shape. The
// real structural difference from that screen: deposits are an explicit daily user
// action ("Save today"), not something a scheduler pulls automatically, so this screen
// surfaces that action front and center rather than just a due-date countdown.
//
// TERM_DAYS is a display-only constant mirrored from Grow31SavingsService.kt.
private const val TERM_DAYS = 31

private enum class Grow31Mode { LIST, NEW }

@Composable
fun Grow31SavingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(Grow31Mode.LIST) }
    var selectedPlanId by remember { mutableStateOf<String?>(null) }
    var plans by remember { mutableStateOf<List<Grow31SavingsPlanDto>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    fun loadPlans() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getGrow31SavingsPlans()
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
            mode == Grow31Mode.NEW -> "New 31-day plan"
            else -> "31-Day Savings"
        }
        val backAction: () -> Unit = when {
            selectedPlanId != null -> { { selectedPlanId = null; refreshKey++ } }
            mode == Grow31Mode.NEW -> { { mode = Grow31Mode.LIST } }
            else -> onBack
        }
        BackTopBar(title = title, onBack = backAction)

        when {
            selectedPlanId != null -> Grow31DetailContent(planId = selectedPlanId!!, onChanged = { refreshKey++ })
            mode == Grow31Mode.NEW -> Grow31CreateContent(onCreated = { mode = Grow31Mode.LIST; refreshKey++ })
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Ids.colors.brand).pressScaleClickable { mode = Grow31Mode.NEW }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ New 31-day plan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                when {
                    listError != null -> item { ErrorCard(listError!!, onRetry = ::loadPlans) }
                    plans == null -> item {
                        SkeletonBlock(height = 120.dp)
                    }
                    plans!!.isEmpty() -> item {
                        EmptyState(
                            "No 31-day plans yet — start one below. Save a small fixed amount once every real day; " +
                                "the longer your unbroken daily streak, the higher your bonus rate -- up to +10% on top " +
                                "of the base rate if you save all $TERM_DAYS days in a row.",
                        )
                    }
                    else -> items(plans!!, key = { it.id }) { plan -> Grow31PlanRow(plan, onClick = { selectedPlanId = plan.id }) }
                }
            }
        }
    }
}

// Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a history/
// progress log of savings plans, kept the per-row Divider convention
// (docs/DESIGN_REFERENCES.md §274).
@Composable
private fun Grow31PlanRow(plan: Grow31SavingsPlanDto, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(plan.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(grow31StatusLabel(plan), color = grow31StatusColor(plan), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text("${formatMoneyGrow31(plan.totalSaved)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("Day ${plan.daysElapsed.coerceAtMost(TERM_DAYS)}/$TERM_DAYS -- streak ${plan.currentStreak}", color = Ids.colors.textSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Grow31ProgressBar(progress = plan.daysElapsed.toFloat() / TERM_DAYS.toFloat())
        Spacer(modifier = Modifier.height(8.dp))
        val bonus = grow31BonusRateForStreak(plan.longestStreak)
        Text(
            if (bonus > 0.0) "Longest streak ${plan.longestStreak} days -- +${"%.0f".format(bonus)}% bonus locked in so far" else "Save today to start your streak",
            color = if (bonus > 0.0) Ids.colors.success else Ids.colors.textSecondary,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
    }
    Divider(color = Ids.colors.divider, thickness = 0.5.dp)
}

private fun grow31StatusLabel(plan: Grow31SavingsPlanDto): String = when (plan.status) {
    "ACTIVE" -> "Active"
    "MATURED" -> if (plan.withdrawnAt != null) "Withdrawn" else "Matured"
    "CANCELLED" -> "Cancelled"
    else -> plan.status
}

@Composable
private fun grow31StatusColor(plan: Grow31SavingsPlanDto): Color = when (plan.status) {
    "ACTIVE" -> Ids.colors.brand
    "MATURED" -> if (plan.withdrawnAt != null) Ids.colors.textSecondary else Ids.colors.success
    "CANCELLED" -> Ids.colors.danger
    else -> Ids.colors.textSecondary
}

// Real, sourced tier table mirrored from Grow31SavingsService.bonusRateForStreak on the
// backend (display-only copy, same convention TERM_DAYS above already follows).
private fun grow31BonusRateForStreak(streak: Int): Double = when {
    streak >= 31 -> 10.0
    streak >= 21 -> 8.0
    streak >= 14 -> 6.0
    streak >= 7 -> 4.0
    streak >= 3 -> 3.0
    else -> 0.0
}

@Composable
private fun Grow31ProgressBar(progress: Float) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ids.colors.surfaceSoft)) {
        Box(
            modifier = Modifier.fillMaxWidth(clamped).fillMaxHeight()
                .clip(RoundedCornerShape(4.dp)).background(Ids.colors.brand),
        )
    }
}

@Composable
private fun Grow31CreateContent(onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var dailyAmount by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun submit() {
        val amountBd = dailyAmount.trim().toBigDecimalOrNull()
        if (name.isBlank()) {
            error = "Give your plan a name."
            return
        }
        if (amountBd == null || amountBd <= BigDecimal.ZERO) {
            error = "Enter a real daily amount."
            return
        }
        submitting = true
        coroutineScope.launch {
            try {
                val idempotencyKey = UUID.randomUUID().toString()
                val request = CreateGrow31SavingsPlanRequest(name = name.trim(), dailyAmount = amountBd)
                NetworkClient.apiService.createGrow31SavingsPlan(idempotencyKey, request)
                error = null
                onCreated()
                rw.itunda.core.designsystem.components.IdsToast.show(coroutineScope, "31-day plan started.")
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    rw.itunda.core.designsystem.components.FixedBottomCta(
        content = {
            Text(
                "Pick a small amount you can realistically save every single day for $TERM_DAYS days. Miss a day and " +
                    "your streak resets -- but your longest streak still locks in a bonus rate at maturity, up to +10% " +
                    "for a full unbroken run.",
                color = Ids.colors.textSecondary, fontSize = 13.sp,
            )
            IdsTextField(value = name, onValueChange = { name = it }, label = "Plan name", modifier = Modifier.fillMaxWidth())
            Text("Daily amount", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            rw.itunda.core.designsystem.components.AmountKeypadInput(
                digits = dailyAmount, onDigitsChange = { dailyAmount = it },
                quickAmounts = listOf(500L, 1_000L),
            )
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
        },
        cta = {
            rw.itunda.core.designsystem.components.IdsButton(
                text = if (submitting) "Working…" else "Start plan",
                onClick = { submit() },
                enabled = !submitting,
            )
        },
    )
}

@Composable
private fun Grow31DetailContent(planId: String, onChanged: () -> Unit) {
    var detail by remember { mutableStateOf<Grow31SavingsPlanDetailResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var confirmingCancel by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    // Real Toss motion research (2026-08-12): "confetti for positive moments like a
    // credit-score increase or payday" -- completing a real 31-day savings challenge is
    // exactly that kind of earned milestone, arguably closer to Toss's own example than
    // this app's one other celebratory moment (claiming savings interest). Before this,
    // a matured-plan withdrawal had ZERO success acknowledgment of any kind: withdraw()
    // just silently updated `detail` in place and the screen re-rendered with a plain
    // "withdrawn" status label. Captures the real totals from the withdrawal response
    // itself (not the post-withdrawal `detail`, which no longer reflects the just-paid
    // interest) so the celebration message stays accurate.
    var withdrawSuccess by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getGrow31SavingsPlan(planId)
                detail = res
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(planId) { load() }

    fun depositToday() {
        submitting = true
        coroutineScope.launch {
            try {
                val idempotencyKey = UUID.randomUUID().toString()
                val res = NetworkClient.apiService.depositGrow31SavingsToday(planId, idempotencyKey)
                detail = Grow31SavingsPlanDetailResponse(res.success, res.plan, res.accountBalance, res.deposits)
                actionError = null
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

    fun cancelPlan() {
        submitting = true
        coroutineScope.launch {
            try {
                val idempotencyKey = UUID.randomUUID().toString()
                val res = NetworkClient.apiService.cancelGrow31SavingsPlan(planId, idempotencyKey)
                detail = Grow31SavingsPlanDetailResponse(res.success, res.plan, res.accountBalance, res.deposits)
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
                val idempotencyKey = UUID.randomUUID().toString()
                val res = NetworkClient.apiService.withdrawGrow31SavingsPlan(planId, idempotencyKey)
                detail = Grow31SavingsPlanDetailResponse(res.success, res.plan, res.accountBalance, res.deposits)
                actionError = null
                withdrawSuccess = res.plan.totalSaved to (res.plan.totalInterestPaid ?: 0.0)
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
            headline = "${formatMoneyGrow31(totalSaved)} RWF saved",
            message = if (interestPaid > 0.0) {
                "31-day challenge complete -- ${formatMoneyGrow31(interestPaid)} RWF bonus interest is in your account."
            } else {
                "31-day challenge complete -- moved to your main account."
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
            val today = java.time.LocalDate.now().toString()
            val alreadyDepositedToday = plan.lastDepositDate == today
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
                        Text("${formatMoneyGrow31(current.accountBalance)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Text("Account balance", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Grow31ProgressBar(progress = plan.daysElapsed.toFloat() / TERM_DAYS.toFloat())
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Day ${plan.daysElapsed.coerceAtMost(TERM_DAYS)} of $TERM_DAYS", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Current streak: ${plan.currentStreak} days -- longest: ${plan.longestStreak} days",
                            color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                        val bonus = grow31BonusRateForStreak(plan.longestStreak)
                        Text(
                            if (bonus > 0.0) {
                                "+${"%.0f".format(bonus)}% bonus locked in on top of the ${"%.1f".format(plan.baseRate)}% base rate"
                            } else {
                                "Save 3 days in a row to unlock your first bonus tier"
                            },
                            color = if (bonus > 0.0) Ids.colors.success else Ids.colors.textSecondary,
                            fontSize = 13.sp,
                        )
                        plan.totalInterestPaid?.let {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Total interest paid: ${formatMoneyGrow31(it)} RWF", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
                actionError?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
                item {
                    when {
                        plan.status == "ACTIVE" && !alreadyDepositedToday && !confirmingCancel -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButtonGrow31("Save today (+${formatMoneyGrow31(plan.dailyAmount)} RWF)", color = Ids.colors.brand, enabled = !submitting) { depositToday() }
                            ActionButtonGrow31("Cancel plan (early withdrawal)", color = Ids.colors.danger, enabled = !submitting) { confirmingCancel = true }
                        }
                        plan.status == "ACTIVE" && alreadyDepositedToday && !confirmingCancel -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("You've already saved today -- come back tomorrow to keep your streak.", color = Ids.colors.success, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            ActionButtonGrow31("Cancel plan (early withdrawal)", color = Ids.colors.danger, enabled = !submitting) { confirmingCancel = true }
                        }
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
                        plan.status == "MATURED" && plan.withdrawnAt == null -> ActionButtonGrow31(
                            "Withdraw to main account", color = Ids.colors.brand, enabled = !submitting,
                        ) { withdraw() }
                        else -> Text(
                            if (plan.status == "CANCELLED") "This plan was cancelled." else "This plan has been withdrawn to your main account.",
                            color = Ids.colors.textSecondary, fontSize = 13.sp,
                        )
                    }
                }
                item { Text("Deposits", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                if (current.deposits.isEmpty()) {
                    item { EmptyState("No deposits yet.") }
                } else {
                    items(current.deposits.sortedByDescending { it.dayNumber }, key = { it.dayNumber }) { deposit ->
                        DepositRowGrow31(deposit)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtonGrow31(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(color)
            .pressScaleClickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DepositRowGrow31(deposit: Grow31SavingsDepositDto) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
            .background(Ids.colors.surface).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Day ${deposit.dayNumber}", color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Streak ${deposit.streakAtDeposit}", color = Ids.colors.textSecondary, fontSize = 11.sp)
        }
        Text("${formatMoneyGrow31(deposit.amount)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun formatMoneyGrow31(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) "%,d".format(rounded.toLong()) else "%,.2f".format(rounded)
}
