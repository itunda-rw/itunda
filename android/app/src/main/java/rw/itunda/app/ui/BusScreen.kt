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
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.BookBusSeatsRequest
import rw.itunda.core.network.BusBookingDto
import rw.itunda.core.network.BusTripDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PostBusTripRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant

// Real Kakao T 시외버스 (intercity bus booking, item 224) -- real PEER-TO-PEER
// coach-operator trip pool (any user self-registers as an operator and posts a
// scheduled route, no admin gate/real transport-licensing check). Fare is known and
// charged in FULL at booking time -- distinct from RideScreen.kt/DesignatedDriverScreen.kt/
// BikeRentalScreen.kt/ParkingScreen.kt, which all either escrow-hold or settle at
// session end. bank-mfe already has this; this is the first Android client, mirroring
// its Find-a-bus/My-routes toggle exactly. Same honest "hours from now" v1 scope-down
// RideScreen.kt's own scheduled-ride booking already establishes for the departure
// time -- no real date/time-picker precedent exists in this app.
private enum class BusTab { RIDE, OPERATE }

@Composable
fun BusScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(BusTab.RIDE) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Bus", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            listOf(BusTab.RIDE to "Find a bus", BusTab.OPERATE to "My routes").forEach { (value, label) ->
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
        if (tab == BusTab.RIDE) BusRideContent() else BusOperateContent()
    }
}

@Composable
private fun BusRideContent() {
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var trips by remember { mutableStateOf<List<BusTripDto>?>(null) }
    var myBookings by remember { mutableStateOf<List<BusBookingDto>>(emptyList()) }
    var seatCounts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busyTripId by remember { mutableStateOf<String?>(null) }
    var busyBookingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun search() {
        coroutineScope.launch {
            try {
                trips = NetworkClient.apiService.searchBusTrips(origin.trim().ifBlank { null }, destination.trim().ifBlank { null }).trips
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    fun loadBookings() {
        coroutineScope.launch {
            try {
                myBookings = NetworkClient.apiService.getMyBusBookings().bookings
            } catch (_: Exception) {
                // Non-critical -- a refresh failure just skips this poll.
            }
        }
    }

    LaunchedEffect(Unit) { search(); loadBookings() }

    fun bookSeats(tripId: String) {
        val seats = seatCounts[tripId]?.toIntOrNull() ?: 1
        if (seats < 1) return
        busyTripId = tripId
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.bookBusSeats(java.util.UUID.randomUUID().toString(), BookBusSeatsRequest(tripId, seats))
                search(); loadBookings()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyTripId = null
            }
        }
    }

    fun cancelBooking(bookingId: String) {
        busyBookingId = bookingId
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.cancelBusBooking(bookingId)
                loadBookings()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyBookingId = null
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
                    IdsTextField(value = origin, onValueChange = { origin = it }, label = "From", modifier = Modifier.fillMaxWidth())
                    IdsTextField(value = destination, onValueChange = { destination = it }, label = "To", modifier = Modifier.fillMaxWidth())
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                            .clickable { search() }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Search", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        val list = trips
        if (list == null) {
            item { Text("Loading…", color = Ids.colors.textSecondary, fontSize = 13.sp) }
        } else if (list.isEmpty()) {
            item { Text("No upcoming trips found.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
        } else {
            items(list, key = { it.id }) { trip ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${trip.origin} → ${trip.destination}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("🕒 Departs ${trip.departureTime.take(16).replace("T", " ")}", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "${formatMoneyBus(trip.farePerSeat)} RWF/seat · ${trip.availableSeats} seat(s) left",
                            color = Ids.colors.textSecondary, fontSize = 12.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IdsTextField(value = seatCounts[trip.id] ?: "1", onValueChange = { seatCounts = seatCounts + (trip.id to it) }, label = "Seats", modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier.weight(2f).clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                    .clickable(enabled = busyTripId != trip.id) { bookSeats(trip.id) }.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (busyTripId == trip.id) "…" else "Book seats", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        val activeBookings = myBookings.filter { it.status == "BOOKED" }
        if (activeBookings.isNotEmpty()) {
            item { Text("Your bookings", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(activeBookings, key = { it.id }) { booking ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${booking.seatCount} seat(s)", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${formatMoneyBus(booking.totalFare)} RWF", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                .clickable(enabled = busyBookingId != booking.id) { cancelBooking(booking.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        ) { Text(if (busyBookingId == booking.id) "…" else "Cancel", color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusOperateContent() {
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var departureHours by remember { mutableStateOf("") }
    var totalSeats by remember { mutableStateOf("") }
    var farePerSeat by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var myTrips by remember { mutableStateOf<List<BusTripDto>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    // Real trip manifest -- see getBusTripBookings's own doc comment. Previously a
    // real, tested backend endpoint with zero client anywhere on any platform: an
    // operator could post a route and see the seat countdown, but never who actually
    // booked. Lazily loaded per trip, matching bank-mfe's own established behavior.
    var expandedTripId by remember { mutableStateOf<String?>(null) }
    var tripBookings by remember { mutableStateOf<List<BusBookingDto>?>(null) }
    var manifestError by remember { mutableStateOf<String?>(null) }

    fun toggleManifest(tripId: String) {
        if (expandedTripId == tripId) {
            expandedTripId = null
            return
        }
        expandedTripId = tripId
        tripBookings = null
        manifestError = null
        coroutineScope.launch {
            try {
                tripBookings = NetworkClient.apiService.getBusTripBookings(tripId).bookings
            } catch (e: HttpException) {
                manifestError = superAppErrorMessage(e)
            } catch (e: IOException) {
                manifestError = "Could not load bookings for this route."
            }
        }
    }

    fun load() {
        coroutineScope.launch {
            try {
                myTrips = NetworkClient.apiService.getMyBusTrips().trips
            } catch (_: Exception) {
                // Leave state as-is; next load may recover.
            }
            loaded = true
        }
    }
    LaunchedEffect(Unit) { load() }

    fun postTrip() {
        val hours = departureHours.toDoubleOrNull()
        val seats = totalSeats.toIntOrNull()
        val fare = farePerSeat.toBigDecimalOrNull()
        if (origin.isBlank() || destination.isBlank() || hours == null || hours <= 0 || seats == null || seats <= 0 || fare == null || fare <= BigDecimal.ZERO) {
            error = "Fill in every field with a real value."
            return
        }
        posting = true
        error = null
        val departureTime = Instant.now().plusSeconds((hours * 3600).toLong()).toString()
        coroutineScope.launch {
            try {
                NetworkClient.apiService.postBusTrip(PostBusTripRequest(origin.trim(), destination.trim(), departureTime, seats, fare))
                origin = ""; destination = ""; departureHours = ""; totalSeats = ""; farePerSeat = ""
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                posting = false
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
                    Text("Post a route", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Any itunda user can post a scheduled trip -- no transport-licensing check.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    IdsTextField(value = origin, onValueChange = { origin = it }, label = "Origin", modifier = Modifier.fillMaxWidth())
                    IdsTextField(value = destination, onValueChange = { destination = it }, label = "Destination", modifier = Modifier.fillMaxWidth())
                    IdsTextField(value = departureHours, onValueChange = { departureHours = it }, label = "Departs in (hours from now)", modifier = Modifier.fillMaxWidth())
                    IdsTextField(value = totalSeats, onValueChange = { totalSeats = it }, label = "Total seats", modifier = Modifier.fillMaxWidth())
                    IdsTextField(value = farePerSeat, onValueChange = { farePerSeat = it }, label = "Fare per seat (RWF)", modifier = Modifier.fillMaxWidth())
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                            .clickable(enabled = !posting) { postTrip() }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (posting) "Posting…" else "Post route", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (!loaded) {
            item { SkeletonBlock(height = 80.dp) }
        } else if (myTrips.isEmpty()) {
            item { Text("You haven't posted any routes yet.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
        } else {
            item { Text("Your routes", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(myTrips, key = { it.id }) { trip ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${trip.origin} → ${trip.destination}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("🕒 Departs ${trip.departureTime.take(16).replace("T", " ")}", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "${trip.availableSeats}/${trip.totalSeats} seats left · ${formatMoneyBus(trip.farePerSeat)} RWF/seat",
                            color = Ids.colors.textSecondary, fontSize = 12.sp,
                        )
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.chip)
                                .clickable { toggleManifest(trip.id) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                if (expandedTripId == trip.id) "Hide bookings" else "View bookings",
                                color = Ids.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                        if (expandedTripId == trip.id) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val bookings = tripBookings
                                when {
                                    manifestError != null -> Text(manifestError!!, color = Ids.colors.danger, fontSize = 12.sp)
                                    bookings == null -> Text("Loading…", color = Ids.colors.textSecondary, fontSize = 12.sp)
                                    bookings.isEmpty() -> Text("No bookings yet.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                                    else -> bookings.forEach { b ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                "Rider #${b.riderUserId.takeLast(6)} · ${b.seatCount} seat${if (b.seatCount > 1) "s" else ""}",
                                                color = Ids.colors.textSecondary, fontSize = 12.sp,
                                            )
                                            Text(
                                                if (b.status == "CANCELLED") "Cancelled" else "${formatMoneyBus(b.totalFare)} RWF",
                                                color = if (b.status == "CANCELLED") Ids.colors.textSecondary else Ids.colors.textPrimary,
                                                fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMoneyBus(value: BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) rounded.toBigInteger().toString() else "%,.2f".format(rounded)
}
