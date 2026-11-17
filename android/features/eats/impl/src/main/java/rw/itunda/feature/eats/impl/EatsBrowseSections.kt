package rw.itunda.feature.eats.impl

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Fulfillment and coupon controls sit below the sticky top bar and before categories. */
@Composable
fun EatsDeliveryPickupRow(state: EatsBrowseState) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        EatsModeSwitch(
            isPickup = state.isPickup,
            onModeChange = { pickup ->
                state.isPickup = pickup
                if (pickup && state.selectedCoupon == "FREEDEL") state.selectedCoupon = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Ids.colors.divider, RoundedCornerShape(14.dp))
                .background(Ids.colors.background)
                .semantics { contentDescription = "Coupons" }
                .pressScaleClickable { state.showCoupons = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ItundaArt("tag", ItundaArtSet.Line, 22.dp, tint = Ids.colors.textBrand)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("Coupons", color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    " · 2 available" + (state.selectedCoupon?.let { " · $it ✓" } ?: ""),
                    color = Ids.colors.textPrimary,
                    fontSize = 14.sp,
                )
            }
            ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = Ids.colors.textSecondary)
        }
    }

    if (state.showCoupons) {
        EatsCouponsSheet(
            isPickup = state.isPickup,
            selectedCoupon = state.selectedCoupon,
            onDismiss = { state.showCoupons = false },
            onSelect = { code ->
                state.selectedCoupon = code
                state.showCoupons = false
                Toast.makeText(context, "Code $code saved. It applies at checkout.", Toast.LENGTH_SHORT).show()
            },
            onUnavailable = {
                state.showCoupons = false
                Toast.makeText(context, "Free delivery only applies to delivery orders", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/** The promo panel remains below the category row; the coupon selector is above it. */
@Composable
fun EatsBrowseOffers(state: EatsBrowseState, showPromo: Boolean = true) {
    val context = LocalContext.current
    if (showPromo) {
        EatsPromoBanner(
            isPickup = state.isPickup,
            selected = false,
            onSelectCoupon = {
                val code = if (state.isPickup) "EATS20" else "FREEDEL"
                state.selectedCoupon = code
                Toast.makeText(context, "Code $code saved. It applies at checkout.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
