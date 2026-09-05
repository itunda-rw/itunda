package rw.itunda.feature.property.impl

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import rw.itunda.core.designsystem.components.ScrollFog
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.TrustBadge
import rw.itunda.core.designsystem.components.relativeTimeAgo
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import rw.itunda.core.designsystem.itundaface.WishlistHeart
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreatePropertyListingRequest
import rw.itunda.core.network.FavoritePropertyListingDto
import rw.itunda.core.network.MakePropertyOfferRequest
import rw.itunda.core.network.MarkTakenRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PropertyListingDto
import rw.itunda.core.network.PropertyTypeDto
import rw.itunda.core.network.PropertyValuationEstimateDto
import rw.itunda.core.network.SubmitHoodReviewRequest
import rw.itunda.core.network.SubmitOwnershipVerificationRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

// Real fix (2026-08-26): split out of PropertyScreen.kt once that file grew past
// its file-size-lint baseline. Property valuation estimate + saved-properties
// wishlist, only rendered inside PropertyContent's own feed. Same package, so
// zero import changes anywhere.

// Real Toss Bank 우리집 시세 (my home's estimated value, item 228) -- see the backend's
// PropertyListingService.estimateValue doc comment. Read-only: enter a location + size,
// get a real comparable-listings-based estimate, nothing persisted. bank-mfe already
// has this (PropertyValuationCard); this is the first Android client.
@Composable
internal fun PropertyValuationCard(propertyTypes: List<PropertyTypeDto>) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var propertyType by remember { mutableStateOf<String?>(null) }
    var listingType by remember { mutableStateOf("SALE") }
    var sizeSqm by remember { mutableStateOf("") }
    var estimate by remember { mutableStateOf<PropertyValuationEstimateDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a lone
    // form section (docs/UI_UX_GUIDELINES.md §10).
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("우리집 시세 — Estimate my home's value", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("A real estimate based on comparable listings near you, not a fabricated number.", color = Ids.colors.textSecondary, fontSize = 12.sp)
            IdsTextField(value = latitude, onValueChange = { latitude = it }, label = "Latitude", keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, modifier = Modifier.fillMaxWidth())
            IdsTextField(value = longitude, onValueChange = { longitude = it }, label = "Longitude", keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                propertyTypes.forEach { t ->
                    val active = propertyType == t.id
                    Text(
                        t.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else Ids.colors.textPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .pressScaleClickable { propertyType = t.id }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("SALE" to "For sale", "RENT" to "For rent").forEach { (v, label) ->
                    val active = listingType == v
                    Text(
                        label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else Ids.colors.textPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .pressScaleClickable { listingType = v }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            IdsTextField(value = sizeSqm, onValueChange = { sizeSqm = it }, label = "Size (sqm)", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            ListingActionButton(if (loading) "Estimating…" else "Estimate value", loading, filled = true) {
                val lat = latitude.toDoubleOrNull()
                val lng = longitude.toDoubleOrNull()
                val size = sizeSqm.toDoubleOrNull()
                if (propertyType == null || lat == null || lng == null || size == null || size <= 0.0) {
                    error = "Fill in a real location, property type, and size."
                    return@ListingActionButton
                }
                loading = true
                error = null
                estimate = null
                scope.launch {
                    try {
                        estimate = NetworkClient.apiService.getPropertyValuation(lat, lng, propertyType!!, listingType, size).estimate
                    } catch (e: HttpException) {
                        error = if (e.code() == 422) "Not enough comparable listings nearby to estimate a value." else superAppErrorMessage(e)
                    } catch (e: IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } finally {
                        loading = false
                    }
                }
            }
            estimate?.let { est ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(12.dp),
                ) {
                    Text(String.format(Locale.US, "%,.0f RWF", est.estimatedValue), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text(
                        String.format(Locale.US, "Based on ${est.comparableCount} comparable listings within ${est.radiusKm.toInt()} km (%,.0f RWF/sqm avg)", est.averagePricePerSqm),
                        color = Ids.colors.textSecondary, fontSize = 12.sp,
                    )
                }
            }
    }
}

@Composable
internal fun PropertyWishlistView(onRemoved: () -> Unit) {
    var favorites by remember { mutableStateOf<List<FavoritePropertyListingDto>?>(null) }; var error by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    fun load() = scope.launch { try { favorites = NetworkClient.apiService.getMyFavoritePropertyListings().favorites; error = null } catch (e: Exception) { error = "Couldn't load your saved properties. Check your connection and try again." } }
    LaunchedEffect(Unit) { load() }
    // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card -- an
    // entity list a user manages (saved properties), no divider, matching
    // GroupAccountScreen's precedent (docs/UI_UX_GUIDELINES.md §10).
    when { error != null -> ErrorCard(error!!, onRetry = ::load); favorites == null -> SkeletonBlock(); favorites!!.isEmpty() -> EmptyState("No saved properties yet — tap ♡ on a property to keep it here.", icon = Icons.Outlined.FavoriteBorder); else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { favorites!!.forEach { f -> Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(f.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold); Text(String.format(Locale.US, "${f.listingType} · %,.0f RWF", f.price), color = Ids.colors.textSecondary, fontSize = 12.sp) }; Text("Remove", color = Ids.colors.textPrimary, modifier = Modifier.pressScaleClickable { scope.launch { try { NetworkClient.apiService.removePropertyListingFavorite(f.propertyListingId); favorites = favorites!!.filterNot { it.propertyListingId == f.propertyListingId }; onRemoved() } catch (e: Exception) { error = "Couldn't remove this saved property. Check your connection and try again." } } }) } } } }
}
