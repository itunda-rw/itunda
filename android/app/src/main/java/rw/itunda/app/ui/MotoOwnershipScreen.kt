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
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ContributeToMotoOwnershipPlanRequest
import rw.itunda.core.network.CreateMotoOwnershipPlanRequest
import rw.itunda.core.network.MotoOwnershipPlanDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RepayMotoOwnershipPlanRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import rw.itunda.core.designsystem.components.EmptyState

/**
 * Real Rwanda moto-taxi ownership savings-to-loan plan -- sourced beyond this
 * session's usual Toss/Kakao/Naver/Coupang reference ecosystems. A real ~600,000 RWF
 * entry-level moto-taxi bike is a documented purchase price (Anadolu Agency, 14 May
 * 2021 -- profiles a rider who saved for years to buy her own bike after paying daily
 * rent to a bike owner). Rent-to-own is a proven-relevant mechanic in this exact
 * sector (Frontier Tech Hub's Kigali e-moto pilot: Ampersand's rent-to-own model
 * increased driver revenue 78%/month; WeeTracker/WEF coverage of the same). This
 * fills the gap left by Rwanda's dissolved taxi-moto cooperatives (Africa-Press,
 * 2026). Honest v1 limitation: once converted to a loan, this is an UNSECURED
 * facility -- itunda has no path to a real chattel lien or RURA vehicle-registry
 * hold, so it cannot repossess the bike or verify it was actually purchased. Mirrors
 * bank-mfe's MotoOwnershipView exactly, same no-ViewModel, direct-NetworkClient-call
 * convention VupLoanScreen.kt/HarvestAdvanceScreen.kt already established.
 */
@Composable
fun MotoOwnershipScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var plans by remember { mutableStateOf<List<MotoOwnershipPlanDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    var bikePrice by remember { mutableStateOf("") }
    var dailyContribution by remember { mutableStateOf("") }
    var contributeAmounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var repayAmounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                plans = NetworkClient.apiService.getMyMotoOwnershipPlans().plans
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun create() {
        val priceValue = bikePrice.toBigDecimalOrNull()
        val contributionValue = dailyContribution.toBigDecimalOrNull()
        if (priceValue == null || priceValue.signum() <= 0) {
            error = "Enter a valid bike price."
            return
        }
        if (contributionValue == null || contributionValue.signum() <= 0) {
            error = "Enter a valid daily contribution."
            return
        }
        busyId = "create"
        coroutineScope.launch {
            try {
                NetworkClient.apiService.createMotoOwnershipPlan(CreateMotoOwnershipPlanRequest(priceValue, contributionValue))
                bikePrice = ""
                dailyContribution = ""
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyId = null
            }
        }
    }

    fun contribute(planId: String) {
        val value = contributeAmounts[planId]?.toBigDecimalOrNull()
        if (value == null || value.signum() <= 0) {
            error = "Enter a valid contribution amount."
            return
        }
        busyId = planId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.contributeToMotoOwnershipPlan(planId, ContributeToMotoOwnershipPlanRequest(value), UUID.randomUUID().toString())
                contributeAmounts = contributeAmounts - planId
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyId = null
            }
        }
    }

    fun cancel(planId: String) {
        busyId = planId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.cancelMotoOwnershipPlan(planId, UUID.randomUUID().toString())
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyId = null
            }
        }
    }

    fun convert(planId: String) {
        busyId = planId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.convertMotoOwnershipPlanToLoan(planId, UUID.randomUUID().toString())
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyId = null
            }
        }
    }

    fun repay(planId: String) {
        val value = repayAmounts[planId]?.toBigDecimalOrNull()
        if (value == null || value.signum() <= 0) {
            error = "Enter a valid repayment amount."
            return
        }
        busyId = planId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.repayMotoOwnershipPlan(planId, RepayMotoOwnershipPlanRequest(value), UUID.randomUUID().toString())
                repayAmounts = repayAmounts - planId
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyId = null
            }
        }
    }

    val hasActivePlan = (plans ?: emptyList()).any { it.status == "SAVING" || it.status == "LOAN_ACTIVE" }
    val previewDownPayment = bikePrice.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.multiply(BigDecimal("0.3"))

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Moto-Taxi Ownership Plan", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Save toward a 30% down payment on your own moto-taxi bike (itunda's own down-payment policy), then convert the rest into an unsecured loan. " +
                        "A real entry-level bike costs around 600,000 RWF -- this fills the gap left since Rwanda's taxi-moto cooperatives, which used to help " +
                        "drivers become owner-operators, were dissolved.",
                    color = Ids.colors.textSecondary, fontSize = 12.sp,
                )
            }
            error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }

            if (plans == null) {
                item {
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(48.dp)) {}
                }
            } else {
                item {
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Moto-Taxi Ownership Plan", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (hasActivePlan) {
                                Text(
                                    "You already have an active moto-taxi ownership plan -- complete or cancel it before starting another.",
                                    color = Ids.colors.textSecondary, fontSize = 12.sp,
                                )
                            } else {
                                IdsTextField(value = bikePrice, onValueChange = { bikePrice = it }, label = "Bike price (RWF, 300,000-2,500,000)", modifier = Modifier.fillMaxWidth())
                                IdsTextField(value = dailyContribution, onValueChange = { dailyContribution = it }, label = "Daily contribution (RWF)", modifier = Modifier.fillMaxWidth())
                                previewDownPayment?.let {
                                    Text("Down payment target (30%): ${formatMoneyMoto(it)} RWF", color = Ids.colors.textSecondary, fontSize = 11.sp)
                                }
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                        .clickable(enabled = busyId != "create") { create() }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (busyId == "create") "Creating…" else "Start plan", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }

            item { Text("My moto-taxi ownership plans", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            when {
                plans == null -> item {}
                plans!!.isEmpty() -> item {
                    EmptyState("No moto-taxi ownership plans yet.")
                }
                else -> items(plans!!, key = { it.id }) { plan ->
                    val progressPct = if (plan.downPaymentTarget.signum() > 0) {
                        (plan.savedAmount.divide(plan.downPaymentTarget, 4, java.math.RoundingMode.HALF_UP).toDouble() * 100).coerceIn(0.0, 100.0)
                    } else 0.0
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${formatMoneyMoto(plan.bikePrice)} RWF bike", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(plan.status, color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            if (plan.status == "SAVING") {
                                Text(
                                    "Saved ${formatMoneyMoto(plan.savedAmount)} / ${formatMoneyMoto(plan.downPaymentTarget)} RWF down payment",
                                    color = Ids.colors.textSecondary, fontSize = 11.sp,
                                )
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ids.colors.surfaceSoft),
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(progressPct.toFloat() / 100f).height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)).background(Ids.colors.brand),
                                    )
                                }
                                IdsTextField(value = contributeAmounts[plan.id] ?: "", onValueChange = { contributeAmounts = contributeAmounts + (plan.id to it) }, label = "Contribution amount (RWF)", modifier = Modifier.fillMaxWidth())
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                            .clickable(enabled = busyId == null) { contribute(plan.id) }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(if (busyId == plan.id) "Saving…" else "Contribute", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                            .clickable(enabled = busyId == null) { cancel(plan.id) }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("Cancel", color = Ids.colors.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                }
                                if (plan.savedAmount >= plan.downPaymentTarget) {
                                    Text(
                                        "This releases your full ${formatMoneyMoto(plan.bikePrice)} RWF bike price to your wallet (your saved down payment plus a new unsecured loan for the rest) -- itunda cannot repossess the bike if you stop repaying.",
                                        color = Ids.colors.textSecondary, fontSize = 10.sp,
                                    )
                                    Box(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                            .clickable(enabled = busyId == null) { convert(plan.id) }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(if (busyId == plan.id) "Converting…" else "Convert to loan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                }
                            }
                            if (plan.status == "LOAN_ACTIVE") {
                                Text("Loan outstanding: ${formatMoneyMoto(plan.loanOutstanding)} RWF", color = Ids.colors.textSecondary, fontSize = 11.sp)
                                IdsTextField(value = repayAmounts[plan.id] ?: "", onValueChange = { repayAmounts = repayAmounts + (plan.id to it) }, label = "Repayment amount (RWF)", modifier = Modifier.fillMaxWidth())
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                        .clickable(enabled = busyId == null) { repay(plan.id) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (busyId == plan.id) "Repaying…" else "Repay", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                            if (plan.status == "COMPLETED") {
                                Text("Paid off -- this bike is now fully yours.", color = Ids.colors.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

private fun formatMoneyMoto(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) rounded.toBigInteger().toString() else "%,.2f".format(rounded)
}
