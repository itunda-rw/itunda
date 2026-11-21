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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
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
    var adding by remember { mutableStateOf(false) }
    if (adding) { PayCouponAddScreen(onBack = { adding = false }); return }
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

    val received = (coupons ?: emptyList()).filter { it.eligible && !it.alreadyRedeemed }.map {
        it.coupon.title to "${it.merchantName} · ${couponDiscountLabel(it.coupon.discountType, it.coupon.discountValue)}"
    }
    val used = redemptions?.map { it.redeemedAt.take(10) to String.format(Locale.US, "-%,.0f RWF", it.discountAmount) }
    CouponBoxContent(
        loaded = coupons != null && redemptions != null, error = error, received = received, used = used.orEmpty(),
        usedSelected = tab == CouponTab.USED, onSelect = { tab = if (it) CouponTab.USED else CouponTab.RECEIVED },
        onBack = onBack, onAdd = { adding = true }, onBrowseMerchants = onBrowseMerchants,
    )
}

/**
 * Prototype `pcoupons`: bar with title and "+", Received / Used chips, then either the empty state
 * (stacked-card art, "No coupons yet", Find new coupons) or plain rows. Stateless so CI can render it.
 */
@Composable
fun CouponBoxContent(
    loaded: Boolean, error: String?, received: List<Pair<String, String>>, used: List<Pair<String, String>>, usedSelected: Boolean,
    onSelect: (Boolean) -> Unit, onBack: () -> Unit, onAdd: () -> Unit, onBrowseMerchants: () -> Unit,
) {
    val p = rememberPayPalette()
    PayTextScope {
        Column(Modifier.fillMaxSize().background(p.page)) {
            Box(Modifier.fillMaxWidth().height(70.dp)) {
                Box(Modifier.padding(start = 6.dp, top = 16.dp).size(48.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(IdsIcons.Back, stringResource(R.string.back), Modifier.size(24.dp), tint = p.text)
                }
                Text(stringResource(R.string.pay_coupon_box_title), color = p.text, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, modifier = Modifier.padding(start = 58.dp, top = 26.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(end = 22.dp, top = 16.dp).size(48.dp).pressScaleClickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
                        val w = 2.dp.toPx()
                        drawLine(p.text, androidx.compose.ui.geometry.Offset(12.dp.toPx(), 4.dp.toPx()), androidx.compose.ui.geometry.Offset(12.dp.toPx(), 20.dp.toPx()), w, androidx.compose.ui.graphics.StrokeCap.Round)
                        drawLine(p.text, androidx.compose.ui.geometry.Offset(4.dp.toPx(), 12.dp.toPx()), androidx.compose.ui.geometry.Offset(20.dp.toPx(), 12.dp.toPx()), w, androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(false to stringResource(R.string.pay_coupons_received, received.size), true to stringResource(R.string.pay_coupons_used_expired, used.size)).forEach { (isUsed, label) ->
                    val on = usedSelected == isUsed
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) (if (p.page == Color(0xFF17171C)) p.text else Color(0xFF191F28)) else p.soft)
                            .pressScaleClickable { onSelect(isUsed) }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (on) (if (p.page == Color(0xFF17171C)) Color(0xFF17171C) else Color.White) else p.text, fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold) }
                }
            }
            val rows = if (usedSelected) used else received
            when {
                error != null -> Text(error, color = Color(0xFFC4281B), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp))
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.brand) }
                rows.isEmpty() && !usedSelected -> Column(Modifier.fillMaxWidth().padding(top = 73.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Canvas(Modifier.size(72.dp)) {
                        val u = size.width / 72f
                        scale(0.86f) {
                        rotate(-8f, center) { drawRoundRect(Color(0xFF1E2230), androidx.compose.ui.geometry.Offset(8f * u, 8f * u), androidx.compose.ui.geometry.Size(40f * u, 46f * u), androidx.compose.ui.geometry.CornerRadius(9f * u)) }
                        rotate(6f, center) { drawRoundRect(Color(0xFF3D6BEA).copy(alpha = 0.85f), androidx.compose.ui.geometry.Offset(26f * u, 22f * u), androidx.compose.ui.geometry.Size(38f * u, 40f * u), androidx.compose.ui.geometry.CornerRadius(9f * u)) }
                        }
                    }
                    Text(stringResource(R.string.pay_coupons_empty_title), color = p.text, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.padding(top = 27.dp))
                    Text(stringResource(R.string.pay_coupons_empty_subtitle), color = if (p.page == Color(0xFF17171C)) p.tt else Color(0xFF5F6A78), fontSize = 16.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                    Box(
                        Modifier.padding(top = 20.dp).size(width = 188.4.dp, height = 48.dp).clip(RoundedCornerShape(14.dp)).background(p.brand).pressScaleClickable(onClick = onBrowseMerchants),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.pay_coupons_find_new), color = Color(0xFF17171C), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
                rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.pay_coupons_none_used), color = p.tt, fontSize = 13.sp) }
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
                    items(rows) { (title, sub) ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(title, color = p.text, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                            Text(sub, color = p.tt, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun couponDiscountLabel(discountType: String, discountValue: java.math.BigDecimal): String =
    if (discountType == "PERCENT") "$discountValue% off" else String.format(Locale.US, "%,.0f RWF off", discountValue)
