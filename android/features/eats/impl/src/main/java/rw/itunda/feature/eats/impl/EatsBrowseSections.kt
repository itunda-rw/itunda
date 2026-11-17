package rw.itunda.feature.eats.impl

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.rotate
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

/** The address and fulfillment row scrolls with browse content, below the sticky action bar. */
@Composable
fun EatsDeliveryPickupRow(state: EatsBrowseState) {
    var showAddressSheet by remember { mutableStateOf(false) }
    val addressLabel = state.address.substringBefore(",").trim().ifBlank { state.address }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f).height(44.dp).semantics { contentDescription = "Change delivery address" }
                .pressScaleClickable { showAddressSheet = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(addressLabel, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = Ids.colors.textTertiary, modifier = Modifier.rotate(90f))
        }
        EatsModeSwitch(
            state.isPickup,
            { pickup ->
                state.isPickup = pickup
                if (pickup && state.selectedCoupon == "FREEDEL") state.selectedCoupon = null
            },
            Modifier.width(144.dp),
        )
    }
    if (showAddressSheet) EatsAddressPickerSheet(state, onDismiss = { showAddressSheet = false })
}

/** Prototype order: promo, then Fastest / Coupons / Eats Club / Saved shortcuts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatsBrowseOffers(
    state: EatsBrowseState,
    onFastestClick: () -> Unit,
    onSavedClick: () -> Unit,
    showPromo: Boolean = true,
    savedSelected: Boolean = false,
) {
    var showClubSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showPromo) EatsPromoBanner(
            state.isPickup,
            state.selectedCoupon != null,
            {
                val code = if (state.isPickup) "EATS20" else "FREEDEL"
                state.selectedCoupon = code
                Toast.makeText(context, "Code $code saved. It applies at checkout.", Toast.LENGTH_SHORT).show()
            },
            Modifier.padding(top = 10.dp),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Row(
                Modifier.offset(x = (-24).dp).width(maxWidth + 48.dp).padding(horizontal = 12.dp).padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EatsShortcut("Fastest", "moto", ItundaArtSet.Color, 34.dp, onFastestClick, Modifier.weight(1f))
                EatsShortcut(
                    if (state.selectedCoupon == null) "Coupons" else "Coupons ✓",
                    "voucher", ItundaArtSet.Color, 34.dp, { state.showCoupons = true }, Modifier.weight(1f), "Coupons",
                )
                EatsShortcut("Eats Club", "gift", ItundaArtSet.Color, 34.dp, { showClubSheet = true }, Modifier.weight(1f))
                EatsShortcut("Saved", "heartred", ItundaArtSet.Proto, 28.dp, onSavedClick, Modifier.weight(1f), selected = savedSelected)
            }
        }
    }
    if (state.showCoupons) EatsCouponsSheet(
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
            Toast.makeText(context, "Free delivery only applies to delivery orders.", Toast.LENGTH_SHORT).show()
        },
    )
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
