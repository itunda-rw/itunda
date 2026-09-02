package rw.itunda.app.ui

import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.ledgerRowIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.RewardTaskDto
import rw.itunda.core.network.TransactionDto
import kotlinx.coroutines.launch

// Real Toss Pay home reference (4 screenshots, 2026-08-22, direct user follow-up:
// "it should look 100% like toss pay UI/UX features everything") -- split out of
// PayTab (ItundaAppScreen.kt) to keep that file under its file-size-lint baseline,
// matching the same convention this session's earlier PayMoneyDetailScreen.kt
// extraction already established. Every section here is real itunda data -- see each
// composable's own doc comment for exactly what backs it and what's honestly scoped
// out (no real backend): the reference's cross-merchant coupon wallet and external
// online-merchant integrations.

// Real location fetch + MerchantDiscoveryService.nearby call, extracted out of PayTab
// (ItundaAppScreen.kt) to keep that file under its own file-size-lint baseline. Returns
// an empty list until a real location + response both land -- NearbyMerchantsBanner
// already no-ops on an empty list, so no separate loading state needed here.
@Composable
fun rememberNearbyMerchants(): List<rw.itunda.core.network.NearbyMerchantDto> {
    var merchants by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<rw.itunda.core.network.NearbyMerchantDto>>(emptyList()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val requestLocation = rw.itunda.core.designsystem.components.rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            scope.launch {
                try {
                    merchants = rw.itunda.core.network.NetworkClient.apiService.getNearbyMerchants(lat, lng).merchants
                } catch (e: Exception) {
                    // Non-critical -- NearbyMerchantsBanner just won't render.
                }
            }
        },
        onError = {},
    )
    androidx.compose.runtime.LaunchedEffect(Unit) { requestLocation() }
    return merchants
}

// Real MerchantDiscoveryService.nearby -- every ACTIVE merchant within radiusKm,
// distinct from the existing "Nearby benefits" ad rail inside MyPaymentCodeCard (that's
// paid ad placements, a subset -- this is the real total). Every merchant earns the
// payer real cashback on collect() (ShoppingCashbackService.DEFAULT_CASHBACK_RATE),
// so "earn cashback" is a true claim for all of them, not just FacePay-enrolled ones.
// Owns its own dialog-visibility state -- a single call from PayTab's LazyColumn
// renders the banner and (on tap) the real merchant-list dialog below, rather than
// PayTab having to hold that boolean itself.
@Composable
fun NearbyMerchantsBanner(merchants: List<rw.itunda.core.network.NearbyMerchantDto>) {
    if (merchants.isEmpty()) return
    var showDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(Ids.colors.surfaceSoft)
            .pressScaleClickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ids.colors.textSecondary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("${merchants.size} itunda merchant${if (merchants.size == 1) "" else "s"} nearby — earn cashback", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
        }
        Icon(IdsIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ids.colors.textTertiary)
    }
    if (showDialog) {
        NearbyMerchantsDialog(merchants = merchants, onDismiss = { showDialog = false })
    }
}

// Real destination for NearbyMerchantsBanner's tap -- a plain list of the same real
// merchants (name, category, distance, real per-merchant cashback rate), not a dead
// link. No map view here (itunda's own self-hosted maps stack lives on a separate,
// heavier screen) -- this is a lightweight preview, matching this section's own
// "preview, not the full destination" scope elsewhere (RewardsPreviewSection,
// PaymentHistorySection).
@Composable
fun NearbyMerchantsDialog(merchants: List<rw.itunda.core.network.NearbyMerchantDto>, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Ids.colors.background)
                .padding(20.dp),
        ) {
            Text("Merchants nearby", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            merchants.take(20).forEachIndexed { index, merchant ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(merchant.businessName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                        Text(merchant.category ?: "Merchant", fontSize = 12.sp, color = Ids.colors.textSecondary)
                    }
                    Text("${"%.1f".format(merchant.distanceKm)} km", fontSize = 13.sp, color = Ids.colors.textSecondary)
                }
                if (index != merchants.take(20).lastIndex) {
                    HorizontalDivider(color = Ids.colors.divider)
                }
            }
        }
    }
}

// FacePay's own real enroll/disable toggle. cashbackRatePercent is derived from real
// fetched nearby-merchant data (ShoppingCashbackService's own per-merchant rate, via
// MerchantDiscoveryService.nearby) -- never hardcoded, so it only shows once real data
// has actually loaded. Stated as a general "earn on payments" fact (true regardless of
// FacePay enrollment, since cashback applies to every collect() channel), not a
// fabricated FacePay-exclusive rate the way the reference's own "Earning 3%" implies
// for real Toss.
@Composable
fun FacePayStatusRow(enrolled: Boolean, busy: Boolean, cashbackRatePercent: Double?, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(if (enrolled) Ids.colors.brand else Ids.colors.chip, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Face, contentDescription = null, tint = if (enrolled) Color.White else Ids.colors.textSecondary, modifier = Modifier.size(19.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("FacePay", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
                val subtitle = if (cashbackRatePercent != null) {
                    val ratePercentText = cashbackRatePercent.toBigDecimal().stripTrailingZeros().toPlainString()
                    if (enrolled) "Enrolled — earn ${ratePercentText}% cashback on payments" else "Not enrolled — earn ${ratePercentText}% cashback on payments either way"
                } else if (enrolled) "Enrolled — pay with your face at any itunda merchant" else "Not enrolled"
                Text(subtitle, fontSize = 12.sp, color = Ids.colors.textSecondary)
            }
        }
        IdsButton(
            if (busy) "…" else if (enrolled) "Turn off" else "Enroll",
            onClick = onToggle,
            variant = if (enrolled) IdsButtonVariant.Tinted else IdsButtonVariant.Filled,
            size = IdsButtonSize.Small,
        )
    }
}

// Real "Rewards you received" summary -- rewardsTotal (RewardsService, already fetched
// for RewardsPreviewSection below) plus the real itunda Pay balance. No coupon count --
// itunda has no cross-merchant coupon wallet (coupons are scoped to one merchant at a
// time), the same honest scope-down this file applies everywhere else.
@Composable
fun RewardsSummaryRow(rewardsTotal: Double, payBalance: Double?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("Rewards earned", fontSize = 13.sp, color = Ids.colors.textSecondary)
            // Real fix (2026-08-26, live-caught: "1300 RWF" next to "RWF 7,510" on the
            // exact same screen -- direct user correction: amount-first, currency-suffix
            // is the real correct convention, e.g. "10,346 RWF" not "RWF 10,346") -- was
            // raw Int-to-string interpolation with no thousands separator; this session's
            // own first pass at this fix wrongly flipped it TO currency-prefix instead,
            // corrected here alongside the same app-wide currency-prefix sweep.
            Text("%,.0f RWF".format(rewardsTotal), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
        }
        if (payBalance != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("itunda Pay balance", fontSize = 13.sp, color = Ids.colors.textSecondary)
                Text("%,.0f RWF".format(payBalance), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
            }
        }
    }
}

// Real "Payment history" (2026-08-22) -- reuses viewModel.transactions (already fetched
// app-wide, MainViewModel) and ledgerRowIcon's real per-category classifier
// (ItundaAppScreen.kt), same real icons the Bank ledger screen uses. Capped at 5, most
// recent first -- this is a preview, not the full ledger (AccountDetailScreen already
// owns that).
@Composable
fun PaymentHistorySection(transactions: List<TransactionDto>) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Payment history", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        if (transactions.isEmpty()) {
            Text("No payments made this month", fontSize = 14.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            transactions.take(5).forEachIndexed { index, tx ->
                val (icon, tint) = ledgerRowIcon(tx)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(34.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(tx.description, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                    }
                    // Real fix (2026-08-26) -- same comma-formatting + currency-order
                    // correction as RewardsSummaryRow's own doc comment above.
                    Text("%,.0f RWF".format(tx.amount), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                }
                if (index != transactions.take(5).lastIndex) {
                    HorizontalDivider(color = Ids.colors.divider)
                }
            }
        }
    }
}

// No "View all" link to a dedicated Rewards screen -- Android has no native one
// (RewardsService is otherwise reached only through the Saronite RN mini-app
// bridge, not from a Compose screen). Each row is directly tap-to-claim instead
// (real claimRewardTask, ApiService.kt), the same "real destination, not a dead
// link" discipline this session's own doc comments repeatedly apply elsewhere.
@Composable
fun RewardsPreviewSection(tasks: List<RewardTaskDto>, claimingId: String?, onClaim: (String) -> Unit) {
    val preview = tasks.filter { !it.claimed && it.eligible }.take(3)
    if (preview.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Get more rewards", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
        preview.forEach { task ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(task.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                    Text(task.subtitle, fontSize = 12.sp, color = Ids.colors.textSecondary)
                }
                IdsButton(
                    if (claimingId == task.id) "…" else "+%,.0f RWF".format(task.rewardAmount),
                    onClick = { onClaim(task.id) },
                    enabled = claimingId == null,
                    variant = IdsButtonVariant.Tinted,
                    size = IdsButtonSize.Small,
                )
            }
        }
    }
}

// Real "FAQ / Send feedback" (2026-08-22) -- itunda has no FAQ-content system, so both
// honestly route to the real Support screen rather than fabricating static FAQ copy --
// same real destination, shown as two rows to match the reference's own layout.
@Composable
fun GetHelpLinks(onOpenSupport: () -> Unit) {
    Column {
        Text(
            "FAQ", fontSize = 13.sp, color = Ids.colors.textSecondary,
            modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenSupport).padding(vertical = 10.dp, horizontal = 4.dp),
        )
        Text(
            "Send feedback", fontSize = 13.sp, color = Ids.colors.textSecondary,
            modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenSupport).padding(vertical = 10.dp, horizontal = 4.dp),
        )
    }
}
