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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Sticky v1038 top bar: address, orders and cart on the first row; search below. */
@Composable
fun EatsBrowseHeader(
    state: EatsBrowseState,
    searchInput: String,
    cartCount: Int,
    onSearchChange: (String) -> Unit,
    onOrdersClick: () -> Unit,
    onCartClick: () -> Unit,
) {
    var showAddressSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val addressLabel = state.address.substringBefore(",").trim().ifBlank { state.address }

    Column(modifier = Modifier.fillMaxWidth().background(Ids.colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 6.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EatsHeaderIcon("Back", "back") { backDispatcher?.onBackPressed() }
            Row(
                modifier = Modifier.weight(1f).height(44.dp)
                    .semantics { contentDescription = "Change delivery address" }
                    .pressScaleClickable { showAddressSheet = true }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ItundaArt("pin", ItundaArtSet.Line, 22.dp, tint = Ids.colors.textBrand)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.isPickup) "Pickup near" else "Deliver to",
                        color = Ids.colors.textSecondary,
                        fontSize = 12.sp,
                    )
                    Text(
                        addressLabel,
                        color = Ids.colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
                ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = Ids.colors.textTertiary)
            }
            EatsHeaderIcon("Orders", "receipt-rw", onOrdersClick)
            Box {
                EatsHeaderIcon("Cart", "bag") {
                    when {
                        cartCount == 0 -> Toast.makeText(context, "Your cart is empty", Toast.LENGTH_SHORT).show()
                        state.cartRestaurant == null -> Toast.makeText(context, "Open your restaurant to continue the cart", Toast.LENGTH_SHORT).show()
                        else -> onCartClick()
                    }
                }
                if (cartCount > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 3.dp)
                            .defaultMinSize(minWidth = 18.dp).height(18.dp)
                            .clip(RoundedCornerShape(9.dp)).background(Ids.colors.danger)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(cartCount.toString(), color = Ids.colors.onBrand, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(46.dp)
                .clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ItundaArt("search", ItundaArtSet.Line, 20.dp, tint = Ids.colors.textSecondary)
            Box(Modifier.weight(1f)) {
                if (searchInput.isEmpty()) {
                    Text("Search restaurants or dishes", color = Ids.colors.textSecondary, fontSize = 15.sp)
                }
                BasicTextField(
                    value = searchInput,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(Ids.colors.brand),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search restaurants or dishes" },
                )
            }
        }
    }

    if (showAddressSheet) EatsAddressPickerSheet(state, onDismiss = { showAddressSheet = false })
}

/** Two large, equal fulfillment cards from prototype v1038. */
@Composable
internal fun EatsModeSwitch(isPickup: Boolean, onModeChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EatsFulfillmentOption(
            label = "Delivery",
            subtitle = "To your door",
            art = "scooter",
            selected = !isPickup,
            onClick = { onModeChange(false) },
            modifier = Modifier.weight(1f),
        )
        EatsFulfillmentOption(
            label = "Pickup",
            subtitle = "No delivery fee",
            art = "bag",
            selected = isPickup,
            onClick = { onModeChange(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EatsFulfillmentOption(
    label: String,
    subtitle: String,
    art: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground = if (selected) Ids.colors.textBrand else Ids.colors.textSecondary
    Row(
        modifier = modifier.height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) Ids.colors.pressed else Ids.colors.chip)
            .semantics { contentDescription = label }
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ItundaArt(art, ItundaArtSet.Line, 22.dp, tint = foreground)
        Column {
            Text(label, color = foreground, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = foreground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EatsHeaderIcon(label: String, art: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).semantics { contentDescription = label }.pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ItundaArt(art, ItundaArtSet.Line, 24.dp, tint = Ids.colors.iconPrimary)
    }
}
