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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Storefront
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.FavoriteRestaurantDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real fix (2026-09-13): split out of EatsFavoritesReview.kt once that file crossed
// the 500-line file-size-lint guideline for the first time (the pagination-discard
// fix on this view pushed it over). This view is self-contained -- only rendered
// inside EatsScreen's own favorites tab/sheet, distinct from the rating/review/tip
// composables left behind. Same package, so zero import changes anywhere else.

@Composable
internal fun FavoriteRestaurantsView(onOpen: (FavoriteRestaurantDto) -> Unit, onChanged: () -> Unit) {
    var favorites by remember { mutableStateOf<List<FavoriteRestaurantDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    // Real pagination-discard fix (2026-09-13, porting web's own fix -- see
    // project_itunda_pagination_discard_sweep memory) -- getMyFavoriteRestaurants
    // silently capped this list at the first 20 favorited restaurants.
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteRestaurants(page = 0)
                if (res.success) { favorites = res.favorites; page = 0; hasMore = res.page + 1 < res.totalPages }
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun loadMore() {
        val nextPage = page + 1
        loadingMore = true
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteRestaurants(page = nextPage)
                if (res.success) {
                    favorites = (favorites ?: emptyList()) + res.favorites
                    page = nextPage
                    hasMore = res.page + 1 < res.totalPages
                }
            } catch (_: Exception) {
                // Non-critical -- the already-loaded page stays visible; the
                // user can retry by tapping "Load more" again.
            } finally {
                loadingMore = false
            }
        }
    }

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
            if (hasMore) {
                IdsButton(
                    text = if (loadingMore) "Loading…" else "Load more",
                    onClick = ::loadMore,
                    enabled = !loadingMore,
                    variant = IdsButtonVariant.Tinted,
                    size = IdsButtonSize.Medium,
                )
            }
        }
    }
}
