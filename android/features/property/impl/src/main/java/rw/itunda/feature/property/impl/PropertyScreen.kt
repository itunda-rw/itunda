package rw.itunda.feature.property.impl

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.HoodReportAction
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.NeighborhoodSetupPrompt
import rw.itunda.core.designsystem.components.RouteMiniMap
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.relativeTimeAgo
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreatePropertyListingRequest
import rw.itunda.core.network.FavoritePropertyListingDto
import rw.itunda.core.network.MakePropertyOfferRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PropertyListingDto
import rw.itunda.core.network.PropertyTypeDto
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Third Feature extraction (2026-07-23) after Marketplace and Jobs, same template -- see
// features/marketplace/impl/.../MarketplaceScreen.kt's own header comment for the full
// account of why this module owns its own NetworkClient calls directly. RouteMiniMap
// imports directly from core/designsystem (see that file's own header comment) rather
// than being injected.

private enum class PropertyView { BROWSE, NEARBY, NEIGHBORHOOD, MINE, SAVED }

@Composable
fun PropertyContent(
    onMessageLister: (String) -> Unit,
) {
    var view by remember { mutableStateOf(PropertyView.BROWSE) }
    var propertyTypes by remember { mutableStateOf<List<PropertyTypeDto>>(emptyList()) }
    var listingTypeFilter by remember { mutableStateOf<String?>(null) }
    var propertyTypeFilter by remember { mutableStateOf<String?>(null) }
    var listings by remember { mutableStateOf<List<PropertyListingDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewListing by remember { mutableStateOf(false) }
    var neighborhoodName by remember { mutableStateOf<String?>(null) }
    var neighborhoodChecked by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var nearbyRadiusKm by remember { mutableStateOf(3.0) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }
    val requestNearbyLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            listings = null
            coroutineScope.launch {
                try {
                    val res = NetworkClient.apiService.getNearbyPropertyListings(lat, lng, nearbyRadiusKm)
                    if (res.success) listings = res.listings
                    error = null
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                    listings = emptyList()
                } catch (e: IOException) {
                    error = "Couldn't load nearby properties. Check your connection and try again."
                    listings = emptyList()
                }
            }
        },
        onError = { message -> error = "$message You can still use Browse or Neighborhood."; listings = emptyList() },
    )

    LaunchedEffect(Unit) {
        try { propertyTypes = NetworkClient.apiService.getPropertyTypes().propertyTypes } catch (e: Exception) { /* chips just won't render */ }
        try { favoriteIds = NetworkClient.apiService.getMyFavoritePropertyListings().favorites.map { it.propertyListingId }.toSet() } catch (e: Exception) { }
    }

    fun load() {
        listings = null
        if (view == PropertyView.SAVED) { listings = emptyList(); error = null; return }
        if (view == PropertyView.NEARBY) {
            requestNearbyLocation()
            return
        }
        if (view == PropertyView.NEIGHBORHOOD) {
            neighborhoodChecked = false
            coroutineScope.launch {
                try {
                    val profileRes = NetworkClient.authApi.getProfile()
                    val res = NetworkClient.apiService.getPropertyListingsMyNeighborhood()
                    neighborhoodName = profileRes.user.neighborhood
                    if (res.success) listings = res.listings
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
                val res = if (view == PropertyView.BROWSE) {
                    NetworkClient.apiService.browsePropertyListings(listingTypeFilter, propertyTypeFilter)
                } else {
                    NetworkClient.apiService.getMyPropertyListings()
                }
                if (res.success) listings = res.listings
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(view, listingTypeFilter, propertyTypeFilter) { load() }

    if (showNewListing) {
        BackHandler { showNewListing = false }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft).padding(4.dp)) {
                listOf(PropertyView.BROWSE to "Browse", PropertyView.NEARBY to "Near me", PropertyView.NEIGHBORHOOD to "Neighborhood", PropertyView.MINE to "My listings", PropertyView.SAVED to "Saved").forEach { (v, label) ->
                    val selected = v == view
                    Text(
                        label,
                        color = if (selected) Color.White else Ids.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Ids.colors.brand else Color.Transparent)
                            .clickable { view = v }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
        if (view == PropertyView.NEARBY) item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(1.0, 3.0, 5.0, 10.0).forEach { radius ->
                val active = nearbyRadiusKm == radius
                Text("${radius.toInt()} km", color = if (active) Color.White else Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft).clickable { nearbyRadiusKm = radius; requestNearbyLocation() }.padding(horizontal = 12.dp, vertical = 7.dp))
            } }
        }
        if (view == PropertyView.BROWSE) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("RENT" to "For rent", "SALE" to "For sale").forEach { (v, label) ->
                        val active = listingTypeFilter == v
                        Box(
                            modifier = Modifier
                                .background(if (active) Ids.colors.brand else Color.White, RoundedCornerShape(999.dp))
                                .border(1.dp, if (active) Ids.colors.brand else Ids.colors.textSecondary.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                                .clickable { listingTypeFilter = if (active) null else v }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else Ids.colors.textPrimary) }
                    }
                }
            }
            if (propertyTypes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        propertyTypes.forEach { t ->
                            val active = propertyTypeFilter == t.id
                            Box(
                                modifier = Modifier
                                    .background(if (active) Ids.colors.brand else Color.White, RoundedCornerShape(999.dp))
                                    .border(1.dp, if (active) Ids.colors.brand else Ids.colors.textSecondary.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                                    .clickable { propertyTypeFilter = if (active) null else t.id }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { Text(t.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else Ids.colors.textPrimary) }
                        }
                    }
                }
            }
        }
        if (view == PropertyView.MINE) {
            item {
                if (!showNewListing) {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ids.colors.brand).clickable { showNewListing = true }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("+ List a property", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    NewPropertyListingForm(propertyTypes, onCreated = { showNewListing = false; load() }, onCancel = { showNewListing = false })
                }
            }
        }
        if (view == PropertyView.NEIGHBORHOOD && neighborhoodChecked && neighborhoodName == null) {
            item { NeighborhoodSetupPrompt(onDone = { load() }) }
        }
        if (view == PropertyView.NEIGHBORHOOD && neighborhoodName != null) {
            item { Text("Your neighborhood: $neighborhoodName", color = Ids.colors.textSecondary, fontSize = 13.sp) }
        }
        if (view == PropertyView.SAVED) {
            item { PropertyWishlistView(onRemoved = { coroutineScope.launch { favoriteIds = NetworkClient.apiService.getMyFavoritePropertyListings().favorites.map { it.propertyListingId }.toSet() } }) }
        } else if (error != null) {
            item { ErrorCard(error!!, onRetry = ::load) }
        } else if (listings == null) {
            item { SkeletonBlock() }
        } else if (listings!!.isEmpty() && (view != PropertyView.NEIGHBORHOOD || neighborhoodName != null)) {
            item {
                Text(
                    when (view) {
                        PropertyView.BROWSE -> "No properties listed yet."
                        PropertyView.NEARBY -> "No properties near you yet."
                        PropertyView.NEIGHBORHOOD -> "No properties in your neighborhood yet."
                        PropertyView.MINE -> "You haven't listed any properties yet."
                        PropertyView.SAVED -> ""
                    },
                    color = Ids.colors.textSecondary, fontSize = 14.sp,
                )
            }
        } else if (listings!!.isNotEmpty()) {
            items(listings!!, key = { it.id }) { listing ->
                PropertyListingCard(
                    listing = listing,
                    propertyTypeLabel = propertyTypes.firstOrNull { it.id == listing.propertyType }?.label ?: listing.propertyType,
                    isMine = view == PropertyView.MINE || listing.listerId == currentUserId,
                    onChanged = ::load,
                    onContact = {
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.contactLister(listing.id)
                                if (res.success) onMessageLister(res.conversation.id)
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
                                val res = NetworkClient.apiService.makePropertyOffer(id, MakePropertyOfferRequest(amount))
                                if (res.success) onMessageLister(res.offer.conversationId)
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            }
                        }
                    },
                    favorited = listing.id in favoriteIds,
                    onToggleFavorite = { coroutineScope.launch { try { if (listing.id in favoriteIds) { NetworkClient.apiService.removePropertyListingFavorite(listing.id); favoriteIds = favoriteIds - listing.id; Toast.makeText(context, "Removed from saved properties", Toast.LENGTH_SHORT).show() } else { NetworkClient.apiService.addPropertyListingFavorite(listing.id); favoriteIds = favoriteIds + listing.id; Toast.makeText(context, "Saved to your properties list", Toast.LENGTH_SHORT).show() } } catch (e: Exception) { error = "Couldn't update your saved properties. Check your connection and try again." } } },
                )
            }
        }
    }
}

@Composable
private fun PropertyWishlistView(onRemoved: () -> Unit) {
    var favorites by remember { mutableStateOf<List<FavoritePropertyListingDto>?>(null) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    fun load() = scope.launch { try { favorites = NetworkClient.apiService.getMyFavoritePropertyListings().favorites; error = null } catch (e: Exception) { error = "Couldn't load your saved properties. Check your connection and try again." } }
    LaunchedEffect(Unit) { load() }
    when { error != null -> ErrorCard(error!!, onRetry = ::load); favorites == null -> SkeletonBlock(); favorites!!.isEmpty() -> Text("No saved properties yet — tap ♡ on a property to keep it here.", color = Ids.colors.textSecondary); else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { favorites!!.forEach { f -> Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(f.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold); Text("${f.listingType} · %,.0f RWF".format(f.price), color = Ids.colors.textSecondary, fontSize = 12.sp) }; Text("Remove", color = Ids.colors.textPrimary, modifier = Modifier.clickable { scope.launch { try { NetworkClient.apiService.removePropertyListingFavorite(f.propertyListingId); favorites = favorites!!.filterNot { it.propertyListingId == f.propertyListingId }; onRemoved() } catch (e: Exception) { error = "Couldn't remove this saved property. Check your connection and try again." } } }) } } } } }
}

@Composable
private fun NewPropertyListingForm(propertyTypes: List<PropertyTypeDto>, onCreated: () -> Unit, onCancel: () -> Unit) {
    var listingType by remember { mutableStateOf("RENT") }
    var propertyType by remember { mutableStateOf(propertyTypes.firstOrNull()?.id ?: "") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var bedrooms by remember { mutableStateOf("") }
    var sizeSqm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
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
            Text("List a property", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("RENT" to "For rent", "SALE" to "For sale").forEach { (v, label) ->
                    val selected = listingType == v
                    Text(
                        label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Ids.colors.textPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .clickable { listingType = v }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                propertyTypes.forEach { t ->
                    val selected = propertyType == t.id
                    Box(
                        modifier = Modifier
                            .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft, RoundedCornerShape(999.dp))
                            .clickable { propertyType = t.id }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text(t.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Ids.colors.textPrimary) }
                }
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("e.g. 2-bedroom apartment in Kacyiru") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, placeholder = { Text("Describe the property") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = price, onValueChange = { price = it },
                    placeholder = { Text(if (listingType == "RENT") "Rent/mo (RWF)" else "Price (RWF)") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(value = bedrooms, onValueChange = { bedrooms = it }, placeholder = { Text("Bedrooms") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = sizeSqm, onValueChange = { sizeSqm = it }, placeholder = { Text("Size (m²)") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft)
                    .clickable(enabled = !locating) { if (shareLocation) shareLocation = false else requestLocation() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) { Text(if (locating) "Finding your real location…" else if (shareLocation) "📍 Property area shared for nearby search" else "📍 Share property area for nearby search (optional)", fontSize = 13.sp, color = if (shareLocation) Ids.colors.brand else Ids.colors.textSecondary) }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ids.colors.brand)
                        .clickable(enabled = !submitting) {
                            val priceValue = price.toDoubleOrNull()
                            if (title.isBlank() || description.isBlank() || propertyType.isBlank() || priceValue == null || priceValue <= 0) {
                                error = "Fill in every field with a real price."
                                return@clickable
                            }
                            submitting = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.createPropertyListing(
                                        CreatePropertyListingRequest(
                                            listingType, propertyType, title, description, priceValue,
                                            bedrooms.toIntOrNull(), sizeSqm.toDoubleOrNull(),
                                            if (shareLocation) myLocation?.first else null, if (shareLocation) myLocation?.second else null,
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
private fun PropertyListingCard(
    listing: PropertyListingDto, propertyTypeLabel: String, isMine: Boolean, onChanged: () -> Unit, onContact: () -> Unit,
    onMakeOffer: (String, Double) -> Unit, favorited: Boolean = false, onToggleFavorite: () -> Unit = {},
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offering by remember { mutableStateOf(false) }
    var offerAmount by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var myLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showRoute by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    val requestLocation = rememberRealLocationRequester(
        onLocating = { locating = it },
        onSuccess = { lat, lng -> myLocation = lat to lng; showRoute = true },
        onError = { error = it },
    )
    val priceLabel = "%,.0f RWF".format(listing.price) + if (listing.listingType == "RENT") "/mo" else ""
    val details = listOfNotNull(
        listing.bedrooms?.let { "$it bd" },
        listing.sizeSqm?.let { "${it} m²" },
    ).joinToString(" · ")

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (listing.listingType == "RENT") "For rent" else "For sale"} · $propertyTypeLabel",
                        color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    )
                    if (listing.status == "TAKEN") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("TAKEN", color = Ids.colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isMine) Text(if (favorited) "♥" else "♡", color = if (favorited) Ids.colors.danger else Ids.colors.textSecondary, fontSize = 22.sp, modifier = Modifier.clickable { onToggleFavorite() }.padding(end = 8.dp))
                    Text(priceLabel, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Text(listing.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val detailsWithTime = (if (details.isNotBlank()) "$details · " else "") + relativeTimeAgo(listing.createdAt)
            Text(detailsWithTime, color = Ids.colors.textSecondary, fontSize = 12.sp)
            Text(listing.description, color = Ids.colors.textSecondary, fontSize = 13.sp)
            // Real 당근-style price-offer negotiation (2026-07-19) -- see
            // PropertyPriceOfferService's own doc comment; mirrors ListingCard's own
            // offering UI exactly.
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
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isMine) {
                    if (listing.status == "AVAILABLE") {
                        ListingActionButton("Mark taken", busy) {
                            busy = true
                            coroutineScope.launch {
                                try { NetworkClient.apiService.markPropertyListingTaken(listing.id); onChanged() }
                                catch (e: HttpException) { error = superAppErrorMessage(e) }
                                finally { busy = false }
                            }
                        }
                    }
                    if (listing.status != "REMOVED") {
                        ListingActionButton("Remove", busy) {
                            busy = true
                            coroutineScope.launch {
                                try { NetworkClient.apiService.removePropertyListing(listing.id); onChanged() }
                                catch (e: HttpException) { error = superAppErrorMessage(e) }
                                finally { busy = false }
                            }
                        }
                    }
                } else if (listing.status == "AVAILABLE" && !offering) {
                    ListingActionButton("Message lister", busy, onClick = onContact)
                    ListingActionButton("Make an offer", busy, filled = true) { offering = true }
                }
            }
            if (!isMine && listing.status == "AVAILABLE") {
                HoodReportAction(targetType = "PROPERTY_LISTING", targetId = listing.id)
            }
            val propertyLat = listing.latitude
            val propertyLng = listing.longitude
            if (!isMine && listing.status == "AVAILABLE" && propertyLat != null && propertyLng != null) {
                ListingActionButton(if (locating) "Finding your real location…" else if (showRoute) "Hide directions" else "🚗 Directions to this property", locating) {
                    if (showRoute) showRoute = false else if (myLocation != null) showRoute = true else requestLocation()
                }
                myLocation?.let { loc ->
                    if (showRoute) RouteMiniMap(loc.first, loc.second, propertyLat, propertyLng, "You", listing.title)
                }
            }
        }
    }
}
