package rw.itunda.feature.eats.impl

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.offset
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

/** Shop/Eats selector, fulfillment cards and coupon entry before the category grid. */
@Composable
fun EatsDeliveryPickupRow(state: EatsBrowseState, onShopClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            EatsBrowseTab("Shop", selected = false, onClick = onShopClick)
            EatsBrowseTab("Eats", selected = true, onClick = null)
        }
        EatsModeSwitch(
            isPickup = state.isPickup,
            onModeChange = { pickup ->
                state.isPickup = pickup
                if (pickup && state.selectedCoupon == "FREEDEL") state.selectedCoupon = null
            },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
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
}

@Composable
private fun EatsBrowseTab(label: String, selected: Boolean, onClick: (() -> Unit)?) {
    val modifier = if (onClick == null) Modifier else Modifier.pressScaleClickable(onClick = onClick)
    Column(modifier) {
        Text(
            label,
            color = if (selected) Ids.colors.textPrimary else Ids.colors.textTertiary,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Box(
            Modifier.padding(top = 8.dp).fillMaxWidth().height(2.dp)
                .background(if (selected) Ids.colors.textPrimary else Ids.colors.background),
        )
    }
}

/** Final v1038 order: promo carousel, then Fastest, Coupons, Eats Club and Saved shortcuts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatsBrowseOffers(
    state: EatsBrowseState,
    onFastestClick: () -> Unit = {},
    onSavedClick: () -> Unit = {},
    showPromo: Boolean = true,
    savedSelected: Boolean = false,
) {
    var showClubSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showPromo) EatsPromoBanner(
            state.isPickup,
            false,
            {
                val code = if (state.isPickup) "EATS20" else "FREEDEL"
                state.selectedCoupon = code
                Toast.makeText(context, "Code $code saved. It applies at checkout.", Toast.LENGTH_SHORT).show()
            },
            Modifier.padding(top = 10.dp),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Row(
                Modifier.offset(x = (-24).dp).requiredWidth(maxWidth + 48.dp).padding(horizontal = 12.dp).padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EatsShortcut("Fastest", "moto", ItundaArtSet.Color, 34.dp, onFastestClick, Modifier.weight(1f))
                EatsShortcut(
                    if (state.selectedCoupon == null) "Coupons" else "Coupons ✓",
                    "voucher", ItundaArtSet.Color, 34.dp, { state.showCoupons = true }, Modifier.weight(1f), "Coupons shortcut",
                )
                EatsShortcut("Eats Club", "gift", ItundaArtSet.Color, 34.dp, { showClubSheet = true }, Modifier.weight(1f))
                EatsShortcut("Saved", "heartred", ItundaArtSet.Proto, 28.dp, onSavedClick, Modifier.weight(1f), selected = savedSelected)
            }
        }
    }
    if (showClubSheet) ModalBottomSheet(
        onDismissRequest = { showClubSheet = false },
        containerColor = Ids.colors.background,
        contentColor = Ids.colors.textPrimary,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            EatsMembershipCard()
            TextButton(onClick = { showClubSheet = false }) { Text("Not now") }
        }
    }
}

@Composable
private fun EatsShortcut(
    label: String,
    art: String,
    artSet: ItundaArtSet,
    artSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = label,
    selected: Boolean = false,
) {
    Column(
        modifier.semantics { contentDescription = accessibilityLabel }.pressScaleClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(if (selected) Ids.colors.pressed else Ids.colors.surfaceSoft),
            contentAlignment = Alignment.Center,
        ) {
            ItundaArt(art, artSet, artSize)
        }
        Text(label, color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
