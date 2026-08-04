package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
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
import rw.itunda.core.network.CreateWeeklySavingsPlanRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.WeeklySavingsInstallmentDto
import rw.itunda.core.network.WeeklySavingsPlanDetailResponse
import rw.itunda.core.network.WeeklySavingsPlanDto
import rw.itunda.core.network.superAppErrorMessage
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import java.io.IOException
import java.math.BigDecimal

// Real Toss/KakaoBank 26주적금-style escalating savings plan UI (2026-07-21) -- the
// first mobile UI this feature has ever had; the backend (WeeklySavingsController.kt/
// WeeklySavingsService.kt) has been real and ledger-backed since it was built. Direct
// sibling of InvestScreen.kt: same no-ViewModel, NetworkClient-direct-from-Composable
// shape, same Toss* color aliases/Ids.layout tokens, same BackTopBar/error-card pattern.
//
// TERM_WEEKS/ESCALATION_STEP_WEEKS are display-only constants mirrored from
// WeeklySavingsService.kt -- there's no endpoint that exposes them since they never
// change for any plan.
private const val TERM_WEEKS = 26
private const val ESCALATION_STEP_WEEKS = 4

private data class EscalationOption(val rate: BigDecimal, val label: String)

private val escalationOptions = listOf(
    EscalationOption(BigDecimal("0.00"), "Flat"),
    EscalationOption(BigDecimal("0.10"), "+10%"),
    EscalationOption(BigDecimal("0.20"), "+20%"),
    EscalationOption(BigDecimal("0.30"), "+30%"),
    EscalationOption(BigDecimal("0.50"), "+50%"),
    EscalationOption(BigDecimal("1.00"), "+100%"),
)

private enum class WeeklySavingsMode { LIST, NEW }

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
            mode == WeeklySavingsMode.NEW -> "New 26-week plan"
            else -> "26-Week Savings"
        }
        val backAction: () -> Unit = when {
            selectedPlanId != null -> { { selectedPlanId = null; refreshKey++ } }
            mode == WeeklySavingsMode.NEW -> { { mode = WeeklySavingsMode.LIST } }
            else -> onBack
        }
        BackTopBar(title = title, onBack = backAction)

        when {
            selectedPlanId != null -> WeeklySavingsDetailContent(planId = selectedPlanId!!, onChanged = { refreshKey++ })
            mode == WeeklySavingsMode.NEW -> WeeklySavingsCreateContent(onCreated = { mode = WeeklySavingsMode.LIST; refreshKey++ })
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(TossBlue).clickable { mode = WeeklySavingsMode.NEW }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ New 26-week plan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                when {
                    listError != null -> item { ErrorCard(listError!!, onRetry = ::loadPlans) }
                    plans == null -> item {
                        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {}
                    }
                    plans!!.isEmpty() -> item {
                        EmptyState(
                            "No 26-week plans yet — start one below. The weekly amount steps up automatically every " +
                                "$ESCALATION_STEP_WEEKS weeks, and keeping an unbroken streak all the way to week " +
                                "$TERM_WEEKS earns a bonus interest rate on top.",
                        )
                    }
                    else -> items(plans!!) { plan -> WeeklySavingsPlanRow(plan, onClick = { selectedPlanId = plan.id }) }
                }
            }
        }
    }
}

@Composable
private fun WeeklySavingsPlanRow(plan: WeeklySavingsPlanDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = TossCard),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(plan.name, color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(planStatusLabel(plan), color = planStatusColor(plan), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text("${formatMoneyWeekly(plan.currentAmount)} RWF", color = TossText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Week ${plan.weeksElapsed.coerceAtMost(TERM_WEEKS)}/$TERM_WEEKS", color = TossSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            WeeklyProgressBar(progress = plan.weeksElapsed.toFloat() / TERM_WEEKS.toFloat())
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (plan.streakBroken) "Streak broken -- bonus forfeited" else "On streak -- bonus rate on track",
                color = if (plan.streakBroken) Ids.colors.danger else Ids.colors.success,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun planStatusLabel(plan: WeeklySavingsPlanDto): String = when (plan.status) {
    "ACTIVE" -> "Active"
    "MATURED" -> if (plan.withdrawnAt != null) "Withdrawn" else "Matured"
    "CANCELLED" -> "Cancelled"
    else -> plan.status
}

@Composable
private fun planStatusColor(plan: WeeklySavingsPlanDto): Color = when (plan.status) {
    "ACTIVE" -> TossBlue
    "MATURED" -> if (plan.withdrawnAt != null) TossSecondary else Ids.colors.success
    "CANCELLED" -> Ids.colors.danger
    else -> TossSecondary
}

@Composable
private fun WeeklyProgressBar(progress: Float) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(TossCardSoft)) {
        Box(
            modifier = Modifier.fillMaxWidth(clamped).fillMaxHeight()
                .clip(RoundedCornerShape(4.dp)).background(TossBlue),
        )
    }
}

@Composable
private fun WeeklySavingsCreateContent(onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var baseAmount by remember { mutableStateOf("") }
    var selectedRate by remember { mutableStateOf(escalationOptions.first()) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun submit() {
        val amountBd = baseAmount.trim().toBigDecimalOrNull()
        if (name.isBlank()) {
            error = "Give your plan a name."
            return
        }
        if (amountBd == null || amountBd <= BigDecimal.ZERO) {
            error = "Enter a real weekly amount."
            return
        }
        submitting = true
        coroutineScope.launch {
            try {
                val request = CreateWeeklySavingsPlanRequest(
                    name = name.trim(),
                    baseWeeklyAmount = amountBd,
                    escalationRate = selectedRate.rate,
                )
                NetworkClient.apiService.createWeeklySavingsPlan(request)
                error = null
                onCreated()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "The weekly amount steps up automatically every $ESCALATION_STEP_WEEKS weeks by your chosen rate, " +
                "and keeping an unbroken streak all the way to week $TERM_WEEKS earns a bonus interest rate on " +
                "top of the base rate.",
            color = TossSecondary, fontSize = 13.sp,
        )
        IdsTextField(value = name, onValueChange = { name = it }, label = "Plan name", modifier = Modifier.fillMaxWidth())
        IdsTextField(value = baseAmount, onValueChange = { baseAmount = it }, label = "Base weekly amount (RWF)", modifier = Modifier.fillMaxWidth())
        Text("Escalation rate", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            escalationOptions.chunked(3).forEach { rowOptions ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        val selected = option.rate == selectedRate.rate
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (selected) TossBlue else TossCardSoft)
                                .clickable { selectedRate = option }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(option.label, color = if (selected) Color.White else TossText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(TossBlue)
                .clickable(enabled = !submitting) { submit() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (submitting) "Working…" else "Start plan", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WeeklySavingsDetailContent(planId: String, onChanged: () -> Unit) {
    var detail by remember { mutableStateOf<WeeklySavingsPlanDetailResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var confirmingCancel by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
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
        }
    }
    LaunchedEffect(planId) { load() }

    fun cancelPlan() {
        submitting = true
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.cancelWeeklySavingsPlan(planId)
                detail = WeeklySavingsPlanDetailResponse(res.success, res.plan, res.walletBalance, res.installments)
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
                detail = WeeklySavingsPlanDetailResponse(res.success, res.plan, res.walletBalance, res.installments)
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

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        detail == null -> Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(220.dp)) {}
        else -> {
            val current = detail!!
            val plan = current.plan
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                        colors = CardDefaults.cardColors(containerColor = TossCard),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(plan.name, color = TossText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${formatMoneyWeekly(current.walletBalance)} RWF", color = TossText, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                            Text("Wallet balance", color = TossSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            WeeklyProgressBar(progress = plan.weeksElapsed.toFloat() / TERM_WEEKS.toFloat())
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Week ${plan.weeksElapsed.coerceAtMost(TERM_WEEKS)} of $TERM_WEEKS -- amount steps up every $ESCALATION_STEP_WEEKS weeks",
                                color = TossSecondary, fontSize = 12.sp,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                if (plan.streakBroken) {
                                    "Streak broken -- bonus rate (+${"%.1f".format(plan.bonusRate)}%) forfeited, " +
                                        "base rate ${"%.1f".format(plan.baseRate)}% still applies"
                                } else {
                                    "On streak -- an unbroken run to maturity earns +${"%.1f".format(plan.bonusRate)}% bonus " +
                                        "on top of the ${"%.1f".format(plan.baseRate)}% base rate"
                                },
                                color = if (plan.streakBroken) Ids.colors.danger else Ids.colors.success,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                            if (plan.status == "ACTIVE") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Next installment due ${formatWeeklyDate(plan.nextInstallmentDueAt)}", color = TossSecondary, fontSize = 12.sp)
                            }
                            plan.totalInterestPaid?.let {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Total interest paid: ${formatMoneyWeekly(it)} RWF", color = TossSecondary, fontSize = 12.sp)
                            }
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
                                color = TossSecondary, fontSize = 13.sp,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(TossCardSoft)
                                        .clickable(enabled = !submitting) { confirmingCancel = false }.padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Keep plan", color = TossText, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.danger)
                                        .clickable(enabled = !submitting) { cancelPlan() }.padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(if (submitting) "Working…" else "Confirm cancel", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        plan.status == "MATURED" && plan.withdrawnAt == null -> ActionButtonWeekly(
                            "Withdraw to main wallet", color = TossBlue, enabled = !submitting,
                        ) { withdraw() }
                        else -> Text(
                            if (plan.status == "CANCELLED") "This plan was cancelled." else "This plan has been withdrawn to your main wallet.",
                            color = TossSecondary, fontSize = 13.sp,
                        )
                    }
                }
                item { Text("Installments", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                if (current.installments.isEmpty()) {
                    item { EmptyState("No installments collected yet.") }
                } else {
                    items(current.installments.sortedByDescending { it.weekNumber }) { installment ->
                        InstallmentRowWeekly(installment)
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
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InstallmentRowWeekly(installment: WeeklySavingsInstallmentDto) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
            .background(TossCard).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Week ${installment.weekNumber}", color = TossText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("${formatMoneyWeekly(installment.amount)} RWF", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun formatWeeklyDate(iso: String): String = try {
    java.time.Instant.parse(iso).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
} catch (_: Exception) {
    iso.take(10)
}

private fun formatMoneyWeekly(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else "%.2f".format(rounded)
}
