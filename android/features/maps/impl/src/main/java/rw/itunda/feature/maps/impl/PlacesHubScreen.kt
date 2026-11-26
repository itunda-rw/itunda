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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

private data class HubPlace(val name: String, val kind: String, val rating: String, val reviews: Int, val closes: String, val distance: String, val emoji: String, val reservable: Boolean, val delivery: Boolean)

private val nearYou = listOf(
    HubPlace("Kigali Roast", "Cafe", "4.4", 212, "20:00", "0.2 km · 3 min walk", "☕", true, true),
    HubPlace("Mama Rice House", "Local", "4.6", 208, "21:00", "2.6 km · 7 min drive", "🍚", true, true),
    HubPlace("Sweet Tooth Cakes", "Dessert, Bakery", "4.9", 221, "20:00", "4.0 km · 9 min drive", "🍰", false, true),
    HubPlace("Fresh Press Juice Bar", "Juice, Healthy", "4.8", 176, "20:00", "4.1 km · 15 min drive", "🥤", false, false),
    HubPlace("Green Bowl", "Healthy", "4.6", 98, "20:00", "4.1 km · 11 min drive", "🥗", true, true),
)

/** Category chips: label, emoji, backend category id (null = open the map as is). */
private val categoryChips = listOf("Map" to ("🗺️" to null), "Cafe" to ("☕" to "CAFE"), "Food" to ("🍽️" to "RESTAURANT"), "Clinic" to ("🏥" to "HOSPITAL"), "Pharmacy" to ("💊" to "PHARMACY"), "ATM" to ("🏧" to "BANK"))

/** Prototype "Places" hub: search, category chips, Near you list, My places and Recent. */
@Composable
fun PlacesHubScreen(onBack: () -> Unit, onOpenMap: (category: String?, query: String?) -> Unit) {
    BackHandler(onBack = onBack)
    var openNow by remember { mutableStateOf(false) }
    var reservable by remember { mutableStateOf(false) }
    var delivery by remember { mutableStateOf(false) }
    val hour = remember { java.time.LocalTime.now().hour }
    val shown = nearYou.filter { (!openNow || hour < it.closes.substringBefore(":").toInt()) && (!reservable || it.reservable) && (!delivery || it.delivery) }
    Column(Modifier.fillMaxSize().background(Ids.colors.background).verticalScroll(rememberScrollState())) {
        ProtoTopBar("Places", onBack)
        Box(Modifier.height(12.dp))
        Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft).clickable { onOpenMap(null, null) }.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            Text("Search places", color = Ids.colors.textPrimary, fontSize = 16.sp)
        }
        Row(Modifier.padding(top = 14.dp, bottom = 4.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            categoryChips.forEach { (label, meta) ->
                Row(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Ids.colors.surfaceSoft).clickable { onOpenMap(meta.second, null) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(meta.first, fontSize = 20.sp)
                    Text(label, color = Ids.colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        HubHeading("Near you", 27.dp)
        Row(Modifier.padding(horizontal = 24.dp).padding(top = 6.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubPill("Open now", openNow) { openNow = !openNow }
            HubPill("Reservable", reservable) { reservable = !reservable }
            HubPill("Delivery", delivery) { delivery = !delivery }
        }
        Box(Modifier.height(6.dp))
        shown.forEach { p -> PlaceRow(p) { onOpenMap(null, p.name) } }
        HubHeading("My places", 25.dp)
        HubRow("Saved", onClick = { onOpenMap(null, null) })
        HubRow("Reservations", onClick = { onOpenMap(null, null) })
        HubRow("My reviews", onClick = { onOpenMap(null, null) })
        HubHeading("Recent", 25.dp)
        HubRow("Nyamirambo Coffee House", trailing = "15 Oct", onClick = { onOpenMap(null, "Nyamirambo Coffee House") }) { HubGlyph("bus") }
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 40.dp).fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp)).background(Ids.colors.brand).clickable { onOpenMap(null, null) }, contentAlignment = Alignment.Center) {
            Text("Pay at a place", color = Color(0xFF17171C), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlaceRow(p: HubPlace, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(115.6.dp).clickable(onClick = onClick).padding(start = 24.dp, end = 24.dp, top = 13.dp)) {
        Column(Modifier.weight(1f)) {
            Text(p.name, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, style = hubTrim)
            Text(p.kind, color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, style = hubTrim, modifier = Modifier.padding(top = 6.dp))
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFFF5B700))) { append("★ ") }
                withStyle(SpanStyle(color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)) { append(p.rating) }
                append(" · ${p.reviews} reviews")
            }, color = Ids.colors.textSecondary, fontSize = 14.sp, lineHeight = 17.sp, style = hubTrim, modifier = Modifier.padding(top = 4.dp))
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF12803C), fontWeight = FontWeight.Bold)) { append("Open till ${p.closes}") }
                append(" · ${p.distance}")
            }, color = Ids.colors.textSecondary, fontSize = 14.sp, lineHeight = 17.sp, style = hubTrim, modifier = Modifier.padding(top = 6.dp))
        }
        Box(Modifier.padding(start = 12.dp).size(64.dp).clip(RoundedCornerShape(18.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { Text(p.emoji, fontSize = 30.sp) }
    }
}
