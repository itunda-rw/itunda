package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.DetectedSubscriptionDto
import rw.itunda.core.network.MerchantBillingSubscriptionDto
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage

// Real recurring-payment ("subscription") detection over a user's own real transaction
// history -- see rw.itunda.wallet.SubscriptionDetectionService's own doc comment. Plus
// real Kakao Pay 정기결제/Toss 빌링키-style merchant subscriptions the customer actually
// authorized (billing-key charges, distinct from the detected-from-history section
// above: real active authorizations, not a heuristic guess). bank-mfe already has both
// (SubscriptionsView); this is the first Android client, mirroring it exactly.
@Composable
fun SubscriptionsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var detected by remember { mutableStateOf<List<DetectedSubscriptionDto>?>(null) }
    var estimatedMonthlyTotal by remember { mutableStateOf(java.math.BigDecimal.ZERO) }
    var billingSubs by remember { mutableStateOf<List<MerchantBillingSubscriptionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadBilling() {
        coroutineScope.launch {
            try {
                billingSubs = NetworkClient.apiService.getMyBillingSubscriptions().subscriptions
            } catch (e: retrofit2.HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: Exception) {
                error = "Could not load your subscriptions."
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val res = NetworkClient.apiService.getDetectedSubscriptions()
            detected = res.subscriptions
            estimatedMonthlyTotal = res.estimatedMonthlyTotal
        } catch (e: retrofit2.HttpException) {
            error = superAppErrorMessage(e)
        } catch (e: Exception) {
            error = "Could not load your subscriptions."
        }
        loadBilling()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Subscriptions", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val detectedList = detected
            if (detectedList == null) {
                item { Text("Loading…", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            } else {
                item {
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Estimated monthly total", color = Ids.colors.textSecondary, fontSize = 12.sp)
                            Text("${"%,.0f".format(estimatedMonthlyTotal)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Text("Detected from your own real payment history, not a linked-card feed.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
                if (detectedList.isEmpty()) {
                    item { EmptyState("No recurring payments detected yet — they'll show up here once we spot a pattern.") }
                } else {
                    items(detectedList, key = { "detected_${it.displayName}_${it.cadence}" }) { s ->
                        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column {
                                    Text(s.displayName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "${if (s.cadence == "WEEKLY") "Weekly" else "Monthly"} · ${s.occurrenceCount} payments seen",
                                        color = Ids.colors.textSecondary, fontSize = 12.sp,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${"%,.0f".format(s.amount)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    val previous = s.previousAmount
                                    if (s.priceIncreased && previous != null) {
                                        Text("↑ from ${"%,.0f".format(previous)} RWF", color = Ids.colors.danger, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Merchant subscriptions", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "Plans you've subscribed to. These charge your wallet automatically until you cancel.",
                            color = Ids.colors.textSecondary, fontSize = 12.sp,
                        )
                        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
                        val subs = billingSubs
                        if (subs == null) {
                            Text("Loading…", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        } else if (subs.isEmpty()) {
                            EmptyState("No merchant subscriptions yet — plans you subscribe to will show up here.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                subs.forEach { sub -> MerchantBillingSubscriptionRow(subscription = sub, onChanged = ::loadBilling) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MerchantBillingSubscriptionRow(subscription: MerchantBillingSubscriptionDto, onChanged: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "${subscription.chargeCount} charge${if (subscription.chargeCount == 1) "" else "s"} so far",
                    color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
                Text(
                    if (subscription.status == "ACTIVE") "Next charge ${subscription.nextChargeAt.take(10)}" else "Cancelled ${subscription.cancelledAt?.take(10) ?: ""}",
                    color = Ids.colors.textSecondary, fontSize = 12.sp,
                )
                subscription.lastFailureReason?.takeIf { subscription.status == "ACTIVE" }?.let {
                    Text("Last charge failed: $it", color = Ids.colors.danger, fontSize = 11.sp)
                }
            }
            if (subscription.status == "ACTIVE") {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surface)
                        .clickable(enabled = !busy) {
                            busy = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.cancelBillingSubscription(subscription.id)
                                    onChanged()
                                } catch (e: retrofit2.HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: Exception) {
                                    error = "Could not cancel this subscription."
                                } finally {
                                    busy = false
                                }
                            }
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text(if (busy) "…" else "Cancel", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 11.sp) }
    }
}
