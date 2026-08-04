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
import rw.itunda.core.designsystem.components.IdsTextField
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
import rw.itunda.core.network.ParkingSessionDto
import rw.itunda.core.network.ParkingSpotDto
import rw.itunda.core.network.RegisterParkingSpotRequest
import rw.itunda.core.network.SetParkingSpotAvailabilityRequest
import rw.itunda.core.network.StartParkingSessionRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal

// Real Kakao T 주차 (Kakao T Parking, item 223) -- real PEER-TO-PEER parking-spot
// rental pool (any user self-lists a spot they own/control, no admin gate), billed by
// elapsed HOURS at checkout -- distinct from RideScreen.kt/DesignatedDriverScreen.kt,
// which both know their fare up front, but the same "settle at session end" shape
// BikeRentalScreen.kt already establishes (just hourly, not per-minute). bank-mfe
// already has this; this is the first Android client, mirroring its Find-a-spot/
// My-spots toggle exactly. Same honest v1 scope-down BikeRentalScreen.kt already
// established: one-tap "Use my location" via the shared rememberRealLocationRequester
// helper, no real map.
private enum class ParkingTab { FIND, MINE }

@Composable
fun ParkingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(ParkingTab.FIND) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Parking", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(TossCardSoft).padding(4.dp),
        ) {
            listOf(ParkingTab.FIND to "Find a spot", ParkingTab.MINE to "My spots").forEach { (value, label) ->
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
        if (tab == ParkingTab.FIND) ParkingFindContent() else ParkingMineContent()
    }
}

@Composable
private fun ParkingFindContent() {
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var locating by remember { mutableStateOf(false) }
    var nearbySpots by remember { mutableStateOf<List<ParkingSpotDto>>(emptyList()) }
    var activeSession by remember { mutableStateOf<ParkingSessionDto?>(null) }
    var pastSessions by remember { mutableStateOf<List<ParkingSessionDto>>(emptyList()) }
    var busySpotId by remember { mutableStateOf<String?>(null) }
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
                val all = NetworkClient.apiService.getMyParkingHistory().sessions
                activeSession = all.firstOrNull { it.status == "ACTIVE" }
                pastSessions = all.filter { it.status == "COMPLETED" }
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
                nearbySpots = NetworkClient.apiService.getNearbyParkingSpots(la, lo).spots
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

    fun startSession(spotId: String) {
        busySpotId = spotId
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.startParkingSession(StartParkingSessionRequest(spotId))
                loadHistory()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busySpotId = null
            }
        }
    }

    fun endSession(sessionId: String) {
        ending = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.endParkingSession(sessionId)
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
        val session = activeSession
        if (session != null) {
            item { Text("Parked now", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            item {
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Checked in -- billed by elapsed hours", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossBlue)
                                .clickable(enabled = !ending) { endSession(session.id) }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (ending) "Checking out…" else "Check out (end session)", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(TossCardSoft)
                            .clickable(enabled = !locating) { requestLocation() }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (locating) "Locating…" else "Find nearby spots", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
            if (lat != null && nearbySpots.isEmpty()) {
                item { Text("No parking spots available nearby.", color = TossSecondary, fontSize = 13.sp) }
            }
            items(nearbySpots, key = { it.id }) { spot ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(spot.address, color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${formatMoneyParking(spot.hourlyRate)} RWF / hour", color = TossSecondary, fontSize = 12.sp)
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossBlue)
                                .clickable(enabled = busySpotId != spot.id) { startSession(spot.id) }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (busySpotId == spot.id) "…" else "Check in", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (pastSessions.isNotEmpty()) {
            item { Text("Past sessions", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(pastSessions, key = { it.id }) { session -> ParkingSessionCard(session) }
        }
    }
}

@Composable
private fun ParkingMineContent() {
    var mySpots by remember { mutableStateOf<List<ParkingSpotDto>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var hourlyRate by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var busySpotId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val requestLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            val rate = hourlyRate.toBigDecimalOrNull()
            if (rate == null) {
                registering = false
            } else {
                coroutineScope.launch {
                    try {
                        NetworkClient.apiService.registerParkingSpot(RegisterParkingSpotRequest(address.trim(), lat, lng, rate))
                        address = ""; hourlyRate = ""
                        mySpots = NetworkClient.apiService.getMyParkingSpots().spots
                    } catch (e: HttpException) {
                        error = superAppErrorMessage(e)
                    } catch (e: IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } finally {
                        registering = false
                    }
                }
            }
        },
        onError = { error = it; registering = false },
    )

    fun load() {
        coroutineScope.launch {
            try {
                mySpots = NetworkClient.apiService.getMyParkingSpots().spots
            } catch (_: Exception) {
                // Leave state as-is; next poll may recover.
            }
            loaded = true
        }
    }
    LaunchedEffect(Unit) { load() }

    fun register() {
        val rate = hourlyRate.toBigDecimalOrNull()
        if (address.isBlank() || rate == null || rate <= BigDecimal.ZERO) {
            error = "Enter a real address and hourly rate."
            return
        }
        registering = true
        error = null
        requestLocation()
    }

    fun toggleAvailable(spot: ParkingSpotDto) {
        busySpotId = spot.id
        coroutineScope.launch {
            try {
                NetworkClient.apiService.setParkingSpotAvailability(spot.id, SetParkingSpotAvailabilityRequest(!spot.available))
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busySpotId = null
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
            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("List a spot you own", color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Any itunda user can list a driveway or private lot space into the shared rental pool.", color = TossSecondary, fontSize = 12.sp)
                    IdsTextField(value = address, onValueChange = { address = it }, label = "Address", modifier = Modifier.fillMaxWidth())
                    IdsTextField(value = hourlyRate, onValueChange = { hourlyRate = it }, label = "Hourly rate (RWF)", modifier = Modifier.fillMaxWidth())
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossBlue)
                            .clickable(enabled = !registering) { register() }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (registering) "Registering…" else "List at my current location", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (!loaded) {
            item { Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(80.dp)) {} }
        } else if (mySpots.isEmpty()) {
            item { Text("You haven't listed any spots yet.", color = TossSecondary, fontSize = 13.sp) }
        } else {
            item { Text("Your spots", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(mySpots, key = { it.id }) { spot ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(spot.address, color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (spot.available) Ids.colors.success else TossCardSoft)
                                    .clickable(enabled = busySpotId != spot.id) { toggleAvailable(spot) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) { Text(if (spot.available) "Available" else "Unavailable", color = if (spot.available) Color.White else TossText, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        Text("${formatMoneyParking(spot.hourlyRate)} RWF / hour", color = TossSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParkingSessionCard(session: ParkingSessionDto) {
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${session.durationMinutes ?: 0} min parked", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val fare = session.totalFare
            if (fare != null) {
                Text("${formatMoneyParking(fare)} RWF", color = TossSecondary, fontSize = 12.sp)
            }
        }
    }
}

private fun formatMoneyParking(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) rounded.toBigInteger().toString() else "%,.2f".format(rounded)
}
