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
import rw.itunda.core.network.ApplyForVupLoanRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RepayVupLoanRequest
import rw.itunda.core.network.VupLoanDto
import rw.itunda.core.network.VupLoanEligibilityResponse
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

/**
 * Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services means-tested
 * microloan -- sourced beyond this session's usual Toss/Kakao/Naver/Coupang reference
 * ecosystems. VUP, run by LODA since 2008, subsidizes microloans for income-generating
 * activities (farming, livestock, small business) targeted at households in poorer
 * Ubudehe categories (NISR EICV7 2023/24: ~100,000 RWF average loan). Since a real
 * 2014-07-29 Cabinet decision, administration moved to Umurenge SACCOs, which set the
 * rate at 11% (Rwanda Inspirer: uptake fell after that rate hike). Honest v1
 * limitation: Ubudehe category is self-declared by the user, not verified against
 * Rwanda's real government Ubudehe household-classification registry. Mirrors
 * bank-mfe's VupLoanView exactly, same no-ViewModel, direct-NetworkClient-call
 * convention HarvestAdvanceScreen.kt/SaccoScreen.kt already established. Unlike
 * HarvestAdvanceScreen.kt's fixed "repay in full" contract, the backend here clamps
 * an overshooting repay amount to the real outstanding balance server-side before
 * touching the ledger, so a free-form repay input field (matching bank-mfe's own
 * VupLoanView exactly) is safe here.
 */
@Composable
fun VupLoanScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var loans by remember { mutableStateOf<List<VupLoanDto>?>(null) }
    var eligibility by remember { mutableStateOf<VupLoanEligibilityResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    var category by remember { mutableStateOf(1) }
    var purpose by remember { mutableStateOf("FARMING") }
    var amount by remember { mutableStateOf("") }
    var repayAmounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                loans = NetworkClient.apiService.getMyVupLoans().loans
                eligibility = NetworkClient.apiService.getVupLoanEligibility()
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun apply() {
        val value = amount.toBigDecimalOrNull()
        if (value == null || value.signum() <= 0) {
            error = "Enter a valid loan amount."
            return
        }
        busyId = "apply"
        coroutineScope.launch {
            try {
                NetworkClient.apiService.applyForVupLoan(ApplyForVupLoanRequest(category, purpose, value))
                amount = ""
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

    fun disburse(loanId: String) {
        busyId = loanId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.disburseVupLoan(loanId, UUID.randomUUID().toString())
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

    fun repay(loanId: String) {
        val value = repayAmounts[loanId]?.toBigDecimalOrNull()
        if (value == null || value.signum() <= 0) {
            error = "Enter a valid repayment amount."
            return
        }
        busyId = loanId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.repayVupLoan(loanId, RepayVupLoanRequest(value), UUID.randomUUID().toString())
                repayAmounts = repayAmounts - loanId
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

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "VUP Financial Services", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Rwanda's Vision 2020 Umurenge Programme subsidized microloan for farming, livestock, or small business. Ubudehe category is self-declared -- not verified against a real government registry.",
                    color = TossSecondary, fontSize = 12.sp,
                )
            }
            error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }

            val eligibilityNow = eligibility
            if (eligibilityNow == null || loans == null) {
                item {
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(48.dp)) {}
                }
            } else {
                item {
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${(eligibilityNow.interestRate * 100).let { "%.0f".format(it) }}% interest · Ubudehe categories ${eligibilityNow.minUbudeheCategory}-${eligibilityNow.maxUbudeheCategory} only",
                                color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            )
                            if (!eligibilityNow.canApply) {
                                Text(
                                    "You already have an active VUP loan -- repay it before applying for another.",
                                    color = TossSecondary, fontSize = 12.sp,
                                )
                            } else {
                                Text("Ubudehe category", color = TossSecondary, fontSize = 12.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1, 2, 3).forEach { c ->
                                        Box(
                                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                                .background(if (category == c) TossBlue else TossCardSoft)
                                                .clickable { category = c }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                        ) { Text("Category $c", color = if (category == c) Color.White else TossText, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                                Text("Purpose", color = TossSecondary, fontSize = 12.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("FARMING" to "Farming", "LIVESTOCK" to "Livestock", "BUSINESS" to "Business").forEach { (value, label) ->
                                        Box(
                                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                                .background(if (purpose == value) TossBlue else TossCardSoft)
                                                .clickable { purpose = value }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                        ) { Text(label, color = if (purpose == value) Color.White else TossText, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                                OutlinedTextField(
                                    value = amount, onValueChange = { amount = it },
                                    label = { Text("Loan amount (RWF, up to ${"%,.0f".format(eligibilityNow.maxAmount)})") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossBlue)
                                        .clickable(enabled = busyId != "apply" && amount.toBigDecimalOrNull()?.signum() == 1) { apply() }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (busyId == "apply") "Applying…" else "Apply", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }

            item { Text("My VUP loans", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            when {
                loans == null -> item {}
                loans!!.isEmpty() -> item {
                    Text("No VUP loans yet.", color = TossSecondary, fontSize = 13.sp)
                }
                else -> items(loans!!, key = { it.id }) { loan ->
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${formatMoneyVup(loan.principalAmount)} RWF · ${loan.purpose}", color = TossText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(loan.status, color = if (loan.status == "OVERDUE") Ids.colors.danger else TossSecondary, fontSize = 12.sp)
                            }
                            Text(
                                "Outstanding: ${formatMoneyVup(loan.outstandingPrincipal)} RWF" + (loan.dueDate?.let { " · Due ${it.take(10)}" } ?: ""),
                                color = TossSecondary, fontSize = 11.sp,
                            )
                            if (loan.status == "REQUESTED") {
                                Text("Demo: instantly approved -- stands in for the real SACCO officer approval step.", color = TossSecondary, fontSize = 10.sp)
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossBlue)
                                        .clickable(enabled = busyId == null) { disburse(loan.id) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (busyId == loan.id) "…" else "Disburse", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                            if (loan.status == "DISBURSED" || loan.status == "OVERDUE") {
                                OutlinedTextField(
                                    value = repayAmounts[loan.id] ?: "",
                                    onValueChange = { repayAmounts = repayAmounts + (loan.id to it) },
                                    label = { Text("Repayment amount (RWF)") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossCardSoft)
                                        .clickable(enabled = busyId == null) { repay(loan.id) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (busyId == loan.id) "…" else "Repay", color = TossText, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

private fun formatMoneyVup(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) rounded.toBigInteger().toString() else "%,.2f".format(rounded)
}
