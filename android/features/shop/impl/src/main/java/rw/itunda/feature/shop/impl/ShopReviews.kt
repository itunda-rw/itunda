package rw.itunda.feature.shop.impl

import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RateReview
import rw.itunda.core.designsystem.theme.IdsIcons
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.components.StarRatingRow
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OrderDto
import rw.itunda.core.network.OrderItemDto
import rw.itunda.core.network.ProductRatingResponse
import rw.itunda.core.network.AskProductInquiryRequest
import rw.itunda.core.network.ProductInquiryDto
import rw.itunda.core.network.ProductReviewDto
import rw.itunda.core.network.SubmitProductReviewRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException




/**
 * Real Coupang-style pre-purchase product Q&A (상품문의) -- see
 * rw.itunda.commerce.ProductInquiryService's own doc comment. Genuinely distinct from
 * ProductRatingBadge's reviews below: no order/purchase required at all, so this is
 * always visible on a product's detail page, not gated behind having bought it.
 * bank-mfe already has this; this is the first Android client.
 */
@Composable
internal fun ProductInquirySection(productId: String) {
    var inquiries by remember { mutableStateOf<List<ProductInquiryDto>?>(null) }
    var question by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                inquiries = NetworkClient.apiService.getProductInquiries(productId).inquiries
            } catch (e: Exception) {
                inquiries = emptyList()
            }
        }
    }
    LaunchedEffect(productId) { load() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Questions & answers", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IdsTextField(value = question, onValueChange = { question = it }, label = "Ask the seller a question", modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (asking || question.isBlank()) Ids.colors.textTertiary else Ids.colors.surfaceSoft)
                    .pressScaleClickable(enabled = !asking && question.isNotBlank()) {
                        asking = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.askProductInquiry(productId, AskProductInquiryRequest(question.trim()))
                                question = ""
                                load()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } finally {
                                asking = false
                            }
                        }
                    }.padding(horizontal = 14.dp, vertical = 12.dp),
            ) { Text("Ask", color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        val list = inquiries
        Spacer(modifier = Modifier.height(8.dp))
        when {
            list == null -> Text("Loading questions…", color = Ids.colors.textSecondary, fontSize = 12.sp)
            list.isEmpty() -> Text("No questions yet -- be the first to ask.", color = Ids.colors.textSecondary, fontSize = 12.sp)
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEach { q ->
                    Column {
                        Row {
                            Text("Q. ", color = Ids.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(q.question, color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                        val answer = q.answer
                        if (answer != null) {
                            Row(modifier = Modifier.padding(start = 12.dp, top = 2.dp)) {
                                Text("A. ", color = Ids.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(answer, color = Ids.colors.textSecondary, fontSize = 12.sp)
                            }
                        } else {
                            Text("Awaiting seller response", color = Ids.colors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

// Real post-delivery product reviews (2026-07-20), mirroring RestaurantRatingBadge/
// ReviewOrderCard (Eats, still in :app) -- see ProductReviewService's own doc comment
// for the full backend account. One real review per real delivered order line item.
@Composable
internal fun ProductRatingBadge(productId: String) {
    var rating by remember { mutableStateOf<ProductRatingResponse?>(null) }
    var open by remember { mutableStateOf(false) }
    var reviews by remember { mutableStateOf<List<ProductReviewDto>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(productId) {
        try {
            rating = NetworkClient.apiService.getProductRating(productId)
        } catch (e: Exception) {
            // Real, non-critical -- a rating fetch failure shouldn't block browsing the catalog.
        }
    }
    val r = rating
    if (r != null && r.count > 0) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pressScaleClickable {
                    val next = !open
                    open = next
                    if (next && reviews == null) {
                        coroutineScope.launch {
                            try {
                                reviews = NetworkClient.apiService.getProductReviews(productId).reviews
                            } catch (e: Exception) {
                                reviews = emptyList()
                            }
                        }
                    }
                },
            ) {
                Icon(IdsIcons.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("%.1f (%d)".format(r.average ?: 0.0, r.count), color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
            if (open) {
                val list = reviews
                if (list == null) {
                    Text("Loading reviews…", color = Ids.colors.textSecondary, fontSize = 12.sp)
                } else if (list.isEmpty()) {
                    EmptyState("No written reviews yet — be the first to share how it went.", icon = Icons.Outlined.RateReview)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
                        list.forEach { rv ->
                            val stars = "★".repeat(rv.rating) + "☆".repeat(5 - rv.rating)
                            Text(
                                if (rv.comment.isNullOrBlank()) stars else "$stars — ${rv.comment}",
                                color = Ids.colors.textSecondary,
                                fontSize = 12.sp,
                            )
                            if (!rv.ownerReply.isNullOrBlank()) {
                                Text(
                                    "↳ Seller: ${rv.ownerReply}",
                                    color = Ids.colors.textTertiary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProductReviewRow(item: OrderItemDto) {
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("${item.productName}: thanks for your review!", color = Ids.colors.textSecondary, fontSize = 12.sp)
        return
    }
    if (!open) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).pressScaleClickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Rate ${item.productName}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(item.productName, color = Ids.colors.textSecondary, fontSize = 12.sp)
        StarRatingRow(rating) { rating = it }
        IdsTextField(
            value = comment,
            onValueChange = { comment = it },
            label = "How was it? (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).pressScaleClickable { open = false }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (submitting) Ids.colors.textTertiary else Ids.colors.brand)
                    .pressScaleClickable(enabled = !submitting) {
                        if (rating == 0) {
                            error = "Pick a star rating."
                            return@pressScaleClickable
                        }
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.submitProductReview(item.id, SubmitProductReviewRequest(rating, comment.trim().ifBlank { null }))
                                done = true
                            } catch (e: HttpException) {
                                // A 409 here is the real PRODUCT_ALREADY_REVIEWED case in
                                // practice -- this form only ever renders for a real
                                // DELIVERED order.
                                if (e.code() == 409) {
                                    done = true
                                } else {
                                    error = superAppErrorMessage(e)
                                }
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                submitting = false
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (submitting) "Submitting…" else "Submit review", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

@Composable
internal fun OrderItemReviews(order: OrderDto) {
    var items by remember { mutableStateOf<List<OrderItemDto>?>(null) }
    LaunchedEffect(order.id) {
        try {
            val res = NetworkClient.apiService.getOrder(order.id)
            if (res.success) items = res.items
        } catch (e: Exception) {
            // Real, non-critical -- if item fetch fails, the order row itself still renders fine.
        }
    }
    val list = items
    if (list != null && list.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { item -> ProductReviewRow(item) }
        }
    }
}
// Real "pay a merchant" UI (PayAMerchantSection/FacePaySettingsCard/couponDiscountLabel/
// PayByCodeCard/PayByScanCard/PayByStaticQrCard/ListingActionButtonShop) moved to
// ShopPay.kt (2026-08-19) -- see that file's own header comment for why.
