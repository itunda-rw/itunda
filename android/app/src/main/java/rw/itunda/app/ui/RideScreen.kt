package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
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
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
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
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.itundaface.ClockGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddRideTrustedContactRequest
import rw.itunda.core.network.MapBookmarkDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RequestRideTripRequest
import rw.itunda.core.network.RideDailyEarnings
import rw.itunda.core.network.RideDriverDto
import rw.itunda.core.network.RideDriverRatingResponse
import rw.itunda.core.network.RideStopRequestDto
import rw.itunda.core.network.RideTripDto
import rw.itunda.core.network.RideTripReviewDto
import rw.itunda.core.network.RideTripStopDto
import rw.itunda.core.network.RideTrustedContactDto
import rw.itunda.core.network.SetRideDriverAvailabilityRequest
import rw.itunda.core.network.SetRideDriverDestinationRequest
import rw.itunda.core.network.StartRideTripRequest
import rw.itunda.core.network.SubmitRideReviewRequest
import rw.itunda.core.network.UpdateRideDriverLocationRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.time.Instant
import java.util.UUID

// Real Kakao T-style ride-hailing (rw.itunda.rideshare, real since 2026-07-26) -- first
// Android client for this feature (item 109, found via a fresh matrix scan: bank-mfe
// has had it since the same day, Android/iOS never did). Mirrors bank-mfe's RidesView
// (RIDE/DRIVE toggle) exactly. Honest v1 scoping: manual address/lat/lng entry for
// dropoff (no autocomplete search integration this pass), "Use my location" one-tap
// pickup via the shared rememberRealLocationRequester helper (already proven in the
// Hood feature) for the single most common real passenger flow.
//
// **Real Kakao T 예약 호출 (scheduled ride booking, item 212)/multi-stop (item 214)/
// driver rating (item 213) added 2026-07-31** -- first Android client for these three,
// backend and bank-mfe real since the same day. Honest platform-specific scope-down:
// bank-mfe's own scheduling UI uses a real `datetime-local` picker; this app has no
// existing date/time-picker precedent anywhere and adding one is a real, separate scope
// increase, so scheduling here is "N hours from now" instead of picking an exact real
// calendar date/time -- itunda's own honest simplification for this platform, not a
// silently-dropped feature (the real backend contract is identical either way).
private enum class RideTab { RIDE, DRIVE }

private data class StopInput(var address: String = "", var lat: String = "", var lng: String = "")

@Composable
fun RideScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(RideTab.RIDE) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Rides", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            listOf(RideTab.RIDE to "Get a ride", RideTab.DRIVE to "Drive").forEach { (value, label) ->
                val selected = tab == value
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Ids.colors.brand else Color.Transparent)
                        .pressScaleClickable { tab = value }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (tab == RideTab.RIDE) RidePassengerContent() else RideDriverContent()
    }
}

@Composable
private fun RidePassengerContent() {
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
                if (rw.itunda.core.network.apiErrorCode(e) == "RIDE_TRIP_ALREADY_REVIEWED") {
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
                }
            }
        }
    }
}

@Composable
private fun RideDriverContent() {
    var driver by remember { mutableStateOf<RideDriverDto?>(null) }
    var driverRating by remember { mutableStateOf<RideDriverRatingResponse?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf(false) }
    var availableTrips by remember { mutableStateOf<List<RideTripDto>>(emptyList()) }
    var myDriverTrips by remember { mutableStateOf<List<RideTripDto>>(emptyList()) }
    var activeTripStops by remember { mutableStateOf<Map<String, List<RideTripStopDto>>>(emptyMap()) }
    var busyTripId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real Uber "Verify Your Ride" PIN -- what the driver has typed in for each real
    // active trip, keyed by trip id.
    var startPinInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Real Uber "Destination Filter" + earnings report (uncalled-endpoint sweep
    // follow-up, item 246) -- both real, fully-built backend endpoints found with
    // zero client anywhere on any platform before bank-mfe's own 2026-08-21 port.
    // Manual lat/lng entry, matching this same screen's existing dropoff-address
    // fields exactly -- no real geocoding-search component is reachable from
    // :app (AddressAutocompleteField lives `internal` inside
    // :features:eats:impl), so this stays consistent with the sibling input
    // already on this screen rather than looking more polished than it.
    var destinationLat by remember { mutableStateOf("") }
    var destinationLng by remember { mutableStateOf("") }
    var destinationBusy by remember { mutableStateOf(false) }
    var earnings by remember { mutableStateOf<List<RideDailyEarnings>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val requestLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            coroutineScope.launch {
                try {
                    driver = NetworkClient.apiService.updateRideDriverLocation(UpdateRideDriverLocationRequest(lat, lng)).driver
                } catch (_: Exception) {
                    // Non-critical -- a location-share failure doesn't block going online.
                }
            }
        },
        onError = {},
    )

    fun loadDriver() {
        coroutineScope.launch {
            try {
                val d = NetworkClient.apiService.getMyRideDriverProfile().driver
                driver = d
                try {
                    driverRating = NetworkClient.apiService.getRideDriverRating(d.id)
                } catch (_: Exception) {
                    // Non-critical -- rating just won't show this poll.
                }
            } catch (e: HttpException) {
                driver = if (rw.itunda.core.network.apiErrorCode(e) == "RIDE_DRIVER_NOT_REGISTERED") null else driver
            } catch (_: Exception) {
                // Leave driver state as-is; next poll may recover.
            }
            loaded = true
        }
    }
    LaunchedEffect(Unit) { loadDriver() }

    fun loadTrips() {
        coroutineScope.launch {
            try {
                availableTrips = NetworkClient.apiService.getAvailableRideTrips().trips
                myDriverTrips = NetworkClient.apiService.getMyRideDriverTrips().trips
            } catch (_: Exception) {
                // Non-critical -- a poll failure just skips this refresh.
            }
        }
    }
    LaunchedEffect(driver?.id) {
        if (driver == null) return@LaunchedEffect
        while (true) {
            loadTrips()
            delay(4000)
        }
    }

    val activeDriverTrips = myDriverTrips.filter { it.status == "DRIVER_ASSIGNED" || it.status == "IN_PROGRESS" }

    LaunchedEffect(activeDriverTrips.map { it.id }) {
        activeDriverTrips.forEach { trip ->
            try {
                val stops = NetworkClient.apiService.getRideTripStops(trip.id).stops
                if (stops.isNotEmpty()) activeTripStops = activeTripStops + (trip.id to stops)
            } catch (_: Exception) {
                // Non-critical -- stops just won't show for this trip this poll.
            }
        }
    }

    fun register() {
        registering = true
        coroutineScope.launch {
            try {
                driver = NetworkClient.apiService.registerAsRideDriver().driver
            } catch (e: HttpException) {
                // Real gap found live (Toss-style error-handling audit, 2026-08-30):
                // same register-once shape as Eats' own RIDER_ALREADY_REGISTERED,
                // apparently missed when that one was fixed -- a double-tap or a
                // second device registering first isn't really a failure.
                if (rw.itunda.core.network.apiErrorCode(e) == "RIDE_DRIVER_ALREADY_REGISTERED") {
                    loadDriver()
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                registering = false
            }
        }
    }

    fun toggleAvailable() {
        val current = driver ?: return
        coroutineScope.launch {
            try {
                val updated = NetworkClient.apiService.setRideDriverAvailability(SetRideDriverAvailabilityRequest(!current.available)).driver
                driver = updated
                if (updated.available) requestLocation()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    LaunchedEffect(driver?.id) {
        if (driver == null) return@LaunchedEffect
        try {
            earnings = NetworkClient.apiService.getMyRideEarnings().days
        } catch (_: Exception) {
            // Non-critical -- the earnings card just stays hidden this pass.
        }
    }

    fun setDestination() {
        val lat = destinationLat.toDoubleOrNull()
        val lng = destinationLng.toDoubleOrNull()
        if (lat == null || lng == null) return
        destinationBusy = true
        coroutineScope.launch {
            try {
                driver = NetworkClient.apiService.setRideDriverDestination(SetRideDriverDestinationRequest(lat, lng)).driver
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                destinationBusy = false
            }
        }
    }

    fun clearDestination() {
        destinationBusy = true
        coroutineScope.launch {
            try {
                driver = NetworkClient.apiService.clearRideDriverDestination().driver
                destinationLat = ""
                destinationLng = ""
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                destinationBusy = false
            }
        }
    }

    fun act(tripId: String, action: suspend (String) -> RideTripDto) {
        busyTripId = tripId
        coroutineScope.launch {
            try {
                action(tripId)
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

    fun arriveAtStop(tripId: String) {
        busyTripId = tripId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.arriveAtRideStop(tripId)
                val stops = NetworkClient.apiService.getRideTripStops(tripId).stops
                activeTripStops = activeTripStops + (tripId to stops)
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyTripId = null
            }
        }
    }

    val pastDriverTrips = myDriverTrips.filter { it.status == "COMPLETED" || it.status == "CANCELLED" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
        val current = driver
        when {
            !loaded -> item { SkeletonBlock(height = 120.dp) }
            current == null -> item {
                // Real fix (flat-design sweep): dropped the Card wrapper -- the
                // screen's own main content when not yet a driver.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Drive with Itunda", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Earn a real fare for every trip you complete, paid straight to your account.",
                            color = Ids.colors.textSecondary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                .pressScaleClickable(enabled = !registering) { register() }.padding(horizontal = 24.dp, vertical = 14.dp),
                        ) { Text(if (registering) "Registering…" else "Become a driver", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            else -> {
                item {
                    // Real fix (flat-design sweep): dropped the Card wrapper -- a
                    // section on an otherwise-flat driver screen.
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(if (current.available) "You're online" else "You're offline", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    if (current.available) "Visible for new trip requests" else "Go online to see trip requests",
                                    color = Ids.colors.textSecondary, fontSize = 12.sp,
                                )
                                val rating = driverRating
                                if (rating != null && rating.count > 0) {
                                    Text(
                                        "★ ${"%.1f".format(rating.average ?: 0.0)} (${rating.count} rating${if (rating.count == 1L) "" else "s"})",
                                        color = Color(0xFFFFC107), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (current.available) Ids.colors.danger else Ids.colors.brand)
                                    .pressScaleClickable { toggleAvailable() }.padding(horizontal = 16.dp, vertical = 10.dp),
                            ) { Text(if (current.available) "Go offline" else "Go online", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
                // Real Uber "Destination Filter" -- see RideDriverDto.destinationLatitude's
                // own doc comment.
                item {
                    // Real fix (flat-design sweep): dropped the Card wrapper -- a
                    // section on an otherwise-flat driver screen.
                    Column {
                            Text("Heading somewhere?", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                            if (current.destinationLatitude != null) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Only offered trips heading your way.", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                                            .pressScaleClickable(enabled = !destinationBusy) { clearDestination() }.padding(horizontal = 14.dp, vertical = 8.dp),
                                    ) { Text(if (destinationBusy) "…" else "Clear", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                }
                            } else {
                                Text(
                                    "Set a destination and you'll only be offered trips heading that direction.",
                                    color = Ids.colors.textSecondary, fontSize = 12.sp,
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    IdsTextField(value = destinationLat, onValueChange = { destinationLat = it }, label = "Destination latitude", modifier = Modifier.weight(1f))
                                    IdsTextField(value = destinationLng, onValueChange = { destinationLng = it }, label = "Destination longitude", modifier = Modifier.weight(1f))
                                }
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(if (destinationLat.toDoubleOrNull() != null && destinationLng.toDoubleOrNull() != null) Ids.colors.brand else Ids.colors.surfaceSoft)
                                        .pressScaleClickable(enabled = !destinationBusy && destinationLat.toDoubleOrNull() != null && destinationLng.toDoubleOrNull() != null) { setDestination() }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (destinationBusy) "Setting…" else "Set destination", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                    }
                }
                // Real Uber Driver-style earnings report -- see ApiService's own
                // getMyRideEarnings doc comment. Hidden entirely for a fresh driver with
                // zero completed trips rather than showing an empty/zero state.
                val weekEarnings = earnings
                if (weekEarnings != null && weekEarnings.isNotEmpty()) {
                    item {
                        // Real fix (flat-design sweep): dropped the Card wrapper -- a
                        // section on an otherwise-flat driver screen.
                        Column {
                                Text("This week", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Trips", color = Ids.colors.textSecondary, fontSize = 11.sp)
                                        Text("${weekEarnings.sumOf { it.tripCount }}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                    Column {
                                        Text("Gross fare", color = Ids.colors.textSecondary, fontSize = 11.sp)
                                        // Real fix (2026-08-26, same comma-formatting sweep as formatMoneyRide's own
                                        // doc comment) -- was raw BigDecimal interpolation with no formatting at all.
                                        Text("${formatMoneyRide(weekEarnings.sumOf { it.grossFare })} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                    Column {
                                        Text("Net earnings", color = Ids.colors.textSecondary, fontSize = 11.sp)
                                        Text("${formatMoneyRide(weekEarnings.sumOf { it.netEarnings })} RWF", color = Ids.colors.success, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                }
                        }
                    }
                }
                if (activeDriverTrips.isNotEmpty()) {
                    item { Text("Active trips", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(activeDriverTrips, key = { it.id }) { trip ->
                        val tripStops = activeTripStops[trip.id]
                        val nextStop = tripStops?.firstOrNull { it.arrivedAt == null }
                        RideTripCard(trip, stops = tripStops) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (trip.status == "IN_PROGRESS" && nextStop != null) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                            .pressScaleClickable(enabled = busyTripId != trip.id) { arriveAtStop(trip.id) }.padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(if (busyTripId == trip.id) "…" else "Arrived at ${nextStop.address}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                }
                                if (trip.status == "DRIVER_ASSIGNED") {
                                    val pin = startPinInputs[trip.id] ?: ""
                                    androidx.compose.material3.OutlinedTextField(
                                        value = pin,
                                        onValueChange = { v -> startPinInputs = startPinInputs + (trip.id to v.filter { it.isDigit() }.take(4)) },
                                        placeholder = { Text("Ask passenger for their 4-digit PIN") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                    )
                                    Box(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                            .background(if (pin.length == 4) Ids.colors.brand else Ids.colors.surfaceSoft)
                                            .pressScaleClickable(enabled = busyTripId != trip.id && pin.length == 4) {
                                                act(trip.id) { id -> NetworkClient.apiService.startRideTrip(id, StartRideTripRequest(pin)).trip }
                                            }.padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(if (busyTripId == trip.id) "…" else "Start trip", color = Color.White, fontWeight = FontWeight.Bold) }
                                } else if (trip.status == "IN_PROGRESS") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                            .pressScaleClickable(enabled = busyTripId != trip.id) { act(trip.id) { id -> NetworkClient.apiService.completeRideTrip(id).trip } }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(if (busyTripId == trip.id) "…" else "Complete trip", color = Color.White, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
                if (availableTrips.isNotEmpty()) {
                    item { Text("Available trips", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(availableTrips, key = { it.id }) { trip ->
                        RideTripCard(trip) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                        .pressScaleClickable(enabled = busyTripId != trip.id) { act(trip.id) { id -> NetworkClient.apiService.declineRideTrip(id).trip } }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Decline", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold) }
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                        .pressScaleClickable(enabled = busyTripId != trip.id) { act(trip.id) { id -> NetworkClient.apiService.acceptRideTrip(id).trip } }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (busyTripId == trip.id) "…" else "Accept", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                if (pastDriverTrips.isNotEmpty()) {
                    item { Text("Past trips", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(pastDriverTrips, key = { it.id }) { trip -> RideTripCard(trip) }
                }
            }
        }
    }
}

