package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
                        }
                    }
                }
            }
        }
    }
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

