package rw.itunda.feature.my.impl

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
fun MyTab(
    onBack: () -> Unit,
    onSwitchToShop: () -> Unit = {},
    onSwitchToEats: () -> Unit = {},
    onSwitchToMarketplace: () -> Unit = {},
    onSwitchToJobs: () -> Unit = {},
    onSwitchToProperty: () -> Unit = {},
) {
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
        try { favoritePropertyListingsCount = rw.itunda.core.network.NetworkClient.apiService.getMyFavoritePropertyListings().favorites.size } catch (_: Exception) { }
        try { favoriteRestaurantsCount = rw.itunda.core.network.NetworkClient.apiService.getMyFavoriteRestaurants().favorites.size } catch (_: Exception) { }
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
        item { BackTopBar("My", onBack) }
        item { ProfilePhotoCard() }
        item { PinUpgradeCard() }
        item { VerificationCard() }
        if (affiliateLinks.isNotEmpty()) {
            item {
                val totalClicks = affiliateLinks.sumOf { it.clickCount }
                val totalEarned = affiliateCommissions.sumOf { it.commissionAmount }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Partner earnings", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Earn 3% on any purchase made through a product link you've shared.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Links shared", color = Ids.colors.textSecondary, fontSize = 13.sp)
                        Text("${affiliateLinks.size}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total clicks", color = Ids.colors.textSecondary, fontSize = 13.sp)
                        Text("$totalClicks", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total earned", color = Ids.colors.textSecondary, fontSize = 13.sp)
                        Text("${String.format(Locale.US, "%,.0f", totalEarned)} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
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
        item { Text("My favorites", color = Ids.colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        item {
            FlatSection(
                "",
                listOf(
                    FlatRow("Marketplace wishlist", trailing = "$favoriteListingsCount", glyph = { WishlistHeart(favorited = true, size = 28.dp) }, onClick = onSwitchToMarketplace),
                    FlatRow("Jobs wishlist", trailing = "$favoriteJobPostsCount", glyph = { WishlistHeart(favorited = true, size = 28.dp) }, onClick = onSwitchToJobs),
                    FlatRow("Property wishlist", trailing = "$favoritePropertyListingsCount", glyph = { WishlistHeart(favorited = true, size = 28.dp) }, onClick = onSwitchToProperty),
                    FlatRow("Restaurant favorites", trailing = "$favoriteRestaurantsCount", glyph = { WishlistHeart(favorited = true, size = 28.dp) }, onClick = onSwitchToEats),
                ),
            )
        }
        item {
            FlatSection(
                "My listings",
                listOf(
                    FlatRow("Marketplace", trailing = "$myListingsCount", glyph = { PlaceMarket(size = 28.dp) }, onClick = onSwitchToMarketplace),
                    FlatRow("Jobs posted", trailing = "$myJobPostsCount", glyph = { BriefcaseGlyph(size = 28.dp) }, onClick = onSwitchToJobs),
                    FlatRow("Property listed", trailing = "$myPropertyListingsCount", glyph = { TravelHouse(size = 28.dp) }, onClick = onSwitchToProperty),
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
    }
}
