package rw.itunda.feature.eats.impl

import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ShoppingMerchantDto

class EatsBrowseState {
    var isPickup by mutableStateOf(false)
    var selectedCoupon by mutableStateOf<String?>(null)
    var address by mutableStateOf("Kimironko, KG 11 Ave")
    var savedAddresses by mutableStateOf(listOf(EatsSavedAddress("Home", "Kimironko, KG 11 Ave"), EatsSavedAddress("Work", "Kacyiru, KG 7 Ave")))
    var cartRestaurant by mutableStateOf<ShoppingMerchantDto?>(null)
    var showCoupons by mutableStateOf(false)
}

@Composable
internal fun rememberEatsBrowseState(): EatsBrowseState = remember { EatsBrowseState() }

@Composable
internal fun EatsModeSwitch(isPickup: Boolean, onModeChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.height(38.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(false to "Delivery", true to "Pickup").forEach { (pickup, label) ->
            val selected = isPickup == pickup
            Box(
                modifier = Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (selected) Ids.colors.background else Color.Transparent)
                    .semantics { contentDescription = label }.pressScaleClickable { onModeChange(pickup) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected) Ids.colors.textPrimary else Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatsBrowseHeader(
    state: EatsBrowseState,
    searchInput: String,
    cartCount: Int,
    onSearchChange: (String) -> Unit,
    onOrdersClick: () -> Unit,
    onCartClick: () -> Unit,
    onFastestClick: () -> Unit = {},
    onSavedClick: () -> Unit = {},
) {
    var showAddressSheet by remember { mutableStateOf(false) }
    var showClubSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val addressLabel = state.address.substringBefore(",").trim().ifBlank { state.address }
    Column(modifier = Modifier.fillMaxWidth().background(Ids.colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 6.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EatsHeaderIcon("Back", "back") { backDispatcher?.onBackPressed() }
            Row(
                modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ItundaArt("search", ItundaArtSet.Line, 20.dp, tint = Ids.colors.textSecondary)
                Box(Modifier.weight(1f)) {
                    if (searchInput.isEmpty()) Text("Search restaurants", color = Ids.colors.textSecondary, fontSize = 15.sp)
                    BasicTextField(
                        value = searchInput, onValueChange = onSearchChange, singleLine = true,
                        textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(Ids.colors.brand),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search restaurants" },
                    )
                }
            }
            EatsHeaderIcon("Orders", "receipt-rw", onOrdersClick)
            Box {
                EatsHeaderIcon("Cart", "bag", {
                    when {
                        cartCount == 0 -> Toast.makeText(context, "Your cart is empty", Toast.LENGTH_SHORT).show()
                        state.cartRestaurant == null -> Toast.makeText(context, "Open your restaurant to continue the cart", Toast.LENGTH_SHORT).show()
                        else -> onCartClick()
                    }
                })
                if (cartCount > 0) Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 3.dp).defaultMinSize(minWidth = 18.dp).height(18.dp)
                        .clip(RoundedCornerShape(9.dp)).background(Ids.colors.danger).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(cartCount.toString(), color = Ids.colors.onBrand, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f).height(44.dp).semantics { contentDescription = "Change delivery address" }
                    .pressScaleClickable { showAddressSheet = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(addressLabel, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = Ids.colors.textTertiary, modifier = Modifier.rotate(90f))
            }
            EatsModeSwitch(state.isPickup, { pickup ->
                state.isPickup = pickup
                if (pickup && state.selectedCoupon == "FREEDEL") state.selectedCoupon = null
            }, Modifier.width(144.dp))
        }
        EatsPromoBanner(
            state.isPickup, state.selectedCoupon != null,
            {
                val code = if (state.isPickup) "EATS20" else "FREEDEL"
                state.selectedCoupon = code
                Toast.makeText(context, "Code " + code + " saved. It applies at checkout.", Toast.LENGTH_SHORT).show()
            },
            Modifier.padding(horizontal = 24.dp).padding(top = 10.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EatsShortcut("Fastest", "moto", ItundaArtSet.Color, 34.dp, onFastestClick, Modifier.weight(1f))
            EatsShortcut(if (state.selectedCoupon == null) "Coupons" else "Coupons ✓", "voucher", ItundaArtSet.Color, 34.dp, { state.showCoupons = true }, Modifier.weight(1f), "Coupons")
            EatsShortcut("Eats Club", "gift", ItundaArtSet.Color, 34.dp, { showClubSheet = true }, Modifier.weight(1f))
            EatsShortcut("Saved", "heartred", ItundaArtSet.Proto, 28.dp, onSavedClick, Modifier.weight(1f))
        }
    }
    if (showAddressSheet) EatsAddressPickerSheet(state, onDismiss = { showAddressSheet = false })
    if (state.showCoupons) EatsCouponsSheet(
        state.isPickup, state.selectedCoupon,
        onDismiss = { state.showCoupons = false },
        onSelect = { code ->
            state.selectedCoupon = code
            state.showCoupons = false
            Toast.makeText(context, "Code " + code + " saved. It applies at checkout.", Toast.LENGTH_SHORT).show()
        },
        onRemove = {
            state.selectedCoupon = null
            state.showCoupons = false
            Toast.makeText(context, "Coupon removed", Toast.LENGTH_SHORT).show()
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
) {
    Column(modifier.semantics { contentDescription = accessibilityLabel }.pressScaleClickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
            ItundaArt(art, artSet, artSize)
        }
        Text(label, color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun EatsHeaderIcon(label: String, art: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).semantics { contentDescription = label }.pressScaleClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        ItundaArt(art, ItundaArtSet.Line, 24.dp, tint = Ids.colors.iconPrimary)
    }
}
