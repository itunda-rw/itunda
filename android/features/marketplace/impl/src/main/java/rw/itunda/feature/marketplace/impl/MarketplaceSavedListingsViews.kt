package rw.itunda.feature.marketplace.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.unit.sp
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.FavoriteListingDto
import rw.itunda.core.network.HiddenListingDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage

// Real fix (2026-09-08): split out of MarketplaceSubScreens.kt once that file crossed
// the 500-line file-size-lint guideline for the first time (the hidden-listings view
// pushed it over). Two self-contained "my saved-state listing" sub-screens (wishlist,
// hidden listings) only rendered inside MarketplaceContent's own hamburger-menu
// hand-off flow. Same package, so zero import changes anywhere else.

// Real Marketplace listing wishlist view (2026-07-21) -- Android port of bank-mfe's
// ListingWishlistView, same day. Lists every real favorited listing (title/price/
// category straight from the favorites endpoint, an honest "Listing no longer
// available" fallback for a favorited-then-deleted listing is the backend's own
// responsibility -- ListingFavoriteService.kt already resolves that server-side).
@Composable
internal fun ListingWishlistView(onRemoved: () -> Unit) {
    var favorites by remember { mutableStateOf<List<FavoriteListingDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    // Real pagination-discard fix (2026-09-13, porting web's own fix -- see
    // project_itunda_pagination_discard_sweep memory) -- getMyFavoriteListings
    // silently capped this list at the first 20 favorited listings.
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteListings(page = 0)
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
                val res = NetworkClient.apiService.getMyFavoriteListings(page = nextPage)
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

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        favorites == null -> SkeletonBlock()
        favorites!!.isEmpty() -> EmptyState("No saved listings yet -- tap ♡ on any listing to save it here.", icon = Icons.Outlined.FavoriteBorder)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
            // an entity list a user manages (saved listings), no divider, matching
            // GroupAccountScreen's identical entity-list conversion
            // (docs/UI_UX_GUIDELINES.md §10).
            favorites!!.forEach { f ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(f.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(String.format(Locale.US, "${f.category} · %,.0f RWF", f.price), color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                    ListingActionButton(if (removingId == f.listingId) "Removing…" else "Remove", removingId == f.listingId) {
                        removingId = f.listingId
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.removeListingFavorite(f.listingId)
                                favorites = favorites?.filterNot { it.listingId == f.listingId }
                                onRemoved()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                removingId = null
                            }
                        }
                    }
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

// Real "Hidden listings" list (Hood product-completeness pass, 2026-09-07) -- see
// backend ListingHideRepository.findByUserIdOrderByCreatedAtDesc's own doc comment: a
// user could hide a listing (real, shipped, wired everywhere) but never see or undo
// that on any platform until now. Mirrors ListingWishlistView's exact shape above.
@Composable
internal fun HiddenListingsView() {
    var hidden by remember { mutableStateOf<List<HiddenListingDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var unhidingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyHiddenListings()
                if (res.success) hidden = res.hidden
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
        hidden == null -> SkeletonBlock()
        hidden!!.isEmpty() -> EmptyState("No hidden listings -- tap Hide on any listing to stop seeing it here.", icon = Icons.Outlined.VisibilityOff)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            hidden!!.forEach { h ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(h.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(String.format(Locale.US, "${h.category} · %,.0f RWF", h.price), color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
                    ListingActionButton(if (unhidingId == h.listingId) "Unhiding…" else "Unhide", unhidingId == h.listingId) {
                        unhidingId = h.listingId
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.unhideListing(h.listingId)
                                hidden = hidden?.filterNot { it.listingId == h.listingId }
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                unhidingId = null
                            }
                        }
                    }
                }
            }
        }
    }
}
