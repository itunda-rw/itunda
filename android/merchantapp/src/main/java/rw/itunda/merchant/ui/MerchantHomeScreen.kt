package rw.itunda.merchant.ui

import rw.itunda.core.designsystem.components.IdsTextField

import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.outlined.RateReview
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.StatusBadge
import rw.itunda.core.designsystem.theme.Ids
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.EatsOrderDto
import rw.itunda.merchant.network.MerchantDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.UpdateEatsOrderStatusRequest

private enum class MerchantTab { ORDERS, DINE_IN, BOOKINGS, CATALOG, REGISTER, BUSINESS_ACCOUNT, REPORTS, REVIEWS, COUPONS, VISITORS, FOLLOWERS, UPDATES, PHOTOS, PAYROLL, ADS, BILLING, CASH_ADVANCE, VOUCHERS }

@Composable
fun MerchantHomeScreen(merchant: MerchantDto, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(MerchantTab.ORDERS) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Itunda Merchant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(merchant.businessName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Real Coupang badge system (2026-08-05) -- see docs/DESIGN_REFERENCES.md
                    // Section 9. A real, named verification tier next to the business name.
                    if (merchant.kybVerified) {
                        StatusBadge("✓ Verified", tint = Ids.colors.brand)
                    }
                }
            }
            IconButton(onClick = { NetworkClient.currentTokenStore().clearSession(); onLogout() }) {
                Icon(Icons.Filled.ExitToApp, contentDescription = "Log out")
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            listOf(
                MerchantTab.ORDERS to "Orders",
                MerchantTab.DINE_IN to "Dine-in",
                MerchantTab.BOOKINGS to "Bookings",
                MerchantTab.CATALOG to "Catalog",
                MerchantTab.REGISTER to "Register",
                MerchantTab.BUSINESS_ACCOUNT to "Business",
                MerchantTab.REPORTS to "Reports",
                MerchantTab.REVIEWS to "Reviews",
                MerchantTab.COUPONS to "Coupons",
                MerchantTab.VISITORS to "Visitors",
                MerchantTab.FOLLOWERS to "Followers",
                MerchantTab.UPDATES to "Updates",
                MerchantTab.PHOTOS to "Photos",
                MerchantTab.PAYROLL to "Payroll",
                MerchantTab.ADS to "Ads",
                MerchantTab.BILLING to "Billing",
                MerchantTab.CASH_ADVANCE to "Cash advance",
                MerchantTab.VOUCHERS to "Vouchers",
            ).forEach { (t, label) ->
                val selected = t == tab
                Text(
                    label,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 18.dp).pressScaleClickable { tab = t },
                )
            }
        }

        when (tab) {
            MerchantTab.ORDERS -> OrdersTab()
            MerchantTab.DINE_IN -> DineInTab(restaurantId = merchant.id)
            MerchantTab.BOOKINGS -> BookingTab()
            MerchantTab.CATALOG -> CatalogTab()
            MerchantTab.REGISTER -> PosTab()
            MerchantTab.BUSINESS_ACCOUNT -> BusinessAccountTab()
            MerchantTab.REPORTS -> ReportsTab()
            MerchantTab.REVIEWS -> ReviewsTab(restaurantId = merchant.id)
            MerchantTab.COUPONS -> CouponsTab()
            MerchantTab.VISITORS -> VisitorAnalyticsTab()
            MerchantTab.FOLLOWERS -> FollowersTab()
            MerchantTab.UPDATES -> UpdatesTab(merchantId = merchant.id)
            MerchantTab.PHOTOS -> PhotosTab()
            MerchantTab.PAYROLL -> PayrollTab()
            MerchantTab.ADS -> AdsTab()
            MerchantTab.BILLING -> BillingTab()
            MerchantTab.CASH_ADVANCE -> VendorCashAdvanceTab(merchantId = merchant.id)
            MerchantTab.VOUCHERS -> VoucherRedeemTab()
        }
    }
}

/**
 * Real incoming Eats orders (restaurant side) -- this previously only ever existed
 * in the consumer app's own bank-mfe (any merchant who wanted to manage incoming
 * orders had to use their buyer account's app, a real structural gap). Restaurant-
 * driven statuses only: PLACED -> ACCEPTED -> PREPARING -> READY_FOR_PICKUP; once a
 * rider claims it, this app's job is done (rider-driven statuses have no action here).
 */
@Composable
private fun OrdersTab() {
    var orders by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            orders = NetworkClient.apiService.getRestaurantOrders().orders
            error = null
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(8000)
            refresh()
        }
    }

    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

    val list = orders
    if (list == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val active = list.filter { it.status in setOf("PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP") }
    if (active.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No open orders right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(active, key = { it.id }) { order ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        StatusBadge(order.status)
                        Text("${"%,.0f".format(order.totalAmount)} RWF", fontWeight = FontWeight.Bold)
                    }
                    Text(order.deliveryAddress, style = MaterialTheme.typography.bodySmall)
                    order.deliveryNotes?.takeIf { it.isNotBlank() }?.let {
                        Text("Note: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    // Real Baemin-style 포장주문 (Pickup) terminal edge (item 208) -- a
                    // PICKUP order at READY_FOR_PICKUP has no `nextRestaurantAction`
                    // (there's no rider to hand off to), so it previously just sat
                    // here forever with no action anywhere to close it out.
                    val readyForPickupHandoff = order.fulfillmentType == "PICKUP" && order.status == "READY_FOR_PICKUP"
                    if (readyForPickupHandoff) {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                        IdsButton(
                            text = "Mark picked up",
                            onClick = {
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.completePickupOrder(order.id)
                                        refresh()
                                    } catch (e: Exception) {
                                        error = "Couldn't update this order. Try again."
                                    }
                                }
                            },
                        )
                    } else {
                        nextRestaurantAction(order.status)?.let { (nextStatus, label) ->
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                            IdsButton(
                                text = label,
                                onClick = {
                                    scope.launch {
                                        try {
                                            NetworkClient.apiService.advanceRestaurantOrderStatus(order.id, UpdateEatsOrderStatusRequest(nextStatus))
                                            refresh()
                                        } catch (e: Exception) {
                                            error = "Couldn't update this order. Try again."
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun nextRestaurantAction(status: String): Pair<String, String>? = when (status) {
    "PLACED" -> "ACCEPTED" to "Accept order"
    "ACCEPTED" -> "PREPARING" to "Start preparing"
    "PREPARING" -> "READY_FOR_PICKUP" to "Mark ready for pickup"
    else -> null
}

// Real written-review list + owner-reply management (item 184/185/188) -- see
// EatsReviewService.replyToRestaurantReview's own doc comment. bank-mfe already has
// this (item 184); this is the first Android client for the owner-reply side. Its own
// dedicated tab, unlike OrdersTab (which only ever shows ACTIVE orders -- reviews only
// exist once an order is DELIVERED, so there's no natural shared list to fold this into).
// Also folds in Commerce product reviews (item 188, mirroring merchant-mfe's item 187):
// no aggregate "all my products' reviews" backend endpoint exists, so this fans out one
// real per-product review fetch across the merchant's own catalog (getProductCatalog).
@Composable
private fun ReviewsTab(restaurantId: String) {
    var restaurantReviews by remember { mutableStateOf<List<rw.itunda.merchant.network.EatsReviewDto>?>(null) }
    var productReviews by remember { mutableStateOf<List<Pair<String, rw.itunda.merchant.network.ProductReviewDto>>?>(null) }
    // Real Coupang-style pre-purchase product Q&A (상품문의), owner-answer side -- see
    // ApiService.kt's own doc comment: ProductInquiryService.answerQuestion existed on
    // the backend with genuinely zero client anywhere until now. Same "no aggregate
    // backend endpoint, fan out per product" reasoning as productReviews above -- no
    // real "all my products' questions" endpoint exists either.
    var productInquiries by remember { mutableStateOf<List<Pair<String, rw.itunda.merchant.network.ProductInquiryDto>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refreshRestaurant() {
        try {
            restaurantReviews = NetworkClient.apiService.getRestaurantReviews(restaurantId).reviews
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
    suspend fun refreshProducts() {
        try {
            val products = NetworkClient.apiService.getProductCatalog().products
            productReviews = products.flatMap { p ->
                try {
                    NetworkClient.apiService.getProductReviews(p.id).reviews.map { p.name to it }
                } catch (e: Exception) {
                    emptyList()
                }
            }.sortedByDescending { it.second.createdAt }
            productInquiries = products.flatMap { p ->
                try {
                    NetworkClient.apiService.getProductInquiries(p.id).inquiries.map { p.name to it }
                } catch (e: Exception) {
                    emptyList()
                }
            }.sortedByDescending { it.second.createdAt }
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
    LaunchedEffect(Unit) {
        error = null
        refreshRestaurant()
        refreshProducts()
    }

    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
    val restaurantList = restaurantReviews
    val productList = productReviews
    val inquiryList = productInquiries
    if (restaurantList == null || productList == null || inquiryList == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (restaurantList.isEmpty() && productList.isEmpty() && inquiryList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("No reviews or questions yet — they'll show up here once customers start ordering.", icon = Icons.Outlined.RateReview)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (inquiryList.isNotEmpty()) {
            item { Text("Product questions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        }
        items(inquiryList, key = { "q_${it.second.id}" }) { (productName, inquiry) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(productName, fontWeight = FontWeight.Bold)
                    Text(inquiry.question, style = MaterialTheme.typography.bodyMedium)
                    ProductInquiryAnswerRow(inquiry = inquiry, onAnswered = { scope.launch { refreshProducts() } })
                }
            }
        }
        if (restaurantList.isNotEmpty()) {
            item { Text("Restaurant reviews", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        }
        items(restaurantList, key = { "r_${it.id}" }) { review ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("★".repeat(review.restaurantRating) + "☆".repeat(5 - review.restaurantRating), color = androidx.compose.ui.graphics.Color(0xFFF5A623))
                    review.restaurantComment?.takeIf { it.isNotBlank() }?.let {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    ReviewReplyRow(review = review, onReplied = { scope.launch { refreshRestaurant() } })
                }
            }
        }
        if (productList.isNotEmpty()) {
            item { Text("Product reviews", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        }
        items(productList, key = { "p_${it.second.id}" }) { (productName, review) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(productName, fontWeight = FontWeight.Bold)
                    Text("★".repeat(review.rating) + "☆".repeat(5 - review.rating), color = androidx.compose.ui.graphics.Color(0xFFF5A623))
                    review.comment?.takeIf { it.isNotBlank() }?.let {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    ProductReviewReplyRow(review = review, onReplied = { scope.launch { refreshProducts() } })
                }
            }
        }
    }
}

@Composable
private fun ReviewReplyRow(review: rw.itunda.merchant.network.EatsReviewDto, onReplied: () -> Unit) {
    var replying by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
    when {
        !review.ownerReply.isNullOrBlank() -> {
            Text(
                "Your reply: ${review.ownerReply}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        replying -> {
            IdsTextField(value = reply, onValueChange = { reply = it }, label = "Write a reply", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            IdsButton(
                text = if (submitting) "Submitting…" else "Reply",
                enabled = !submitting && reply.isNotBlank(),
                onClick = {
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            NetworkClient.apiService.replyToRestaurantReview(review.id, rw.itunda.merchant.network.ReplyToEatsReviewRequest(reply.trim()))
                            replying = false
                            onReplied()
                        } catch (e: Exception) {
                            error = "Could not submit your reply."
                        } finally {
                            submitting = false
                        }
                    }
                },
            )
        }
        else -> {
            IdsButton(text = "Reply", size = IdsButtonSize.Small, onClick = { replying = true })
        }
    }
}

@Composable
private fun ProductInquiryAnswerRow(inquiry: rw.itunda.merchant.network.ProductInquiryDto, onAnswered: () -> Unit) {
    var answering by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
    when {
        !inquiry.answer.isNullOrBlank() -> {
            Text(
                "Your answer: ${inquiry.answer}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        answering -> {
            IdsTextField(value = answer, onValueChange = { answer = it }, label = "Write an answer", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            IdsButton(
                text = if (submitting) "Submitting…" else "Answer",
                enabled = !submitting && answer.isNotBlank(),
                onClick = {
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            NetworkClient.apiService.answerProductInquiry(inquiry.id, rw.itunda.merchant.network.AnswerProductInquiryRequest(answer.trim()))
                            answering = false
                            onAnswered()
                        } catch (e: Exception) {
                            error = "Could not submit your answer."
                        } finally {
                            submitting = false
                        }
                    }
                },
            )
        }
        else -> {
            IdsButton(text = "Answer", size = IdsButtonSize.Small, onClick = { answering = true })
        }
    }
}

@Composable
private fun ProductReviewReplyRow(review: rw.itunda.merchant.network.ProductReviewDto, onReplied: () -> Unit) {
    var replying by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
    when {
        !review.ownerReply.isNullOrBlank() -> {
            Text(
                "Your reply: ${review.ownerReply}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        replying -> {
            IdsTextField(value = reply, onValueChange = { reply = it }, label = "Write a reply", modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            IdsButton(
                text = if (submitting) "Submitting…" else "Reply",
                enabled = !submitting && reply.isNotBlank(),
                onClick = {
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            NetworkClient.apiService.replyToProductReview(review.id, rw.itunda.merchant.network.ReplyToProductReviewRequest(reply.trim()))
                            replying = false
                            onReplied()
                        } catch (e: Exception) {
                            error = "Could not submit your reply."
                        } finally {
                            submitting = false
                        }
                    }
                },
            )
        }
        else -> {
            IdsButton(text = "Reply", size = IdsButtonSize.Small, onClick = { replying = true })
        }
    }
}

// Real Naver Smart Store-style "관심고객" (interested-customer) follower count +
// broadcast-to-followers (item 118) -- the merchant-owner-facing half of
// MerchantFollowService; the customer-facing follow/unfollow toggle already shipped
// on bank-mfe/Android app/iOS app. merchant-mfe already has this; this is the first
// native-merchant-app client, mirroring its FollowersCard field-for-field.
// Moved to its own file, FollowersTab.kt, 2026-08-30 -- see that file's own doc
// comment for why.

@Composable
internal fun StatusBadge(status: String) {
    val label = when (status) {
        "PLACED" -> "New order"
        "ACCEPTED" -> "Accepted"
        "PREPARING" -> "Preparing"
        "READY_FOR_PICKUP" -> "Ready for pickup"
        "RIDER_ASSIGNED" -> "Rider on the way"
        "PICKED_UP" -> "Out for delivery"
        "DELIVERED" -> "Delivered"
        "CANCELLED" -> "Cancelled"
        else -> status
    }
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
