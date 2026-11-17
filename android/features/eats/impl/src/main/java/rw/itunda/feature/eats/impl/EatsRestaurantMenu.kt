package rw.itunda.feature.eats.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.StatusBadge
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.QtyButton
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddressSuggestionDto
import rw.itunda.core.network.MapBookmarkDto
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ShoppingMerchantDto




@Composable
fun RestaurantMenuView(
    restaurant: ShoppingMerchantDto,
    menu: List<MerchantProductDto>?,
    cart: SnapshotStateMap<String, EatsCartLine>,
    onBack: () -> Unit,
    onCheckout: () -> Unit,
    isPickup: Boolean = false,
) {
    BackHandler(onBack = onBack)
    // Real menu-options selection UI (2026-07-21, v1: required single-select only) --
    // ports bank-mfe's own MenuView 1:1. Only one item's option panel is expanded at a
    // time, matching this file's own established "inline-card-replaces-trigger"
    // convention (no modal-overlay pattern exists anywhere in this app).
    var selectedTab by remember { mutableStateOf("Menu") }
    var expandedProductId by remember { mutableStateOf<String?>(null) }
    // Real optional/multi-select menu option groups (itunda Eats redesign,
    // 2026-08-28) -- the backend has always supported 4 real group combinations
    // (required x multiSelect, see MenuOptionGroup.kt's own doc comment), but this
    // UI only ever rendered required-single-select radios. groupId -> the real
    // selected choiceIds for that group (0 or 1 for a single-select group, 0+ for
    // multiSelect) -- eatsCartKey/eatsLineUnitPrice/eatsOptionsSummary already
    // operate on a plain choiceIds list with no single-choice assumption, so this
    // is a real UI-layer fix, not a pricing/cart-model change.
    val pendingChoices = remember { mutableStateMapOf<String, List<String>>() }
    val cartCount = cart.values.sumOf { it.quantity }
    // Real cart-bar subtotal + savings breakdown (2026-08-28) -- the fixed bottom
    // bar previously only showed the item count. Purely a client-side sum over
    // cart data already fetched -- no new backend call.
    val cartSubtotal = cart.values.filter { it.quantity > 0 }.sumOf { line ->
        val item = menu?.find { it.id == line.productId }
        if (item != null) eatsLineUnitPrice(item, line.choiceIds) * line.quantity else 0.0
    }
    val cartOriginalSubtotal = cart.values.filter { it.quantity > 0 }.sumOf { line ->
        val item = menu?.find { it.id == line.productId }
        if (item != null) (item.originalPrice ?: item.price) * line.quantity else 0.0
    }

    fun setSimpleQty(productId: String, qty: Int) {
        val key = eatsCartKey(productId, emptyList())
        cart[key] = EatsCartLine(productId, maxOf(0, qty))
    }

    fun toggleExpand(productId: String) {
        pendingChoices.clear()
        expandedProductId = if (expandedProductId == productId) null else productId
    }

    fun addConfiguredToCart(item: MerchantProductDto) {
        val groups = item.optionGroups
        if (groups.any { it.required && pendingChoices[it.id].isNullOrEmpty() }) return
        val choiceIds = groups.flatMap { pendingChoices[it.id] ?: emptyList() }
        val key = eatsCartKey(item.id, choiceIds)
        cart[key] = EatsCartLine(item.id, (cart[key]?.quantity ?: 0) + 1, choiceIds)
        pendingChoices.clear()
        expandedProductId = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(bottom = Ids.layout.screenVertical)) {
        val eta = restaurant.deliveryTimeMinutes?.let { if (isPickup) maxOf(10, it - 10) else it }
        val heroMeta = buildList {
            eta?.let { add(if (isPickup) "Ready in $it min" else "$it min") }
            restaurant.distanceKm?.let { add(String.format(Locale.US, "%.1f km", it)) }
            if (isPickup) add("Pickup · No delivery fee")
            else if (restaurant.participatesInEatsMembership) add("Free delivery")
        }.joinToString(" · ").ifBlank { if (isPickup) "Pickup · No delivery fee" else "Delivery" }
        EatsRestaurantHero(restaurant.businessName, onBack, meta = heroMeta, category = restaurant.category, photoUrl = restaurant.photoUrl)
        if (selectedTab != "Reviews") RestaurantRatingBadge(restaurant.merchantId)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            listOf("Menu", "Info", "Reviews").forEach { tab ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.pressScaleClickable { selectedTab = tab },
                ) {
                    Text(
                        tab,
                        color = if (selectedTab == tab) Ids.colors.textPrimary else Ids.colors.textSecondary,
                        fontSize = 15.sp,
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                    )
                    Box(
                        Modifier.width(22.dp).height(2.dp)
                            .background(if (selectedTab == tab) Ids.colors.brand else Color.Transparent, RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
        if (selectedTab == "Info") {
            val details = listOfNotNull(
                restaurant.category?.let { "Category" to it },
                restaurant.openingHours?.let { "Opening hours" to it },
                restaurant.phoneNumber?.let { "Phone" to it },
                restaurant.distanceKm?.let { "Distance" to String.format(Locale.US, "%.1f km", it) },
                restaurant.minOrderAmount?.let { "Minimum order" to String.format(Locale.US, "%,.0f RWF", it) },
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("About this restaurant", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (details.isEmpty()) {
                    Text("Restaurant details aren't available yet.", color = Ids.colors.textSecondary, fontSize = 14.sp)
                } else {
                    details.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(label, color = Ids.colors.textTertiary, fontSize = 12.sp)
                            Text(value, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        } else if (selectedTab == "Reviews") {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp)) {
                RestaurantRatingBadge(restaurant.merchantId, initiallyExpanded = true)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (menu == null) {
                item { SkeletonBlock(height = 72.dp) }
            } else if (menu.isEmpty()) {
                item { EmptyState("This restaurant hasn't added menu items yet — check back soon.") }
            } else {
                item { Text("Popular", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) }
                items(menu, key = { it.id }) { p ->
                    val hasOptions = p.optionGroups.isNotEmpty()
                    val simpleKey = eatsCartKey(p.id, emptyList())
                    val simpleQty = if (hasOptions) 0 else (cart[simpleKey]?.quantity ?: 0)
                    val isExpanded = expandedProductId == p.id
                    val allGroupsChosen = p.optionGroups.all { !it.required || !pendingChoices[it.id].isNullOrEmpty() }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    ) {
                        EatsMenuItemRow(
                            name = p.name,
                            description = p.description,
                            price = String.format(Locale.US, "%,.0f RWF", p.price),
                            originalPrice = p.originalPrice?.takeIf { it > p.price }?.let { String.format(Locale.US, "%,.0f RWF", it) },
                            imageUrl = p.imageUrl,
                            qty = simpleQty,
                            optionsHint = if (hasOptions) (if (isExpanded) "Tap + to close options" else "Options required — tap + to choose") else null,
                            onAdd = { if (hasOptions) toggleExpand(p.id) else setSimpleQty(p.id, simpleQty + 1) },
                            onMinus = { if (simpleQty > 0) setSimpleQty(p.id, simpleQty - 1) },
                        )
                        if (hasOptions && isExpanded) {
                            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                p.optionGroups.forEach { group ->
                                    val selectedIds = pendingChoices[group.id] ?: emptyList()
                                    // Real optional/multi-select labels (2026-08-28) -- honest
                                    // about what this group actually requires, matching its own
                                    // real required/multiSelect flags rather than always
                                    // claiming "choose 1".
                                    val groupHint = if (group.multiSelect) {
                                        if (group.required) "choose at least 1" else "choose any (optional)"
                                    } else {
                                        if (group.required) "choose 1" else "choose 1 (optional)"
                                    }
                                    Column {
                                        Row {
                                            Text(group.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("· $groupHint", color = Ids.colors.textTertiary, fontSize = 13.sp)
                                        }
                                        Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            group.choices.forEach { choice ->
                                                val selected = choice.id in selectedIds
                                                fun toggle() {
                                                    pendingChoices[group.id] = if (group.multiSelect) {
                                                        if (selected) selectedIds - choice.id else selectedIds + choice.id
                                                    } else {
                                                        // Single-select: re-tapping the current choice
                                                        // clears it when the group is optional (a real
                                                        // "none of these"), never for a required group.
                                                        if (selected && !group.required) emptyList() else listOf(choice.id)
                                                    }
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth().pressScaleClickable { toggle() },
                                                ) {
                                                    if (group.multiSelect) {
                                                        androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { toggle() })
                                                    } else {
                                                        androidx.compose.material3.RadioButton(selected = selected, onClick = { toggle() })
                                                    }
                                                    Text(
                                                        choice.name + if (choice.priceDelta > 0) String.format(Locale.US, " (+%,.0f RWF)", choice.priceDelta) else "",
                                                        color = Ids.colors.textPrimary, fontSize = 13.sp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (allGroupsChosen) Ids.colors.brand else Ids.colors.textTertiary)
                                        .pressScaleClickable(enabled = allGroupsChosen) { addConfiguredToCart(p) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Add to cart", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
        // Real "frequently ordered together" cross-sell (itunda Eats redesign,
        // 2026-08-28) -- keyed off the most recently added cart line. See
        // EatsFrequentlyOrderedWith's own doc comment.
        cart.values.filter { it.quantity > 0 }.lastOrNull()?.let { lastLine ->
            EatsFrequentlyOrderedWith(productId = lastLine.productId) { item ->
                setSimpleQty(item.id, (cart[eatsCartKey(item.id, emptyList())]?.quantity ?: 0) + 1)
            }
        }
        if (cartCount > 0) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand).pressScaleClickable(onClick = onCheckout).padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItundaArt("bag", ItundaArtSet.Line, 18.dp, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checkout ($cartCount item${if (cartCount == 1) "" else "s"})", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    // Real computed subtotal + savings (2026-08-28) -- see
                    // cartSubtotal's own doc comment just above.
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (cartOriginalSubtotal > cartSubtotal) {
                            Text(
                                String.format(Locale.US, "%,.0f RWF", cartOriginalSubtotal), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                        Text(String.format(Locale.US, "%,.0f RWF", cartSubtotal), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    }
}

// Real self-hosted address-search autocomplete (2026-07-18) -- itunda's own Nominatim
// geocoder, not a third-party Maps API. Mirrors bank-mfe's AddressAutocomplete component:
// debounced real search-as-you-type, a real suggestion dropdown, and on selection the
// real resolved coordinates are handed back so the caller can submit them explicitly
// (taking priority over EatsOrderService's own automatic single-best-match fallback).
// Typing without selecting still places a real order via that fallback.
@Composable
internal fun AddressAutocompleteField(
    address: String,
    onAddressChange: (String) -> Unit,
    onSuggestionSelected: (AddressSuggestionDto) -> Unit,
) {
    var suggestions by remember { mutableStateOf<List<AddressSuggestionDto>>(emptyList()) }
    var justSelected by remember { mutableStateOf(false) }
    // Real Uber/Kakao T-style saved-places quick-select (2026-08-23) -- same real gap
    // already closed for ride booking (RideScreen.kt): itunda's own "map bookmarks"
    // feature (the Maps tab's star/save) was never surfaced here either, despite a
    // delivery address being an even more universal need than a ride destination
    // (every single delivery order needs one). No new backend work -- same existing
    // GET /api/v1/maps/bookmarks this field's own search suggestions already sit
    // alongside.
    var bookmarks by remember { mutableStateOf<List<MapBookmarkDto>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            bookmarks = NetworkClient.apiService.getMyMapBookmarks().bookmarks
        } catch (_: Exception) {
            // Real, non-critical -- the quick-select list just won't render if this fails.
        }
    }

    LaunchedEffect(address) {
        if (justSelected) {
            justSelected = false
            return@LaunchedEffect
        }
        if (address.trim().length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        suggestions = try {
            val res = NetworkClient.apiService.searchDeliveryAddress(address.trim())
            if (res.success) res.suggestions else emptyList()
        } catch (e: Exception) {
            // Real, non-critical -- a failed suggestion fetch shouldn't block typing a
            // plain address; the order still places, just without a confirmed pin.
            emptyList()
        }
    }

    Column {
        IdsTextField(
            value = address,
            onValueChange = onAddressChange,
            label = "Delivery address",
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column {
                    suggestions.forEach { s ->
                        Text(
                            s.displayName,
                            color = Ids.colors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressScaleClickable {
                                    justSelected = true
                                    suggestions = emptyList()
                                    onSuggestionSelected(s)
                                }
                                .padding(12.dp),
                        )
                    }
                }
            }
        } else if (address.isBlank() && bookmarks.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column {
                    Text(
                        "Saved places", color = Ids.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    bookmarks.forEach { b ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressScaleClickable {
                                    justSelected = true
                                    onSuggestionSelected(AddressSuggestionDto(b.displayName, b.latitude, b.longitude))
                                }
                                .padding(12.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                    .background(runCatching { Color(android.graphics.Color.parseColor(b.color)) }.getOrDefault(Ids.colors.brand)),
                            )
                            Text(b.displayName, color = Ids.colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

