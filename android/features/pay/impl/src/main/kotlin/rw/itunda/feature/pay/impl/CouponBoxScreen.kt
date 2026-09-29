package rw.itunda.feature.pay.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.feature.pay.impl.R
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.CouponBrowseViewDto
import rw.itunda.core.network.CouponRedemptionDto
import rw.itunda.core.network.NetworkClient

private enum class CouponTab { RECEIVED, USED }

// Real "Coupon box" (itunda Pay redesign, 2026-08-28, direct user reference: real
// Toss Pay Coupon box screen) -- see this file's own web sibling (CouponBoxView.tsx)
// for the full sourced account of what's honestly scoped in vs. out. "Used/expired"
// is sourced purely from real redemption history -- browseCoupons already excludes
// expired coupons server-side, so a coupon that expired unused simply never appears
// anywhere rather than fabricating a bucket the real data doesn't populate.
@Composable
fun CouponBoxScreen(onBack: () -> Unit, onBrowseMerchants: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(CouponTab.RECEIVED) }
    var coupons by remember { mutableStateOf<List<CouponBrowseViewDto>?>(null) }
    var redemptions by remember { mutableStateOf<List<CouponRedemptionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            coupons = NetworkClient.apiService.browseCoupons().coupons
            redemptions = NetworkClient.apiService.getMyCouponRedemptions().redemptions
        } catch (e: Exception) {
            error = "Could not load your coupons."
        }
    }

    val received = (coupons ?: emptyList()).filter { it.eligible && !it.alreadyRedeemed }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        Row(Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(Ids.layout.minTouchTarget).clip(CircleShape).pressScaleClickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(IdsIcons.Back, contentDescription = stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pay_coupon_box_title), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
        }

        Row(
            Modifier.padding(horizontal = Ids.layout.screenHorizontal).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            listOf(
                CouponTab.RECEIVED to stringResource(R.string.pay_coupons_received, received.size),
                CouponTab.USED to stringResource(R.string.pay_coupons_used_expired, (redemptions ?: emptyList()).size),
            ).forEach { (key, label) ->
                val active = tab == key
                Text(
                    label, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) Ids.colors.textPrimary else Ids.colors.textSecondary,
                    modifier = Modifier.pressScaleClickable { tab = key },
                )
            }
        }

        if (coupons == null || redemptions == null) {
            if (error != null) {
                Text(error!!, color = Ids.colors.danger, modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal))
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ids.colors.brand) }
            }
        } else if (tab == CouponTab.RECEIVED) {
            if (received.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(message = stringResource(R.string.pay_coupons_empty_title))
                    Text(stringResource(R.string.pay_coupons_empty_subtitle), color = Ids.colors.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    IdsButton(text = stringResource(R.string.pay_coupons_find_new), onClick = onBrowseMerchants)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)) {
                    items(received, key = { it.coupon.id }) { view ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(view.coupon.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "${view.merchantName} · ${couponDiscountLabel(view.coupon.discountType, view.coupon.discountValue)}",
                                color = Ids.colors.textSecondary, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        } else if ((redemptions ?: emptyList()).isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.pay_coupons_none_used), color = Ids.colors.textSecondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)) {
                items(redemptions!!, key = { it.id }) { redemption ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(redemption.redeemedAt.take(10), color = Ids.colors.textSecondary, fontSize = 13.sp)
                        Text(String.format(Locale.US, "-%,.0f RWF", redemption.discountAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun couponDiscountLabel(discountType: String, discountValue: java.math.BigDecimal): String =
    if (discountType == "PERCENT") "$discountValue% off" else String.format(Locale.US, "%,.0f RWF off", discountValue)
