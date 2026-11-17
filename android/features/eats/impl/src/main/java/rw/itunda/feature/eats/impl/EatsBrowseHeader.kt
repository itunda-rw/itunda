package rw.itunda.feature.eats.impl

import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ShoppingMerchantDto

/** Prototype Eats promo strip: tappable delivery-only offer with fixed contrast-safe art. */
@Composable
fun EatsPromoBanner(
    isPickup: Boolean,
    selected: Boolean,
    onSelectCoupon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFE08A))
            .pressScaleClickable {
                if (isPickup) Toast.makeText(context, "Free delivery only applies to delivery orders.", Toast.LENGTH_SHORT).show()
                else onSelectCoupon()
            }.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Free delivery on your first order", color = Color(0xFF191F28), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (selected) "Code FREEDEL saved" else "Delivery only · Code FREEDEL", color = Color(0xFF4E5968), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        ItundaArt("gift", ItundaArtSet.Color, 52.dp)
    }
}

/** State shared by the Eats browse header and checkout entry point. */
class EatsBrowseState {
    var isPickup by mutableStateOf(false)
    var selectedCoupon by mutableStateOf<String?>(null)
    var address by mutableStateOf("Kimironko, KG 11 Ave")
    var savedAddresses by mutableStateOf(listOf("Kimironko, KG 11 Ave", "Kacyiru, KG 7 Ave"))
    var cartRestaurant by mutableStateOf<ShoppingMerchantDto?>(null)
    var showCoupons by mutableStateOf(false)
}

@Composable
internal fun rememberEatsBrowseState(): EatsBrowseState = remember { EatsBrowseState() }

/** Native Compose version of the v1038 Eats address, search, fulfillment and coupon header. */
@OptIn(ExperimentalMaterial3Api::class)
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
    var newAddress by remember { mutableStateOf("") }
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Column(modifier = Modifier.fillMaxWidth().background(Ids.colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EatsHeaderIcon("Back", "back") { backDispatcher?.onBackPressed() }
            Row(
                modifier = Modifier.weight(1f).height(48.dp)
                    .semantics { contentDescription = "Change delivery address" }
                    .pressScaleClickable { newAddress = ""; showAddressSheet = true }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ItundaArt("pin", ItundaArtSet.Line, 22.dp, tint = Ids.colors.textBrand)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.isPickup) "Pickup near" else "Deliver to",
                        color = Ids.colors.textTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.address,
                            color = Ids.colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = Ids.colors.textTertiary)
                    }
                }
            }
            EatsHeaderIcon("Orders", "receipt-rw", onOrdersClick)
            Box {
                EatsHeaderIcon("Cart", "bag", {
                    if (cartCount == 0) Toast.makeText(context, "Your cart is empty", Toast.LENGTH_SHORT).show()
                    else if (state.cartRestaurant == null) Toast.makeText(context, "Open your restaurant to continue the cart", Toast.LENGTH_SHORT).show()
                    else onCartClick()
                })
                if (cartCount > 0) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 1.dp)
                            .clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(cartCount.toString(), color = Ids.colors.onBrand, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(46.dp)
                .clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItundaArt("search", ItundaArtSet.Line, 20.dp, tint = Ids.colors.textTertiary)
            Box(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                if (searchInput.isEmpty()) {
                    Text("Search restaurants or dishes", color = Ids.colors.textTertiary, fontSize = 15.sp)
                }
                BasicTextField(
                    value = searchInput,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(Ids.colors.brand),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search restaurants" },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EatsModeChoice(
                label = "Delivery",
                subtitle = "To your door",
                art = "scooter",
                selected = !state.isPickup,
                modifier = Modifier.weight(1f),
            ) { state.isPickup = false }
            EatsModeChoice(
                label = "Pickup",
                subtitle = "No delivery fee",
                art = "bag",
                selected = state.isPickup,
                modifier = Modifier.weight(1f),
            ) {
                state.isPickup = true
                if (state.selectedCoupon == "FREEDEL") state.selectedCoupon = null
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp)
                .height(48.dp).clip(RoundedCornerShape(14.dp))
                .background(Ids.colors.surface)
                .border(1.dp, Ids.colors.divider, RoundedCornerShape(14.dp))
                .semantics { contentDescription = "Coupons" }
                .pressScaleClickable { state.showCoupons = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItundaArt("tag", ItundaArtSet.Line, 22.dp, tint = Ids.colors.textBrand)
            Spacer(Modifier.width(10.dp))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("Coupons", color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(" · ${if (state.isPickup) 1 else 2} available", color = Ids.colors.textSecondary, fontSize = 14.sp)
                state.selectedCoupon?.let {
                    Text(" · $it ✓", color = Ids.colors.textBrand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            ItundaArt("chev", ItundaArtSet.Line, 16.dp, tint = Ids.colors.textTertiary)
        }
    }

    if (showAddressSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddressSheet = false },
            containerColor = Ids.colors.surface,
            contentColor = Ids.colors.textPrimary,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Delivery address", color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                state.savedAddresses.forEachIndexed { index, savedAddress ->
                    Row(
                        modifier = Modifier.fillMaxWidth().pressScaleClickable {
                            state.address = savedAddress
                            showAddressSheet = false
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(listOf("Home", "Work").getOrElse(index) { "Other" }, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
                            Text(savedAddress, color = Ids.colors.textSecondary, fontSize = 13.sp)
                        }
                        androidx.compose.material3.RadioButton(
                            selected = state.address == savedAddress,
                            onClick = { state.address = savedAddress; showAddressSheet = false },
                        )
                    }
                }
                if (state.savedAddresses.size < 5) {
                    IdsTextField(
                        value = newAddress,
                        onValueChange = { newAddress = it.take(40) },
                        label = "Street or landmark, e.g. Remera, KG 5 Ave",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        enabled = newAddress.isNotBlank(),
                        onClick = {
                            state.savedAddresses = state.savedAddresses + newAddress.trim()
                            state.address = newAddress.trim()
                            showAddressSheet = false
                        },
                    ) { Text("Add and use") }
                }
                TextButton(onClick = { showAddressSheet = false }) { Text("Close") }
                Text("Sample addresses for this prototype.", color = Ids.colors.textTertiary, fontSize = 12.sp)
            }
        }
    }

    if (state.showCoupons) {
        EatsCouponsSheet(
            isPickup = state.isPickup,
            selectedCoupon = state.selectedCoupon,
            onDismiss = { state.showCoupons = false },
            onSelect = { code -> state.selectedCoupon = code; state.showCoupons = false },
        )
    }
}

@Composable
private fun EatsModeChoice(
    label: String,
    subtitle: String,
    art: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier.height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) Ids.colors.pressed else Ids.colors.surfaceSoft)
            .semantics { contentDescription = label }
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ItundaArt(art, ItundaArtSet.Line, 22.dp, tint = if (selected) Ids.colors.textBrand else Ids.colors.textSecondary)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = if (selected) Ids.colors.textBrand else Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = if (selected) Ids.colors.textBrand else Ids.colors.textSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EatsHeaderIcon(label: String, art: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).semantics { contentDescription = label }.pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ItundaArt(art, ItundaArtSet.Line, 24.dp, tint = Ids.colors.iconPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatsCouponsSheet(
    isPickup: Boolean,
    selectedCoupon: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Ids.colors.surface,
        contentColor = Ids.colors.textPrimary,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Your coupons", color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            EatsCouponCard("Free delivery", "First order, delivery only", "FREEDEL", Color(0xFFFFE08A), selectedCoupon == "FREEDEL") {
                if (isPickup) {
                    onDismiss()
                    Toast.makeText(context, "Free delivery only applies to delivery orders.", Toast.LENGTH_SHORT).show()
                } else {
                    onSelect("FREEDEL")
                }
            }
            EatsCouponCard("20% off", "Up to 2,000 RWF off your meal", "EATS20", Color(0xFFCFE8FF), selectedCoupon == "EATS20") {
                onSelect("EATS20")
            }
        }
    }
}

@Composable
private fun EatsCouponCard(
    title: String,
    description: String,
    code: String,
    background: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color(0xFF191F28), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("$description · $code", color = Color(0xFF191F28), fontSize = 13.sp)
        }
        Box(
            modifier = Modifier.clip(RoundedCornerShape(18.dp))
                .background(Ids.colors.textPrimary).alpha(if (selected) 0.45f else 1f)
                .pressScaleClickable(enabled = !selected, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (selected) "Saved" else "Use", color = Ids.colors.background, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
