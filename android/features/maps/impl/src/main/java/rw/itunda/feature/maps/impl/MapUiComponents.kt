package rw.itunda.feature.maps.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.BusTripDto

// Extracted from MapsScreen.kt (2026-08-19) -- see MapStyle.kt's own header comment for
// the full account of why/how. These three are small, stateless leaf composables/pure
// functions taking simple params, the same shape BusTripResultsView was already safely
// extracted TO within MapsScreen.kt itself (2026-07-23, to work around a real JVM
// "Method too large" error) -- this pass just moves that existing pattern one step
// further, into its own file.

// Real Naver Map place-card layout (2026-08-04) -- confirmed live against itunda's own
// self-hosted Nominatim (previously misdiagnosed as "no real Rwanda POI data" while this
// screen's own backend restart was missing NOMINATIM_BASE_URL; re-verified with it
// actually configured and it returns rich real results, e.g. "Miracle Pharmacy, KN 81
// Street, Nyarugenge, Nyarugenge District, City of Kigali, Rwanda"). Real Naver place
// cards show a bold name with a muted address line below, not one long run-on string --
// itunda's own `displayName` already carries the full real address, just unsplit. Splits
// on the first comma only (Nominatim's own convention: segment 0 is always the specific
// place/building name, everything after is the real address hierarchy) -- no new backend
// field, no new data, just real presentation of what's already there.
// Real Naver Maps-style pill action button (2026-08-09) -- outlined by default (a
// hairline border in Ids.colors.divider), filled solid brand when `filled` is true
// (matches the real reference screenshots' own convention of a solid-filled pill for
// a toggled-on state like a saved bookmark).
// Real scheduled bus trips (BusService.kt, extracted as its own composable so its
// bytecode doesn't count against MapScreen's own already-large generated method --
// hit a real JVM "Method too large" compile error before this extraction, not a
// stylistic choice). Honest "Scheduled" labeling, no live-tracking claim; real
// fare/seats/departure time, no OSRM route line since there's no real road-route
// concept for a peer-posted coach trip.
@Composable
internal fun BusTripResultsView(placeName: String, busSearching: Boolean, busTrips: List<BusTripDto>?) {
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (busSearching) {
            Text("Searching real scheduled trips to $placeName…", fontSize = 13.sp, color = Ids.colors.textSecondary)
        } else if (busTrips.isNullOrEmpty()) {
            Text("No scheduled bus trips found to $placeName right now.", fontSize = 13.sp, color = Ids.colors.textSecondary)
        } else {
            busTrips.forEach { trip ->
                Column(
                    modifier = Modifier.fillMaxWidth().background(Ids.colors.surfaceSoft, RoundedCornerShape(10.dp)).padding(12.dp),
                ) {
                    Text("${trip.origin} → ${trip.destination}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
                    Text(
                        "Scheduled · ${trip.departureTime.take(16).replace("T", " ")} · ${trip.availableSeats} seat(s) left · %,.0f RWF/seat".format(trip.farePerSeat),
                        fontSize = 12.sp, color = Ids.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlaceActionPill(icon: String, label: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (filled) Modifier.background(Ids.colors.brand)
                else Modifier.border(1.dp, Ids.colors.divider, RoundedCornerShape(999.dp)),
            )
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(icon, fontSize = 13.sp)
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) androidx.compose.ui.graphics.Color.White else Ids.colors.textPrimary,
        )
    }
}

internal fun splitPlaceName(displayName: String): Pair<String, String?> {
    val comma = displayName.indexOf(',')
    return if (comma < 0) displayName to null else displayName.substring(0, comma).trim() to displayName.substring(comma + 1).trim()
}
