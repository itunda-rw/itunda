package rw.itunda.feature.shop.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ORDER_RETURN_REASON_CODES
import rw.itunda.core.network.OrderReturnRequestDto
import rw.itunda.core.network.MerchantBillingPlanDto
import rw.itunda.core.network.MerchantBillingSubscriptionDto
import rw.itunda.core.network.RequestOrderReturnRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID




// Real Coupang-style post-delivery Return & Exchange request (반품/교환 신청) (item 166/174)
// -- see OrderReturnService's own doc comment for the full account: a real 7-day window
// from delivery, an approved RETURN triggers a real refund via reversed ledger legs, an
// approved EXCHANGE moves no money. Merchant-side approve/reject queue has zero Android
// client anywhere (Shop has no merchant order-management screen on this platform at all
// yet) -- a real, separate, not-yet-started gap; this is the buyer-side request form only,
// mirroring bank-mfe's own ReturnExchangeAction (item 166).
@Composable
internal fun ReturnExchangeAction(orderId: String) {
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("RETURN") }
    var reasonCode by remember { mutableStateOf(ORDER_RETURN_REASON_CODES.first()) }
    var note by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("Return/exchange requested -- the seller will review it.", color = Ids.colors.textSecondary, fontSize = 12.sp)
        return
    }
    if (!open) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Return or exchange", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("RETURN" to "Return", "EXCHANGE" to "Exchange").forEach { (value, label) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (type == value) Ids.colors.brand else Ids.colors.textTertiary)
                        .clickable { type = value }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(label, color = if (type == value) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ORDER_RETURN_REASON_CODES.forEach { code ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (reasonCode == code) Ids.colors.brand else Ids.colors.textTertiary)
                        .clickable { reasonCode = code }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(code, color = if (reasonCode == code) Color.White else Ids.colors.textPrimary, fontSize = 11.sp)
                }
            }
        }
        IdsTextField(
            value = note,
            onValueChange = { note = it },
            label = "Add a note (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).clickable { open = false }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (submitting) Ids.colors.textTertiary else Ids.colors.brand)
                    .clickable(enabled = !submitting) {
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.requestOrderReturn(orderId, RequestOrderReturnRequest(type, reasonCode, note.trim().ifBlank { null }))
                                done = true
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                submitting = false
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (submitting) "Submitting…" else "Submit request", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

internal val RETURN_STATUS_LABEL = mapOf("REQUESTED" to "Pending review", "APPROVED" to "Approved", "REJECTED" to "Rejected")

@Composable
internal fun MyReturnRequestsView() {
    var requests by remember { mutableStateOf<List<OrderReturnRequestDto>?>(null) }

    LaunchedEffect(Unit) {
        try {
            requests = NetworkClient.apiService.getMyReturnRequests().returnRequests
        } catch (e: Exception) {
            requests = emptyList()
        }
    }
    val list = requests
    if (list != null && list.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("My return/exchange requests", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            list.forEach { r ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(if (r.type == "RETURN") "Return" else "Exchange", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                RETURN_STATUS_LABEL[r.status] ?: r.status,
                                color = when (r.status) { "APPROVED" -> Ids.colors.brand; "REJECTED" -> Ids.colors.danger; else -> Ids.colors.textSecondary },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                        Text(r.reasonCode, color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Real Kakao Pay 정기결제/Toss 빌링키-style subscribe/cancel -- subscribing charges the
 * first cycle immediately (real "인증 + 첫결제"), same as
 * rw.itunda.merchant.MerchantBillingService.subscribe's own doc comment. One real
 * active subscription per plan; cancelling stops future charges but doesn't refund the
 * current cycle already paid for. bank-mfe already has this; this is the first Android
 * client.
 */
@Composable
internal fun BillingPlanRow(plan: MerchantBillingPlanDto, subscription: MerchantBillingSubscriptionDto?, onChanged: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plan.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "%,.0f RWF every ${plan.intervalDays} days".format(plan.amount),
                    color = Ids.colors.textSecondary, fontSize = 12.sp,
                )
                plan.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Ids.colors.textSecondary, fontSize = 11.sp) }
            }
            if (subscription != null) {
                ListingActionButtonShop(if (busy) "…" else "Cancel", busy) {
                    busy = true
                    error = null
                    coroutineScope.launch {
                        try {
                            NetworkClient.apiService.cancelBillingSubscription(subscription.id)
                            onChanged()
                        } catch (e: Exception) {
                            error = "Could not cancel this subscription."
                        } finally {
                            busy = false
                        }
                    }
                }
            } else {
                ListingActionButtonShop(if (busy) "…" else "Subscribe", busy, filled = true) {
                    busy = true
                    error = null
                    coroutineScope.launch {
                        try {
                            NetworkClient.apiService.subscribeToBillingPlan(plan.id, UUID.randomUUID().toString())
                            onChanged()
                        } catch (e: Exception) {
                            error = "Could not subscribe to this plan."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
        if (subscription != null) {
            Text(
                if (subscription.status == "ACTIVE") "Next charge ${subscription.nextChargeAt.take(10)}" else "Cancelled",
                color = Ids.colors.textSecondary, fontSize = 11.sp,
            )
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 11.sp) }
    }
}

