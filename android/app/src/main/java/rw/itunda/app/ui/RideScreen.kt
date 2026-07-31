package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
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
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RequestRideTripRequest
import rw.itunda.core.network.RideDriverDto
import rw.itunda.core.network.RideDriverRatingResponse
import rw.itunda.core.network.RideStopRequestDto
import rw.itunda.core.network.RideTripDto
import rw.itunda.core.network.RideTripStopDto
import rw.itunda.core.network.SetRideDriverAvailabilityRequest
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
                .clip(RoundedCornerShape(10.dp)).background(TossCardSoft).padding(4.dp),
        ) {
            listOf(RideTab.RIDE to "Get a ride", RideTab.DRIVE to "Drive").forEach { (value, label) ->
                val selected = tab == value
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) TossBlue else Color.Transparent)
                        .clickable { tab = value }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) Color.White else TossText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
    val coroutineScope = rememberCoroutineScope()

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

    LaunchedEffect(activeTrip?.id) {
        val id = activeTrip?.id
        activeTripStops = if (id == null) null else try {
            NetworkClient.apiService.getRideTripStops(id).stops.takeIf { it.isNotEmpty() }
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
            item { Text("Your ride", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            item {
                RideTripCard(activeTrip, stops = activeTripStops) {
                    if (activeTrip.status != "IN_PROGRESS") {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.danger)
                                .clickable(enabled = busyTripId != activeTrip.id) { cancelTrip(activeTrip.id) }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (busyTripId == activeTrip.id) "Cancelling…" else "Cancel ride", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Request a ride", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pickupAddress, onValueChange = { pickupAddress = it }, label = { Text("Pickup") },
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TossCardSoft)
                                    .clickable(enabled = !locating) { requestLocation() }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                            ) { Text(if (locating) "…" else "Use my location", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        OutlinedTextField(value = dropoffAddress, onValueChange = { dropoffAddress = it }, label = { Text("Dropoff address") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = dropoffLat, onValueChange = { dropoffLat = it }, label = { Text("Dropoff latitude") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = dropoffLng, onValueChange = { dropoffLng = it }, label = { Text("Dropoff longitude") }, modifier = Modifier.weight(1f))
                        }
                        stops.forEachIndexed { index, stop ->
                            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = TossCardSoft), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Stop ${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossSecondary)
                                        Text("Remove", fontSize = 12.sp, color = Ids.colors.danger, modifier = Modifier.clickable { stops.removeAt(index) })
                                    }
                                    OutlinedTextField(value = stop.address, onValueChange = { stops[index] = stop.copy(address = it) }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = stop.lat, onValueChange = { stops[index] = stop.copy(lat = it) }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
                                        OutlinedTextField(value = stop.lng, onValueChange = { stops[index] = stop.copy(lng = it) }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        if (stops.size < 3) {
                            Text(
                                "+ Add a stop", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                                modifier = Modifier.clickable { stops.add(StopInput()) },
                            )
                        }
                        OutlinedTextField(
                            value = scheduleHours, onValueChange = { scheduleHours = it },
                            label = { Text("Schedule for later (hours from now, optional)") }, modifier = Modifier.fillMaxWidth(),
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(TossBlue)
                                .clickable(enabled = !requesting && pickupLat != null && dropoffAddress.isNotBlank()) { requestRide() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (requesting) "Requesting…" else if (scheduleHours.toDoubleOrNull() != null) "Schedule ride" else "Request ride", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (pastTrips.isNotEmpty()) {
            item { Text("Past rides", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
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

// Real Kakao T-style post-trip driver rating (item 213) -- see this file's own doc
// comment. Simple 5-star tap-to-rate row, matching bank-mfe's RideReviewPrompt shape.
@Composable
private fun RideReviewRow(busy: Boolean, onSubmit: (Int, String) -> Unit) {
    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Rate your driver", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossText)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (n in 1..5) {
                Text(
                    "★", fontSize = 20.sp, color = if (n <= rating) Color(0xFFFFC107) else TossSecondary,
                    modifier = Modifier.clickable { rating = n },
                )
            }
        }
        if (rating > 0) {
            OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment (optional)") }, modifier = Modifier.fillMaxWidth())
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossBlue)
                    .clickable(enabled = !busy) { onSubmit(rating, comment) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (busy) "Submitting…" else "Submit rating", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
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
                error = superAppErrorMessage(e)
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
            !loaded -> item { Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {} }
            current == null -> item {
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Drive with Itunda", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Earn a real fare for every trip you complete, paid straight to your wallet.",
                            color = TossSecondary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TossBlue)
                                .clickable(enabled = !registering) { register() }.padding(horizontal = 24.dp, vertical = 14.dp),
                        ) { Text(if (registering) "Registering…" else "Become a driver", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            else -> {
                item {
                    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(if (current.available) "You're online" else "You're offline", color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    if (current.available) "Visible for new trip requests" else "Go online to see trip requests",
                                    color = TossSecondary, fontSize = 12.sp,
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
                                    .background(if (current.available) Ids.colors.danger else TossBlue)
                                    .clickable { toggleAvailable() }.padding(horizontal = 16.dp, vertical = 10.dp),
                            ) { Text(if (current.available) "Go offline" else "Go online", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        }
                    }
                }
                if (activeDriverTrips.isNotEmpty()) {
                    item { Text("Active trips", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(activeDriverTrips, key = { it.id }) { trip ->
                        val tripStops = activeTripStops[trip.id]
                        val nextStop = tripStops?.firstOrNull { it.arrivedAt == null }
                        RideTripCard(trip, stops = tripStops) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (trip.status == "IN_PROGRESS" && nextStop != null) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossCardSoft)
                                            .clickable(enabled = busyTripId != trip.id) { arriveAtStop(trip.id) }.padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(if (busyTripId == trip.id) "…" else "Arrived at ${nextStop.address}", color = TossText, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                }
                                val nextAction: (suspend (String) -> RideTripDto)? = when (trip.status) {
                                    "DRIVER_ASSIGNED" -> { id -> NetworkClient.apiService.startRideTrip(id).trip }
                                    "IN_PROGRESS" -> { id -> NetworkClient.apiService.completeRideTrip(id).trip }
                                    else -> null
                                }
                                nextAction?.let { action ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossBlue)
                                            .clickable(enabled = busyTripId != trip.id) { act(trip.id, action) }.padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            if (busyTripId == trip.id) "…" else if (trip.status == "DRIVER_ASSIGNED") "Start trip" else "Complete trip",
                                            color = Color.White, fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (availableTrips.isNotEmpty()) {
                    item { Text("Available trips", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(availableTrips, key = { it.id }) { trip ->
                        RideTripCard(trip) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(TossCardSoft)
                                        .clickable(enabled = busyTripId != trip.id) { act(trip.id) { id -> NetworkClient.apiService.declineRideTrip(id).trip } }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Decline", color = TossText, fontWeight = FontWeight.Bold) }
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(TossBlue)
                                        .clickable(enabled = busyTripId != trip.id) { act(trip.id) { id -> NetworkClient.apiService.acceptRideTrip(id).trip } }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (busyTripId == trip.id) "…" else "Accept", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                if (pastDriverTrips.isNotEmpty()) {
                    item { Text("Past trips", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(pastDriverTrips, key = { it.id }) { trip -> RideTripCard(trip) }
                }
            }
        }
    }
}

@Composable
private fun RideTripCard(trip: RideTripDto, stops: List<RideTripStopDto>? = null, action: (@Composable () -> Unit)? = null) {
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(trip.pickupAddress, color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            stops?.forEach { stop ->
                Text(
                    "${if (stop.arrivedAt != null) "✓" else "→"} ${stop.address}", fontSize = 12.sp,
                    color = if (stop.arrivedAt != null) TossSecondary else TossText,
                )
            }
            Text("→ ${trip.dropoffAddress}", color = TossSecondary, fontSize = 13.sp)
            val scheduledFor = trip.scheduledFor
            if (scheduledFor != null) {
                Text("🕒 Scheduled for ${scheduledFor.take(16).replace("T", " ")}", color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(rideTripStatusLabel(trip), color = rideTripStatusColor(trip.status), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${formatMoneyRide(trip.fare)} RWF · ${"%.1f".format(trip.distanceKm)} km", color = TossSecondary, fontSize = 12.sp)
            }
            action?.let {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                it()
            }
        }
    }
}

private fun rideTripStatusLabel(trip: RideTripDto): String {
    if (trip.status == "REQUESTED" && trip.scheduledFor != null) return "Scheduled"
    return when (trip.status) {
        "REQUESTED" -> "Finding a driver…"
        "DRIVER_ASSIGNED" -> "Driver assigned"
        "IN_PROGRESS" -> "In progress"
        "COMPLETED" -> "Completed"
        "CANCELLED" -> "Cancelled"
        else -> trip.status
    }
}

@Composable
private fun rideTripStatusColor(status: String): Color = when (status) {
    "COMPLETED" -> Ids.colors.success
    "CANCELLED" -> Ids.colors.danger
    else -> TossBlue
}

private fun formatMoneyRide(value: java.math.BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) rounded.toBigInteger().toString() else "%,.2f".format(rounded)
}
