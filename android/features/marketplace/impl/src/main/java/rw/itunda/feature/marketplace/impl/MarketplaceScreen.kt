package rw.itunda.feature.marketplace.impl

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.HoodReportAction
import rw.itunda.core.designsystem.components.HoodReviewForm
import rw.itunda.core.designsystem.components.HoodReviewResultView
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.NeighborhoodSetupPrompt
import rw.itunda.core.designsystem.components.RouteMiniMap
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.TrustBadge
import rw.itunda.core.designsystem.components.relativeTimeAgo
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddKeywordAlertRequest
import rw.itunda.core.network.CreateListingRequest
import rw.itunda.core.network.FavoriteListingDto
import rw.itunda.core.network.KeywordAlertDto
import rw.itunda.core.network.KeywordAlertQuietHoursDto
import rw.itunda.core.network.ListingDto
import rw.itunda.core.network.MakeOfferRequest
import rw.itunda.core.network.BoostListingRequest
import rw.itunda.core.network.MarkSoldRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetKeywordAlertQuietHoursRequest
import rw.itunda.core.network.SubmitHoodReviewRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.time.Instant
import java.util.UUID

// Real proof-of-slice extraction (2026-07-22/23) -- itunda's real Toss-Microfeatures
// pattern (toss.tech/article/slash23-iOS: Feature/Interface/Testing/Tests/Example,
// build system refuses sideways Feature-to-Feature deps) applied to Marketplace, the
// first Hood-mode section pulled out of app/ui/SuperAppTabs.kt. Unlike the existing
// :features:payments:impl "dumb view" precedent (screens take plain data + callbacks
// because :impl can't depend back on :app), Marketplace drives ~15 distinct API calls
// directly from user interaction, so this module owns its own real NetworkClient calls
// end-to-end rather than threading every one of them back up as a callback prop.
// RouteMiniMap (real drawn road route) used to be injected here for the same reason --
// it needed MapLibre + :app's own BuildConfig.TILES_BASE_URL -- until MapConfig.kt
// (2026-07-23) gave it the same BuildConfig-avoidance NetworkClient.init already had,
// letting it move to core/designsystem and be imported directly, same as every other
// shared UI atom.

// Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
// recommendation #6: Karrot's real screen splits a user's own activity into labeled
// sales/purchases/wishlist tabs, "specifically to avoid one overloaded list mixing
// different user intents." Only newly buildable now that buyerId is captured.
private enum class HoodView { BROWSE, NEARBY, NEIGHBORHOOD, MINE, PURCHASES, WISHLIST, ALERTS }

private fun HoodView.label() = when (this) {
    HoodView.BROWSE -> "Browse"
    HoodView.NEARBY -> "Near me"
    HoodView.NEIGHBORHOOD -> "Neighborhood"
    HoodView.MINE -> "My listings"
    HoodView.PURCHASES -> "Purchases"
    HoodView.WISHLIST -> "♡ Wishlist"
    HoodView.ALERTS -> "🔔 Alerts"
}

@Composable
fun MarketplaceContent(
    onMessageSeller: (String) -> Unit,
    // Real Karrot 글쓰기 FAB hand-off (2026-08-03) -- HoodTab's own floating orange
    // write button lives one level up (SuperAppTabs.kt), outside this Composable's
    // private `view`/`showNewListing` state, so it can't reach in and open the new-
    // listing form directly. This pair of params is that hand-off: the FAB bumps a
    // counter (any change, not just true/false, so tapping it twice in a row without
    // this screen ever resetting it still re-triggers), this screen reacts by forcing
    // Mine + the new-listing form open, matching real Karrot's own 글쓰기 -> always
    // lands you in a fresh post draft regardless of which category chip was selected.
    requestNewListingSignal: Int = 0,
) {
    var view by remember { mutableStateOf(HoodView.BROWSE) }
    var neighborhoodName by remember { mutableStateOf<String?>(null) }
    var neighborhoodChecked by remember { mutableStateOf(false) }
    var listings by remember { mutableStateOf<List<ListingDto>?>(null) }
    // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment;
    // the backend has spread this sellerId->score map alongside every browse response
    // since 2026-07-21, this just finally reads and renders it.
    var trustScores by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    // Real like/unlike toggle (2026-08-03) -- declared here (not next to toggleLike
    // itself, further down) so requestNearbyLocation's onSuccess closure below can
    // reference it; Kotlin locals must be declared before any use, even inside a
    // lambda that only runs later.
    var likedListingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewListing by remember { mutableStateOf(false) }
    if (showNewListing) {
        BackHandler { showNewListing = false }
    }
    LaunchedEffect(requestNewListingSignal) {
        if (requestNewListingSignal > 0) {
            view = HoodView.MINE
            showNewListing = true
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }
    var locatingNearby by remember { mutableStateOf(false) }
    val requestNearbyLocation = rememberRealLocationRequester(
        onLocating = { locatingNearby = it },
        onSuccess = { lat, lng ->
            listings = null
            coroutineScope.launch {
                try {
                    val res = NetworkClient.apiService.getNearbyListings(lat, lng)
                    if (res.success) { listings = res.listings; trustScores = res.trustScores; likedListingIds = res.likedByMe }
                    error = null
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                    listings = emptyList()
                } catch (e: IOException) {
                    error = "Couldn't load nearby listings. Check your connection and try again."
                    listings = emptyList()
                }
            }
        },
        onError = { message -> error = "$message You can still use Browse or Neighborhood."; listings = emptyList() },
    )

    // Real distance display (2026-08-03), matching a real 당근마켓 screenshot: every
    // row shows neighborhood/distance/time, not neighborhood/category/time -- itunda
    // was substituting category because it had no live distance to show. Deliberately
    // silent/opt-in: this only ever fetches location if ACCESS_FINE_LOCATION is
    // *already* granted (e.g. from a prior real use of Near me) -- it never triggers
    // its own permission prompt on a screen the user didn't ask location-based content
    // from, matching this file's own existing "opt-in, never assumed" discipline for
    // seller location-sharing.
    val context = LocalContext.current
    var browseLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val requestBrowseLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng -> browseLocation = lat to lng },
        onError = {},
    )
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) requestBrowseLocation()
    }

    // Real Marketplace listing wishlist (2026-07-21) -- porting bank-mfe's wishlist
    // (backend + web UI shipped earlier the same day) to Android. Favorite state is
    // lifted here, same as bank-mfe's own MarketplaceView, so the heart on every
    // ListingCard in Browse/Neighborhood/My-listings stays correct after a toggle from
    // any of them, not just the dedicated Wishlist tab.
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoritingId by remember { mutableStateOf<String?>(null) }
    var favoriteNotice by remember { mutableStateOf<String?>(null) }

    fun loadFavoriteIds() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteListings()
                if (res.success) favoriteIds = res.favorites.map { it.listingId }.toSet()
            } catch (e: Exception) {
                // Best-effort -- hearts just won't render as filled if this fails; the
                // rest of the tab still works.
            }
        }
    }
    LaunchedEffect(Unit) { loadFavoriteIds() }

    fun toggleFavorite(listingId: String) {
        favoritingId = listingId
        coroutineScope.launch {
            try {
                if (listingId in favoriteIds) {
                    NetworkClient.apiService.removeListingFavorite(listingId)
                    favoriteIds = favoriteIds - listingId
                    favoriteNotice = "Removed from your wishlist."
                } else {
                    NetworkClient.apiService.addListingFavorite(listingId)
                    favoriteIds = favoriteIds + listingId
                    favoriteNotice = "Saved to your wishlist."
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                favoritingId = null
            }
        }
    }

    // Real like/unlike toggle (2026-08-03) -- see backend MarketplaceController.kt's
    // own doc comment. likedListingIds itself is declared up near trustScores (a
    // Kotlin-locals-must-be-declared-before-use requirement, see that declaration's
    // own comment); the displayed *count* is tracked locally inside each ListingCard
    // instead (see its own doc comment) -- ListingDto is an immutable data class
    // inside an immutable list here, so optimistically bumping one row's count
    // without a full list rebuild is simpler done right where it's rendered.
    fun toggleLike(listingId: String) {
        val wasLiked = listingId in likedListingIds
        // Optimistic update -- matches this file's own toggleFavorite discipline just
        // above, real functionality first, no fake spinner-and-wait for a like tap.
        likedListingIds = if (wasLiked) likedListingIds - listingId else likedListingIds + listingId
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.toggleListingLike(listingId)
                likedListingIds = if (res.liked) likedListingIds + listingId else likedListingIds - listingId
            } catch (e: Exception) {
                // Real revert on failure -- an optimistic like that silently failed
                // would drift from the real server state forever.
                likedListingIds = if (wasLiked) likedListingIds + listingId else likedListingIds - listingId
            }
        }
    }

    fun load() {
        listings = null
        if (view == HoodView.NEARBY) {
            requestNearbyLocation()
            return
        }
        if (view == HoodView.WISHLIST || view == HoodView.ALERTS) {
            // ListingWishlistView/KeywordAlertsView below own their own fetch (neither
            // needs the ListingDto shape) -- nothing to load into `listings` here.
            return
        }
        if (view == HoodView.NEIGHBORHOOD) {
            neighborhoodChecked = false
            coroutineScope.launch {
                try {
                    val profileRes = NetworkClient.authApi.getProfile()
                    val res = NetworkClient.apiService.getListingsMyNeighborhood()
                    neighborhoodName = profileRes.user.neighborhood
                    if (res.success) { listings = res.listings; trustScores = res.trustScores; likedListingIds = res.likedByMe }
                    error = null
                } catch (e: HttpException) {
                    if (e.code() == 400) {
                        neighborhoodName = null
                        listings = emptyList()
                        error = null
                    } else {
                        error = superAppErrorMessage(e)
                    }
                } catch (e: IOException) {
                    error = "Couldn't reach itunda. Check your connection and try again."
                } finally {
                    neighborhoodChecked = true
                }
            }
            return
        }
        coroutineScope.launch {
            try {
                val res = when (view) {
                    HoodView.BROWSE -> NetworkClient.apiService.browseListings()
                    HoodView.PURCHASES -> NetworkClient.apiService.getMyPurchases()
                    else -> NetworkClient.apiService.getMyListings()
                }
                if (res.success) { listings = res.listings; trustScores = res.trustScores; likedListingIds = res.likedByMe }
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(view) {
        if (view == HoodView.NEARBY) requestNearbyLocation() else load()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal),
        // Real fix, 2026-08-03: a real device screenshot showed the last-visible
        // listing's own action row (Message seller/Make an offer) sitting directly
        // under HoodTab's floating Write FAB, genuinely overlapping and unreadable.
        // contentPadding (not a Modifier padding, which would just shrink the visible
        // viewport permanently) so the list can still scroll its last item clear of
        // the FAB. 96dp covers the real FAB's height (~48dp) plus its own 20dp margin
        // plus slack.
        contentPadding = PaddingValues(top = Ids.layout.screenVertical, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        // No TabHeader here (2026-07-24, was `TabHeader("Hood")`): the bottom nav
        // already highlights "Hood" and HoodTab's own Market/Life/Jobs/Home strip
        // sits right above this screen, so a third "Hood" label was pure repeat
        // noise, not information.
        item {
            // Real Karrot pill-chip category row (2026-08-03, user-provided real
            // 당근마켓 screenshots, light + dark) -- replaces the earlier Toss-style
            // underline-tab strip. Real Karrot chips: selected = a solid pill in the
            // near-black/near-white extreme of the theme (pixel-sampled from the real
            // screenshots: #2B3034 in light mode, #F3F4F8 in dark -- i.e. exactly
            // Ids.colors.textPrimary in both themes, not a separately invented color),
            // unselected = a soft neutral pill (#F3F3F3 light / #333438 dark -- a close
            // match for the existing Ids.colors.surfaceSoft token). No new tokens
            // needed -- this reuses the same common theme colors, only the shape
            // (pill, not underline) changes, matching "themes/interaction/graphics
            // stay common, only Hood's own chrome adopts Karrot" from the same request.
            //
            // Deliberately smaller/lighter than HoodTab's own Market/Life/Jobs/Home
            // row (2026-08-03, comparing against a real screenshot that only ever
            // shows ONE chip row under the top bar): real Karrot has no second-tier
            // chip strip like this one -- it's itunda's own real need (switching
            // Browse/Near me/Neighborhood/My listings/Purchases/Wishlist/Alerts,
            // real navigation with no other entry point yet), so rather than fake a
            // second identical-weight Karrot row, this reads as a visually secondary,
            // compact filter bar under the real primary chip row above it.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HoodView.entries.forEach { v ->
                    val selected = v == view
                    Text(
                        v.label(),
                        color = if (selected) Ids.colors.background else Ids.colors.textSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
                            .clickable { view = v }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        favoriteNotice?.let { notice ->
            item { Text(notice, color = Ids.colors.brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (view == HoodView.MINE) {
            item {
                if (!showNewListing) {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ids.colors.brand).clickable { showNewListing = true }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("+ List an item", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    NewListingForm(onCreated = { showNewListing = false; load() }, onCancel = { showNewListing = false })
                }
            }
        }
        if (view == HoodView.NEIGHBORHOOD && neighborhoodChecked && neighborhoodName == null) {
            item { NeighborhoodSetupPrompt(onDone = { load() }) }
        }
        if (view == HoodView.NEIGHBORHOOD && neighborhoodName != null) {
            item { Text("Your neighborhood: $neighborhoodName", color = Ids.colors.textSecondary, fontSize = 13.sp) }
        }
        if (view == HoodView.WISHLIST) {
            item { ListingWishlistView(onRemoved = ::loadFavoriteIds) }
        } else if (view == HoodView.ALERTS) {
            item { KeywordAlertsView() }
        } else if (error != null) {
            item { ErrorCard(error!!, onRetry = ::load) }
        } else if (listings == null) {
            item { SkeletonBlock() }
        } else if (listings!!.isEmpty() && (view != HoodView.NEIGHBORHOOD || neighborhoodName != null)) {
            item {
                EmptyState(
                    when (view) {
                        HoodView.BROWSE -> "No listings yet."
                        HoodView.NEARBY -> "No listings near you yet."
                        HoodView.NEIGHBORHOOD -> "No listings in your neighborhood yet."
                        HoodView.MINE -> "You haven't listed anything yet."
                        HoodView.PURCHASES -> "No purchases recorded yet."
                        HoodView.WISHLIST -> "No saved listings yet."
                        HoodView.ALERTS -> "" // unreachable -- ALERTS is intercepted earlier
                    },
                    icon = Icons.Outlined.ShoppingBag,
                )
            }
        } else if (listings!!.isNotEmpty()) {
            items(listings!!, key = { it.id }) { listing ->
                ListingCard(
                    listing = listing,
                    isMine = view == HoodView.MINE || listing.sellerId == currentUserId,
                    currentUserId = currentUserId,
                    sellerTrustScore = trustScores[listing.sellerId],
                    viewerLocation = browseLocation,
                    onChanged = ::load,
                    favorited = listing.id in favoriteIds,
                    favoriteBusy = favoritingId == listing.id,
                    onToggleFavorite = { toggleFavorite(listing.id) },
                    liked = listing.id in likedListingIds,
                    onToggleLike = { toggleLike(listing.id) },
                    onMessageSeller = { id ->
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.contactSeller(id)
                                if (res.success) onMessageSeller(res.conversation.id)
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            }
                        }
                    },
                    onMakeOffer = { id, amount ->
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.makeOffer(id, MakeOfferRequest(amount))
                                if (res.success) onMessageSeller(res.offer.conversationId)
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            }
                        }
                    },
                )
            }
        }
    }
}

// Real Marketplace listing wishlist view (2026-07-21) -- Android port of bank-mfe's
// ListingWishlistView, same day. Lists every real favorited listing (title/price/
// category straight from the favorites endpoint, an honest "Listing no longer
// available" fallback for a favorited-then-deleted listing is the backend's own
// responsibility -- ListingFavoriteService.kt already resolves that server-side).
@Composable
private fun ListingWishlistView(onRemoved: () -> Unit) {
    var favorites by remember { mutableStateOf<List<FavoriteListingDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteListings()
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
        favorites!!.isEmpty() -> EmptyState("No saved listings yet -- tap ♡ on any listing to save it here.", icon = Icons.Outlined.FavoriteBorder)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            favorites!!.forEach { f ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(f.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${f.category} · %,.0f RWF".format(f.price), color = Ids.colors.textSecondary, fontSize = 13.sp)
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
            }
        }
    }
}

// Real 당근마켓-style Keyword Alert -- first Android client for this feature (item 115,
// found via a content-grep sweep confirming zero client anywhere; bank-mfe ported it
// the same day as item 114). Real, published Karrot 30-keyword-per-user cap enforced
// server-side, surfaced via the backend's own KEYWORD_ALERT_CAP_REACHED error.
@Composable
private fun KeywordAlertsView() {
    var alerts by remember { mutableStateOf<List<KeywordAlertDto>?>(null) }
    var keyword by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var removingId by remember { mutableStateOf<String?>(null) }
    var quietHours by remember { mutableStateOf<KeywordAlertQuietHoursDto?>(null) }
    var quietHoursLoaded by remember { mutableStateOf(false) }
    var quietStart by remember { mutableStateOf("22:00") }
    var quietEnd by remember { mutableStateOf("08:00") }
    var savingQuietHours by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getKeywordAlerts()
                if (res.success) alerts = res.alerts
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
            try {
                val qh = NetworkClient.apiService.getKeywordAlertQuietHours().quietHours
                quietHours = qh
                if (qh != null) { quietStart = qh.startTime; quietEnd = qh.endTime }
            } catch (e: Exception) {
                // Non-critical -- the quiet-hours card just stays hidden.
            } finally {
                quietHoursLoaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun addAlert() {
        if (keyword.isBlank()) return
        adding = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.addKeywordAlert(AddKeywordAlertRequest(keyword.trim()))
                keyword = ""
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                adding = false
            }
        }
    }

    fun removeAlert(alertId: String) {
        removingId = alertId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.removeKeywordAlert(alertId)
                alerts = alerts?.filterNot { it.id == alertId }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                removingId = null
            }
        }
    }

    fun saveQuietHours(enabled: Boolean) {
        savingQuietHours = true
        error = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.setKeywordAlertQuietHours(SetKeywordAlertQuietHoursRequest(quietStart, quietEnd, enabled))
                quietHours = res.quietHours
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                savingQuietHours = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IdsTextField(
                    value = keyword, onValueChange = { keyword = it }, label = "Alert me for (e.g. iPhone 15)",
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                ListingActionButton(if (adding) "…" else "Add", adding, filled = true) { addAlert() }
            }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
        when {
            alerts == null -> SkeletonBlock()
            alerts!!.isEmpty() -> EmptyState("No keyword alerts yet -- add one to get notified when a matching listing is posted.", icon = Icons.Outlined.Notifications)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alerts!!.forEach { a ->
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(a.keyword, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            ListingActionButton(if (removingId == a.id) "Removing…" else "Remove", removingId == a.id) { removeAlert(a.id) }
                        }
                    }
                }
            }
        }
        if (quietHoursLoaded) {
            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quiet hours", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Don't send alert notifications during these hours.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IdsTextField(value = quietStart, onValueChange = { quietStart = it }, label = "Start (HH:mm)", modifier = Modifier.weight(1f))
                        IdsTextField(value = quietEnd, onValueChange = { quietEnd = it }, label = "End (HH:mm)", modifier = Modifier.weight(1f))
                    }
                    ListingActionButton(
                        if (savingQuietHours) "…" else if (quietHours?.enabled == true) "Turn off quiet hours" else "Turn on quiet hours",
                        savingQuietHours, filled = quietHours?.enabled != true,
                    ) { saveQuietHours(quietHours?.enabled != true) }
                }
            }
        }
    }
}

@Composable
private fun NewListingForm(onCreated: () -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var meetingPlace by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Real photo picker + upload (2026-07-24) -- see UploadController's own doc
    // comment on why "paste a URL" wasn't good enough. photoUrl holds the real
    // server-returned URL once upload succeeds; pickedImageUri is the local preview
    // shown immediately (before/during upload) so the seller isn't staring at a blank
    // box while a real network call happens.
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    var uploadingPhoto by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pickedImageUri = uri
        photoUrl = null
        uploadingPhoto = true
        error = null
        coroutineScope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    error = "Couldn't read that photo."
                    pickedImageUri = null
                    return@launch
                }
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "photo.jpg", body)
                photoUrl = NetworkClient.apiService.uploadPhoto(part).url
            } catch (e: HttpException) {
                pickedImageUri = null
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                pickedImageUri = null
                error = "Couldn't upload that photo. Check your connection and try again."
            } finally {
                uploadingPhoto = false
            }
        }
    }

    // Real optional seller location (2026-07-19) -- powers real proximity search and
    // "Directions to this seller"; a listing without it simply doesn't appear in either,
    // an honest opt-in, never assumed.
    var shareLocation by remember { mutableStateOf(false) }
    var myLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locating by remember { mutableStateOf(false) }
    val requestLocation = rememberRealLocationRequester(
        onLocating = { locating = it },
        onSuccess = { lat, lng -> myLocation = lat to lng; shareLocation = true },
        onError = { error = it },
    )

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("List an item", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IdsTextField(value = title, onValueChange = { title = it }, label = "What are you selling?", singleLine = true, modifier = Modifier.fillMaxWidth())
            IdsTextField(value = description, onValueChange = { description = it }, label = "Description", modifier = Modifier.fillMaxWidth())
            // Real photo picker (2026-07-24) -- a real photo is what a Karrot-style
            // listing card actually needs most, see ListingCard's own header comment.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ids.colors.surfaceSoft)
                    .clickable(enabled = !uploadingPhoto) { pickPhoto.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) {
                if (pickedImageUri != null) {
                    AsyncImage(
                        model = pickedImageUri,
                        contentDescription = "Selected photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (uploadingPhoto) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            Text("Uploading…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                } else {
                    Text("📷 Add a photo (optional)", color = Ids.colors.textSecondary, fontSize = 13.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IdsTextField(value = price, onValueChange = { price = it }, label = "Price (RWF)", singleLine = true, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, modifier = Modifier.weight(1f))
                IdsTextField(value = category, onValueChange = { category = it }, label = "Category", singleLine = true, modifier = Modifier.weight(1f))
            }
            IdsTextField(
                value = meetingPlace,
                onValueChange = { meetingPlace = it },
                label = "Suggested meeting place (optional)",
                supportingText = "Use a public landmark, not a home address.",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ids.colors.surfaceSoft)
                    .clickable(enabled = !locating) { if (shareLocation) shareLocation = false else requestLocation() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    if (locating) "Finding your real location…"
                    else if (shareLocation) "📍 Real location shared -- buyers can see distance & get directions"
                    else "📍 Share my real location (optional)",
                    fontSize = 13.sp,
                    color = if (shareLocation) Ids.colors.brand else Ids.colors.textSecondary,
                )
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ids.colors.brand)
                        .clickable(enabled = !submitting && !uploadingPhoto) {
                            val priceValue = price.toDoubleOrNull()
                            if (title.isBlank() || description.isBlank() || category.isBlank() || priceValue == null || priceValue <= 0) {
                                error = "Fill in every field with a real price."
                                return@clickable
                            }
                            submitting = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val loc = if (shareLocation) myLocation else null
                                    val res = NetworkClient.apiService.createListing(
                                        CreateListingRequest(
                                            title, description, priceValue, category, loc?.first, loc?.second,
                                            meetingPlace.trim().takeIf { it.isNotEmpty() },
                                            photoUrl,
                                        ),
                                    )
                                    if (res.success) onCreated()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally {
                                    submitting = false
                                }
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (submitting) "Listing…" else "List it", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun ListingPhotoPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.ShoppingBag, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(40.dp))
    }
}

// Real distance display (2026-08-03) -- see ListingCard's own doc comment on
// viewerLocation. Standard great-circle distance, not a fabricated straight-line
// guess.
private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2).let { it * it }
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadiusKm * c
}

private fun formatDistanceKm(km: Double): String =
    if (km < 1.0) "${(km * 1000).toInt()}m" else "%.1fkm".format(km)

@Composable
// Real seller-paid sponsored placement (2026-07-25) -- see backend
// MarketplaceService.boostListing's own doc comment. A real, still-future boostedUntil
// only -- never fabricated for an unpaid or expired listing.
private fun isListingBoosted(boostedUntil: String?): Boolean {
    if (boostedUntil == null) return false
    return try {
        Instant.parse(boostedUntil).isAfter(Instant.now())
    } catch (e: Exception) {
        false
    }
}

@Composable
private fun ListingCard(
    listing: ListingDto, isMine: Boolean, onChanged: () -> Unit, onMessageSeller: (String) -> Unit, onMakeOffer: (String, Double) -> Unit,
    // Real Marketplace listing wishlist (2026-07-21) -- state is lifted to
    // MarketplaceContent (mirroring FavoriteRestaurantsView's own already-real
    // lifted-favoriteIds pattern) so the heart stays correct across Browse/
    // Neighborhood/My-listings without a per-card refetch.
    favorited: Boolean = false, favoriteBusy: Boolean = false, onToggleFavorite: () -> Unit = {},
    sellerTrustScore: Int? = null,
    // Real "pay via itunda" Marketplace escrow (2026-07-25) -- needed to tell whether
    // the viewer is the buyer of an already-SOLD listing, so the Confirm-receipt/
    // dispute actions only ever show to the one real party who can act on them.
    currentUserId: String? = null,
    // Real distance display (2026-08-03) -- see MarketplaceContent's own doc comment
    // on browseLocation for the silent/opt-in sourcing. Null whenever location isn't
    // already granted, in which case the meta line falls back to category (below).
    viewerLocation: Pair<Double, Double>? = null,
    // Real like count (2026-08-03) -- see MarketplaceContent's own doc comment on
    // toggleLike. `liked` (whether the viewer has liked it) is lifted the same way
    // `favorited` above is; the *displayed count* is tracked locally just below since
    // ListingDto is immutable inside an immutable list up in MarketplaceContent.
    liked: Boolean = false, onToggleLike: () -> Unit = {},
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offering by remember { mutableStateOf(false) }
    // Real like count (2026-08-03) -- local optimistic display, reset whenever this
    // exact listing's own server-sourced count changes (a real refetch, e.g. after
    // pull-to-refresh), keyed on (listing.id, listing.likeCount) so a stale local
    // bump from a previous render of a *different* listing recycled into this slot
    // never leaks through.
    var displayedLikeCount by remember(listing.id, listing.likeCount) { mutableStateOf(listing.likeCount) }
    var offerAmount by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Real optional buyer identification at mark-sold time (2026-07-24) -- see backend
    // MarketplaceService.markSold's own doc comment. Deliberately optional: Confirm
    // with a phone number or Skip, either way the sale completes.
    var markingSold by remember { mutableStateOf(false) }
    var buyerPhone by remember { mutableStateOf("") }

    // Real seller-paid sponsored placement (2026-07-25) -- see backend
    // MarketplaceService.boostListing's own doc comment.
    var showBoostPicker by remember { mutableStateOf(false) }
    var boostTiers by remember { mutableStateOf<Map<String, Double>?>(null) }
    var boosting by remember { mutableStateOf(false) }

    // Real "pay via itunda" Marketplace escrow (2026-07-25) -- see backend
    // MarketplaceEscrow.kt's own doc comment. Opt-in alongside the existing in-person
    // cash handoff -- paying = the buyer committing to escrow; escrow/loadedEscrow =
    // the buyer's own already-paid escrow status once this listing is SOLD to them.
    var paying by remember { mutableStateOf(false) }
    var escrow by remember { mutableStateOf<rw.itunda.core.network.MarketplaceEscrowDto?>(null) }
    var loadedEscrow by remember { mutableStateOf(false) }
    var showDispute by remember { mutableStateOf(false) }
    var disputeReason by remember { mutableStateOf("") }
    var resolvingEscrow by remember { mutableStateOf(false) }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    var showReviewSheet by remember { mutableStateOf(false) }
    var selectedGoodPoints by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedUncomfortablePoints by remember { mutableStateOf<Set<String>>(emptySet()) }
    var submittingReview by remember { mutableStateOf(false) }
    var reviewSubmitted by remember { mutableStateOf(false) }
    // Real read-back for the review above (item 192/198) -- see bank-mfe's
    // HoodReviewResultView (item 192) for the full account. Seeds reviewSubmitted from
    // a real fetch instead of leaving it purely local/optimistic.
    var hoodReviews by remember { mutableStateOf<List<rw.itunda.core.network.HoodReviewDto>?>(null) }
    LaunchedEffect(listing.id, listing.status, listing.buyerId, isMine) {
        if (isMine && listing.status == "SOLD" && listing.buyerId != null) {
            try {
                val reviews = NetworkClient.apiService.getListingReviews(listing.id).reviews
                hoodReviews = reviews
                if (reviews.any { it.reviewerId == currentUserId }) reviewSubmitted = true
            } catch (e: Exception) {
                // Real, non-critical -- the review form itself still works without this.
            }
        }
    }

    // Real "directions to this seller" (2026-07-19, item 8 on the Maps "100%" roadmap) --
    // reuses itunda's own self-hosted OSRM directions.
    var myLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showRoute by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    val requestLocation = rememberRealLocationRequester(
        onLocating = { locating = it },
        onSuccess = { lat, lng -> myLocation = lat to lng; showRoute = true },
        onError = { error = it },
    )

    // Real escrow-status lazy fetch (2026-07-25) -- only for the one real buyer of an
    // already-SOLD listing, since that's the only person `getEscrow` will actually
    // return data to.
    val isMyEscrowPurchase = !isMine && listing.status == "SOLD" && currentUserId != null && listing.buyerId == currentUserId
    LaunchedEffect(listing.id, isMyEscrowPurchase) {
        if (isMyEscrowPurchase && !loadedEscrow) {
            escrow = try { NetworkClient.apiService.getEscrow(listing.id).escrow } catch (e: Exception) { null }
            loadedEscrow = true
        }
    }

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column {
            // Real Karrot list-row layout (2026-08-03, user-provided real 당근마켓
            // screenshots, light + dark) -- corrects the 2026-07-24 comment this
            // replaced, which claimed a large full-width hero photo led Karrot's real
            // card and price came before title; neither holds up against the actual
            // screenshots: every real row is a small square thumbnail beside the text,
            // and the real order is title (bold) -> location/time (muted) -> price
            // (bold, largest). The full-width hero-image version wasn't sourced from a
            // real screenshot at the time it was written.
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Real fix, 2026-08-03: was 96.dp -- a fresh real 당근마켓 screenshot
                // (user-provided) measured the actual thumbnail at ~124dp square on an
                // equivalent screen (326px of a 1080px-wide capture), notably more
                // prominent than itunda's first pass.
                Box(modifier = Modifier.size(124.dp).clip(RoundedCornerShape(12.dp))) {
                    if (listing.photoUrl != null) {
                        // SubcomposeAsyncImage, not AsyncImage (2026-07-24): plain AsyncImage
                        // renders nothing at all while loading or on a failed fetch -- a
                        // real gap that showed up live as a blank black box on a listing
                        // whose photoUrl was valid but hadn't finished loading yet. Now
                        // both the loading and error states fall back to the same
                        // placeholder icon the "no photo" branch below already used, so a
                        // slow or failed load never reads as broken UI.
                        SubcomposeAsyncImage(
                            model = listing.photoUrl,
                            contentDescription = listing.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            when (painter.state) {
                                is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                                else -> ListingPhotoPlaceholder()
                            }
                        }
                    } else {
                        ListingPhotoPlaceholder()
                    }
                    if (listing.status == "SOLD") {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("SOLD", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    // Real "Sponsored" badge (2026-07-25) -- only ever shown for a listing
                    // with a real, still-future boostedUntil, matching Coupang/Baemin's own
                    // real sponsored-placement labeling convention. Never fabricated on an
                    // unpaid listing.
                    if (isListingBoosted(listing.boostedUntil)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Ids.colors.brand)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) { Text("AD", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listing.title,
                            color = Ids.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!isMine) {
                            Icon(
                                if (favorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (favorited) "Remove from wishlist" else "Add to wishlist",
                                tint = if (favorited) Ids.colors.danger else Ids.colors.textTertiary,
                                modifier = Modifier.size(20.dp).clickable(enabled = !favoriteBusy, onClick = onToggleFavorite),
                            )
                        }
                    }
                    // Real distance display (2026-08-03) -- matches a real 당근마켓
                    // screenshot showing neighborhood/distance/time, not neighborhood/
                    // category/time. Falls back to category (the previous behavior)
                    // whenever distance isn't available -- see viewerLocation's own doc
                    // comment for why that's the common case, not a bug.
                    val viewerLat = viewerLocation?.first
                    val viewerLng = viewerLocation?.second
                    val listingLat = listing.latitude
                    val listingLng = listing.longitude
                    val distanceLabel = if (viewerLat != null && viewerLng != null && listingLat != null && listingLng != null) {
                        formatDistanceKm(haversineKm(viewerLat, viewerLng, listingLat, listingLng))
                    } else null
                    Text(
                        listOfNotNull(listing.neighborhood, distanceLabel ?: listing.category, relativeTimeAgo(listing.createdAt)).joinToString(" · "),
                        color = Ids.colors.textSecondary,
                        fontSize = 12.sp,
                    )
                    // Real 나눔 (free giveaway) treatment (2026-08-03) -- real Karrot
                    // shows "나눔 🧡" instead of "0원" for a free item; itunda already
                    // lets a seller set price to 0 (no separate listing-type flag
                    // needed on the backend), just never gave it special client
                    // treatment before. 🧡 is literal emoji text, not a themed color --
                    // same pattern this file's own CommunityPostCard-equivalent ❤️/💬
                    // counts already use, not a deviation from "themes stay common."
                    if (listing.price <= 0.0) {
                        Text("Free 🧡", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    } else {
                        Text("%,.0f RWF".format(listing.price), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
            // Real like count (2026-08-03) -- matches a real 당근마켓 screenshot's
            // bottom-right heart count on every row. Comment count is deliberately
            // NOT shown alongside it (unlike the real reference) -- there is no real
            // comment-thread feature on listings yet, and this app doesn't fabricate
            // a count for a feature that doesn't exist.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (liked) "Unlike" else "Like",
                    tint = if (liked) Ids.colors.danger else Ids.colors.textTertiary,
                    modifier = Modifier.size(16.dp).clickable {
                        displayedLikeCount = if (liked) (displayedLikeCount - 1).coerceAtLeast(0) else displayedLikeCount + 1
                        onToggleLike()
                    },
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("$displayedLikeCount", color = Ids.colors.textTertiary, fontSize = 12.sp)
            }
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Real Karrot-Score trust badge (2026-07-24) -- social proof, the fourth
            // element in Karrot's own real card hierarchy (price -> title ->
            // location/time -> social proof). Only shown for someone else's listing --
            // a trust score about yourself is meaningless here.
            if (!isMine && sellerTrustScore != null) {
                TrustBadge(sellerTrustScore)
            }
            Text(listing.description, color = Ids.colors.textSecondary, fontSize = 13.sp)
            listing.meetingPlace?.let {
                Text("Suggested hand-off: $it", color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            if (offering) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IdsTextField(
                        value = offerAmount,
                        onValueChange = { offerAmount = it },
                        label = "Your offer (RWF)",
                        singleLine = true,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    ListingActionButton("Send", busy || offerAmount.toDoubleOrNull() == null, filled = true) {
                        val amount = offerAmount.toDoubleOrNull() ?: return@ListingActionButton
                        offering = false
                        offerAmount = ""
                        onMakeOffer(listing.id, amount)
                    }
                }
            }
            // Real optional "who bought this?" prompt (2026-07-24) -- see backend
            // MarketplaceService.markSold's own doc comment. Shown inline instead of
            // immediately marking sold so the seller can Confirm with a phone number
            // or Skip; either way the sale completes.
            if (markingSold) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdsTextField(
                        value = buyerPhone,
                        onValueChange = { buyerPhone = it },
                        label = "Buyer's phone (optional)",
                        singleLine = true,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListingActionButton("Skip", busy) {
                        busy = true
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.markListingSold(listing.id)
                                markingSold = false
                                onChanged()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } finally {
                                busy = false
                            }
                        }
                    }
                    ListingActionButton("Confirm", busy, filled = true) {
                        busy = true
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.markListingSold(listing.id, MarkSoldRequest(buyerPhone.trim().ifBlank { null }))
                                markingSold = false
                                onChanged()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            }
            // Real seller-paid sponsored placement picker (2026-07-25) -- see backend
            // MarketplaceService.boostListing's own doc comment for the real flat-fee
            // tiers (never client-invented -- fetched from GET /boost-tiers).
            if (showBoostPicker) {
                val tiers = boostTiers
                if (tiers == null) {
                    Text("Loading boost options…", color = Ids.colors.textSecondary, fontSize = 13.sp)
                } else if (tiers.isEmpty()) {
                    Text("Couldn't load boost options. Try again.", color = Ids.colors.danger, fontSize = 13.sp)
                } else {
                    Text("Boost this listing to the top of search results", color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tiers.toSortedMap(compareBy { it.toInt() }).forEach { (days, price) ->
                            ListingActionButton(if (boosting) "…" else "${days}d · %,.0f RWF".format(price), boosting, filled = true) {
                                boosting = true
                                error = null
                                coroutineScope.launch {
                                    try {
                                        NetworkClient.apiService.boostListing(listing.id, UUID.randomUUID().toString(), BoostListingRequest(days.toInt()))
                                        showBoostPicker = false
                                        onChanged()
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    } finally {
                                        boosting = false
                                    }
                                }
                            }
                        }
                    }
                    ListingActionButton("Cancel", boosting) { showBoostPicker = false }
                }
            }
            if (isMine && listing.status == "SOLD" && listing.buyerId != null && reviewSubmitted) {
                hoodReviews?.let { HoodReviewResultView(it, currentUserId) }
            }
            // Real post-transaction review, preset checklist with asymmetric public/
            // private visibility (2026-07-24) -- see backend HoodReviewService's own
            // doc comment. Only offered once a real buyer was recorded at mark-sold
            // time.
            if (isMine && listing.status == "SOLD" && listing.buyerId != null && !reviewSubmitted) {
                if (showReviewSheet) {
                    HoodReviewForm(
                        selectedGoodPoints = selectedGoodPoints,
                        onToggleGoodPoint = { p -> selectedGoodPoints = if (p in selectedGoodPoints) selectedGoodPoints - p else selectedGoodPoints + p },
                        selectedUncomfortablePoints = selectedUncomfortablePoints,
                        onToggleUncomfortablePoint = { p -> selectedUncomfortablePoints = if (p in selectedUncomfortablePoints) selectedUncomfortablePoints - p else selectedUncomfortablePoints + p },
                        submitting = submittingReview,
                        onCancel = { showReviewSheet = false },
                        onSubmit = {
                            submittingReview = true
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.submitListingReview(
                                        listing.id,
                                        SubmitHoodReviewRequest(selectedGoodPoints.toList(), selectedUncomfortablePoints.toList()),
                                    )
                                    reviewSubmitted = true
                                    showReviewSheet = false
                                    hoodReviews = (hoodReviews ?: emptyList()) + res.review
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } finally {
                                    submittingReview = false
                                }
                            }
                        },
                    )
                } else {
                    ListingActionButton("Rate this buyer", busy, filled = true) { showReviewSheet = true }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isMine) {
                    if (listing.status == "ACTIVE" && !markingSold) {
                        ListingActionButton("Mark sold", busy) { markingSold = true }
                        ListingActionButton("Boost", busy) {
                            showBoostPicker = true
                            if (boostTiers == null) {
                                coroutineScope.launch {
                                    boostTiers = try { NetworkClient.apiService.getBoostTiers().tiers } catch (e: Exception) { emptyMap() }
                                }
                            }
                        }
                    }
                    if (listing.status != "REMOVED") {
                        ListingActionButton("Remove", busy) {
                            busy = true
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.removeListing(listing.id)
                                    onChanged()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                } else if (listing.status == "ACTIVE" && !offering) {
                    ListingActionButton(if (busy) "Starting…" else "Message seller", busy) {
                        busy = true
                        onMessageSeller(listing.id)
                        busy = false
                    }
                    ListingActionButton("Make an offer", busy, filled = true) { offering = true }
                    // Real "pay via itunda" Marketplace escrow (2026-07-25) -- an
                    // opt-in safer alternative to the existing in-person cash handoff,
                    // never replacing it.
                    ListingActionButton(if (paying) "Paying…" else "🔒 Pay via itunda", paying) {
                        paying = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.payEscrow(listing.id, UUID.randomUUID().toString())
                                onChanged()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                paying = false
                            }
                        }
                    }
                }
            }
            // Real buyer-side escrow status (2026-07-25) -- Confirm receipt releases
            // payment to the seller; Report a problem flags it for real human admin
            // review instead of an automated resolution. See backend
            // MarketplaceEscrow.kt's own doc comment.
            if (isMyEscrowPurchase && escrow != null) {
                val currentEscrow = escrow!!
                when (currentEscrow.status) {
                    "HELD" -> {
                        Text("🔒 Payment held by itunda until you confirm receipt", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        if (showDispute) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                IdsTextField(
                                    value = disputeReason, onValueChange = { disputeReason = it },
                                    label = "What went wrong?", modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ListingActionButton("Cancel", resolvingEscrow) { showDispute = false }
                                    ListingActionButton("Submit", resolvingEscrow || disputeReason.isBlank(), filled = true) {
                                        resolvingEscrow = true
                                        coroutineScope.launch {
                                            try {
                                                escrow = NetworkClient.apiService.disputeEscrow(listing.id, rw.itunda.core.network.DisputeEscrowRequest(disputeReason)).escrow
                                                showDispute = false
                                            } catch (e: HttpException) {
                                                error = superAppErrorMessage(e)
                                            } finally {
                                                resolvingEscrow = false
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ListingActionButton(if (resolvingEscrow) "Working…" else "Confirm receipt", resolvingEscrow, filled = true) {
                                    resolvingEscrow = true
                                    coroutineScope.launch {
                                        try {
                                            escrow = NetworkClient.apiService.confirmEscrowReceipt(listing.id, UUID.randomUUID().toString()).escrow
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } finally {
                                            resolvingEscrow = false
                                        }
                                    }
                                }
                                ListingActionButton("Report a problem", resolvingEscrow) { showDispute = true }
                            }
                        }
                    }
                    "DISPUTED" -> Text("⚠️ Reported -- itunda is reviewing this trade", color = Ids.colors.danger, fontSize = 12.sp)
                    "RELEASED" -> Text("✅ Payment released to the seller", color = Ids.colors.success, fontSize = 12.sp)
                    "REFUNDED" -> Text("↩️ Refunded to you", color = Ids.colors.success, fontSize = 12.sp)
                }
            }
            if (!isMine && listing.status == "ACTIVE") {
                HoodReportAction(targetType = "MARKETPLACE_LISTING", targetId = listing.id)
            }
            if (!isMine && listing.status == "ACTIVE" && listing.latitude != null && listing.longitude != null) {
                ListingActionButton(
                    if (locating) "Finding your real location…" else if (showRoute) "Hide directions" else "🚗 Directions to this seller",
                    locating,
                ) {
                    if (showRoute) showRoute = false else if (myLocation != null) showRoute = true else requestLocation()
                }
            }
            val loc = myLocation
            val listingLat = listing.latitude
            val listingLng = listing.longitude
            // Real cross-module smart-cast limitation (found 2026-07-22 while
            // relocating ApiService.kt's DTOs into :core:network): Kotlin only
            // smart-casts a nullable property after a null-check within the same
            // module -- captured into local vals above instead.
            if (showRoute && loc != null && listingLat != null && listingLng != null) {
                RouteMiniMap(loc.first, loc.second, listingLat, listingLng, "You", listing.title)
            }
            }
        }
    }
}
