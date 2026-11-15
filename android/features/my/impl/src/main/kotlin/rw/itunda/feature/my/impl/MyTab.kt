package rw.itunda.feature.my.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.FlatRow
import rw.itunda.core.designsystem.components.FlatSection
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.relativeTimeAgo
import rw.itunda.core.designsystem.itundaface.BriefcaseGlyph
import rw.itunda.core.designsystem.itundaface.PlaceMarket
import rw.itunda.core.designsystem.itundaface.TravelHouse
import rw.itunda.core.designsystem.itundaface.WishlistHeart
import rw.itunda.core.designsystem.theme.Ids

// Real Naver-style "My" personal hub (2026-07-22), replacing what used to be this
// bottom tab's entire content (the exhaustive service catalog, now MenuScreen) --
// at the user's direct request: "My should be like Naver style My since we have
// shopping and eats and other products where users need to easily get track of
// their orders, reservation, favorites." Every number/row here is a real fetched
// count or preview, not decoration.
//
// Moved into :features:my:impl (2026-09-02, My Feature-module decomposition,
// completing the Home/Pay/Menu/My scope) -- confirmed zero MainViewModel/
// cross-Feature coupling before moving, unlike PayTab/MenuScreen. See
// [[project_itunda_feature_isolation]] for the full account.
@Composable
fun MyEntryPoint(
    onBack: () -> Unit,
    onSwitchToShop: () -> Unit = {},
    onSwitchToEats: () -> Unit = {},
    onSwitchToMarketplace: () -> Unit = {},
    onSwitchToJobs: () -> Unit = {},
    onSwitchToProperty: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    var profile by remember { mutableStateOf<rw.itunda.core.network.PublicUser?>(null) }
    LaunchedEffect(Unit) { try { profile = rw.itunda.core.network.NetworkClient.authApi.getProfile().user } catch (_: Exception) { } }
    var shopOrders by remember { mutableStateOf<List<rw.itunda.core.network.OrderDto>>(emptyList()) }
    var eatsOrders by remember { mutableStateOf<List<rw.itunda.core.network.EatsOrderDto>>(emptyList()) }
    var favoriteListingsCount by remember { mutableStateOf(0) }
    var favoriteJobPostsCount by remember { mutableStateOf(0) }
    var favoritePropertyListingsCount by remember { mutableStateOf(0) }
    var favoriteRestaurantsCount by remember { mutableStateOf(0) }
    var myListingsCount by remember { mutableStateOf(0) }
    var myJobPostsCount by remember { mutableStateOf(0) }
    var myPropertyListingsCount by remember { mutableStateOf(0) }
    var affiliateLinks by remember { mutableStateOf<List<rw.itunda.core.network.AffiliateLinkDto>>(emptyList()) }
    var affiliateCommissions by remember { mutableStateOf<List<rw.itunda.core.network.AffiliateCommissionDto>>(emptyList()) }
    var myScamReports by remember { mutableStateOf<List<rw.itunda.core.network.ScamReportDto>>(emptyList()) }
    var myBookingReviews by remember { mutableStateOf<List<rw.itunda.core.network.MerchantBookingReviewDto>>(emptyList()) }

    LaunchedEffect(Unit) {
        try { shopOrders = rw.itunda.core.network.NetworkClient.apiService.getMyOrders().orders } catch (_: Exception) { }
        try { eatsOrders = rw.itunda.core.network.NetworkClient.apiService.getMyEatsOrders().orders } catch (_: Exception) { }
        // Real accuracy fix (2026-09-13, same fix as myListingsCount below) -- this
        // badge previously showed page 1's item count (capped at 20), not the real
        // total, for any user with more than 20 real favorited listings.
        try { favoriteListingsCount = rw.itunda.core.network.NetworkClient.apiService.getMyFavoriteListings().totalElements } catch (_: Exception) { }
        try { favoriteJobPostsCount = rw.itunda.core.network.NetworkClient.apiService.getMyFavoriteJobPosts().totalElements } catch (_: Exception) { }
        try { favoritePropertyListingsCount = rw.itunda.core.network.NetworkClient.apiService.getMyFavoritePropertyListings().totalElements } catch (_: Exception) { }
        try { favoriteRestaurantsCount = rw.itunda.core.network.NetworkClient.apiService.getMyFavoriteRestaurants().totalElements } catch (_: Exception) { }
        // Real accuracy fix (2026-09-09, same pass as getMyListings's pagination
        // fix): this badge previously showed page 1's item count (capped at 20),
        // not the real total, for any user with more than 20 real listings.
        try { myListingsCount = rw.itunda.core.network.NetworkClient.apiService.getMyListings().totalElements } catch (_: Exception) { }
        // Real accuracy fix (2026-09-09, same pass as getMyJobPosts's pagination
        // fix): this badge previously showed page 1's item count (capped at 20),
        // not the real total, for any user with more than 20 real job posts.
        try { myJobPostsCount = rw.itunda.core.network.NetworkClient.apiService.getMyJobPosts().totalElements } catch (_: Exception) { }
        // Real accuracy fix (2026-09-09, same pass as getMyPropertyListings's
        // pagination fix): this badge previously showed page 1's item count
        // (capped at 20), not the real total, for any user with more than 20
        // real property listings.
        try { myPropertyListingsCount = rw.itunda.core.network.NetworkClient.apiService.getMyPropertyListings().totalElements } catch (_: Exception) { }
        try { affiliateLinks = rw.itunda.core.network.NetworkClient.apiService.getMyAffiliateLinks().links } catch (_: Exception) { }
        try { affiliateCommissions = rw.itunda.core.network.NetworkClient.apiService.getMyAffiliateCommissions().commissions } catch (_: Exception) { }
        try { myScamReports = rw.itunda.core.network.NetworkClient.apiService.getMyScamReports().reports } catch (_: Exception) { }
        try { myBookingReviews = rw.itunda.core.network.NetworkClient.apiService.getMyBookingReviews().reviews } catch (_: Exception) { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal, top = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("You", color = Ids.colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(CircleShape).pressScaleClickable(onClick = onOpenSettings), contentAlignment = Alignment.Center) {
                    rw.itunda.core.designsystem.components.ItundaArt("gear", rw.itunda.core.designsystem.components.ItundaArtSet.Line, 26.dp, tint = Ids.colors.textTertiary)
                }
            }
        }
        profile?.let { u ->
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        rw.itunda.core.designsystem.components.ItundaArt("shopkeeper", rw.itunda.core.designsystem.components.ItundaArtSet.Face, 58.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("${u.firstName} ${u.lastName}", color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            u.phoneNumber.let { if (it.length > 7) it.take(4) + " ••• " + it.takeLast(3) else it } + if (u.kycVerified) " · Verified" else "",
                            color = Ids.colors.textTertiary, fontSize = 15.sp,
                        )
                    }
                }
            }
        }
        item { PinUpgradeCard() }
        item { VerificationCard() }
        if (affiliateLinks.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Partner earnings", color = Ids.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text("Earn 3% when people buy via your links.", color = Ids.colors.textTertiary, fontSize = 14.sp)
                }
            }
            item {
                val totalClicks = affiliateLinks.sumOf { it.clickCount }
                val totalEarned = affiliateCommissions.sumOf { it.commissionAmount }
                FlatSection(
                    "",
                    listOf(
                        FlatRow("Shared links", trailing = "${affiliateLinks.size}", glyph = { ArtTile("send") }),
                        FlatRow("Clicks", trailing = "$totalClicks", glyph = { ArtTile("chart") }),
                        FlatRow("Earned", trailing = "${String.format(Locale.US, "%,.0f", totalEarned)} RWF", glyph = { ArtTile("money") }),
                    ),
                )
            }
        }
        if (shopOrders.isNotEmpty() || eatsOrders.isNotEmpty()) {
            item { Text("My orders", color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            items(shopOrders.take(3), key = { it.id }) { order ->
                Row(
                    modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onSwitchToShop).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Shop order", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(order.status, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                    Text(String.format(Locale.US, "%,.0f RWF", order.totalAmount), color = Ids.colors.textPrimary, fontSize = 15.sp)
                }
            }
            items(eatsOrders.take(3), key = { it.id }) { order ->
                Row(
                    modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onSwitchToEats).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Eats order", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(order.status, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                    Text(String.format(Locale.US, "%,.0f RWF", order.totalAmount), color = Ids.colors.textPrimary, fontSize = 15.sp)
                }
            }
        }
        item { Text("My favorites", color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp)) }
        item {
            FlatSection(
                "",
                listOf(
                    FlatRow("Marketplace wishlist", trailing = "$favoriteListingsCount", glyph = { ArtTile("market", badge = true) }, onClick = onSwitchToMarketplace),
                    FlatRow("Jobs wishlist", trailing = "$favoriteJobPostsCount", glyph = { ArtTile("briefcase", badge = true) }, onClick = onSwitchToJobs),
                    FlatRow("Property wishlist", trailing = "$favoritePropertyListingsCount", glyph = { ArtTile("house", badge = true) }, onClick = onSwitchToProperty),
                    FlatRow("Restaurant favorites", trailing = "$favoriteRestaurantsCount", glyph = { ArtTile("restaurant", badge = true) }, onClick = onSwitchToEats),
                ),
            )
        }
        item {
            FlatSection(
                "My listings",
                listOf(
                    FlatRow("Marketplace", trailing = "$myListingsCount", glyph = { ArtTile("market") }, onClick = onSwitchToMarketplace),
                    FlatRow("Jobs posted", trailing = "$myJobPostsCount", glyph = { ArtTile("briefcase") }, onClick = onSwitchToJobs),
                    FlatRow("Property listed", trailing = "$myPropertyListingsCount", glyph = { ArtTile("house") }, onClick = onSwitchToProperty),
                ),
            )
        }
        if (myBookingReviews.isNotEmpty()) {
            item { Text("My reviews", color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            items(myBookingReviews, key = { it.id }) { review ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(review.serviceName, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("★".repeat(review.rating.coerceIn(0, 5)), color = StarGold, fontSize = 13.sp)
                    }
                    review.comment?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                    review.ownerReply?.takeIf { it.isNotBlank() }?.let { reply ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                        ) {
                            Text("Owner replied", color = Ids.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(reply, color = Ids.colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                    Text(relativeTimeAgo(review.createdAt), color = Ids.colors.textTertiary, fontSize = 11.sp)
                }
                Divider(color = Ids.colors.divider, thickness = 0.5.dp)
            }
        }
        if (myScamReports.isNotEmpty()) {
            item { Text("My scam reports", color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            items(myScamReports, key = { it.id }) { report ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(report.reportedIdentifier, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(report.reason, color = Ids.colors.textSecondary, fontSize = 13.sp)
                    Text(relativeTimeAgo(report.createdAt), color = Ids.colors.textTertiary, fontSize = 11.sp)
                }
                Divider(color = Ids.colors.divider, thickness = 0.5.dp)
            }
        }
        item { ProfilePhotoCard() }
    }
}

/** Prototype row glyph: 40dp soft tile with the illustrated icon; [badge] adds the small red heart. */
@Composable
private fun ArtTile(name: String, badge: Boolean = false) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
        rw.itunda.core.designsystem.components.ItundaArt(name, rw.itunda.core.designsystem.components.ItundaArtSet.Color, 28.dp)
        if (badge) Box(Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(Ids.colors.surface), contentAlignment = Alignment.Center) {
            rw.itunda.core.designsystem.components.ItundaArt("heartred", rw.itunda.core.designsystem.components.ItundaArtSet.Proto, 14.dp)
        }
    }
}
