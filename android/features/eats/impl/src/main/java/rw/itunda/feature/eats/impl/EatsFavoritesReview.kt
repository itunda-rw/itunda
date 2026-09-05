package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import java.util.Locale
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
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storefront
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
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.components.StarRatingRow
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsRatingResponse
import rw.itunda.core.network.EatsReviewDto
import rw.itunda.core.network.FavoriteRestaurantDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ReportEatsReviewRequest
import rw.itunda.core.network.SubmitEatsReviewRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException




@Composable
internal fun FavoriteRestaurantsView(onOpen: (FavoriteRestaurantDto) -> Unit, onChanged: () -> Unit) {
    var favorites by remember { mutableStateOf<List<FavoriteRestaurantDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteRestaurants()
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

    fun remove(restaurantId: String) {
        removingId = restaurantId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.removeFavoriteRestaurant(restaurantId)
                favorites = favorites?.filterNot { it.restaurantId == restaurantId }
                onChanged()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                removingId = null
            }
        }
    }

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        favorites == null -> SkeletonBlock()
        favorites!!.isEmpty() -> EmptyState("No favorite restaurants yet. Tap the heart on a restaurant to save it here.", icon = Icons.Outlined.FavoriteBorder)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            favorites!!.forEach { f ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                        .background(Ids.colors.surface)
                        .pressScaleClickable { onOpen(f) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ids.colors.brand)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(f.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            if (f.category != null) "${f.category} · Real menu, real delivery" else "Real menu, real delivery",
                            color = Ids.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Remove from favorites",
                        tint = Ids.colors.danger,
                        modifier = Modifier
                            .size(22.dp)
                            .pressScaleClickable(enabled = removingId != f.restaurantId) { remove(f.restaurantId) },
                    )
                }
            }
        }
    }
}

// Real written-review list + owner-reply display (item 184/185) -- bank-mfe already has
// this (item 184); this is the first Android client. Mirrors ProductRatingBadge's own
// expand-on-click pattern exactly (ShopScreen.kt, this app's Commerce equivalent).
@Composable
internal fun RestaurantRatingBadge(restaurantId: String) {
    var rating by remember { mutableStateOf<EatsRatingResponse?>(null) }
    var open by remember { mutableStateOf(false) }
    var reviews by remember { mutableStateOf<List<EatsReviewDto>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    // Real Coupang/Naver-style "도움돼요" (helpful) toggle -- see backend
    // EatsReviewService.toggleHelpful's own doc comment. Real, shipped on the backend +
    // bank-mfe with zero Android client until now -- found via a cross-platform-parity
    // check.
    var helpfulVoted by remember { mutableStateOf<Set<String>>(emptySet()) }
    fun toggleHelpful(reviewId: String) {
        coroutineScope.launch {
            try {
                val helpful = NetworkClient.apiService.toggleEatsReviewHelpful(reviewId).helpful
                helpfulVoted = if (helpful) helpfulVoted + reviewId else helpfulVoted - reviewId
                reviews = reviews?.map { if (it.id == reviewId) it.copy(helpfulCount = it.helpfulCount + (if (helpful) 1 else -1)) else it }
            } catch (e: Exception) {
                // Real, non-critical -- a failed helpful-vote shouldn't block reading reviews.
            }
        }
    }

    LaunchedEffect(restaurantId) {
        try {
            rating = NetworkClient.apiService.getRestaurantRating(restaurantId)
        } catch (e: Exception) {
            // Real, non-critical -- a rating fetch failure shouldn't block browsing the menu.
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
                                reviews = NetworkClient.apiService.getRestaurantReviews(restaurantId).reviews
                            } catch (e: Exception) {
                                reviews = emptyList()
                            }
                        }
                    }
                },
            ) {
                Icon(IdsIcons.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("%.1f (%d)".format(r.average ?: 0.0, r.count), color = Ids.colors.textSecondary, fontSize = 13.sp)
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
                            val stars = "★".repeat(rv.restaurantRating) + "☆".repeat(5 - rv.restaurantRating)
                            Text(
                                if (rv.restaurantComment.isNullOrBlank()) stars else "$stars — ${rv.restaurantComment}",
                                color = Ids.colors.textSecondary,
                                fontSize = 12.sp,
                            )
                            // Real review photo (2026-08-04) -- see EatsReviewDto.photoUrl's
                            // own doc comment.
                            if (!rv.photoUrl.isNullOrBlank()) {
                                SubcomposeAsyncImage(
                                    model = rv.photoUrl,
                                    contentDescription = "Review photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp).size(72.dp).clip(RoundedCornerShape(8.dp)),
                                ) {
                                    when (painter.state) {
                                        is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                                        else -> RestaurantPhotoPlaceholder()
                                    }
                                }
                            }
                            if (!rv.ownerReply.isNullOrBlank()) {
                                Text(
                                    "↳ Restaurant: ${rv.ownerReply}",
                                    color = Ids.colors.textTertiary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 2.dp)) {
                                Text(
                                    "👍 Helpful" + (if (rv.helpfulCount > 0) " (${rv.helpfulCount})" else ""),
                                    color = if (rv.id in helpfulVoted) Ids.colors.brand else Ids.colors.textSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.pressScaleClickable { toggleHelpful(rv.id) },
                                )
                                ReportEatsReviewButton(rv.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Real 배달의민족 리뷰 신고하기 (report a review) -- see backend
// EatsReviewService.reportReview's own doc comment. Same real preset-reason-picker
// shape as bank-mfe's own ReportReviewButton -- genuinely NOT covered by the generic
// HoodReportButton mechanism (no REVIEW target exists there).
private val EATS_REVIEW_REPORT_REASONS = listOf(
    "DEFAMATION" to "False or defamatory",
    "PERSONAL_INFO_EXPOSURE" to "Shares personal information",
    "OBSCENE_OR_VIOLENT" to "Obscene or violent",
    "UNRELATED_ABUSE" to "Unrelated or abusive",
)

@Composable
private fun ReportEatsReviewButton(reviewId: String) {
    var showChoices by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun send(reason: String) {
        showChoices = false
        sending = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.reportEatsReview(reviewId, ReportEatsReviewRequest(reason))
                message = "Thanks. Your report was sent for review."
            } catch (e: HttpException) {
                message = if (e.code() == 409) "You already reported this review." else "Could not send the report."
            } catch (e: Exception) {
                message = "Could not send the report."
            } finally {
                sending = false
            }
        }
    }

    val currentMessage = message
    if (currentMessage != null) {
        Text(currentMessage, color = if (currentMessage.startsWith("Thanks")) Ids.colors.success else Ids.colors.danger, fontSize = 11.sp)
        return
    }

    if (showChoices) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            EATS_REVIEW_REPORT_REASONS.forEach { (reason, label) ->
                Text(label, color = Ids.colors.textSecondary, fontSize = 11.sp, modifier = Modifier.pressScaleClickable { send(reason) })
            }
            Text("Cancel", color = Ids.colors.textTertiary, fontSize = 11.sp, modifier = Modifier.pressScaleClickable { showChoices = false })
        }
        return
    }

    Text(
        if (sending) "Reporting…" else "Report",
        color = Ids.colors.textSecondary,
        fontSize = 11.sp,
        modifier = Modifier.pressScaleClickable(enabled = !sending) { showChoices = true },
    )
}

@Composable
internal fun ReviewOrderCard(order: EatsOrderDto) {
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var restaurantRating by remember { mutableStateOf(0) }
    var restaurantComment by remember { mutableStateOf("") }
    // Real optional review photo (2026-08-04) -- see EatsReviewDto.photoUrl's own doc
    // comment. Same real-external-URL-only convention as Merchant.photoUrl's own input
    // elsewhere in this app -- a real URL the buyer pastes, never an upload pipeline.
    var photoUrl by remember { mutableStateOf("") }
    var riderRating by remember { mutableStateOf(0) }
    var riderComment by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("Thanks for your review!", color = Ids.colors.textSecondary, fontSize = 13.sp)
        return
    }
    if (!open) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).pressScaleClickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Rate this order", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        Column {
            Text("Restaurant", color = Ids.colors.textSecondary, fontSize = 12.sp)
            StarRatingRow(restaurantRating) { restaurantRating = it }
            IdsTextField(
                value = restaurantComment,
                onValueChange = { restaurantComment = it },
                label = "How was the food? (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            IdsTextField(
                value = photoUrl,
                onValueChange = { photoUrl = it },
                label = "Photo URL of your food (optional)",
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        Column {
            Text("Rider", color = Ids.colors.textSecondary, fontSize = 12.sp)
            StarRatingRow(riderRating) { riderRating = it }
            IdsTextField(
                value = riderComment,
                onValueChange = { riderComment = it },
                label = "How was the delivery? (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
                        if (restaurantRating == 0 || riderRating == 0) {
                            error = "Rate both the restaurant and the rider."
                            return@pressScaleClickable
                        }
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.submitEatsReview(
                                    order.id,
                                    SubmitEatsReviewRequest(restaurantRating, restaurantComment.trim().ifBlank { null }, riderRating, riderComment.trim().ifBlank { null }, photoUrl.trim().ifBlank { null }),
                                )
                                done = true
                            } catch (e: HttpException) {
                                // A 409 here is the real ORDER_ALREADY_REVIEWED case in
                                // practice -- this form only ever renders for a real
                                // DELIVERED order, so the sibling "not yet delivered"
                                // 409 can't actually occur through this UI path.
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


// Real Uber Eats post-delivery tip -- ported from bank-mfe (2026-09-03), see
// EatsOrderDto.tipAmount's own doc comment. Reuses the exact TIP_PRESETS bank-mfe's
// own TipRiderPrompt established. Caller only renders this for a real DELIVERY order
// (a PICKUP order has no rider) that hasn't been tipped yet.
private val EATS_TIP_PRESETS = listOf(500, 1000, 2000)

@Composable
internal fun TipRiderPrompt(orderId: String, onTipped: () -> Unit) {
    var amount by remember { mutableStateOf<Int?>(null) }
    var customAmount by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun submit(overrideAmount: Int? = null) {
        val finalAmount = overrideAmount ?: amount ?: customAmount.toIntOrNull()
        if (finalAmount == null || finalAmount <= 0) return
        submitting = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.tipEatsOrderRider(
                    orderId,
                    rw.itunda.core.network.TipEatsOrderRequest(finalAmount.toDouble()),
                    java.util.UUID.randomUUID().toString(),
                )
                onTipped()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Tip your rider", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            EATS_TIP_PRESETS.forEach { preset ->
                val selected = amount == preset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Ids.colors.brand else Color.Transparent)
                        .pressScaleClickable(enabled = !submitting) { amount = preset; customAmount = ""; submit(preset) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(String.format(Locale.US, "%,d", preset), color = if (selected) Color.White else Ids.colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IdsTextField(
                value = customAmount,
                onValueChange = { customAmount = it; amount = null },
                label = "Custom amount (RWF)",
                modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (submitting || (customAmount.toIntOrNull() ?: 0) <= 0) Ids.colors.textTertiary else Ids.colors.brand)
                    .pressScaleClickable(enabled = !submitting && (customAmount.toIntOrNull() ?: 0) > 0) { submit() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) { Text(if (submitting) "…" else "Tip", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}
