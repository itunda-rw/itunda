package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CompleteVehicleInspectionRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RegisterInspectionMechanicRequest
import rw.itunda.core.network.RequestVehicleInspectionRequest
import rw.itunda.core.network.SetInspectionMechanicAvailabilityRequest
import rw.itunda.core.network.VehicleInspectionBookingDto
import rw.itunda.core.network.VehicleInspectionMechanicDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import rw.itunda.core.designsystem.components.EmptyState

// Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
// rw.itunda.marketplace.VehicleInspectionService's own doc comment. A buyer books and
// 100%-prepays a real mechanic to inspect a real Marketplace used-car listing before
// purchase; a mechanic can register, browse incoming bookings, and deliver findings.
// bank-mfe already has this (VehicleInspectionsView); this is the first Android client,
// mirroring its BUYER/MECHANIC toggle exactly. Same honest platform scope-down as
// RideScreen.kt's own scheduled-ride booking: "N hours from now" instead of a real
// calendar date/time picker (no existing precedent on this platform for one).
private enum class InspectionTab { BUYER, MECHANIC }

@Composable
fun VehicleInspectionScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(InspectionTab.BUYER) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Vehicle inspection", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            listOf(InspectionTab.BUYER to "Get a car inspected", InspectionTab.MECHANIC to "Mechanic").forEach { (value, label) ->
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
        if (tab == InspectionTab.BUYER) InspectionBuyerContent() else InspectionMechanicContent()
    }
}

@Composable
private fun InspectionBuyerContent() {
    var mechanics by remember { mutableStateOf<List<VehicleInspectionMechanicDto>?>(null) }
    var myBookings by remember { mutableStateOf<List<VehicleInspectionBookingDto>?>(null) }
    var listingId by remember { mutableStateOf("") }
    var selectedMechanicId by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var scheduleHours by remember { mutableStateOf("") }
    var requesting by remember { mutableStateOf(false) }
    var busyBookingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                mechanics = NetworkClient.apiService.getAvailableInspectionMechanics().mechanics
                myBookings = NetworkClient.apiService.getMyInspectionBookings().bookings
            } catch (e: Exception) {
                error = "Couldn't load inspections."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun requestInspection() {
        val numericFee = fee.toBigDecimalOrNull()
        val hours = scheduleHours.toDoubleOrNull()
        if (listingId.isBlank() || selectedMechanicId.isBlank() || numericFee == null || numericFee <= BigDecimal.ZERO || hours == null || hours <= 0.0) {
            error = "Fill in the listing id, a mechanic, a valid fee, and hours from now."
            return
        }
        requesting = true
        error = null
        coroutineScope.launch {
            try {
                val scheduledFor = Instant.now().plusSeconds((hours * 3600).toLong()).toString()
                NetworkClient.apiService.requestVehicleInspection(
                    UUID.randomUUID().toString(),
                    RequestVehicleInspectionRequest(listingId.trim(), selectedMechanicId, numericFee, scheduledFor),
                )
                listingId = ""
                selectedMechanicId = ""
                fee = ""
                scheduleHours = ""
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                requesting = false
            }
        }
    }

    fun cancel(bookingId: String) {
        busyBookingId = bookingId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.cancelVehicleInspection(bookingId)
                load()
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ids.colors.surfaceSoft)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Book an inspection", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Pay a local mechanic to inspect a used car before you buy it -- held until they deliver their findings.",
                        fontSize = 12.sp, color = Ids.colors.textSecondary,
                    )
                    IdsTextField(value = listingId, onValueChange = { listingId = it }, label = "Listing ID", modifier = Modifier.fillMaxWidth())
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Mechanic", fontSize = 12.sp, color = Ids.colors.textSecondary)
                        (mechanics ?: emptyList()).forEach { m ->
                            val selected = m.id == selectedMechanicId
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Ids.colors.brand.copy(alpha = 0.12f) else Color.Transparent)
                                    .pressScaleClickable { selectedMechanicId = m.id }.padding(10.dp),
                            ) { Text(m.businessName, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                        }
                        if (mechanics?.isEmpty() == true) Text("No mechanics available right now.", fontSize = 12.sp, color = Ids.colors.textSecondary)
                    }
                    IdsTextField(value = fee, onValueChange = { fee = it }, label = "Inspection fee (RWF)", modifier = Modifier.fillMaxWidth())
                    IdsTextField(value = scheduleHours, onValueChange = { scheduleHours = it }, label = "Hours from now", modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = Color(0xFFE53935), fontSize = 12.sp) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (requesting) Ids.colors.brand.copy(alpha = 0.5f) else Ids.colors.brand)
                            .pressScaleClickable(enabled = !requesting) { requestInspection() }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { Text(if (requesting) "Booking…" else "Book & pay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }
            }
        }
        val bookings = myBookings
        if (bookings == null) {
            item { Text("Loading…", fontSize = 13.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(8.dp)) }
        } else if (bookings.isEmpty()) {
            item { EmptyState("No inspections booked yet — book one to get a real used car checked before you buy.") }
        } else {
            items(bookings, key = { it.id }) { b ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ids.colors.surfaceSoft)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Listing ${b.listingId}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${"%,.0f".format(b.fee)} RWF · ${b.status}", fontSize = 12.sp, color = Ids.colors.textSecondary)
                        b.findings?.let { Text(it, fontSize = 13.sp) }
                        if (b.status == "REQUESTED" || b.status == "ACCEPTED") {
                            Row(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFE53935))
                                    .pressScaleClickable(enabled = busyBookingId != b.id) { cancel(b.id) }.padding(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text(if (busyBookingId == b.id) "Cancelling…" else "Cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectionMechanicContent() {
    var profile by remember { mutableStateOf<VehicleInspectionMechanicDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var businessName by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var bookings by remember { mutableStateOf<List<VehicleInspectionBookingDto>?>(null) }
    var findingsByBooking by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busyBookingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val mechanic = NetworkClient.apiService.getMyInspectionMechanicProfile().mechanic
                profile = mechanic
                if (mechanic != null) bookings = try { NetworkClient.apiService.getMyInspectionMechanicBookings().bookings } catch (_: Exception) { emptyList() }
            } catch (e: Exception) {
                error = "Couldn't load your mechanic profile."
            } finally {
                loaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun register() {
        if (businessName.isBlank()) return
        registering = true
        error = null
        coroutineScope.launch {
            try {
                profile = NetworkClient.apiService.registerAsInspectionMechanic(RegisterInspectionMechanicRequest(businessName.trim())).mechanic
                load()
            } catch (e: HttpException) {
                if (rw.itunda.core.network.apiErrorCode(e) == "MECHANIC_ALREADY_REGISTERED") {
                    // Real Toss-style resolution, not a dead-end error: a fresh
                    // install/reinstall has no local memory of a prior registration,
                    // but the account genuinely IS already a registered mechanic --
                    // load the existing profile and move forward.
                    load()
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
        val current = profile ?: return
        coroutineScope.launch {
            try {
                profile = NetworkClient.apiService.setInspectionMechanicAvailability(SetInspectionMechanicAvailabilityRequest(!current.available)).mechanic
            } catch (_: Exception) {
                // Real, non-critical -- an availability toggle failure isn't worth a hard error.
            }
        }
    }

    fun accept(bookingId: String) {
        busyBookingId = bookingId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.acceptVehicleInspection(bookingId)
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } finally {
                busyBookingId = null
            }
        }
    }

    fun complete(bookingId: String) {
        busyBookingId = bookingId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.completeVehicleInspection(bookingId, CompleteVehicleInspectionRequest(findingsByBooking[bookingId]))
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } finally {
                busyBookingId = null
            }
        }
    }

    if (!loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = Ids.colors.textSecondary) }
        return
    }

    val current = profile
    if (current == null) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ids.colors.surfaceSoft)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Become an inspection mechanic", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Get booked and paid to inspect used cars for real buyers before they purchase.", fontSize = 12.sp, color = Ids.colors.textSecondary)
                    IdsTextField(value = businessName, onValueChange = { businessName = it }, label = "Business name", modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = Color(0xFFE53935), fontSize = 12.sp) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (registering) Ids.colors.brand.copy(alpha = 0.5f) else Ids.colors.brand)
                            .pressScaleClickable(enabled = !registering) { register() }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { Text(if (registering) "Registering…" else "Register", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ids.colors.surfaceSoft)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(current.businessName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(if (current.available) "Visible for new bookings" else "Not accepting bookings", fontSize = 12.sp, color = Ids.colors.textSecondary)
                    }
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (current.available) Color(0xFFE53935) else Ids.colors.brand)
                            .pressScaleClickable { toggleAvailable() }.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(if (current.available) "Go unavailable" else "Go available", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        error?.let { item { Text(it, color = Color(0xFFE53935), fontSize = 13.sp) } }
        val list = bookings
        if (list.isNullOrEmpty()) {
            item { EmptyState("No bookings yet — they'll show up here once a buyer books an inspection.") }
        } else {
            items(list, key = { it.id }) { b ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ids.colors.surfaceSoft)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Listing ${b.listingId}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${"%,.0f".format(b.fee)} RWF · ${b.status}", fontSize = 12.sp, color = Ids.colors.textSecondary)
                        if (b.status == "REQUESTED") {
                            Row(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.brand)
                                    .pressScaleClickable(enabled = busyBookingId != b.id) { accept(b.id) }.padding(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text(if (busyBookingId == b.id) "Accepting…" else "Accept", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        if (b.status == "ACCEPTED") {
                            IdsTextField(value = findingsByBooking[b.id] ?: "", onValueChange = { findingsByBooking = findingsByBooking + (b.id to it) }, label = "Inspection findings", modifier = Modifier.fillMaxWidth())
                            Row(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ids.colors.brand)
                                    .pressScaleClickable(enabled = busyBookingId != b.id) { complete(b.id) }.padding(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text(if (busyBookingId == b.id) "Completing…" else "Mark complete", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}
