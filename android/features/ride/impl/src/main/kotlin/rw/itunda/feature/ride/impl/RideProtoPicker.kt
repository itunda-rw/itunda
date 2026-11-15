package rw.itunda.feature.ride.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

private data class RidePlace(val name: String, val lat: Double, val lng: Double)
private data class RideOption(val emoji: String, val name: String, val eta: String, val price: String)

private val places = listOf(
    RidePlace("Kigali Heights", -1.9536, 30.0927),
    RidePlace("Nyabugogo", -1.9392, 30.0447),
    RidePlace("Airport", -1.9686, 30.1395),
)
// Dummy fares and ETAs, as in the prototype, until the backend quotes real ones.
private val options = listOf(
    RideOption("🏍️", "Moto", "2 min away", "800 RWF"),
    RideOption("🚗", "Car", "5 min away", "1,800 RWF"),
)

/** Prototype ride request top: route card, "Where to?" chips and Moto/Car options. The real fields stay below. */
@Composable
fun RideProtoPicker(pickup: String, dropoff: String, onPlace: (name: String, lat: Double, lng: Double) -> Unit) {
    var option by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(20.dp)) {
            RouteRow(Ids.colors.success, pickup.ifBlank { "KG 11 Ave, Kimironko" })
            Box(Modifier.padding(start = 5.dp).size(1.dp, 14.dp).background(Ids.colors.divider))
            RouteRow(Ids.colors.danger, dropoff.ifBlank { "Where to?" })
        }
        Spacer(Modifier.height(24.dp))
        Text("Where to?", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            places.forEach { p ->
                val on = p.name == dropoff
                Text(
                    p.name, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (on) Ids.colors.background else Ids.colors.textPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
                        .pressScaleClickable { onPlace(p.name, p.lat, p.lng) }.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        options.forEachIndexed { i, o ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (i == option) Ids.colors.surfaceSoft else Ids.colors.background)
                    .pressScaleClickable { option = i }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                    Text(o.emoji, fontSize = 24.sp)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(o.name, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(o.eta, color = Ids.colors.textTertiary, fontSize = 13.sp)
                }
                Text(o.price, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun RouteRow(dot: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(label, color = Ids.colors.textPrimary, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
    }
}
