package rw.itunda.merchant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import rw.itunda.core.designsystem.components.IdsButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.ApplyForVendorCashAdvanceRequest
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.RepayVendorCashAdvanceEarlyRequest
import rw.itunda.merchant.network.VendorCashAdvanceDto
import rw.itunda.merchant.network.VendorCashAdvanceOfferResponse
import java.util.UUID

/**
 * Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see
 * VendorCashAdvanceDto's own doc comment for the full sourced account. merchant-mfe
 * shipped first (its own doc comment explicitly named Android/iOS as a follow-up, not
 * built there); this is the first native client, porting that same real business
 * logic and copy verbatim: an offer computed from the merchant's own real average
 * daily itunda-routed settlement (never a fixed installment the merchant initiates --
 * repayment is auto-collected as a % of real daily sales), and the same honest
 * disclosure that off-platform cash sales are invisible to both underwriting and
 * collection.
 */
@Composable
fun VendorCashAdvanceTab(merchantId: String) {
    var advance by remember { mutableStateOf<VendorCashAdvanceDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var offer by remember { mutableStateOf<VendorCashAdvanceOfferResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var repayAmount by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val a = NetworkClient.apiService.getMyVendorCashAdvance(merchantId).advance
                advance = a
                loaded = true
                error = null
                if (a == null) {
                    try { offer = NetworkClient.apiService.getVendorCashAdvanceOffer(merchantId) } catch (_: Exception) { offer = null }
                }
            } catch (e: Exception) {
                error = "Could not load your vendor cash advance."
            }
        }
    }
    LaunchedEffect(merchantId) { load() }

    fun apply() {
        busy = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.applyForVendorCashAdvance(ApplyForVendorCashAdvanceRequest(merchantId))
                load()
            } catch (e: retrofit2.HttpException) {
                error = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't apply for a vendor cash advance."
            } catch (e: Exception) {
                error = "Couldn't apply for a vendor cash advance."
            } finally {
                busy = false
            }
        }
    }

    fun disburse(advanceId: String) {
        busy = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.disburseVendorCashAdvance(advanceId, UUID.randomUUID().toString())
                load()
            } catch (e: retrofit2.HttpException) {
                error = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't disburse this advance."
            } catch (e: Exception) {
                error = "Couldn't disburse this advance."
            } finally {
                busy = false
            }
        }
    }

    fun repayEarly(advanceId: String) {
        val value = repayAmount.toBigDecimalOrNull()
        if (value == null || value.signum() <= 0) {
            error = "Enter a real amount."
            return
        }
        busy = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.repayVendorCashAdvanceEarly(advanceId, RepayVendorCashAdvanceEarlyRequest(value), UUID.randomUUID().toString())
                repayAmount = ""
                load()
            } catch (e: retrofit2.HttpException) {
                error = rw.itunda.merchant.network.apiErrorMessage(e) ?: "Couldn't repay this advance. Check your balance."
            } catch (e: Exception) {
                error = "Couldn't repay this advance. Check your balance."
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Isoko Vendor Cash Advance", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "A cash advance against your own real itunda sales history. There's no fixed repayment schedule -- " +
                        "itunda automatically collects a share of your real QR/card sales here each day until it's paid off. " +
                        "This can only see and collect sales that actually go through itunda; cash you collect off-platform " +
                        "isn't part of this at all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (!loaded) {
            return@Column
        }

        val currentAdvance = advance
        val currentOffer = offer

        if (currentAdvance == null && currentOffer?.eligible == true) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("You're eligible for", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%,.0f".format(currentOffer.offerAmount)} RWF", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "One-time fee: ${"%,.0f".format(currentOffer.feeAmount)} RWF -- itunda then collects ${currentOffer.collectionRatePercent}% of your " +
                            "real daily itunda-collected sales here until ${"%,.0f".format((currentOffer.offerAmount ?: java.math.BigDecimal.ZERO) + (currentOffer.feeAmount ?: java.math.BigDecimal.ZERO))} RWF " +
                            "is repaid. Based on your real average of ${"%,.0f".format(currentOffer.averageDailySettlement)} RWF/day over your last ${currentOffer.tradingDays} real trading days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IdsButton(text = if (busy) "Applying…" else "Apply for this advance", enabled = !busy, onClick = ::apply)
                }
            }
        }

        if (currentAdvance == null && currentOffer != null && !currentOffer.eligible) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Not eligible yet -- ${currentOffer.reason}. Keep collecting real QR/card sales through itunda and check back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (currentAdvance != null && currentAdvance.status == "REQUESTED") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Your ${"%,.0f".format(currentAdvance.principalAmount)} RWF advance was approved and is ready to disburse to your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IdsButton(text = if (busy) "Disbursing…" else "Disburse to my account", enabled = !busy, onClick = { disburse(currentAdvance.id) })
                }
            }
        }

        if (currentAdvance != null && currentAdvance.status == "DISBURSED") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Remaining owed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%,.0f".format(currentAdvance.remainingOwed)} RWF", style = MaterialTheme.typography.headlineSmall)
                    val progress = if (currentAdvance.totalOwed.signum() > 0) {
                        ((currentAdvance.totalOwed - currentAdvance.remainingOwed) / currentAdvance.totalOwed).toFloat().coerceIn(0f, 1f)
                    } else 0f
                    Box(
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(progress).height(8.dp).clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Text(
                        "of ${"%,.0f".format(currentAdvance.totalOwed)} RWF total owed -- ${currentAdvance.collectionRatePercent}% of your real daily " +
                            "itunda sales is collected automatically" +
                            (currentAdvance.lastCollectionAt?.let { ", last collected ${it.take(10)}" } ?: "") + ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Repay early", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    IdsTextField(value = repayAmount, onValueChange = { repayAmount = it }, label = "Amount (RWF)", modifier = Modifier.fillMaxWidth())
                    IdsButton(text = if (busy) "Repaying…" else "Repay now", enabled = !busy, onClick = { repayEarly(currentAdvance.id) })
                }
            }
        }
    }
}
