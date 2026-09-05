package rw.itunda.feature.ride.impl

import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddRideTrustedContactRequest
import rw.itunda.core.network.MapBookmarkDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RequestRideTripRequest
import rw.itunda.core.network.RideStopRequestDto
import rw.itunda.core.network.RideTripDto
import rw.itunda.core.network.RideTripStopDto
import rw.itunda.core.network.RideTrustedContactDto
import rw.itunda.core.network.SubmitRideReviewRequest
import rw.itunda.core.network.apiErrorCode
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.time.Instant
import java.util.UUID

// Split out of RideScreen.kt (2026-09-05) -- see that file's own doc comment. The
// passenger ("Get a ride") side of the RIDE/DRIVE tab toggle: request/track a trip,
// multi-stop, scheduled rides, trusted contacts, and the review/tip flow after a
// completed trip.
internal data class StopInput(var address: String = "", var lat: String = "", var lng: String = "")

@Composable
internal fun RidePassengerContent() {
    var pickupAddress by remember { mutableStateOf("") }
    var pickupLat by remember { mutableStateOf<Double?>(null) }
    var pickupLng by remember { mutableStateOf<Double?>(null) }
    var locating by remember { mutableStateOf(false) }
    var dropoffAddress by remember { mutableStateOf("") }
    var dropoffLat by remember { mutableStateOf("") }
    var dropoffLng by remember { mutableStateOf("") }
    // Real Uber/Kakao T-style saved-places quick-select (2026-08-23) -- itunda
    // already has a real, backend-synced "map bookmarks" feature (the Maps tab's own
    // star/save, folders/colors and all -- MapsService.addBookmark/getMyBookmarks),
    // never surfaced anywhere in ride booking despite being exactly the real "Home"/
    // "Work" shortcut every real ride-hailing app shows before you type anything.
    // Especially valuable here: unlike bank-mfe's real search-autocomplete dropoff
    // field, this screen has no autocomplete at all -- a rider currently has to know
    // and type the exact GPS coordinates by hand. Fetched once on entering this
    // content, not gated behind any interaction (no focus-driven dropdown mechanism
    // exists on this screen to gate it behind).
    var bookmarks by remember { mutableStateOf<List<MapBookmarkDto>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            bookmarks = NetworkClient.apiService.getMyMapBookmarks().bookmarks
        } catch (_: Exception) {
            // Real, non-critical -- the quick-select row just won't render if this fails.
        }
    }
    // Real Kakao T 예약 호출 (item 212) -- empty means ASAP, unchanged from before. See
    // this file's own doc comment for the real "hours from now" platform scope-down.
    var scheduleHours by remember { mutableStateOf("") }
    // Real Kakao T-style multi-stop rides (item 214) -- up to 3 real extra stops.
    val stops = remember { mutableStateListOf<StopInput>() }
    var myTrips by remember { mutableStateOf<List<RideTripDto>?>(null) }
    var activeTripStops by remember { mutableStateOf<List<RideTripStopDto>?>(null) }
    var requesting by remember { mutableStateOf(false) }
    var busyTripId by remember { mutableStateOf<String?>(null) }
    var reviewedTripIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Real optimistic-hide for TipDriverPrompt -- same pattern bank-mfe's own
    // tippedTripIds establishes.
    var tippedTripIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real Uber Safety "Trusted Contacts" (item 160) -- see ApiService.kt's own doc
    // comment for the full sourced account. First Android client; mirrors bank-mfe's
    // TrustedContactsSection.
    var trustedContacts by remember { mutableStateOf<List<RideTrustedContactDto>?>(null) }
    var addContactPhone by remember { mutableStateOf("") }
    var addContactName by remember { mutableStateOf("") }
    var addingContact by remember { mutableStateOf(false) }
    var removingContactId by remember { mutableStateOf<String?>(null) }
    var contactError by remember { mutableStateOf<String?>(null) }
    var sendingStatus by remember { mutableStateOf(false) }
    var sendStatusResult by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadTrustedContacts() {
        coroutineScope.launch {
            try {
                trustedContacts = NetworkClient.apiService.getRideTrustedContacts().contacts
            } catch (_: Exception) {
                // Non-critical -- the section just stays empty/unloaded this pass.
            }
        }
    }
    LaunchedEffect(Unit) { loadTrustedContacts() }

    fun addTrustedContact() {
        if (addContactPhone.isBlank()) return
        addingContact = true
        contactError = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.addRideTrustedContact(AddRideTrustedContactRequest(addContactPhone.trim(), addContactName.trim()))
                addContactPhone = ""
                addContactName = ""
                loadTrustedContacts()
            } catch (e: HttpException) {
                contactError = superAppErrorMessage(e)
            } catch (e: IOException) {
                contactError = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                addingContact = false
            }
        }
    }

    fun removeTrustedContact(contactId: String) {
        removingContactId = contactId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.removeRideTrustedContact(contactId)
                loadTrustedContacts()
            } catch (e: HttpException) {
                contactError = superAppErrorMessage(e)
            } catch (e: IOException) {
                contactError = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                removingContactId = null
            }
        }
    }

    fun sendStatusToTrustedContacts(tripId: String) {
        sendingStatus = true
        sendStatusResult = null
        coroutineScope.launch {
            try {
                val sentCount = NetworkClient.apiService.sendStatusToRideTrustedContacts(tripId).sentCount
                sendStatusResult = if (sentCount > 0) "Sent to $sentCount trusted contact${if (sentCount == 1) "" else "s"}" else "Add a trusted contact first"
            } catch (e: HttpException) {
                sendStatusResult = superAppErrorMessage(e)
            } catch (e: IOException) {
                sendStatusResult = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                sendingStatus = false
            }
        }
    }

    val requestLocation = rememberRealLocationRequester(
        onLocating = { locating = it },
        onSuccess = { lat, lng -> pickupLat = lat; pickupLng = lng; pickupAddress = "Current location" },
        onError = { error = it },
    )

    fun loadTrips() {
        coroutineScope.launch {
            try {
                myTrips = NetworkClient.apiService.getMyRideTrips().trips
            } catch (_: Exception) {
                // Non-critical -- a poll failure just skips this refresh.
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            loadTrips()
            delay(4000)
        }
    }

    val activeTrip = myTrips?.firstOrNull { it.status == "REQUESTED" || it.status == "DRIVER_ASSIGNED" || it.status == "IN_PROGRESS" }
    val pastTrips = myTrips?.filter { it.status == "COMPLETED" || it.status == "CANCELLED" } ?: emptyList()
    // Real Uber "Verify Your Ride" PIN -- fetched once a driver is assigned so the
    // passenger can read it aloud before pickup.
    var activeTripPin by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeTrip?.id) {
        val id = activeTrip?.id
        activeTripStops = if (id == null) null else try {
            NetworkClient.apiService.getRideTripStops(id).stops.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(activeTrip?.id, activeTrip?.status) {
        activeTripPin = if (activeTrip == null || activeTrip.status == "REQUESTED") null else try {
            NetworkClient.apiService.getRideTripPin(activeTrip.id).pin
        } catch (_: Exception) {
            null
        }
    }

    fun requestRide() {
        val lat = pickupLat
        val lng = pickupLng
        val dLat = dropoffLat.toDoubleOrNull()
        val dLng = dropoffLng.toDoubleOrNull()
        if (lat == null || lng == null || dLat == null || dLng == null || dropoffAddress.isBlank()) return
        val resolvedStops = stops.filter { it.address.isNotBlank() && it.lat.toDoubleOrNull() != null && it.lng.toDoubleOrNull() != null }
            .map { RideStopRequestDto(it.address, it.lat.toDouble(), it.lng.toDouble()) }
        val hours = scheduleHours.toDoubleOrNull()
        val scheduledFor = if (hours != null && hours > 0) Instant.now().plusSeconds((hours * 3600).toLong()).toString() else null
        requesting = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.requestRideTrip(
                    RequestRideTripRequest(
                        pickupAddress, lat, lng, dropoffAddress, dLat, dLng,
                        scheduledFor = scheduledFor, stops = resolvedStops.ifEmpty { null },
                    ),
                    UUID.randomUUID().toString(),
                )
                dropoffAddress = ""
                dropoffLat = ""
                dropoffLng = ""
                scheduleHours = ""
                stops.clear()
                loadTrips()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                requesting = false
            }
        }
    }

    fun cancelTrip(tripId: String) {
        busyTripId = tripId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.cancelRideTrip(tripId)
                loadTrips()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyTripId = null
            }
        }
    }

    fun submitReview(tripId: String, rating: Int, comment: String) {
        busyTripId = tripId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.submitRideReview(tripId, SubmitRideReviewRequest(rating, comment.ifBlank { null }))
                reviewedTripIds = reviewedTripIds + tripId
            } catch (e: HttpException) {
                if (apiErrorCode(e) == "RIDE_TRIP_ALREADY_REVIEWED") {
                    reviewedTripIds = reviewedTripIds + tripId
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyTripId = null
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
        if (activeTrip != null) {
            item { Text("Your ride", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            item {
                RideTripCard(activeTrip, stops = activeTripStops) {
                    if (activeTripPin != null && activeTrip.status == "DRIVER_ASSIGNED") {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand.copy(alpha = 0.1f))
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Tell your driver this PIN before you get in", color = Ids.colors.textSecondary, fontSize = 12.sp)
                                Text(activeTripPin ?: "", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 4.sp)
                            }
                        }
                    }
                    activeTrip.driverId?.let { DriverRatingSection(it) }
                    if (!trustedContacts.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                .pressScaleClickable(enabled = !sendingStatus) { sendStatusToTrustedContacts(activeTrip.id) }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (sendingStatus) "Sending…" else "Send status to trusted contacts", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        sendStatusResult?.let { Text(it, color = Ids.colors.textSecondary, fontSize = 12.sp) }
                    }
                    if (activeTrip.status != "IN_PROGRESS") {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.danger)
                                .pressScaleClickable(enabled = busyTripId != activeTrip.id) { cancelTrip(activeTrip.id) }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (busyTripId == activeTrip.id) "Cancelling…" else "Cancel ride", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        } else {
            item {
                // Real fix (flat-design sweep): dropped the Card wrapper -- the
                // screen's own main content when no ride is active.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Request a ride", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IdsTextField(value = pickupAddress, onValueChange = { pickupAddress = it }, label = "Pickup", modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                    .pressScaleClickable(enabled = !locating) { requestLocation() }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                            ) { Text(if (locating) "…" else "Use my location", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        IdsTextField(value = dropoffAddress, onValueChange = { dropoffAddress = it }, label = "Dropoff address", modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IdsTextField(value = dropoffLat, onValueChange = { dropoffLat = it }, label = "Dropoff latitude", modifier = Modifier.weight(1f))
                            IdsTextField(value = dropoffLng, onValueChange = { dropoffLng = it }, label = "Dropoff longitude", modifier = Modifier.weight(1f))
                        }
                        if (bookmarks.isNotEmpty()) {
                            Text("Saved places", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                bookmarks.forEach { b ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Ids.colors.surfaceSoft)
                                            .pressScaleClickable {
                                                dropoffAddress = b.displayName
                                                dropoffLat = b.latitude.toString()
                                                dropoffLng = b.longitude.toString()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                                .background(runCatching { Color(android.graphics.Color.parseColor(b.color)) }.getOrDefault(Ids.colors.brand)),
                                        )
                                        Text(b.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                        stops.forEachIndexed { index, stop ->
                            // Real fix (flat-design sweep): dropped the per-row Card --
                            // a stop list separates entries with spacing alone.
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Stop ${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary)
                                        Text("Remove", fontSize = 12.sp, color = Ids.colors.danger, modifier = Modifier.pressScaleClickable { stops.removeAt(index) })
                                    }
                                    IdsTextField(value = stop.address, onValueChange = { stops[index] = stop.copy(address = it) }, label = "Address", modifier = Modifier.fillMaxWidth())
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IdsTextField(value = stop.lat, onValueChange = { stops[index] = stop.copy(lat = it) }, label = "Latitude", modifier = Modifier.weight(1f))
                                        IdsTextField(value = stop.lng, onValueChange = { stops[index] = stop.copy(lng = it) }, label = "Longitude", modifier = Modifier.weight(1f))
                                    }
                            }
                        }
                        if (stops.size < 3) {
                            Text(
                                "+ Add a stop", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                                modifier = Modifier.pressScaleClickable { stops.add(StopInput()) },
                            )
                        }
                        IdsTextField(value = scheduleHours, onValueChange = { scheduleHours = it }, label = "Schedule for later (hours from now, optional)", modifier = Modifier.fillMaxWidth())
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.brand)
                                .pressScaleClickable(enabled = !requesting && pickupLat != null && dropoffAddress.isNotBlank()) { requestRide() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (requesting) "Requesting…" else if (scheduleHours.toDoubleOrNull() != null) "Schedule ride" else "Request ride", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            TrustedContactsSection(
                contacts = trustedContacts,
                phone = addContactPhone, onPhoneChange = { addContactPhone = it },
                name = addContactName, onNameChange = { addContactName = it },
                adding = addingContact, onAdd = { addTrustedContact() },
                removingContactId = removingContactId, onRemove = { removeTrustedContact(it) },
                error = contactError,
            )
        }
        if (pastTrips.isNotEmpty()) {
            item { Text("Past rides", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(pastTrips, key = { it.id }) { trip ->
                RideTripCard(trip) {
                    if (trip.status == "COMPLETED" && trip.driverId != null && trip.id !in reviewedTripIds) {
                        RideReviewRow(busy = busyTripId == trip.id) { rating, comment -> submitReview(trip.id, rating, comment) }
                    }
                    if (trip.status == "COMPLETED" && trip.driverId != null && trip.tipAmount == null && trip.id !in tippedTripIds) {
                        TipDriverPrompt(tripId = trip.id, onTipped = { tippedTripIds = tippedTripIds + trip.id })
                    }
                }
            }
        }
    }
}
