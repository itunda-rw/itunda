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
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ApplyForStudentLoanRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RepayStudentLoanRequest
import rw.itunda.core.network.StudentLoanDto
import rw.itunda.core.network.StudentLoanSuggestedPaymentResponse
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan -- see
 * StudentLoanDto's own doc comment for the full sourced account. bank-mfe shipped
 * first (StudentLoanView in BankDashboard.tsx); this is the first native client,
 * porting that same real business logic and copy verbatim: self-declared household
 * income (not government-verified), a mandatory grace period after graduation before
 * repayment starts, and an income-percentage-SUGGESTED (not fixed-installment)
 * repayment itunda cannot enforce via payroll deduction. Same no-ViewModel,
 * direct-NetworkClient-call convention HarvestAdvanceScreen.kt/VupLoanScreen.kt
 * already established. Expected graduation date is collected as "years from now"
 * (no native date-picker exists anywhere else in this codebase to mirror -- HarvestAdvanceScreen's
 * own "months from now" pattern is the real, already-proven convention this follows).
 */
@Composable
fun StudentLoanScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var loans by remember { mutableStateOf<List<StudentLoanDto>?>(null) }
    var suggested by remember { mutableStateOf<Map<String, StudentLoanSuggestedPaymentResponse>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    var level by remember { mutableStateOf("UNDERGRADUATE") }
    var income by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var yearsToGraduation by remember { mutableStateOf("") }
    var repayAmounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val myLoans = NetworkClient.apiService.getMyStudentLoans().loans
                loans = myLoans
                error = null
                myLoans.filter { it.status == "REPAYING" || it.status == "OVERDUE" }.forEach { loan ->
                    coroutineScope.launch {
                        try {
                            val s = NetworkClient.apiService.getStudentLoanSuggestedPayment(loan.id)
                            suggested = suggested + (loan.id to s)
                        } catch (_: Exception) {
                            // Best-effort -- the repayment form still works without the hint.
                        }
                    }
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    val hasActiveLoan = loans?.any { it.status != "REPAID" } == true

    fun apply() {
        val incomeValue = income.toBigDecimalOrNull()
        val amountValue = amount.toBigDecimalOrNull()
        val years = yearsToGraduation.toLongOrNull()
        if (incomeValue == null || incomeValue.signum() <= 0) { error = "Enter a valid declared annual household income."; return }
        if (amountValue == null || amountValue.signum() <= 0) { error = "Enter a valid loan amount."; return }
        if (years == null || years <= 0) { error = "Enter how many years until you graduate."; return }
        busyId = "apply"
        error = null
        coroutineScope.launch {
            try {
                val graduationDate = Instant.now().plus(years * 365, ChronoUnit.DAYS).toString().take(10)
                NetworkClient.apiService.applyForStudentLoan(ApplyForStudentLoanRequest(level, incomeValue, amountValue, graduationDate))
                income = ""; amount = ""; yearsToGraduation = ""
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
                NetworkClient.apiService.disburseStudentLoan(loanId, UUID.randomUUID().toString())
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

    fun declareGraduated(loanId: String) {
        busyId = loanId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.declareStudentLoanGraduated(loanId)
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
        if (value == null || value.signum() <= 0) { error = "Enter a valid repayment amount."; return }
        busyId = loanId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.repayStudentLoan(loanId, RepayStudentLoanRequest(value), UUID.randomUUID().toString())
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
        BackTopBar(title = "Student loan", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
            item {
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("BRD Student Loan", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Rwanda's national higher-education student loan, run by the Development Bank of Rwanda (BRD) since 2016 -- 11% undergraduate / 12% postgraduate, " +
                                "with a grace period after graduation before repayment starts. Declared household income is self-declared -- not verified against BRD's real " +
                                "Financial Means Testing process. Repayment here is user-initiated from your wallet -- itunda cannot deduct from your paycheck like the real " +
                                "8%-of-income scheme BRD uses.",
                            color = TossSecondary, fontSize = 12.sp,
                        )
                        if (hasActiveLoan) {
                            Text("You already have an active student loan -- repay it before applying for another.", color = TossSecondary, fontSize = 12.sp)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("UNDERGRADUATE" to "Undergraduate (11%)", "POSTGRADUATE" to "Postgraduate (12%)").forEach { (value, label) ->
                                    val selected = value == level
                                    Text(
                                        label,
                                        color = if (selected) Color.White else TossText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (selected) TossBlue else TossCardSoft)
                                            .clickable { level = value }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                            IdsTextField(value = income, onValueChange = { income = it }, label = "Declared annual household income (RWF)", modifier = Modifier.fillMaxWidth())
                            IdsTextField(value = amount, onValueChange = { amount = it }, label = "Loan amount (RWF, up to 2,000,000)", modifier = Modifier.fillMaxWidth())
                            IdsTextField(value = yearsToGraduation, onValueChange = { yearsToGraduation = it }, label = "Years until you graduate", modifier = Modifier.fillMaxWidth())
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossBlue)
                                    .clickable(enabled = busyId != "apply") { apply() }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (busyId == "apply") "Applying…" else "Apply", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            item { Text("My student loans", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            when {
                loans == null -> item {
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(80.dp)) {}
                }
                loans!!.isEmpty() -> item {
                    EmptyState("No student loans yet.")
                }
                else -> items(loans!!) { loan ->
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${formatMoneyStudentLoan(loan.principalAmount)} RWF · ${loan.level}", color = TossText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(loan.status, color = if (loan.status == "OVERDUE") Ids.colors.danger else TossBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "Outstanding: ${formatMoneyStudentLoan(loan.outstandingBalance)} RWF" +
                                    (loan.graceEndsAt?.let { " · Grace ends ${it.take(10)}" } ?: ""),
                                color = TossSecondary, fontSize = 11.sp,
                            )
                            if (loan.status == "REQUESTED") {
                                Text("Demo: instantly approved -- stands in for the real BRD/MINEDUC approval step.", color = TossTertiary, fontSize = 10.sp)
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossBlue)
                                        .clickable(enabled = busyId != loan.id) { disburse(loan.id) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (busyId == loan.id) "Disbursing…" else "Disburse", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                            if (loan.status == "DISBURSED") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossCardSoft)
                                        .clickable(enabled = busyId != loan.id) { declareGraduated(loan.id) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (busyId == loan.id) "Updating…" else "Declare graduated", color = TossText, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                            if (loan.status == "IN_GRACE_PERIOD") {
                                Text("In your grace period -- repayment isn't due yet.", color = TossSecondary, fontSize = 11.sp)
                            }
                            if (loan.status == "REPAYING" || loan.status == "OVERDUE") {
                                suggested[loan.id]?.let { s ->
                                    Text(
                                        "Suggested: ${formatMoneyStudentLoan(s.suggestedMonthlyPayment)} RWF/mo · ${s.note}",
                                        color = TossSecondary, fontSize = 11.sp,
                                    )
                                }
                                IdsTextField(value = repayAmounts[loan.id] ?: "", onValueChange = { repayAmounts = repayAmounts + (loan.id to it) }, label = "Repayment amount (RWF)", modifier = Modifier.fillMaxWidth())
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossCardSoft)
                                        .clickable(enabled = busyId != loan.id) { repay(loan.id) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (busyId == loan.id) "Repaying…" else "Repay", color = TossText, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

private fun formatMoneyStudentLoan(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) rounded.toBigInteger().toString() else "%,.2f".format(rounded)
}
