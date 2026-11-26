package rw.itunda.feature.maps.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ProtoTopBar
import rw.itunda.core.designsystem.theme.Ids

private data class HubPlace(
    val name: String,
    val kind: String,
    val rating: String,
    val reviews: Int,
    val closes: String,
    val distance: String,
    val emoji: String,
    val reservable: Boolean,
    val delivery: Boolean,
)

private val nearYou = listOf(
    HubPlace("Kigali Roast", "Cafe", "4.4", 212, "20:00", "0.2 km · 3 min walk", "☕", true, true),
    HubPlace("Mama Rice House", "Local", "4.6", 208, "21:00", "2.6 km · 7 min drive", "🍚", true, true),
    HubPlace("Sweet Tooth Cakes", "Dessert, Bakery", "4.9", 221, "20:00", "4.0 km · 9 min drive", "🍰", false, true),
    HubPlace("Fresh Press Juice Bar", "Juice, Healthy", "4.8", 176, "20:00", "4.1 km · 15 min drive", "🥤", false, false),
    HubPlace("Green Bowl", "Healthy", "4.6", 98, "20:00", "4.1 km · 11 min drive", "🥗", true, true),
)

/** Category chips: label, emoji, backend category id (null = open the map as is). */
private val categoryChips = listOf(
    "Map" to ("🗺️" to null),
    "Cafe" to ("☕" to "CAFE"),
    "Food" to ("🍽️" to "RESTAURANT"),
    "Hospital" to ("🏥" to "HOSPITAL"),
    "Pharmacy" to ("💊" to "PHARMACY"),
    "ATM" to ("🏧" to "BANK"),
    "Bus stops" to ("🚌" to "BUS_STOP"),
)

private data class HubReservation(val place: String, val time: String, val guests: Int)

/** Prototype Places hub with the Naver-style place cards and a working reservation sheet. */
@Composable
fun PlacesHubScreen(onBack: () -> Unit, onOpenMap: (category: String?, query: String?) -> Unit) {
    BackHandler(onBack = onBack)
    var openNow by remember { mutableStateOf(false) }
    var reservable by remember { mutableStateOf(false) }
    var delivery by remember { mutableStateOf(false) }
    var reservationPlace by remember { mutableStateOf<HubPlace?>(null) }
    var reservationTime by remember { mutableStateOf("18:00") }
    var reservationGuests by remember { mutableStateOf(2) }
    var showReservations by remember { mutableStateOf(false) }
    val reservations = remember { mutableStateListOf<HubReservation>() }
    val hour = remember { java.time.LocalTime.now().hour }
    val shown = nearYou.filter {
        (!openNow || hour < it.closes.substringBefore(":").toInt()) &&
            (!reservable || it.reservable) && (!delivery || it.delivery)
    }

    Box(Modifier.fillMaxSize().background(Ids.colors.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ProtoTopBar("Places", onBack)
            Box(Modifier.height(12.dp))
            Box(
                Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft)
                    .clickable { onOpenMap(null, null) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Search places", color = Ids.colors.textPrimary, fontSize = 16.sp)
            }
            Row(
                Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categoryChips.forEach { (label, meta) ->
                    Row(
                        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
                            .background(Ids.colors.surfaceSoft)
                            .clickable { onOpenMap(meta.second, null) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(meta.first, fontSize = 20.sp)
                        Text(label, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HubHeading("Near you", 22.dp)
            Row(
                Modifier.padding(horizontal = 24.dp).padding(top = 2.dp, bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HubPill("Open now", openNow) { openNow = !openNow }
                HubPill("Reservable", reservable) { reservable = !reservable }
                HubPill("Delivery", delivery) { delivery = !delivery }
            }
            shown.forEach { place ->
                PlaceRow(
                    place = place,
                    onClick = { onOpenMap(null, place.name) },
                    onReserve = {
                        reservationPlace = place
                        reservationTime = "18:00"
                        reservationGuests = 2
                    },
                    onDirections = { onOpenMap(null, place.name) },
                )
            }
            HubHeading("My places", 25.dp)
            HubRow("Saved", trailing = null, onClick = { onOpenMap(null, null) }) { HubGlyph("star") }
            HubRow(
                "Reservations",
                trailing = reservations.size.takeIf { it > 0 }?.toString(),
                onClick = { showReservations = true },
            ) { HubGlyph("calendar") }
            HubRow("My reviews", onClick = { onOpenMap(null, null) }) { HubGlyph("message-circle") }
            HubHeading("Recent", 25.dp)
            HubRow("Nyamirambo Coffee House", trailing = "15 Oct", onClick = { onOpenMap(null, "Nyamirambo Coffee House") }) { HubGlyph("pin") }
            Box(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 40.dp)
                    .fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp))
                    .background(Ids.colors.brand).clickable { onOpenMap(null, null) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Pay at a place", color = Color(0xFF17171C), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (reservationPlace != null) {
            ReservationSheet(
                place = reservationPlace!!,
                time = reservationTime,
                guests = reservationGuests,
                onTimeChange = { reservationTime = it },
                onGuestsChange = { reservationGuests = it },
                onDismiss = { reservationPlace = null },
                onConfirm = {
                    reservations.add(HubReservation(reservationPlace!!.name, reservationTime, reservationGuests))
                    reservationPlace = null
                },
            )
        } else if (showReservations) {
            ReservationsSheet(
                reservations = reservations,
                onDismiss = { showReservations = false },
                onCancel = { reservations.removeAt(it) },
            )
        }
    }
}

@Composable
private fun PlaceRow(
    place: HubPlace,
    onClick: () -> Unit,
    onReserve: () -> Unit,
    onDirections: () -> Unit,
) {
    val photoEmojis = when {
        place.kind.contains("Cafe", ignoreCase = true) -> listOf("☕", "🍰", "🥐")
        place.kind.contains("Local", ignoreCase = true) -> listOf("🍚", "🥘", "🍲")
        place.kind.contains("Dessert", ignoreCase = true) -> listOf("🍰", "🍓", "🧁")
        place.kind.contains("Juice", ignoreCase = true) -> listOf("🥤", "🍊", "🍋")
        else -> listOf("🥗", "🥑", "🥬")
    }
    val photoColors = listOf(Color(0xFFFFE8C7), Color(0xFFFFE1D6), Color(0xFFE3F4E8))
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(place.name, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(place.kind, color = Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFFF5B700), fontWeight = FontWeight.Bold)) { append("★ ") }
                withStyle(SpanStyle(color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)) { append(place.rating) }
                withStyle(SpanStyle(color = Ids.colors.textSecondary)) { append(" · Visitor reviews ${place.reviews}") }
            },
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF12803C), fontWeight = FontWeight.Bold)) { append("Open till ${place.closes}") }
                withStyle(SpanStyle(color = Ids.colors.textSecondary)) { append(" · ${place.distance}") }
            },
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).height(92.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            photoEmojis.forEachIndexed { index, emoji ->
                Box(
                    Modifier.weight(if (index == 0) 2f else 1f).height(92.dp)
                        .clip(RoundedCornerShape(if (index == 0) 14.dp else 10.dp))
                        .background(photoColors[index]),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = if (index == 0) 38.sp else 30.sp)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (place.reservable) {
                HubPill("Reserve", height = 38, fontSize = 14, onClick = onReserve)
            }
            HubPill("Directions", height = 38, fontSize = 14, onClick = onDirections)
        }
    }
}

@Composable
private fun ReservationSheet(
    place: HubPlace,
    time: String,
    guests: Int,
    onTimeChange: (String) -> Unit,
    onGuestsChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Ids.colors.background).clickable(enabled = false) {}
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Text("Reserve a table", color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(place.name, color = Ids.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Text("Time", color = Ids.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("12:00", "14:00", "18:00", "20:00").forEach { value ->
                    HubPill(value, selected = time == value, height = 38, fontSize = 13, onClick = { onTimeChange(value) })
                }
            }
            Text("Guests", color = Ids.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 4, 6).forEach { value ->
                    HubPill("$value person${if (value == 1) "" else "s"}", selected = guests == value, height = 38, fontSize = 13, onClick = { onGuestsChange(value) })
                }
            }
            Text("Today · $time · $guests ${if (guests == 1) "person" else "people"}", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp, bottom = 14.dp))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(Ids.colors.brand).clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) { Text("Confirm reservation", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ReservationsSheet(
    reservations: List<HubReservation>,
    onDismiss: () -> Unit,
    onCancel: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Ids.colors.background).padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Text("Reservations", color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (reservations.isEmpty()) {
                Text("No reservations yet.", color = Ids.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
            } else {
                reservations.forEachIndexed { index, reservation ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(reservation.place, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Today · ${reservation.time} · ${reservation.guests} guests", color = Ids.colors.textSecondary, fontSize = 13.sp)
                        }
                        Text("Cancel", color = Ids.colors.brand, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onCancel(index) }.padding(8.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Text("Close", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
