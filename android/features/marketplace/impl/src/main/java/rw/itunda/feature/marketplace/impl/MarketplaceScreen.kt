package rw.itunda.feature.marketplace.impl

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
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import rw.itunda.core.designsystem.components.HoodReportAction
import rw.itunda.core.designsystem.components.HoodReviewForm
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.NeighborhoodSetupPrompt
import rw.itunda.core.designsystem.components.RouteMiniMap
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.TrustBadge
import rw.itunda.core.designsystem.components.relativeTimeAgo
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreateListingRequest
import rw.itunda.core.network.FavoriteListingDto
import rw.itunda.core.network.ListingDto
import rw.itunda.core.network.MakeOfferRequest
import rw.itunda.core.network.MarkSoldRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SubmitHoodReviewRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

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
private enum class HoodView { BROWSE, NEARBY, NEIGHBORHOOD, MINE, PURCHASES, WISHLIST }

private fun HoodView.label() = when (this) {
    HoodView.BROWSE -> "Browse"
    HoodView.NEARBY -> "Near me"
    HoodView.NEIGHBORHOOD -> "Neighborhood"
    HoodView.MINE -> "My listings"
    HoodView.PURCHASES -> "Purchases"
    HoodView.WISHLIST -> "♡ Wishlist"
}

@Composable
fun MarketplaceContent(
    onMessageSeller: (String) -> Unit,
) {
    var view by remember { mutableStateOf(HoodView.BROWSE) }
    var neighborhoodName by remember { mutableStateOf<String?>(null) }
    var neighborhoodChecked by remember { mutableStateOf(false) }
    var listings by remember { mutableStateOf<List<ListingDto>?>(null) }
    // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment;
    // the backend has spread this sellerId->score map alongside every browse response
    // since 2026-07-21, this just finally reads and renders it.
    var trustScores by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewListing by remember { mutableStateOf(false) }
    if (showNewListing) {
        BackHandler { showNewListing = false }
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
                    if (res.success) { listings = res.listings; trustScores = res.trustScores }
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

    fun load() {
        listings = null
        if (view == HoodView.NEARBY) {
            requestNearbyLocation()
            return
        }
        if (view == HoodView.WISHLIST) {
            // ListingWishlistView below owns its own fetch (it needs title/price/category
            // straight from the favorites endpoint, not the ListingDto shape) -- nothing
            // to load into `listings` here.
            return
        }
        if (view == HoodView.NEIGHBORHOOD) {
            neighborhoodChecked = false
            coroutineScope.launch {
                try {
                    val profileRes = NetworkClient.authApi.getProfile()
                    val res = NetworkClient.apiService.getListingsMyNeighborhood()
                    neighborhoodName = profileRes.user.neighborhood
                    if (res.success) { listings = res.listings; trustScores = res.trustScores }
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
                if (res.success) { listings = res.listings; trustScores = res.trustScores }
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
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        // No TabHeader here (2026-07-24, was `TabHeader("Hood")`): the bottom nav
        // already highlights "Hood" and HoodTab's own Market/Life/Jobs/Home strip
        // sits right above this screen, so a third "Hood" label was pure repeat
        // noise, not information.
        item {
            // Flat, horizontally-scrolling category strip (2026-07-24), replacing
            // a filled-pill segmented row -- same Karrot/Toss-Shopping-style flat
            // tab treatment as HoodTab's own Market/Life/Jobs/Home row in
            // SuperAppTabs.kt, so the two stacked nav rows read as one language
            // instead of two different chrome styles.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                HoodView.entries.forEach { v ->
                    val selected = v == view
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { view = v }) {
                        Text(
                            v.label(),
                            color = if (selected) Ids.colors.textPrimary else Ids.colors.textSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        )
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .width(18.dp)
                                .background(if (selected) Ids.colors.brand else Color.Transparent, RoundedCornerShape(1.dp)),
                        )
                    }
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
                    },
                    icon = Icons.Outlined.ShoppingBag,
                )
            }
        } else if (listings!!.isNotEmpty()) {
            items(listings!!, key = { it.id }) { listing ->
                ListingCard(
                    listing = listing,
                    isMine = view == HoodView.MINE || listing.sellerId == currentUserId,
                    sellerTrustScore = trustScores[listing.sellerId],
                    onChanged = ::load,
                    favorited = listing.id in favoriteIds,
                    favoriteBusy = favoritingId == listing.id,
                    onToggleFavorite = { toggleFavorite(listing.id) },
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
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("What are you selling?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, placeholder = { Text("Description") }, modifier = Modifier.fillMaxWidth())
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
                OutlinedTextField(value = price, onValueChange = { price = it }, placeholder = { Text("Price (RWF)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = category, onValueChange = { category = it }, placeholder = { Text("Category") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = meetingPlace,
                onValueChange = { meetingPlace = it },
                placeholder = { Text("Suggested meeting place (optional)") },
                supportingText = { Text("Use a public landmark, not a home address.") },
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

@Composable
private fun ListingCard(
    listing: ListingDto, isMine: Boolean, onChanged: () -> Unit, onMessageSeller: (String) -> Unit, onMakeOffer: (String, Double) -> Unit,
    // Real Marketplace listing wishlist (2026-07-21) -- state is lifted to
    // MarketplaceContent (mirroring FavoriteRestaurantsView's own already-real
    // lifted-favoriteIds pattern) so the heart stays correct across Browse/
    // Neighborhood/My-listings without a per-card refetch.
    favorited: Boolean = false, favoriteBusy: Boolean = false, onToggleFavorite: () -> Unit = {},
    sellerTrustScore: Int? = null,
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offering by remember { mutableStateOf(false) }
    var offerAmount by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Real optional buyer identification at mark-sold time (2026-07-24) -- see backend
    // MarketplaceService.markSold's own doc comment. Deliberately optional: Confirm
    // with a phone number or Skip, either way the sale completes.
    var markingSold by remember { mutableStateOf(false) }
    var buyerPhone by remember { mutableStateOf("") }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    var showReviewSheet by remember { mutableStateOf(false) }
    var selectedGoodPoints by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedUncomfortablePoints by remember { mutableStateOf<Set<String>>(emptySet()) }
    var submittingReview by remember { mutableStateOf(false) }
    var reviewSubmitted by remember { mutableStateOf(false) }

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

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column {
            // Real Karrot-style photo-forward card (2026-07-24) -- a real listing
            // photo (see Listing.photoUrl's own doc comment for the honest
            // "seller-provided URL, no upload pipeline" scope) leads the card, since
            // that's the actual #1 element in Karrot's real info hierarchy (confirmed:
            // price -> title -> location/time -> social proof, always led by a large
            // thumbnail) -- previously this card was pure text, closer to a bank
            // transaction row than a marketplace listing. An unset photo falls back to
            // a plain placeholder box, never a fabricated image.
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(topStart = Ids.layout.cardCornerRadius, topEnd = Ids.layout.cardCornerRadius))) {
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
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surface).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("SOLD", color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (!isMine) {
                    Icon(
                        if (favorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (favorited) "Remove from wishlist" else "Add to wishlist",
                        tint = if (favorited) Ids.colors.danger else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(22.dp)
                            .clickable(enabled = !favoriteBusy, onClick = onToggleFavorite),
                    )
                }
            }
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Real Karrot info order -- price first (most prominent), then title,
            // then location + time, matching this session's own live research into
            // 당근마켓's real listing-card hierarchy.
            Text("%,.0f RWF".format(listing.price), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(listing.title, color = Ids.colors.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(listing.neighborhood, listing.category, relativeTimeAgo(listing.createdAt)).joinToString(" · "),
                color = Ids.colors.textSecondary,
                fontSize = 12.sp,
            )
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
                    OutlinedTextField(
                        value = offerAmount,
                        onValueChange = { offerAmount = it },
                        placeholder = { Text("Your offer (RWF)") },
                        singleLine = true,
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
                    OutlinedTextField(
                        value = buyerPhone,
                        onValueChange = { buyerPhone = it },
                        placeholder = { Text("Buyer's phone (optional)") },
                        singleLine = true,
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
            // Real post-transaction review, preset checklist with asymmetric public/
            // private visibility (2026-07-24) -- see backend HoodReviewService's own
            // doc comment. Only offered once a real buyer was recorded at mark-sold
            // time; no pre-check for "already reviewed" (a real, honest v1 -- a second
            // attempt just surfaces the backend's own REVIEW_ALREADY_SUBMITTED error).
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
                                    NetworkClient.apiService.submitListingReview(
                                        listing.id,
                                        SubmitHoodReviewRequest(selectedGoodPoints.toList(), selectedUncomfortablePoints.toList()),
                                    )
                                    reviewSubmitted = true
                                    showReviewSheet = false
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
