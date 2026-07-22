package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.itunda.app.network.ApplyLoanRequest
import rw.itunda.app.network.LoanAccountDto
import rw.itunda.app.network.LoanOfferDto
import rw.itunda.app.network.NetworkClient
import rw.itunda.app.network.RepayLoanRequest
import java.math.BigDecimal
import java.util.UUID

// Real multi-lender loan marketplace (2026-07-22) -- found fully built on the backend
// (rw.itunda.loans, BNR-licensed partner banks alongside itunda's own underwriting
// book, see LoanOffer.kt's own doc comment) while every "Loan"/"Get a loan" row in
// this app was 100% hardcoded static text ("11% ~ 24%") with zero API call behind it.
// This screen replaces that decoration with the real offers/apply/repay flow.
private enum class LoansMode { OFFERS, MY_LOANS }

@Composable
fun LoansScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(LoansMode.OFFERS) }
    var offers by remember { mutableStateOf<List<LoanOfferDto>?>(null) }
    var myLoans by remember { mutableStateOf<List<LoanAccountDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var repayAmounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            offers = NetworkClient.apiService.getLoanOffers().offers
            myLoans = NetworkClient.apiService.getMyLoans().loans
            error = null
        } catch (_: Exception) {
            error = "Could not load loans."
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BackTopBar(title = "Loans", onBack = onBack)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { mode = LoansMode.OFFERS }) { Text("Offers") }
            Button(onClick = { mode = LoansMode.MY_LOANS }) { Text("My loans (${myLoans?.size ?: 0})") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (mode == LoansMode.OFFERS) {
                val currentOffers = offers
                if (currentOffers == null) item { Text("Loading…") }
                else items(currentOffers, key = { it.id }) { offer ->
                    OfferCard(offer, busyId == offer.id) { amount ->
                        busyId = offer.id
                        scope.launch {
                            try {
                                NetworkClient.apiService.applyForLoan(
                                    UUID.randomUUID().toString(),
                                    ApplyLoanRequest(offer.id, amount),
                                )
                                mode = LoansMode.MY_LOANS
                                refresh()
                            } catch (_: Exception) {
                                error = "That loan application could not be completed."
                            } finally { busyId = null }
                        }
                    }
                }
            } else {
                val currentLoans = myLoans
                if (currentLoans == null) item { Text("Loading…") }
                else if (currentLoans.isEmpty()) item { Text("You have no loans yet.") }
                else items(currentLoans, key = { it.id }) { loan ->
                    MyLoanCard(
                        loan = loan,
                        busy = busyId == loan.id,
                        repayText = repayAmounts[loan.id] ?: "",
                        onRepayTextChanged = { repayAmounts = repayAmounts + (loan.id to it) },
                        onRepay = {
                            val amount = (repayAmounts[loan.id] ?: "").toBigDecimalOrNull()
                            if (amount == null || amount <= BigDecimal.ZERO) { error = "Enter a valid repayment amount."; return@MyLoanCard }
                            busyId = loan.id
                            scope.launch {
                                try {
                                    NetworkClient.apiService.repayLoan(UUID.randomUUID().toString(), RepayLoanRequest(loan.id, amount))
                                    repayAmounts = repayAmounts - loan.id
                                    refresh()
                                } catch (_: Exception) {
                                    error = "That repayment could not be completed."
                                } finally { busyId = null }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferCard(offer: LoanOfferDto, busy: Boolean, onApply: (BigDecimal) -> Unit) {
    var amountText by remember { mutableStateOf(offer.maxAmount.toPlainString()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(offer.name, style = MaterialTheme.typography.titleMedium)
            Text(offer.lenderName, style = MaterialTheme.typography.bodySmall)
            Text("Up to RWF ${offer.maxAmount} · ${offer.interestRate}% · ${offer.term}", style = MaterialTheme.typography.bodyMedium)
            Text(offer.requirements, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(amountText, { amountText = it }, label = { Text("Amount (RWF)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val amount = amountText.toBigDecimalOrNull()
                    if (amount != null && amount > BigDecimal.ZERO) onApply(amount)
                },
            ) { Text(if (busy) "Applying…" else "Apply") }
        }
    }
}

@Composable
private fun MyLoanCard(
    loan: LoanAccountDto,
    busy: Boolean,
    repayText: String,
    onRepayTextChanged: (String) -> Unit,
    onRepay: () -> Unit,
) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text("RWF ${loan.principal} loan", style = MaterialTheme.typography.titleMedium)
        Text("Outstanding: RWF ${loan.outstanding}", style = MaterialTheme.typography.bodyMedium)
        Text("Status: ${loan.status} · ${loan.interestRate}%", style = MaterialTheme.typography.bodySmall)
        if (loan.status == "ACTIVE") {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(repayText, onRepayTextChanged, label = { Text("Repay amount (RWF)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(enabled = !busy, onClick = onRepay, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Repaying…" else "Repay") }
        }
    }
}
