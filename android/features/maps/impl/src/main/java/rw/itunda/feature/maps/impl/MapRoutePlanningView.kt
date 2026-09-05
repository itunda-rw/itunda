package rw.itunda.feature.maps.impl

import androidx.compose.foundation.background
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.BusTripDto
import rw.itunda.core.network.MapsDirectionsResponse
import rw.itunda.core.network.RouteResultDto
import rw.itunda.core.network.TransitJourneyDto

// Extracted from MapPlaceDetailView.kt (2026-08-20), same real reason every other
// piece of this bottom sheet was already extracted -- pure "values in, callbacks
// out" rendering, MapScreen still owns every var this reads. Split from
// MapPlaceDetailView.kt's own place-info branch specifically because the combined
// file was itself over the 500-line guideline; these two branches are mutually
// exclusive (only one of {place info, this} is ever visible at a time) so the split
// is a real, natural seam, not an arbitrary cut.
@Composable
internal fun RoutePlanningView(
    placeName: String,
    currentRoute: MapsDirectionsResponse,
    navigating: Boolean,
    travelMode: String,
    otherModeEtaMinutes: Double?,
    routing: Boolean,
    busSearching: Boolean,
    busTrips: List<BusTripDto>?,
    transitSearching: Boolean,
    transitJourneys: List<TransitJourneyDto>?,
    routeAlternatives: List<RouteResultDto>?,
    selectedRouteIndex: Int,
    showSteps: Boolean,
    currentStepIndex: Int,
    voiceEnabled: Boolean,
    onClearRoute: () -> Unit,
    onFetchDirections: (String) -> Unit,
    onSearchBus: (String) -> Unit,
    onSearchTransit: () -> Unit,
    onSelectRouteAlternative: (Int, RouteResultDto) -> Unit,
    onToggleShowSteps: () -> Unit,
    onStartNavigation: () -> Unit,
    onEndNavigation: () -> Unit,
    onToggleVoice: () -> Unit,
) {
    if (!navigating) {
        Text(
            "← Back to $placeName",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
            modifier = Modifier.pressScaleClickable { onClearRoute() }.padding(bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                "DRIVING" to "🚗 Driving", "WALKING" to "🚶 Walking", "BIKING" to "🚴 Bike",
                "BUS" to "🚌 Intercity bus", "TRANSIT" to "🚏 City transit",
            ).forEach { (mode, label) ->
                val active = travelMode == mode
                // Real per-mode precomputed time (2026-08-09) -- Bus/Transit have no
                // real precomputed ETA, so this honestly shows a real trip/journey
                // count once searched instead of a fake time.
                val eta = if (mode == "BUS" || mode == "TRANSIT") null else if (active) currentRoute.route.durationMinutes else otherModeEtaMinutes
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                        .pressScaleClickable(enabled = !routing && !busSearching && !transitSearching) {
                            if (mode == "BUS") {
                                onSearchBus(placeName)
                            } else if (mode == "TRANSIT") {
                                onSearchTransit()
                            } else if (mode != travelMode) {
                                onFetchDirections(mode)
                            }
                        }
                        .padding(vertical = 6.dp),
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) androidx.compose.ui.graphics.Color.White else Ids.colors.textSecondary)
                    if (eta != null) {
                        Text(
                            "${eta.toInt()} min",
                            fontSize = 10.sp,
                            color = if (active) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f) else Ids.colors.textTertiary,
                        )
                    } else if (mode == "BUS" && active) {
                        Text(
                            if (busSearching) "…" else "${busTrips?.size ?: 0} found",
                            fontSize = 10.sp,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        )
                    } else if (mode == "TRANSIT" && active) {
                        Text(
                            if (transitSearching) "…" else "${transitJourneys?.size ?: 0} found",
                            fontSize = 10.sp,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
        if (travelMode == "BUS") {
            BusTripResultsView(placeName, busSearching, busTrips)
        } else if (travelMode == "TRANSIT") {
            TransitJourneyResultsView(transitJourneys, transitSearching)
        } else {
            Text(
                "${travelModeIcon(travelMode)} ${String.format(Locale.US, "%.1f", currentRoute.route.distanceKm)} km · ${currentRoute.route.durationMinutes.toInt()} min by real road, via itunda's own self-hosted OSRM",
                fontSize = 13.sp, color = Ids.colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
            // Real alternative-route picker (2026-07-22) -- only rendered when OSRM
            // genuinely offered more than one real route for this trip.
            val alternatives = routeAlternatives
            if (alternatives != null && alternatives.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
                    alternatives.forEachIndexed { i, alt ->
                        val active = selectedRouteIndex == i
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                                .pressScaleClickable { onSelectRouteAlternative(i, alt) }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Route ${i + 1} · ${String.format(Locale.US, "%.1f", alt.distanceKm)}km · ${alt.durationMinutes.toInt()}min",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = if (active) androidx.compose.ui.graphics.Color.White else Ids.colors.textSecondary,
                            )
                        }
                    }
                }
            }
            if (currentRoute.route.steps.isNotEmpty()) {
                Text(
                    if (showSteps) "Hide turn-by-turn directions" else "Show turn-by-turn directions (${currentRoute.route.steps.size} steps)",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).pressScaleClickable { onToggleShowSteps() },
                )
            }
            // Real full-width prominent CTA (2026-08-09), matching real Naver Maps'
            // own "안내시작" (Start guide) bottom bar.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(Ids.colors.brand, RoundedCornerShape(12.dp))
                    .pressScaleClickable(onClick = onStartNavigation)
                    .padding(vertical = 13.dp),
            ) {
                Text(
                    "▶  Start navigation",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
            if (showSteps) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    currentRoute.route.steps.forEachIndexed { i, step ->
                        Text(
                            "${i + 1}. ${step.instruction}" + if (step.distanceMeters >= 10) " (${step.distanceMeters.toInt()} m)" else "",
                            fontSize = 12.sp, color = Ids.colors.textSecondary,
                        )
                    }
                }
            }
        }
    } else {
        // Real "Start Navigation" mode (2026-08-09) -- shows ONLY the current
        // maneuver -- no mode toggle, no alternatives, no distance summary --
        // matching real Naver/Kakao/Google's own turn-by-turn view exactly.
        val steps = currentRoute.route.steps
        val stepIdx = currentStepIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
        val activeStep = steps.getOrNull(stepIdx)
        val remainingKm = steps.drop(stepIdx + 1).sumOf { it.distanceMeters } / 1000.0 +
            (activeStep?.distanceMeters ?: 0.0) / 1000.0
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ids.colors.brand, RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Column {
                Text(
                    activeStep?.instruction ?: "Arriving at your destination",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                )
                Text(
                    "Step ${stepIdx + 1} of ${steps.size} · ${String.format(Locale.US, "%.1f", remainingKm)} km remaining",
                    fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "End navigation",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .pressScaleClickable(onClick = onEndNavigation)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Text(
                        if (voiceEnabled) "🔊 Voice on" else "🔇 Voice off",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .pressScaleClickable(onClick = onToggleVoice)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// Real per-mode icon for the real-road (OSRM) result line above -- mirrors web's own
// travelModeIcon() helper in lib/maps.ts, added once BIKING became a 3rd real mode.
private fun travelModeIcon(mode: String): String = when (mode) {
    "DRIVING" -> "🚗"
    "BIKING" -> "🚴"
    else -> "🚶"
}
