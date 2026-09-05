package rw.itunda.feature.ride.impl

import androidx.compose.foundation.background
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.formatMoney
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RegisterRideDriverRequest
import rw.itunda.core.network.RideDailyEarnings
import rw.itunda.core.network.RideDriverDto
import rw.itunda.core.network.RideDriverRatingResponse
import rw.itunda.core.network.RideTripDto
import rw.itunda.core.network.RideTripStopDto
import rw.itunda.core.network.SetRideDriverAvailabilityRequest
import rw.itunda.core.network.SetRideDriverDestinationRequest
import rw.itunda.core.network.StartRideTripRequest
import rw.itunda.core.network.UpdateRideDriverLocationRequest
import rw.itunda.core.network.apiErrorCode
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Split out of RideScreen.kt (2026-09-05) -- see that file's own doc comment. The
// driver ("Drive") side of the RIDE/DRIVE tab toggle: registration, going online/
// offline, the Uber-style Destination Filter, a weekly earnings report, and the real
// accept/decline/start/complete trip lifecycle.
@Composable
internal fun RideDriverContent() {
    var driver by remember { mutableStateOf<RideDriverDto?>(null) }
    var driverRating by remember { mutableStateOf<RideDriverRatingResponse?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf(false) }
    // Real gap found live (2026-08-31, market-readiness audit) -- see backend
    // RideDriverService.kt's own doc comment. An honest, self-declared informational
    // text field, not a real license-verification gate this backend has no path to
    // check.
    var licenseNumberInput by remember { mutableStateOf("") }
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
                driver = if (apiErrorCode(e) == "RIDE_DRIVER_NOT_REGISTERED") null else driver
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
                driver = NetworkClient.apiService.registerAsRideDriver(
                    RegisterRideDriverRequest(licenseNumberInput),
                ).driver
            } catch (e: HttpException) {
                // Real gap found live (Toss-style error-handling audit, 2026-08-30):
                // same register-once shape as Eats' own RIDER_ALREADY_REGISTERED,
                // apparently missed when that one was fixed -- a double-tap or a
                // second device registering first isn't really a failure.
                if (apiErrorCode(e) == "RIDE_DRIVER_ALREADY_REGISTERED") {
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Earn a real fare for every trip you complete, paid straight to your account.",
                            color = Ids.colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        IdsTextField(
                            value = licenseNumberInput,
                            onValueChange = { licenseNumberInput = it },
                            label = "Driver's license number",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (licenseNumberInput.isNotBlank()) Ids.colors.brand else Ids.colors.divider)
                                .pressScaleClickable(enabled = !registering && licenseNumberInput.isNotBlank()) { register() }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
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
                                        "★ ${String.format(Locale.US, "%.1f", rating.average ?: 0.0)} (${rating.count} rating${if (rating.count == 1L) "" else "s"})",
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
                            Spacer(modifier = Modifier.height(4.dp))
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
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    IdsTextField(value = destinationLat, onValueChange = { destinationLat = it }, label = "Destination latitude", modifier = Modifier.weight(1f))
                                    IdsTextField(value = destinationLng, onValueChange = { destinationLng = it }, label = "Destination longitude", modifier = Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(10.dp))
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
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Trips", color = Ids.colors.textSecondary, fontSize = 11.sp)
                                        Text("${weekEarnings.sumOf { it.tripCount }}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                    Column {
                                        Text("Gross fare", color = Ids.colors.textSecondary, fontSize = 11.sp)
                                        // Real fix (2026-08-26, same comma-formatting sweep as formatMoney's own
                                        // doc comment) -- was raw BigDecimal interpolation with no formatting at all.
                                        Text("${formatMoney(weekEarnings.sumOf { it.grossFare })} RWF", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                    Column {
                                        Text("Net earnings", color = Ids.colors.textSecondary, fontSize = 11.sp)
                                        Text("${formatMoney(weekEarnings.sumOf { it.netEarnings })} RWF", color = Ids.colors.success, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                                    OutlinedTextField(
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
                                        .pressScaleClickable(enabled = busyTripId != trip.id) { act(trip.id) { id -> NetworkClient.apiService.acceptRideTrip(id, java.util.UUID.randomUUID().toString()).trip } }
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
