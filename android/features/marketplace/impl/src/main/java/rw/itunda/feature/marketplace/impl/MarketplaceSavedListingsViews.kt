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
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.FavoriteListingDto
import rw.itunda.core.network.ListingDto
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
fun ListingWishlistView(
    onRemoved: () -> Unit,
    onOpen: (String) -> Unit,
    initialFavorites: List<FavoriteListingDto>? = null,
) {
    var favorites by remember { mutableStateOf(initialFavorites) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val response = NetworkClient.apiService.getMyFavoriteListings(page = 0)
                if (response.success) {
                    favorites = response.favorites
                    error = null
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't load saved listings. Check your connection and try again."
            }
        }
    }

    LaunchedEffect(initialFavorites) {
        if (initialFavorites != null) favorites = initialFavorites else load()
    }

    when {
        error != null -> ErrorCard(error!!)
        favorites == null -> SkeletonBlock()
        favorites!!.isEmpty() -> MarketplacePrototypeEmptyState()
        else -> Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            favorites!!.forEach { favorite ->
                val listing = ListingDto(
                    id = favorite.listingId,
                    sellerId = "",
                    title = favorite.title,
                    description = "",
                    price = favorite.price,
                    category = favorite.category,
                    status = "ACTIVE",
                    createdAt = favorite.favoritedAt,
                    neighborhood = null,
                    likeCount = 0,
                )
                ListingRow(
                    listing = listing,
                    isMine = false,
                    favorited = true,
                    favoriteBusy = removingId == favorite.listingId,
                    onToggleFavorite = {
                        removingId = favorite.listingId
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.removeListingFavorite(favorite.listingId)
                                favorites = favorites?.filterNot { it.listingId == favorite.listingId }
                                onRemoved()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't update saved listings. Check your connection and try again."
                            } finally {
                                removingId = null
                            }
                        }
                    },
                    onOpen = { onOpen(favorite.listingId) },
                )
            }
        }
    }
}

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
