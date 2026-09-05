package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storefront
import rw.itunda.core.designsystem.theme.IdsIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.EatsDishDto
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.ShoppingMerchantDto




// Real Coupang Eats-style photo-forward restaurant card (2026-07-24) -- confirmed via
// real reference research that Coupang Eats leads every restaurant card with real food
// photography (not a store icon/logo) specifically because it reads faster than text,
// and deliberately keeps the info-dense secondary line (rating/distance/ETA/min-order)
// itunda already had -- the same research flagged that Coupang Eats itself hides that
// line until you open the restaurant, which it calls out as a real usability flaw, so
// this keeps it visible rather than copying that specific weakness.
// Real category -> icon mapping (2026-08-12) -- covers itunda's own real seeded
// merchant categories (SeedDataRunner.kt: "Rwandan"/"Fast Food"/"Coffee & Bakery"),
// with a generic fallback for any other real category a merchant sets that isn't
// explicitly mapped, so a new category never renders with no icon at all.
internal fun eatsCategoryIcon(category: String) = when {
    category.contains("fast food", ignoreCase = true) -> Icons.Outlined.Fastfood
    category.contains("coffee", ignoreCase = true) || category.contains("bakery", ignoreCase = true) -> Icons.Outlined.Coffee
    else -> Icons.Outlined.Restaurant
}

@Composable
internal fun EatsCategoryIconRow(categories: List<String>, selectedCategory: String?, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        categories.forEach { c ->
            val selected = c == selectedCategory
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).pressScaleClickable { onSelect(c) }) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                        .background(if (selected) Ids.colors.brand.copy(alpha = 0.15f) else Ids.colors.surfaceSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(eatsCategoryIcon(c), contentDescription = null, tint = if (selected) Ids.colors.brand else Ids.colors.textSecondary, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    c,
                    color = if (selected) Ids.colors.textPrimary else Ids.colors.textSecondary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// Real Baemin/Coupang Eats-style sort chip (itunda Eats redesign, 2026-08-28) --
// see EatsScreen.kt's own sortMode doc comment for the full account.
@Composable
internal fun EatsSortChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.pressScaleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (active) Ids.colors.brand else Ids.colors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            label,
            color = if (active) Ids.colors.brand else Ids.colors.textSecondary,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

@Composable
internal fun RestaurantCard(m: ShoppingMerchantDto, isFavorite: Boolean, favoriteBusy: Boolean, onOpen: () -> Unit, onToggleFavorite: () -> Unit, isScrollTouched: Boolean = false) {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
        // Real fix (2026-08-25, direct user directive: "implant that into our
        // designs and apply it across our ecosystems") -- matches Shop's own
        // StoreCard identical fix; see IdsInteractions.kt's pressScaleClickable
        // doc comment for the full sourced account.
        modifier = Modifier.fillMaxWidth().pressScaleClickable(isScrollTouched = isScrollTouched, onClick = onOpen),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = Ids.layout.cardCornerRadius, topEnd = Ids.layout.cardCornerRadius))) {
                if (m.photoUrl != null) {
                    SubcomposeAsyncImage(
                        model = m.photoUrl,
                        contentDescription = m.businessName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (painter.state) {
                            is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                            else -> RestaurantPhotoPlaceholder()
                        }
                    }
                } else {
                    RestaurantPhotoPlaceholder()
                }
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) Ids.colors.danger else Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(22.dp)
                        .pressScaleClickable(enabled = !favoriteBusy, onClick = onToggleFavorite),
                )
                // Real Coupang 와우(WOW)-style per-restaurant membership badge (itunda
                // Eats redesign, 2026-08-28) -- see
                // ShoppingMerchantDto.participatesInEatsMembership's own doc comment.
                if (m.participatesInEatsMembership) {
                    Text(
                        "Member — free delivery", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .background(Ids.colors.brand, RoundedCornerShape(999.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(m.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                // Real "Discount" badge (itunda Eats redesign, 2026-08-28) -- see
                // ShoppingMerchantDto.maxDiscountPercent's own doc comment: the real,
                // currently-highest discount among this restaurant's own active menu.
                val maxDiscountPercent = m.maxDiscountPercent
                if (maxDiscountPercent != null && maxDiscountPercent > 0) {
                    Text("Up to $maxDiscountPercent% off", color = Ids.colors.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(
                    listOfNotNull(m.category, "${m.cashbackRate} cashback").joinToString(" · "),
                    color = Ids.colors.textSecondary,
                    fontSize = 12.sp,
                )
                if (m.rating != null || m.distanceKm != null || m.minOrderAmount != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        if (m.rating != null) {
                            Icon(IdsIcons.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("%.1f (%d)".format(m.rating, m.reviewCount), color = Ids.colors.textSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            listOfNotNull(
                                m.distanceKm?.let { "%.1f km".format(it) },
                                m.deliveryTimeMinutes?.let { "~$it min" },
                                m.minOrderAmount?.let { "Min ${formatMoneyEatsCategory(it)} RWF" },
                            ).joinToString(" · "),
                            color = Ids.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

// Real Coupang Eats-style dish grid (2026-08-03) -- see EatsDishDto's own doc comment
// for the sourcing (a real Coupang Eats UX teardown, re-verified directly against the
// primary article text: "actual food photographs arranged in a three-column grid...
// much more intuitive"; the same article separately confirms rating/delivery-time/fee
// are NOT shown here, only surfacing once you open the restaurant -- matching this
// grid's deliberately bare tile). Tapping a dish opens its restaurant (itunda's own
// domain model requires the restaurant context to price/order any item, not a claim
// about Coupang Eats specifically) -- there's no standalone dish-detail concept.
// Additive, not a replacement for the restaurant list below: that list's search-by-name
// and full alphabetical browse are real, working, and this dish endpoint has no
// text-search of its own, so removing the list would be a real functionality loss, not
// just a visual one. Hidden once a real search is active for the same reason.
@Composable
internal fun EatsDishGrid(dishes: List<EatsDishDto>, onOpen: (EatsDishDto) -> Unit) {
    if (dishes.isEmpty()) return
    val rows = (dishes.size + 2) / 3
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(148.dp * rows),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        gridItems(dishes, key = { it.id }) { dish ->
            Column(modifier = Modifier.pressScaleClickable { onOpen(dish) }) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Ids.layout.cardCornerRadius))) {
                    if (dish.imageUrl != null) {
                        SubcomposeAsyncImage(
                            model = dish.imageUrl,
                            contentDescription = dish.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            when (painter.state) {
                                is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                                else -> RestaurantPhotoPlaceholder()
                            }
                        }
                    } else {
                        RestaurantPhotoPlaceholder()
                    }
                    // Real "recommended for you" signal (2026-08-16) -- see
                    // EatsDishDto.recommended's own doc comment. Kept as a small corner
                    // tag, not a full redesign, to preserve this grid's own deliberately
                    // bare-tile intent (real, sourced Coupang Eats research above).
                    if (dish.recommended) {
                        Text(
                            "For you", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .background(Ids.colors.brand, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(dish.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Text(dish.merchantName, color = Ids.colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun RestaurantPhotoPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Storefront, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(40.dp))
    }
}

// Real menu-item photo thumbnail (2026-08-12) -- same real SubcomposeAsyncImage +
// placeholder-on-failure pattern this file already uses for RestaurantCard/
// EatsDishGrid, just sized for an inline menu row. Kept local to this module rather
// than reusing Shop's private ProductImageThumb (a different Gradle module, that
// composable isn't visible here).
@Composable
internal fun MenuItemThumb(imageUrl: String?, size: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp))) {
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (painter.state) {
                    is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                    else -> RestaurantPhotoPlaceholder()
                }
            }
        } else {
            RestaurantPhotoPlaceholder()
        }
    }
}

// Real, human-readable summary of a resolved cart line's selected options -- mirrors
// the backend's own EatsOrderService.buildSelectedOptionsJson, but purely for display;
// pricing always comes from the real menu item + real choice deltas, never this string.
internal fun eatsOptionsSummary(item: MerchantProductDto, choiceIds: List<String>): String {
    if (choiceIds.isEmpty()) return ""
    val names = item.optionGroups.flatMap { it.choices }.filter { it.id in choiceIds }.map { it.name }
    return if (names.isEmpty()) "" else " (${names.joinToString(", ")})"
}

internal fun eatsLineUnitPrice(item: MerchantProductDto, choiceIds: List<String>): Double {
    val delta = item.optionGroups.flatMap { it.choices }.filter { it.id in choiceIds }.sumOf { it.priceDelta }
    return item.price + delta
}


// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same shape BikeRentalScreen.kt/BusScreen.kt already use.
private fun formatMoneyEatsCategory(value: Number): String = String.format(Locale.US, "%,d", value.toLong())
