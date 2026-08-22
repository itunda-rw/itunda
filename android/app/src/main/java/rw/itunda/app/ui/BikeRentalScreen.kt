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
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.itundaface.BikeTypeGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.BikeDto
import rw.itunda.core.network.BikeRentalSessionDto
import rw.itunda.core.network.EndBikeRentalRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RegisterBikeRequest
import rw.itunda.core.network.SetBikeAvailabilityRequest
import rw.itunda.core.network.StartBikeRentalRequest
import rw.itunda.core.network.UpdateBikeLocationRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real Kakao T 바이크 (Kakao T Bike, item 222) -- real PEER-TO-PEER bike/scooter rental
// pool (any user self-registers a bike they own, no admin gate), billed by elapsed TIME
// at rental end -- distinct from DesignatedDriverScreen.kt/RideScreen.kt, which both
// know their fare up front. bank-mfe already has this; this is the first Android
// client, mirroring its Rent-a-bike/My-bikes toggle exactly. Same honest v1
// scope-down RideScreen.kt/DesignatedDriverScreen.kt already established: one-tap
// "Use my location" via the shared rememberRealLocationRequester helper, no real map.
private enum class BikeRentalTab { RENT, MINE }

@Composable
fun BikeRentalScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(BikeRentalTab.RENT) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Bike rental", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            listOf(BikeRentalTab.RENT to "Rent a bike", BikeRentalTab.MINE to "My bikes").forEach { (value, label) ->
                val selected = tab == value
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Ids.colors.brand else Color.Transparent)
                        .clickable { tab = value }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (tab == BikeRentalTab.RENT) BikeRentContent() else BikeMineContent()
    }
}

@Composable
private fun BikeRentContent() {
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var locating by remember { mutableStateOf(false) }
    var nearbyBikes by remember { mutableStateOf<List<BikeDto>>(emptyList()) }
    var activeRental by remember { mutableStateOf<BikeRentalSessionDto?>(null) }
    var pastRentals by remember { mutableStateOf<List<BikeRentalSessionDto>>(emptyList()) }
    var busyBikeId by remember { mutableStateOf<String?>(null) }
    var ending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val requestLocation = rememberRealLocationRequester(
        onLocating = { locating = it },
        onSuccess = { la, lo -> lat = la; lng = lo },
        onError = { error = it },
    )

    fun loadHistory() {
        coroutineScope.launch {
            try {
                val all = NetworkClient.apiService.getMyBikeRentalHistory().rentals
                activeRental = all.firstOrNull { it.status == "ACTIVE" }
                pastRentals = all.filter { it.status == "COMPLETED" }
            } catch (_: Exception) {
                // Non-critical -- a poll failure just skips this refresh.
            }
        }
    }

    fun loadNearby() {
        val la = lat
        val lo = lng
        if (la == null || lo == null) return
        coroutineScope.launch {
            try {
                nearbyBikes = NetworkClient.apiService.getNearbyBikes(la, lo).bikes
            } catch (_: Exception) {
                // Non-critical -- a poll failure just skips this refresh.
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            loadHistory()
            delay(4000)
        }
    }
    LaunchedEffect(lat, lng) { loadNearby() }

    fun startRental(bikeId: String) {
        val la = lat
        val lo = lng
        if (la == null || lo == null) return
        busyBikeId = bikeId
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.startBikeRental(StartBikeRentalRequest(bikeId, la, lo))
                loadHistory()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyBikeId = null
            }
        }
    }

    fun endRental(sessionId: String) {
        val la = lat
        val lo = lng
        if (la == null || lo == null) {
            error = "Share your location to end this rental."
            return
        }
        ending = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.endBikeRental(sessionId, EndBikeRentalRequest(la, lo))
                loadHistory()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                ending = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
        val rental = activeRental
        if (rental != null) {
            item { Text("Riding now", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            item {
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bike unlocked -- billed by elapsed time", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                .clickable(enabled = !ending) { endRental(rental.id) }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (ending) "Ending…" else "End rental (park here)", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                            .clickable(enabled = !locating) { requestLocation() }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (locating) "Locating…" else "Find nearby bikes", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
            if (lat != null && nearbyBikes.isEmpty()) {
                item { Text("No bikes available nearby.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            }
            items(nearbyBikes, key = { it.id }) { bike ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BikeTypeGlyph(electric = bike.type == "ELECTRIC", size = 15.dp)
                            Text(if (bike.type == "ELECTRIC") "Electric bike" else "Regular bike", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(if (bike.type == "ELECTRIC") "150 RWF/minute" else "80 RWF/minute", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                .clickable(enabled = busyBikeId != bike.id) { startRental(bike.id) }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (busyBikeId == bike.id) "…" else "Unlock", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (pastRentals.isNotEmpty()) {
            item { Text("Past rides", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(pastRentals, key = { it.id }) { session -> BikeRentalSessionCard(session) }
        }
    }
}

@Composable
private fun BikeMineContent() {
    var myBikes by remember { mutableStateOf<List<BikeDto>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var bikeType by remember { mutableStateOf("ELECTRIC") }
    var registering by remember { mutableStateOf(false) }
    var busyBikeId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val requestLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            coroutineScope.launch {
                try {
                    NetworkClient.apiService.registerBike(RegisterBikeRequest(bikeType, lat, lng))
                    myBikes = NetworkClient.apiService.getMyBikes().bikes
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                } catch (e: IOException) {
                    error = "Couldn't reach itunda. Check your connection and try again."
                } finally {
                    registering = false
                }
            }
        },
        onError = { error = it; registering = false },
    )

    // Real fix (2026-08-15) -- updateBikeLocation existed on ApiService and the backend
    // since day one, but was never called from any client (bank-mfe's identical gap
    // fixed the same session). A bike owner could register a bike and toggle its
    // availability, but never update its position after moving it, so getNearbyBikes
    // would show a stale location forever after the first registration.
    var updatingLocationBikeId by remember { mutableStateOf<String?>(null) }
    val requestLocationUpdate = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            val bikeId = updatingLocationBikeId
            if (bikeId == null) return@rememberRealLocationRequester
            coroutineScope.launch {
                try {
                    NetworkClient.apiService.updateBikeLocation(bikeId, UpdateBikeLocationRequest(lat, lng))
                    myBikes = NetworkClient.apiService.getMyBikes().bikes
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                } catch (e: IOException) {
                    error = "Couldn't reach itunda. Check your connection and try again."
                } finally {
                    updatingLocationBikeId = null
                }
            }
        },
        onError = { error = it; updatingLocationBikeId = null },
    )

    fun updateBikeLocationAction(bikeId: String) {
        updatingLocationBikeId = bikeId
        error = null
        requestLocationUpdate()
    }

    fun load() {
        coroutineScope.launch {
            try {
                myBikes = NetworkClient.apiService.getMyBikes().bikes
            } catch (_: Exception) {
                // Leave state as-is; next poll may recover.
            }
            loaded = true
        }
    }
    LaunchedEffect(Unit) { load() }

    fun register() {
        registering = true
        error = null
        requestLocation()
    }

    fun toggleAvailable(bike: BikeDto) {
        busyBikeId = bike.id
        coroutineScope.launch {
            try {
                NetworkClient.apiService.setBikeAvailability(bike.id, SetBikeAvailabilityRequest(!bike.available))
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyBikeId = null
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }
        item {
            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Register a bike you own", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Any itunda user can list a bike or scooter into the shared rental pool.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ELECTRIC" to "Electric", "REGULAR" to "Regular").forEach { (value, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(if (bikeType == value) Ids.colors.brand else Ids.colors.surfaceSoft)
                                    .clickable { bikeType = value }.padding(vertical = 10.dp),
                            ) {
                                BikeTypeGlyph(electric = value == "ELECTRIC", size = 13.dp)
                                Text(label, color = if (bikeType == value) Color.White else Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                            .clickable(enabled = !registering) { register() }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (registering) "Registering…" else "Register at my current location", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (!loaded) {
            item { SkeletonBlock(height = 80.dp) }
        } else if (myBikes.isEmpty()) {
            item { Text("You haven't registered any bikes yet.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
        } else {
            item { Text("Your bikes", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(myBikes, key = { it.id }) { bike ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BikeTypeGlyph(electric = bike.type == "ELECTRIC", size = 15.dp)
                            Text(if (bike.type == "ELECTRIC") "Electric bike" else "Regular bike", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                    .clickable(enabled = updatingLocationBikeId != bike.id) { updateBikeLocationAction(bike.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) { Text(if (updatingLocationBikeId == bike.id) "…" else "Update location", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (bike.available) Ids.colors.success else Ids.colors.surfaceSoft)
                                    .clickable(enabled = busyBikeId != bike.id) { toggleAvailable(bike) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) { Text(if (bike.available) "Available" else "Unavailable", color = if (bike.available) Color.White else Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BikeRentalSessionCard(session: BikeRentalSessionDto) {
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${session.durationMinutes ?: 0} min ride", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val fare = session.totalFare
            if (fare != null) {
                Text("${formatMoneyBike(fare)} RWF", color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

private fun formatMoneyBike(value: java.math.BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) rounded.toBigInteger().toString() else "%,.2f".format(rounded)
}
