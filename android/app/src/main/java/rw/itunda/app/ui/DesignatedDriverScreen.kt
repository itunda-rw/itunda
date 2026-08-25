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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.DesignatedDriverDto
import rw.itunda.core.network.DesignatedDriverTripDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RegisterDesignatedDriverRequest
import rw.itunda.core.network.RequestDesignatedDriverTripRequest
import rw.itunda.core.network.SetDesignatedDriverAvailabilityRequest
import rw.itunda.core.network.UpdateDesignatedDriverLocationRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real Kakao T 대리운전 (designated driver, item 221) -- a professional driver comes to
// the customer's location and drives the CUSTOMER'S OWN CAR home for them, distinct
// from RideScreen.kt's ride-hailing (driver uses their own vehicle). bank-mfe already
// has this; this is the first Android client, mirroring its Get-a-driver/Drive toggle
// exactly. Same honest v1 scope-down RideScreen.kt already established for this app:
// manual dropoff address/lat/lng entry (no autocomplete search integration), one-tap
// "Use my location" for pickup via the shared rememberRealLocationRequester helper.
private enum class DesignatedDriverTab { REQUEST, DRIVE }

@Composable
fun DesignatedDriverScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(DesignatedDriverTab.REQUEST) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Designated driver", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            listOf(DesignatedDriverTab.REQUEST to "Get a driver", DesignatedDriverTab.DRIVE to "Drive").forEach { (value, label) ->
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
        if (tab == DesignatedDriverTab.REQUEST) DesignatedDriverRequestContent() else DesignatedDriverDriveContent()
    }
}

@Composable
private fun DesignatedDriverRequestContent() {
    var pickupAddress by remember { mutableStateOf("") }
    var pickupLat by remember { mutableStateOf<Double?>(null) }
    var pickupLng by remember { mutableStateOf<Double?>(null) }
    var locating by remember { mutableStateOf(false) }
    var dropoffAddress by remember { mutableStateOf("") }
    var dropoffLat by remember { mutableStateOf("") }
    var dropoffLng by remember { mutableStateOf("") }
    var vehicleMake by remember { mutableStateOf("") }
    var vehicleModel by remember { mutableStateOf("") }
    var vehiclePlate by remember { mutableStateOf("") }
    var myTrips by remember { mutableStateOf<List<DesignatedDriverTripDto>?>(null) }
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
                myTrips = NetworkClient.apiService.getMyDesignatedDriverTrips().trips
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

    val activeTrip = myTrips?.firstOrNull { it.status == "REQUESTED" || it.status == "ACCEPTED" || it.status == "DRIVING" }
    val pastTrips = myTrips?.filter { it.status == "COMPLETED" || it.status == "CANCELLED" } ?: emptyList()

    fun requestTrip() {
        val lat = pickupLat
        val lng = pickupLng
        val dLat = dropoffLat.toDoubleOrNull()
        val dLng = dropoffLng.toDoubleOrNull()
        if (lat == null || lng == null || dLat == null || dLng == null || dropoffAddress.isBlank() ||
            vehicleMake.isBlank() || vehicleModel.isBlank() || vehiclePlate.isBlank()
        ) return
        requesting = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.requestDesignatedDriverTrip(
                    java.util.UUID.randomUUID().toString(),
                    RequestDesignatedDriverTripRequest(
                        pickupAddress, lat, lng, dropoffAddress, dLat, dLng,
                        vehicleMake.trim(), vehicleModel.trim(), vehiclePlate.trim(),
                    ),
                )
                dropoffAddress = ""
                dropoffLat = ""
                dropoffLng = ""
                vehicleMake = ""
                vehicleModel = ""
                vehiclePlate = ""
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
                NetworkClient.apiService.cancelDesignatedDriverTrip(tripId)
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
            item { Text("Your driver", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            item {
                DesignatedDriverTripCard(activeTrip) {
                    if (activeTrip.status == "REQUESTED") {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.danger)
                                .pressScaleClickable(enabled = busyTripId != activeTrip.id) { cancelTrip(activeTrip.id) }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (busyTripId == activeTrip.id) "Cancelling…" else "Cancel", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        } else {
            item {
                // Real fix (flat-design sweep): dropped the Card wrapper.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Get a designated driver", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "A real professional driver comes to you and drives YOUR OWN CAR home.",
                            color = Ids.colors.textSecondary, fontSize = 12.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IdsTextField(value = pickupAddress, onValueChange = { pickupAddress = it }, label = "Pickup", modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                    .pressScaleClickable(enabled = !locating) { requestLocation() }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                            ) { Text(if (locating) "…" else "Use my location", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        IdsTextField(value = dropoffAddress, onValueChange = { dropoffAddress = it }, label = "Drop-off address", modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IdsTextField(value = dropoffLat, onValueChange = { dropoffLat = it }, label = "Drop-off latitude", modifier = Modifier.weight(1f))
                            IdsTextField(value = dropoffLng, onValueChange = { dropoffLng = it }, label = "Drop-off longitude", modifier = Modifier.weight(1f))
                        }
                        IdsTextField(value = vehicleMake, onValueChange = { vehicleMake = it }, label = "Car make (e.g. Toyota)", modifier = Modifier.fillMaxWidth())
                        IdsTextField(value = vehicleModel, onValueChange = { vehicleModel = it }, label = "Car model (e.g. RAV4)", modifier = Modifier.fillMaxWidth())
                        IdsTextField(value = vehiclePlate, onValueChange = { vehiclePlate = it }, label = "License plate", modifier = Modifier.fillMaxWidth())
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.brand)
                                .pressScaleClickable(
                                    enabled = !requesting && pickupLat != null && dropoffAddress.isNotBlank() &&
                                        vehicleMake.isNotBlank() && vehicleModel.isNotBlank() && vehiclePlate.isNotBlank(),
                                ) { requestTrip() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (requesting) "Requesting…" else "Request a driver", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (pastTrips.isNotEmpty()) {
            item { Text("Past trips", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(pastTrips, key = { it.id }) { trip -> DesignatedDriverTripCard(trip) }
        }
    }
}

@Composable
private fun DesignatedDriverDriveContent() {
    var driver by remember { mutableStateOf<DesignatedDriverDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var licenseNumber by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var availableTrips by remember { mutableStateOf<List<DesignatedDriverTripDto>>(emptyList()) }
    var myDriverTrips by remember { mutableStateOf<List<DesignatedDriverTripDto>>(emptyList()) }
    var busyTripId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val requestLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            coroutineScope.launch {
                try {
                    driver = NetworkClient.apiService.updateDesignatedDriverLocation(UpdateDesignatedDriverLocationRequest(lat, lng)).driver
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
                driver = NetworkClient.apiService.getMyDesignatedDriverProfile().driver
            } catch (e: HttpException) {
                driver = if (rw.itunda.core.network.apiErrorCode(e) == "DESIGNATED_DRIVER_NOT_REGISTERED") null else driver
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
                availableTrips = NetworkClient.apiService.getAvailableDesignatedDriverTrips().trips
                myDriverTrips = NetworkClient.apiService.getMyDesignatedDriverDriverTrips().trips
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
        if (licenseNumber.isBlank()) return
        registering = true
        coroutineScope.launch {
            try {
                driver = NetworkClient.apiService.registerAsDesignatedDriver(RegisterDesignatedDriverRequest(licenseNumber.trim())).driver
                licenseNumber = ""
            } catch (e: HttpException) {
                if (rw.itunda.core.network.apiErrorCode(e) == "DESIGNATED_DRIVER_ALREADY_REGISTERED") {
                    // Real Toss-style resolution, not a dead-end error: a fresh
                    // install/reinstall has no local memory of a prior registration,
                    // but the account genuinely IS already a registered driver --
                    // load the existing profile and move forward.
                    try {
                        driver = NetworkClient.apiService.getMyDesignatedDriverProfile().driver
                        licenseNumber = ""
                    } catch (e2: Exception) {
                        error = "You're already registered, but we couldn't load your profile right now. Try again."
                    }
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
                val updated = NetworkClient.apiService.setDesignatedDriverAvailability(SetDesignatedDriverAvailabilityRequest(!current.available)).driver
                driver = updated
                if (updated?.available == true) requestLocation()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    fun act(tripId: String, action: suspend (String) -> DesignatedDriverTripDto) {
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

    val activeDriverTrips = myDriverTrips.filter { it.status == "ACCEPTED" || it.status == "DRIVING" }
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
                // Real fix (flat-design sweep): dropped the Card wrapper.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Become a designated driver", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Any itunda user can register. License number is self-declared, not verified against a real registry.",
                            color = Ids.colors.textSecondary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                        IdsTextField(value = licenseNumber, onValueChange = { licenseNumber = it }, label = "License number", modifier = Modifier.fillMaxWidth())
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                .pressScaleClickable(enabled = !registering && licenseNumber.isNotBlank()) { register() }.padding(horizontal = 24.dp, vertical = 14.dp),
                        ) { Text(if (registering) "Registering…" else "Register", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            else -> {
                item {
                    // Real fix (flat-design sweep): dropped the Card wrapper.
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (current.available) "You're online" else "You're offline", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (current.available) Ids.colors.danger else Ids.colors.brand)
                                    .pressScaleClickable { toggleAvailable() }.padding(horizontal = 16.dp, vertical = 10.dp),
                            ) { Text(if (current.available) "Go offline" else "Go online", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
                if (activeDriverTrips.isNotEmpty()) {
                    item { Text("Active", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(activeDriverTrips, key = { it.id }) { trip ->
                        DesignatedDriverTripCard(trip) {
                            val nextAction: (suspend (String) -> DesignatedDriverTripDto)? = when (trip.status) {
                                "ACCEPTED" -> { id -> NetworkClient.apiService.startDesignatedDriverTrip(id).trip }
                                "DRIVING" -> { id -> NetworkClient.apiService.completeDesignatedDriverTrip(id).trip }
                                else -> null
                            }
                            nextAction?.let { action ->
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                        .pressScaleClickable(enabled = busyTripId != trip.id) { act(trip.id, action) }.padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (busyTripId == trip.id) "…" else if (trip.status == "ACCEPTED") "Start driving" else "Complete",
                                        color = Color.White, fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
                if (availableTrips.isNotEmpty()) {
                    item { Text("Nearby requests", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(availableTrips, key = { it.id }) { trip ->
                        DesignatedDriverTripCard(trip) {
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                    .pressScaleClickable(enabled = busyTripId != trip.id) { act(trip.id) { id -> NetworkClient.apiService.acceptDesignatedDriverTrip(id).trip } }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (busyTripId == trip.id) "…" else "Accept", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (pastDriverTrips.isNotEmpty()) {
                    item { Text("Completed", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(pastDriverTrips, key = { it.id }) { trip -> DesignatedDriverTripCard(trip) }
                }
            }
        }
    }
}

@Composable
private fun DesignatedDriverTripCard(trip: DesignatedDriverTripDto, action: (@Composable () -> Unit)? = null) {
    // Real fix (flat-design sweep): dropped the Card wrapper -- used as a
    // repeated active/past-trips list row.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(trip.pickupAddress, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("→ ${trip.dropoffAddress}", color = Ids.colors.textSecondary, fontSize = 13.sp)
            Text("${trip.vehicleMake} ${trip.vehicleModel} · ${trip.vehiclePlate}", color = Ids.colors.textSecondary, fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Real Toss reference (2026-08-11) -- see RideScreen.kt's identical fix
                // for the full account: a real-world matching wait (finding a driver)
                // deserves a real "actively working" signal, not static text
                // indistinguishable from a frozen screen.
                val searching = trip.status == "REQUESTED"
                val pulseAlpha = if (searching) {
                    val transition = rememberInfiniteTransition(label = "searchingPulse")
                    val alpha by transition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 900),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "searchingPulseAlpha",
                    )
                    alpha
                } else 1f
                Text(
                    designatedDriverStatusLabel(trip.status),
                    color = designatedDriverStatusColor(trip.status),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.alpha(pulseAlpha),
                )
                Text("${formatMoneyDesignatedDriver(trip.fare)} RWF · ${"%.1f".format(trip.distanceKm)} km", color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
            action?.let {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                it()
            }
    }
}

private fun designatedDriverStatusLabel(status: String): String = when (status) {
    "REQUESTED" -> "Finding a driver…"
    "ACCEPTED" -> "Driver on the way"
    "DRIVING" -> "Driving you home"
    "COMPLETED" -> "Completed"
    "CANCELLED" -> "Cancelled"
    else -> status
}

@Composable
private fun designatedDriverStatusColor(status: String): Color = when (status) {
    "COMPLETED" -> Ids.colors.success
    "CANCELLED" -> Ids.colors.danger
    else -> Ids.colors.brand
}

private fun formatMoneyDesignatedDriver(value: java.math.BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) "%,d".format(rounded.toBigInteger()) else "%,.2f".format(rounded)
}
