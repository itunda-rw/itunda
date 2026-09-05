package rw.itunda.feature.shop.impl

import androidx.compose.foundation.background
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import rw.itunda.core.designsystem.theme.IdsIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import rw.itunda.core.designsystem.components.StatusBadge
import kotlin.math.roundToInt
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.DealProductDto
import rw.itunda.core.network.TimeDealViewDto
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.ShoppingMerchantDto




// Real Toss Shopping banner carousel (2026-08-12, direct user screenshot) -- a
// swipeable full-width promo card with a page indicator, built on real active Time
// Deal data (see backend TimeDealService.getBanners's own doc comment for why this
// isn't a separate fabricated CMS). Tapping a banner isn't wired to a merchant open
// here (the caller doesn't pass openMerchant in) -- deliberately kept read-only for
// this first pass rather than half-wiring a tap target, a real follow-up if needed.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ShoppingBannerCarousel(banners: List<rw.itunda.core.network.TimeDealViewDto>) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { banners.size }
    Column {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(140.dp),
        ) { page ->
            val v = banners[page]
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                    .background(Ids.colors.brand.copy(alpha = 0.12f)),
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
                        val discountPercent = if (v.deal.originalPrice > 0) {
                            (100 - (v.deal.dealPrice / v.deal.originalPrice * 100)).roundToInt()
                        } else 0
                        if (discountPercent > 0) {
                            Text("$discountPercent% off", color = Ids.colors.danger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        // Real live HH:MM:SS countdown (2026-08-25, direct Toss Shopping
                        // reference screenshot -- "⏰ 23:24:20 Limited time offer") --
                        // ticks off the same real v.deal.endsAt the rail badge below
                        // already reads via formatTimeDealCountdown, just to the second.
                        var countdown by remember(v.deal.endsAt) { mutableStateOf(formatTimeDealCountdownHms(v.deal.endsAt)) }
                        LaunchedEffect(v.deal.endsAt) {
                            while (true) {
                                countdown = formatTimeDealCountdownHms(v.deal.endsAt)
                                delay(1000)
                            }
                        }
                        Text("⏰ $countdown left", color = Ids.colors.danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(v.productName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2)
                        Text(String.format(Locale.US, "%,.0f RWF", v.deal.dealPrice), color = Ids.colors.textPrimary, fontSize = 15.sp)
                        Text(v.businessName, color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                    ProductImageThumb(v.productImageUrl, size = 96.dp, corner = 12.dp)
                }
                if (banners.size > 1) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("${pagerState.currentPage + 1} | ${banners.size}", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row -- see
// backend ShoppingMissionService's own doc comment. Every icon here is a real,
// backend-tracked once-per-day (or once-ever, for the welcome bonus) claim that
// credits real RWF straight into the real account -- no fabricated points currency.
@Composable
internal fun ShoppingPointsRow(
    missions: List<rw.itunda.core.network.ShoppingMissionDto>,
    spinOutcomes: List<rw.itunda.core.network.SpinOutcomeDto>,
    busyType: String?,
    onComplete: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Get points and coupons", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            missions.forEach { m ->
                val done = if (m.type == "WELCOME_BONUS") m.claimedEver else m.completedToday
                // Real fix (2026-08-13, direct user report against a live screenshot):
                // SCROLL used a share/network icon and CAT_FEED used a plain heart --
                // neither matched what the row's own label said. Swipe reads as
                // "scroll through content," Pets reads as "cat," matching every other
                // row here (Autorenew for the daily check-in cycle, Star for the spin
                // wheel) where the icon and label already agreed.
                val icon = when (m.type) {
                    "CHECK_IN" -> Icons.Outlined.Autorenew
                    "SCROLL" -> Icons.Outlined.Swipe
                    "SPIN" -> IdsIcons.Star
                    "CAT_FEED" -> Icons.Outlined.Pets
                    else -> Icons.AutoMirrored.Outlined.ReceiptLong
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .pressScaleClickable(enabled = !done && busyType == null) { onComplete(m.type) },
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (done) Ids.colors.chip else Ids.colors.brand.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (busyType == m.type) {
                            Text("…", color = Ids.colors.textSecondary, fontSize = 18.sp)
                        } else {
                            Icon(icon, contentDescription = null, tint = if (done) Ids.colors.textTertiary else Ids.colors.brand)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        m.label,
                        color = if (done) Ids.colors.textTertiary else Ids.colors.textPrimary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                    if (!done) {
                        // Real, stated odds for SPIN (item 248 discipline) -- shows the
                        // real min-max range up front rather than a hidden mechanic.
                        val rewardText = if (m.type == "SPIN" && spinOutcomes.isNotEmpty()) {
                            String.format(Locale.US, "+%,.0f~%,.0f", spinOutcomes.minOf { it.amount }, spinOutcomes.maxOf { it.amount })
                        } else {
                            String.format(Locale.US, "+%,.0f", m.rewardAmount)
                        }
                        Text(rewardText, color = Ids.colors.brand, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// Real Toss Shopping "Recommended for you" 2-column grid (2026-08-12, direct user
// screenshot) -- restyles the existing real Deals rail data (badge/rating/cashback/
// heart) instead of a horizontal-scroll rail, matching the reference layout exactly.
// rating/reviewCount (2026-08-25) is real ProductReview data, batched server-side
// (see ShoppingController.getDeals' own doc comment) -- same IdsIcons.Star/StarGold/
// "%.1f (%d)" treatment EatsCategoryBrowse's restaurant cards already use, for the
// same real reason (an honest star only when a real review exists, never a fabricated
// default). Real cashback (via ShoppingCashbackService's own published flat rate,
// itunda's real "1%" the same fallback the merchant-open shortcuts elsewhere in this
// file already use) and the real discount/stock badges are shown too. Deliberately NOT
// shown: a "Now at 30-day low" price-history indicator -- itunda has no price-history
// table, so that reference-screenshot element stays honestly scoped out rather than
// fabricated.
@Composable
internal fun RecommendedForYouGrid(
    deals: List<DealProductDto>,
    favoriteProductIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onOpen: (DealProductDto) -> Unit,
) {
    val rowCount = (deals.size + 1) / 2
    Column(
        // 284dp (was 260dp) -- +24dp for the new rating row / strikethrough price line
        // added 2026-08-25.
        modifier = Modifier.height((rowCount * 284).dp),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(deals, key = { it.id }) { d ->
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface)
                        .pressScaleClickable { onOpen(d) }.padding(10.dp),
                ) {
                    Box {
                        ProductImageThumb(d.imageUrl, size = 140.dp, corner = 10.dp)
                        val discountPercent = d.discountPercent
                        if (discountPercent != null && discountPercent > 0) {
                            StatusBadge("$discountPercent% off deal", tint = Ids.colors.danger, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
                        }
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.35f)).pressScaleClickable { onToggleFavorite(d.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (d.id in favoriteProductIds) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (d.id in favoriteProductIds) Ids.colors.danger else Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(d.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(String.format(Locale.US, "%,.0f RWF", d.price), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        // Real strikethrough original price (2026-08-25, matches the
                        // Toss Shopping reference) -- only shown when the merchant
                        // actually set a higher originalPrice, same field the discount
                        // badge above already derives from.
                        val originalPrice = d.originalPrice
                        if (originalPrice != null && originalPrice > d.price) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                String.format(Locale.US, "%,.0f RWF", originalPrice),
                                color = Ids.colors.textTertiary,
                                fontSize = 11.sp,
                                textDecoration = TextDecoration.LineThrough,
                            )
                        }
                    }
                    if (d.rating != null && d.reviewCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                            Icon(IdsIcons.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("%.1f (%d)".format(d.rating, d.reviewCount), color = Ids.colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                    // Real "Best seller" badge (2026-08-28) -- see ShopSellerContactPicker.kt's
                    // own doc comment. "Ships today" below replaced with real
                    // available/out-of-stock stock copy -- itunda's real commerce
                    // fulfillment model is merchant-pickup/delivery-time-estimate, not
                    // multi-day parcel shipping, so that literal reference copy was never
                    // honest here.
                    if (d.isBestSeller) ShopBestSellerBadge()
                    Text(
                        d.stockQuantity?.let { if (it == 0) "Out of stock" else "$it available" } ?: "Available",
                        color = if (d.stockQuantity == 0) Ids.colors.danger else Ids.colors.textSecondary,
                        fontSize = 11.sp,
                    )
                    // Real cashback (ShoppingCashbackService's own published flat rate)
                    // -- matches the "Earn up to ₩X" real reference row exactly, in RWF.
                    Text(String.format(Locale.US, "Earn up to %,.0f RWF", d.price * 0.01), color = Ids.colors.brand, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun CartFab(totalItems: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand).pressScaleClickable(onClick = onClick).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("View cart ($totalItems item${if (totalItems == 1) "" else "s"})", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// Real Coupang-style photo-forward store card (2026-07-24) -- same treatment as the
// Eats restaurant card and Marketplace's ListingCard: a real merchant-set photo leads,
// same ShoppingMerchantDto.photoUrl field Eats already uses (this endpoint and Eats'
// share the same DTO), so no backend change was needed here.
@Composable
internal fun StoreCard(m: ShoppingMerchantDto, onOpen: () -> Unit, isScrollTouched: Boolean = false) {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
        // Real fix (2026-08-25, direct user directive after the account ledger's
        // own version shipped: "implant that into our designs and apply it across
        // our ecosystems") -- same live, finger-follows-through-a-scroll-drag
        // spring feedback, now a real parameter of the shared pressScaleClickable
        // (IdsInteractions.kt), not duplicated logic.
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
                            else -> StorePhotoPlaceholder()
                        }
                    }
                } else {
                    StorePhotoPlaceholder()
                }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(m.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    listOfNotNull(m.category, "${m.cashbackRate} cashback").joinToString(" · "),
                    color = Ids.colors.textSecondary,
                    fontSize = 12.sp,
                )
                // Real rating/distance/ETA row (2026-08-14) -- ShoppingMerchantDto
                // already carries these fields (rating/distanceKm/deliveryTimeMinutes/
                // minOrderAmount are the exact same DTO Eats' own RestaurantCard
                // already renders this way), just never shown on Shop's own store
                // card. Same "no fabricated data" gating: only renders when at least
                // one real value is present.
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
                                m.minOrderAmount?.let { "Min ${formatMoneyShopBrowse(it)} RWF" },
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

@Composable
internal fun StorePhotoPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Storefront, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(40.dp))
    }
}

// Real product-image thumbnail (2026-07-21) -- imageUrl is a merchant-supplied external
// URL (see backend MerchantProduct.kt's own doc comment: no upload/storage layer exists
// in this backend, so this is a real "bring your own URL" v1, not a fake pipeline). Coil
// handles the null/broken-URL case itself (falls through to `error`), same fallback icon
// shown for a product that simply has no image set at all -- both are real, valid states.
@Composable
internal fun ProductImageThumb(imageUrl: String?, size: Dp = 44.dp, corner: Dp = 14.dp) {
    if (imageUrl.isNullOrBlank()) {
        Box(modifier = Modifier.size(size).clip(RoundedCornerShape(corner)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(size / 2), tint = Ids.colors.brand)
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(corner)).background(Ids.colors.surfaceSoft),
        )
    }
}

// Real discount-price display (2026-07-21) -- Baymard Institute's own placement research
// (docs/DESIGN_REFERENCES.md Section 5): the discount % must sit immediately next to the
// struck-through original price, not elsewhere on the card. discountPercent is always
// server-computed (see backend doc comment), never trusted from the client, so this is
// purely a rendering of numbers the server already validated.
@Composable
internal fun ProductPriceRow(p: MerchantProductDto) {
    val discountPercent = p.discountPercent
    if (p.originalPrice != null && discountPercent != null && discountPercent > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$discountPercent%",
                color = Ids.colors.danger,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(String.format(Locale.US, "%,.0f RWF", p.price), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(
            String.format(Locale.US, "%,.0f RWF", p.originalPrice),
            color = Ids.colors.textSecondary,
            fontSize = 11.sp,
            textDecoration = TextDecoration.LineThrough,
        )
    } else {
        Text(String.format(Locale.US, "%,.0f RWF", p.price), color = Ids.colors.textSecondary, fontSize = 13.sp)
    }
    // Real bulk/wholesale pricing (2026-07-25) -- closes the gap named in Baemin's own
    // real 배민상회 B2B supplies marketplace research. Shows the best (highest-quantity)
    // real tier as a hint; the actual price used at checkout is always resolved
    // server-side from the real ordered quantity, never trusted from this display.
    p.priceTiers.maxByOrNull { it.minQuantity }?.let { bestTier ->
        Text(
            String.format(Locale.US, "Buy ${bestTier.minQuantity}+ for %,.0f RWF each", bestTier.unitPrice),
            color = Ids.colors.success, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}


// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same shape BikeRentalScreen.kt/BusScreen.kt already use.
private fun formatMoneyShopBrowse(value: Number): String = String.format(Locale.US, "%,d", value.toLong())
