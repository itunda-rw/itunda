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
import rw.itunda.core.network.RideTripDto
import rw.itunda.core.network.SetRideDriverAvailabilityRequest
import rw.itunda.core.network.UpdateRideDriverLocationRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID

// Real Kakao T-style ride-hailing (rw.itunda.rideshare, real since 2026-07-26) -- first
// Android client for this feature (item 109, found via a fresh matrix scan: bank-mfe
// has had it since the same day, Android/iOS never did). Mirrors bank-mfe's RidesView
// (RIDE/DRIVE toggle) exactly. Honest v1 scoping: manual address/lat/lng entry for
// dropoff (no autocomplete search integration this pass), "Use my location" one-tap
// pickup via the shared rememberRealLocationRequester helper (already proven in the
// Hood feature) for the single most common real passenger flow.
private enum class RideTab { RIDE, DRIVE }

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
    var myTrips by remember { mutableStateOf<List<RideTripDto>?>(null) }
    var requesting by remember { mutableStateOf(false) }
    var busyTripId by remember { mutableStateOf<String?>(null) }
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

    fun requestRide() {
        val lat = pickupLat
        val lng = pickupLng
        val dLat = dropoffLat.toDoubleOrNull()
        val dLng = dropoffLng.toDoubleOrNull()
        if (lat == null || lng == null || dLat == null || dLng == null || dropoffAddress.isBlank()) return
        requesting = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.requestRideTrip(
                    RequestRideTripRequest(pickupAddress, lat, lng, dropoffAddress, dLat, dLng),
                    UUID.randomUUID().toString(),
                )
                dropoffAddress = ""
                dropoffLat = ""
                dropoffLng = ""
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
        if (activeTrip != null) {
            item { Text("Your ride", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            item {
                RideTripCard(activeTrip) {
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
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(TossBlue)
                                .clickable(enabled = !requesting && pickupLat != null && dropoffAddress.isNotBlank()) { requestRide() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (requesting) "Requesting…" else "Request ride", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (pastTrips.isNotEmpty()) {
            item { Text("Past rides", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(pastTrips, key = { it.id }) { trip -> RideTripCard(trip) }
        }
    }
}

@Composable
private fun RideDriverContent() {
    var driver by remember { mutableStateOf<RideDriverDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf(false) }
    var availableTrips by remember { mutableStateOf<List<RideTripDto>>(emptyList()) }
    var myDriverTrips by remember { mutableStateOf<List<RideTripDto>>(emptyList()) }
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
                driver = NetworkClient.apiService.getMyRideDriverProfile().driver
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

    val activeDriverTrips = myDriverTrips.filter { it.status == "DRIVER_ASSIGNED" || it.status == "IN_PROGRESS" }
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
                        RideTripCard(trip) {
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
private fun RideTripCard(trip: RideTripDto, action: (@Composable () -> Unit)? = null) {
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${trip.pickupAddress} → ${trip.dropoffAddress}", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(rideTripStatusLabel(trip.status), color = rideTripStatusColor(trip.status), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${formatMoneyRide(trip.fare)} RWF · ${"%.1f".format(trip.distanceKm)} km", color = TossSecondary, fontSize = 12.sp)
            }
            action?.let {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                it()
            }
        }
    }
}

private fun rideTripStatusLabel(status: String): String = when (status) {
    "REQUESTED" -> "Finding a driver…"
    "DRIVER_ASSIGNED" -> "Driver assigned"
    "IN_PROGRESS" -> "In progress"
    "COMPLETED" -> "Completed"
    "CANCELLED" -> "Cancelled"
    else -> status
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
