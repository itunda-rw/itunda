package rw.itunda.feature.shop.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.itundaface.PriceDropGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ProductSubscriptionDto
import rw.itunda.core.network.FavoriteProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OrderDto
import rw.itunda.core.network.ProductInquiryDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException




internal val COMMERCE_STATUS_LABEL = mapOf(
    "PLACED" to "Placed",
    "PACKED" to "Packed",
    "SHIPPED" to "Shipped",
    "DELIVERED" to "Delivered",
    "CANCELLED" to "Cancelled — refunded",
)

// Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a real
// order-history row (a log of past purchases), kept the per-row Divider convention
// (docs/DESIGN_REFERENCES.md §274).
@Composable
internal fun CommerceOrderRow(order: OrderDto, action: (@Composable () -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(COMMERCE_STATUS_LABEL[order.status] ?: order.status, color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(order.deliveryAddress, color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
            Text("%,.0f RWF".format(order.totalAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        action?.invoke()
    }
    Divider(color = Ids.colors.divider, thickness = 0.5.dp)
}

// Real Shop product wishlist view (2026-07-24) -- Android port of bank-mfe's
// ProductCatalogView wishlist tab, mirroring Marketplace's own ListingWishlistView
// field-for-field. Closes docs/DESIGN_REFERENCES.md Section 5 recommendation #3.
@Composable
internal fun ProductWishlistView(onRemoved: () -> Unit) {
    var favorites by remember { mutableStateOf<List<FavoriteProductDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteProducts()
                if (res.success) favorites = res.favorites
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        favorites == null -> SkeletonBlock()
        favorites!!.isEmpty() -> EmptyState("No saved products yet -- tap ♡ on any product to save it here.", icon = Icons.Outlined.FavoriteBorder)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
            // an entity list a user manages (saved products), no divider, matching
            // GroupAccountScreen's precedent (docs/UI_UX_GUIDELINES.md §10).
            favorites!!.forEach { f ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductImageThumb(f.imageUrl, size = 48.dp, corner = 10.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(f.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${f.businessName} · %,.0f RWF".format(f.price), color = Ids.colors.textSecondary, fontSize = 13.sp)
                            // Real Naver Shopping price-drop alert (item 227) -- see
                            // FavoriteProductDto's own doc comment.
                            if (f.priceDropped) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    PriceDropGlyph(size = 11.dp)
                                    Text("Price dropped", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        removingId = f.productId
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.removeProductFavorite(f.productId)
                                favorites = favorites?.filterNot { it.productId == f.productId }
                                onRemoved()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                removingId = null
                            }
                        }
                    }) {
                        Text(if (removingId == f.productId) "Removing…" else "Remove", color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// Real "my questions across every product I've ever asked about" (2026-08-04) -- see
// core/network's getMyProductInquiries doc comment. ProductInquirySection (on a single
// product's detail page) already lets a buyer ask/view that one product's Q&A; this is
// the first place a buyer can see every question they've ever asked, across every
// product, in one list. Read-only from here -- answering is the merchant app's job.
@Composable
internal fun MyProductInquiriesView() {
    var inquiries by remember { mutableStateOf<List<ProductInquiryDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                inquiries = NetworkClient.apiService.getMyProductInquiries().inquiries
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        inquiries == null -> SkeletonBlock()
        inquiries!!.isEmpty() -> EmptyState("No questions asked yet -- ask one from any product's detail page.", icon = Icons.Outlined.RateReview)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
            // a history log of questions asked, kept the per-row Divider convention.
            inquiries!!.forEach { q ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(q.question, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (!q.answer.isNullOrBlank()) {
                        Text("Answered: ${q.answer}", color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Text("Waiting for an answer…", color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Divider(color = Ids.colors.divider, thickness = 0.5.dp)
            }
        }
    }
}

// Real Coupang 정기배송 (subscribe & save)-style recurring product delivery -- see
// core/network's ProductSubscriptionDto doc comment. bank-mfe already had this;
// this is the first Android client, mirroring bank-mfe's MyProductSubscriptionsCard.
@Composable
internal fun MyProductSubscriptionsView() {
    var subscriptions by remember { mutableStateOf<List<ProductSubscriptionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyProductSubscriptions()
                if (res.success) subscriptions = res.subscriptions
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        subscriptions == null -> SkeletonBlock()
        subscriptions!!.isEmpty() -> EmptyState("No recurring deliveries yet -- subscribe from any product's detail page.", icon = Icons.Outlined.Autorenew)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
            // a status/history log of recurring deliveries, kept the per-row Divider
            // convention.
            subscriptions!!.forEach { s ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text("Qty ${s.quantity} · every ${s.intervalDays}d", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val cancelledAt = s.cancelledAt
                    Text(
                        if (s.status == "CANCELLED" && cancelledAt != null) "Cancelled ${cancelledAt.take(10)}" else "${s.status} · ${s.deliveryCount} delivered",
                        color = Ids.colors.textSecondary, fontSize = 13.sp,
                    )
                    if (s.lastFailureReason != null && s.status == "ACTIVE") {
                        Text("Last delivery failed: ${s.lastFailureReason}", color = Ids.colors.danger, fontSize = 12.sp)
                    }
                    if (s.status != "CANCELLED") {
                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                busyId = s.id
                                coroutineScope.launch {
                                    try {
                                        if (s.status == "ACTIVE") NetworkClient.apiService.pauseProductSubscription(s.id)
                                        else NetworkClient.apiService.resumeProductSubscription(s.id)
                                        load()
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    } catch (e: IOException) {
                                        error = "Couldn't reach itunda. Check your connection and try again."
                                    } finally {
                                        busyId = null
                                    }
                                }
                            }, enabled = busyId != s.id) {
                                Text(if (busyId == s.id) "…" else if (s.status == "ACTIVE") "Pause" else "Resume", fontSize = 13.sp)
                            }
                            TextButton(onClick = {
                                busyId = s.id
                                coroutineScope.launch {
                                    try {
                                        NetworkClient.apiService.cancelProductSubscription(s.id)
                                        load()
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    } catch (e: IOException) {
                                        error = "Couldn't reach itunda. Check your connection and try again."
                                    } finally {
                                        busyId = null
                                    }
                                }
                            }, enabled = busyId != s.id) {
                                Text("Cancel", color = Ids.colors.textSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Divider(color = Ids.colors.divider, thickness = 0.5.dp)
            }
        }
    }
}

