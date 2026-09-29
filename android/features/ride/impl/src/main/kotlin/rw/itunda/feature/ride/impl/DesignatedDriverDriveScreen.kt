package rw.itunda.feature.ride.impl

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
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
import androidx.compose.ui.draw.alpha
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
import rw.itunda.core.network.DesignatedDriverDto
import rw.itunda.core.network.DesignatedDriverTripDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RegisterDesignatedDriverRequest
import rw.itunda.core.network.SetDesignatedDriverAvailabilityRequest
import rw.itunda.core.network.UpdateDesignatedDriverLocationRequest
import rw.itunda.core.network.apiErrorCode
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID

// Split out of DesignatedDriverScreen.kt (2026-09-05) -- see that file's own doc
// comment. The "Drive" side: registration, going online/offline, and the real
// accept/start/complete trip lifecycle. Also owns DesignatedDriverTripCard/
// designatedDriverStatusLabel/designatedDriverStatusColor (`internal`, not
// `private`) since DesignatedDriverRequestScreen.kt's own past/active-trip list
// uses the identical card.
@Composable
internal fun DesignatedDriverDriveContent() {
    var driver by remember { mutableStateOf<DesignatedDriverDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var licenseNumber by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var availableTrips by remember { mutableStateOf<List<DesignatedDriverTripDto>>(emptyList()) }
    var myDriverTrips by remember { mutableStateOf<List<DesignatedDriverTripDto>>(emptyList()) }
    // Real pagination-discard fix (2026-09-13, porting web's own fix -- see
    // project_itunda_pagination_discard_sweep memory) -- getMyDesignatedDriverDriverTrips
    // silently capped this list at the first 20 trips. Page 0 is polled every 4s
    // for real-time active-trip accuracy, so it must stay a live, page-0-only
    // fetch; olderDriverTrips is a separate accumulator populated only by loadMore.
    var olderDriverTrips by remember { mutableStateOf<List<DesignatedDriverTripDto>>(emptyList()) }
    var driverTripsPage by remember { mutableStateOf(0) }
    var driverTripsHasMore by remember { mutableStateOf(false) }
    var loadingMoreDriverTrips by remember { mutableStateOf(false) }
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
                driver = if (apiErrorCode(e) == "DESIGNATED_DRIVER_NOT_REGISTERED") null else driver
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
                val res = NetworkClient.apiService.getMyDesignatedDriverDriverTrips(page = 0)
                myDriverTrips = res.trips
                driverTripsHasMore = res.page + 1 < res.totalPages
            } catch (_: Exception) {
                // Non-critical -- a poll failure just skips this refresh.
            }
        }
    }

    fun loadMoreDriverTrips() {
        val nextPage = driverTripsPage + 1
        loadingMoreDriverTrips = true
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyDesignatedDriverDriverTrips(page = nextPage)
                olderDriverTrips = olderDriverTrips + res.trips.filter { it.status == "COMPLETED" || it.status == "CANCELLED" }
                driverTripsPage = nextPage
                driverTripsHasMore = res.page + 1 < res.totalPages
            } catch (_: Exception) {
                // Non-critical -- the already-loaded page stays visible; the
                // user can retry by tapping "Load more" again.
            } finally {
                loadingMoreDriverTrips = false
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
                driver = NetworkClient.apiService.registerAsDesignatedDriver(UUID.randomUUID().toString(), RegisterDesignatedDriverRequest(licenseNumber.trim())).driver
                licenseNumber = ""
            } catch (e: HttpException) {
                if (apiErrorCode(e) == "DESIGNATED_DRIVER_ALREADY_REGISTERED") {
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
    val pastDriverTrips = myDriverTrips.filter { it.status == "COMPLETED" || it.status == "CANCELLED" } + olderDriverTrips

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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Any itunda user can register. License number is self-declared, not verified against a real registry.",
                            color = Ids.colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        IdsTextField(value = licenseNumber, onValueChange = { licenseNumber = it }, label = "License number", modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))
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
                                    .pressScaleClickable(enabled = busyTripId != trip.id) { act(trip.id) { id -> NetworkClient.apiService.acceptDesignatedDriverTrip(id, java.util.UUID.randomUUID().toString()).trip } }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (busyTripId == trip.id) "…" else "Accept", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (pastDriverTrips.isNotEmpty()) {
                    item { Text("Completed", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    items(pastDriverTrips, key = { it.id }) { trip -> DesignatedDriverTripCard(trip) }
                    if (driverTripsHasMore) {
                        item {
                            IdsButton(
                                text = if (loadingMoreDriverTrips) "Loading…" else "Load more",
                                onClick = ::loadMoreDriverTrips,
                                enabled = !loadingMoreDriverTrips,
                                variant = IdsButtonVariant.Tinted,
                                size = IdsButtonSize.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesignatedDriverTripCard(trip: DesignatedDriverTripDto, action: (@Composable () -> Unit)? = null) {
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
                Text("${formatMoney(trip.fare)} RWF · ${String.format(Locale.US, "%.1f", trip.distanceKm)} km", color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
            action?.let {
                Spacer(modifier = Modifier.height(4.dp))
                it()
            }
    }
}

internal fun designatedDriverStatusLabel(status: String): String = when (status) {
    "REQUESTED" -> "Finding a driver…"
    "ACCEPTED" -> "Driver on the way"
    "DRIVING" -> "Driving you home"
    "COMPLETED" -> "Completed"
    "CANCELLED" -> "Cancelled"
    else -> status
}

@Composable
internal fun designatedDriverStatusColor(status: String): Color = when (status) {
    "COMPLETED" -> Ids.colors.success
    "CANCELLED" -> Ids.colors.danger
    else -> Ids.colors.brand
}
