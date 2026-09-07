package rw.itunda.feature.ride.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids

// Real Kakao T-style ride-hailing (rw.itunda.rideshare, real since 2026-07-26) -- first
// Android client for this feature (item 109, found via a fresh matrix scan: bank-mfe
// has had it since the same day, Android/iOS never did). Mirrors bank-mfe's RidesView
// (RIDE/DRIVE toggle) exactly. Honest v1 scoping: manual address/lat/lng entry for
// dropoff (no autocomplete search integration this pass), "Use my location" one-tap
// pickup via the shared rememberRealLocationRequester helper (already proven in the
// Hood feature) for the single most common real passenger flow.
//
// **Real Kakao T 예약 호출 (scheduled ride booking, item 212)/multi-stop (item 214)/
// driver rating (item 213) added 2026-07-31** -- first Android client for these three,
// backend and bank-mfe real since the same day. Honest platform-specific scope-down:
// bank-mfe's own scheduling UI uses a real `datetime-local` picker; this app has no
// existing date/time-picker precedent anywhere and adding one is a real, separate scope
// increase, so scheduling here is "N hours from now" instead of picking an exact real
// calendar date/time -- itunda's own honest simplification for this platform, not a
// silently-dropped feature (the real backend contract is identical either way).
//
// Split into RidePassengerScreen.kt/RideDriverScreen.kt (2026-09-05) once the module
// extraction pushed this file back over the file-size-lint threshold -- same shape as
// this session's other screen-extraction splits, just by real RIDE/DRIVE tab content
// instead of an arbitrary line count.
private enum class RideTab { RIDE, DRIVE }

@Composable
fun RideScreen(onBack: () -> Unit, onReportIssue: (String) -> Unit = {}) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(RideTab.RIDE) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Rides", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            listOf(RideTab.RIDE to "Get a ride", RideTab.DRIVE to "Drive").forEach { (value, label) ->
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
        if (tab == RideTab.RIDE) RidePassengerContent(onReportIssue = onReportIssue) else RideDriverContent()
    }
}
