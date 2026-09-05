package rw.itunda.feature.marketplace.impl

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.itundaface.CameraGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.AddKeywordAlertRequest
import rw.itunda.core.network.CreateListingRequest
import rw.itunda.core.network.FavoriteListingDto
import rw.itunda.core.network.KeywordAlertDto
import rw.itunda.core.network.KeywordAlertQuietHoursDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetKeywordAlertQuietHoursRequest
import rw.itunda.core.network.superAppErrorMessage

// Real fix (2026-08-26): split out of MarketplaceScreen.kt once that file grew
// past its file-size-lint baseline. Three self-contained sub-screens (wishlist,
// keyword alerts, new listing form) only rendered inside MarketplaceContent's own
// sheet/tab flow. Same package, so zero import changes anywhere.

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
        }
    }
}

// Real 당근마켓-style Keyword Alert -- first Android client for this feature (item 115,
// found via a content-grep sweep confirming zero client anywhere; bank-mfe ported it
// the same day as item 114). Real, published Karrot 30-keyword-per-user cap enforced
// server-side, surfaced via the backend's own KEYWORD_ALERT_CAP_REACHED error.
@Composable
internal fun KeywordAlertsView() {
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

    // Real fix (2026-08-24, flat-design sweep): dropped all 3 Card wrappers in this
    // function -- add-alert form (lone), the alert list (an entity list a user
    // manages, no divider, matching GroupAccountScreen), and Quiet hours (its own
    // bold title already marks the section boundary, no divider needed)
    // (docs/UI_UX_GUIDELINES.md §10).
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IdsTextField(
                value = keyword, onValueChange = { keyword = it }, label = "Alert me for (e.g. iPhone 15)",
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            ListingActionButton(if (adding) "…" else "Add", adding, filled = true) { addAlert() }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
        when {
            alerts == null -> SkeletonBlock()
            alerts!!.isEmpty() -> EmptyState("No keyword alerts yet -- add one to get notified when a matching listing is posted.", icon = IdsIcons.Bell)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alerts!!.forEach { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(a.keyword, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        ListingActionButton(if (removingId == a.id) "Removing…" else "Remove", removingId == a.id) { removeAlert(a.id) }
                    }
                }
            }
        }
        if (quietHoursLoaded) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
internal fun NewListingForm(onCreated: () -> Unit, onCancel: () -> Unit) {
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
    val vehicleState = rememberVehicleListingState()
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

    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- this
    // screen's own main content, a lone form (docs/UI_UX_GUIDELINES.md §10).
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    .pressScaleClickable(enabled = !uploadingPhoto) { pickPhoto.launch("image/*") },
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CameraGlyph(size = 13.dp)
                        Text("Add a photo (optional)", color = Ids.colors.textSecondary, fontSize = 13.sp)
                    }
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
            VehicleListingFieldsSection(vehicleState)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ids.colors.surfaceSoft)
                    .pressScaleClickable(enabled = !locating) { if (shareLocation) shareLocation = false else requestLocation() }
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
                        .pressScaleClickable(enabled = !submitting && !uploadingPhoto) {
                            val priceValue = price.toDoubleOrNull()
                            if (title.isBlank() || description.isBlank() || category.isBlank() || priceValue == null || priceValue <= 0) {
                                error = "Fill in every field with a real price."
                                return@pressScaleClickable
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
                                            vehicleMileageKm = vehicleState.mileageKm.toIntOrNull().takeIf { vehicleState.isVehicle },
                                            vehicleInsuranceClaimCount = vehicleState.insuranceClaimCount.toIntOrNull().takeIf { vehicleState.isVehicle },
                                            vehicleIsLeaseTakeover = vehicleState.isVehicle && vehicleState.isLeaseTakeover,
                                            leaseTotalAcquisitionCost = vehicleState.leaseTotalAcquisitionCost.toDoubleOrNull().takeIf { vehicleState.isLeaseTakeover },
                                            leaseRemainingMonths = vehicleState.leaseRemainingMonths.toIntOrNull().takeIf { vehicleState.isLeaseTakeover },
                                            leaseTotalMonths = vehicleState.leaseTotalMonths.toIntOrNull().takeIf { vehicleState.isLeaseTakeover },
                                            leaseMonthlyPayment = vehicleState.leaseMonthlyPayment.toDoubleOrNull().takeIf { vehicleState.isLeaseTakeover },
                                            leaseSubsidyAmount = vehicleState.leaseSubsidyAmount.toDoubleOrNull().takeIf { vehicleState.isLeaseTakeover },
                                            leaseReturnFee = vehicleState.leaseReturnFee.toDoubleOrNull().takeIf { vehicleState.isLeaseTakeover },
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
