package rw.itunda.merchant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.merchant.network.AvailabilityWindowDto
import rw.itunda.merchant.network.MerchantBookingDto
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.RespondToBookingRequest
import rw.itunda.merchant.network.SetAvailabilityRequest

private val DAYS = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
private val DAY_LABEL = mapOf(
    "MONDAY" to "Mon", "TUESDAY" to "Tue", "WEDNESDAY" to "Wed", "THURSDAY" to "Thu",
    "FRIDAY" to "Fri", "SATURDAY" to "Sat", "SUNDAY" to "Sun",
)

/**
 * Real local-business appointment booking, owner side (2026-07-25) -- closes the
 * "business profile + real booking" gap independently converged on by Naver Smart
 * Place, Kakao Hair Shop, and Karrot's Business Profile research
 * (docs/DESIGN_REFERENCES.md). Two real, independent jobs: declare a weekly
 * availability schedule (top), and confirm/decline/complete incoming requests
 * (below) -- same restaurant-driven-only status-chain shape DineInTab's own queue
 * already established, minus the multi-status progression this only needs a binary
 * confirm/decline for.
 */
@Composable
fun BookingTab() {
    Column(modifier = Modifier.fillMaxSize()) {
        AvailabilityEditor()
        androidx.compose.material3.Divider(modifier = Modifier.padding(vertical = 4.dp))
        BookingQueue()
    }
}

@Composable
private fun AvailabilityEditor() {
    var windows by remember { mutableStateOf<List<AvailabilityWindowDto>?>(null) }
    var day by remember { mutableStateOf(DAYS[0]) }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("17:00") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            windows = try { NetworkClient.apiService.getMyAvailability().windows } catch (e: Exception) { emptyList() }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun save(next: List<AvailabilityWindowDto>) {
        saving = true
        error = null
        scope.launch {
            try {
                windows = NetworkClient.apiService.setAvailability(SetAvailabilityRequest(next)).windows
            } catch (e: Exception) {
                error = "Couldn't save your availability. Try again."
            } finally {
                saving = false
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Weekly availability", fontWeight = FontWeight.Bold)
        Text(
            "Customers can only request an appointment inside these windows.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.padding(top = 8.dp))

        val current = windows
        if (current == null) {
            CircularProgressIndicator()
        } else {
            if (current.isEmpty()) {
                Text("No availability set yet -- add a window below.", style = MaterialTheme.typography.bodySmall)
            } else {
                current.forEach { w ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${DAY_LABEL[w.dayOfWeek] ?: w.dayOfWeek} ${w.startTime.take(5)}-${w.endTime.take(5)}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { save(current.filter { it != w }) }, enabled = !saving) { Text("Remove") }
                    }
                }
            }
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                DAYS.forEach { d ->
                    val selected = d == day
                    Text(
                        DAY_LABEL[d] ?: d,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 10.dp).clickable { day = d },
                    )
                }
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start (HH:mm)") }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End (HH:mm)") }, modifier = Modifier.weight(1f))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val startTime = parseHm(start)
                    val endTime = parseHm(end)
                    if (startTime == null || endTime == null || startTime >= endTime) {
                        error = "Enter a real start time before the end time (HH:mm)."
                        return@Button
                    }
                    save(current.orEmpty() + AvailabilityWindowDto(day, "$start:00", "$end:00"))
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (saving) "Saving…" else "Add window") }
        }
    }
}

private fun parseHm(v: String): Int? {
    val parts = v.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

@Composable
private fun BookingQueue() {
    var bookings by remember { mutableStateOf<List<MerchantBookingDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            bookings = NetworkClient.apiService.getMerchantBookings().bookings
            error = null
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(8000)
            refresh()
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Bookings", fontWeight = FontWeight.Bold)
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

    val list = bookings
    if (list == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val active = list.filter { it.status in setOf("REQUESTED", "CONFIRMED") }
    if (active.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No open bookings right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(active, key = { it.id }) { booking ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        BookingStatusBadge(booking.status)
                        Text("${booking.bookingDate} ${booking.startTime.take(5)}", fontWeight = FontWeight.Bold)
                    }
                    Text(booking.serviceName, style = MaterialTheme.typography.bodySmall)
                    booking.notes?.takeIf { it.isNotBlank() }?.let {
                        Text("Note: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    if (booking.status == "REQUESTED") {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Button(
                                onClick = {
                                    busyId = booking.id
                                    scope.launch {
                                        try {
                                            NetworkClient.apiService.respondToBooking(booking.id, RespondToBookingRequest(true))
                                            refresh()
                                        } catch (e: Exception) {
                                            error = "Couldn't update this booking."
                                        } finally {
                                            busyId = null
                                        }
                                    }
                                },
                                enabled = busyId != booking.id,
                                modifier = Modifier.weight(1f),
                            ) { Text("Confirm") }
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            TextButton(
                                onClick = {
                                    busyId = booking.id
                                    scope.launch {
                                        try {
                                            NetworkClient.apiService.respondToBooking(booking.id, RespondToBookingRequest(false))
                                            refresh()
                                        } catch (e: Exception) {
                                            error = "Couldn't update this booking."
                                        } finally {
                                            busyId = null
                                        }
                                    }
                                },
                                enabled = busyId != booking.id,
                            ) { Text("Decline") }
                        }
                    } else if (booking.status == "CONFIRMED") {
                        Button(
                            onClick = {
                                busyId = booking.id
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.completeBooking(booking.id)
                                        refresh()
                                    } catch (e: Exception) {
                                        error = "Couldn't update this booking."
                                    } finally {
                                        busyId = null
                                    }
                                }
                            },
                            enabled = busyId != booking.id,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(if (busyId == booking.id) "Updating…" else "Mark completed") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingStatusBadge(status: String) {
    val label = when (status) {
        "REQUESTED" -> "Requested"
        "CONFIRMED" -> "Confirmed"
        "DECLINED" -> "Declined"
        "CANCELLED" -> "Cancelled"
        "COMPLETED" -> "Completed"
        else -> status
    }
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
