package rw.itunda.feature.maps.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
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

private val recentRoutes = listOf(
    Triple("Nyamirambo Coffee House", "10.6 km · 35 min · 15 Oct", "Nyamirambo Coffee House"),
    Triple("KG 7 Ave 22, Kacyiru", "6.2 km · 24 min · 04 Oct", "KG 7 Ave 22, Kacyiru"),
    Triple("Kimironko Market", "0.9 km · 10 min · 04 Oct", "Kimironko Market"),
)
private val busStops = listOf(
    Triple("Kimironko Market Stop", "305, 311 · 0.3 km · 311 arriving soon", "Kimironko Market Stop"),
    Triple("Remera Taxi Park", "305, 322 · 9.0 km · 322 in 4 min", "Remera Taxi Park"),
    Triple("Nyabugogo Stop", "305, 102 · 10.9 km · 305 in 1 min", "Nyabugogo Stop"),
)

private val intercity = listOf(Triple("Huye", "about 2 h · 3,500 RWF", "Fastest"), Triple("Musanze", "about 2 h · 3,000 RWF", "Cheapest"), Triple("Rubavu", "about 3 h 30 · 4,000 RWF", null), Triple("Rusizi", "about 5 h · 7,500 RWF", null))

/** Prototype "Directions" hub: from/to card, Recent routes, Bus stops, Bus lines, Fares and rides. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DirectionsHubScreen(onBack: () -> Unit, onOpenMap: (query: String?) -> Unit) {
    BackHandler(onBack = onBack)
    var cityOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Ids.colors.background).verticalScroll(rememberScrollState())) {
        ProtoTopBar("Directions", onBack)
        Box(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(102.dp).clip(RoundedCornerShape(22.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(12.dp).height(92.dp).padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 1.dp, height = 28.dp).background(Ids.colors.textTertiary.copy(alpha = 0.4f)))
                Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(10.dp).background(Color(0xFF30B27F), CircleShape))
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(10.dp).background(Color(0xFFF04452), CircleShape))
            }
            Column(Modifier.padding(start = 16.dp).padding(bottom = 4.dp)) {
                Text("My location · KG 11 Ave, Kimironko", color = Ids.colors.textPrimary, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.height(46.dp).wrapContentHeight(Alignment.CenterVertically))
                Text("Where to?", color = Ids.colors.textSecondary, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.clickable { onOpenMap(null) }.height(46.dp).wrapContentHeight(Alignment.CenterVertically))
            }
        }
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 13.dp, bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubPill("Home", height = 40, suffix = "· 24 min") { onOpenMap("Home") }
            HubPill("Work", height = 40, suffix = "· 25 min") { onOpenMap("Work") }
        }
        HubHeading("Recent routes", 23.dp)
        recentRoutes.forEach { (t, sub, q) -> HubRow(t, sub, onClick = { onOpenMap(q) }) { Icon(Icons.Outlined.Schedule, null, tint = Ids.colors.textTertiary, modifier = Modifier.size(22.dp)) } }
        HubHeading("Bus stops near you", 23.dp)
        busStops.forEach { (t, sub, q) -> HubRow(t, sub, onClick = { onOpenMap(q) }) { HubGlyph("bus") } }
        HubHeading("Bus lines", 23.dp)
        FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("102", "305", "311", "322").forEach { HubPill(it, height = 40, fontSize = 15) { onOpenMap("Bus $it") } }
        }
        HubHeading("Fares and rides", 23.dp)
        HubRow("Bus fare · Tap&Go", onClick = { onOpenMap(null) }) { HubGlyph("card") }
        HubRow("Ride or bike there", "Moto, car, or a rented bike", onClick = { onOpenMap(null) }) { HubGlyph("mcar") }
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 40.dp).fillMaxWidth().height(56.dp).clickable { cityOpen = !cityOpen }, verticalAlignment = Alignment.CenterVertically) {
            Text("Going to another city?", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Ids.colors.textTertiary, modifier = Modifier.size(20.dp))
        }
        if (cityOpen) {
            Text("Buses leave Nyabugogo. Free to cancel up to 2 hours before.", color = Ids.colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 6.dp))
            intercity.forEach { (city, sub, tag) -> HubRow(city, sub + (tag?.let { " · $it" } ?: ""), onClick = {}) { Text("🚍", fontSize = 20.sp) } }
            Box(Modifier.height(24.dp))
        }
    }
}
