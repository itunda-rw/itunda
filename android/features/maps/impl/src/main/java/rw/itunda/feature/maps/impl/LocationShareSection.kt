package rw.itunda.feature.maps.impl

import android.annotation.SuppressLint
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ExtendLocationShareRequest
import rw.itunda.core.network.LiveLocationShareDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.StartLocationShareRequest
import rw.itunda.core.network.UpdateLocationShareRequest
import rw.itunda.core.network.superAppErrorMessage
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun formatShareExpiry(iso: String): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val date = parser.parse(iso.substringBefore('.').removeSuffix("Z")) ?: Date()
    SimpleDateFormat("h:mm a", Locale.US).format(date)
} catch (_: Exception) {
    iso
}

/**
 * Real Kakao Map-style "친구위치" (Friend Location) live location sharing -- ported from
 * bank-mfe/maps-mfe (2026-09-03), a real gap: the backend (LiveLocationShareService,
 * MapsController's /location-share routes) and web already had this fully wired, native
 * clients had zero UI or network calls for it. A real, moving position shared with one
 * specific person for a bounded window, distinct from the static bookmark-folder
 * share/subscribe above it in the saved-places sheet. "Live" means periodically-refreshed
 * via polling, not a push channel -- itunda has no WebSocket infra for this specifically.
 *
 * [onWatchedPositionChanged] fires with the watched incoming share's (lat, lng), or null
 * when watching stops, so the host screen can fly the camera / drop a marker without this
 * section needing its own handle onto the MapLibre style.
 */
@SuppressLint("MissingPermission")
@Composable
fun LocationShareSection(
    myLocation: Pair<Double, Double>?,
    onWatchedPositionChanged: (Pair<Double, Double>?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showStartShare by remember { mutableStateOf(false) }
    var recipientPhone by remember { mutableStateOf("") }
    var durationHours by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var myShares by remember { mutableStateOf<List<LiveLocationShareDto>>(emptyList()) }
    var sharesWithMe by remember { mutableStateOf<List<LiveLocationShareDto>>(emptyList()) }
    var watchingShareId by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            try { myShares = NetworkClient.apiService.getMyLocationShares().shares } catch (_: Exception) {}
            try { sharesWithMe = NetworkClient.apiService.getLocationSharesWithMe().shares } catch (_: Exception) {}
        }
    }
    LaunchedEffect(Unit) { reload() }

    // Real "client owns when to push a fresh reading" loop -- pushes this device's own
    // real location every 30s while at least one real share is active, matching
    // RideDriverService.updateLocation's own real backend rate limit (20/min = one push
    // every 3s minimum; 30s is comfortably under that with real headroom for retries).
    LaunchedEffect(myShares.isNotEmpty()) {
        if (myShares.isEmpty()) return@LaunchedEffect
        while (true) {
            val location = myLocation
            if (location != null) {
                try { NetworkClient.apiService.updateMyLocationShare(UpdateLocationShareRequest(location.first, location.second)) } catch (_: Exception) {}
            }
            delay(30_000)
        }
    }

    // Real recipient-side watch -- polls the sharer's latest pushed position every 15s.
    LaunchedEffect(watchingShareId) {
        val shareId = watchingShareId ?: run { onWatchedPositionChanged(null); return@LaunchedEffect }
        while (true) {
            try {
                val share = NetworkClient.apiService.getLocationShare(shareId).share
                val lat = share.latitude
                val lng = share.longitude
                if (lat != null && lng != null) {
                    onWatchedPositionChanged(lat to lng)
                }
            } catch (_: Exception) {
                // A real expired/revoked share (or a transient network hiccup) just means
                // this poll cycle doesn't move the marker -- explicit stop is the real way
                // to end a watch, not an inferred failure here.
            }
            delay(15_000)
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Divider(color = Ids.colors.divider)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📍 Live location sharing", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textTertiary)
            Text(
                if (showStartShare) "Cancel" else "+ Share my location",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                modifier = Modifier.pressScaleClickable { showStartShare = !showStartShare },
            )
        }

        if (showStartShare) {
            Column(
                Modifier.fillMaxWidth().padding(top = 6.dp).background(Ids.colors.surfaceSoft, RoundedCornerShape(8.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IdsTextField(value = recipientPhone, onValueChange = { recipientPhone = it }, label = "Recipient's phone number")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("For", fontSize = 12.sp, color = Ids.colors.textSecondary)
                    listOf(1, 2, 3, 4, 5, 6).forEach { h ->
                        Text(
                            "${h}h",
                            fontSize = 12.sp,
                            fontWeight = if (durationHours == h) FontWeight.Bold else FontWeight.Normal,
                            color = if (durationHours == h) Ids.colors.brand else Ids.colors.textSecondary,
                            modifier = Modifier.pressScaleClickable { durationHours = h }.padding(4.dp),
                        )
                    }
                }
                error?.let { Text(it, fontSize = 12.sp, color = Ids.colors.danger) }
                Text(
                    if (busy) "Starting…" else "Start sharing",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ids.colors.brand, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .pressScaleClickable(enabled = !busy && recipientPhone.isNotBlank()) {
                            busy = true; error = null
                            scope.launch {
                                try {
                                    val share = NetworkClient.apiService.startLocationShare(StartLocationShareRequest(recipientPhone.trim(), durationHours)).share
                                    myShares = listOf(share) + myShares
                                    showStartShare = false
                                    recipientPhone = ""
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                )
            }
        }

        myShares.forEach { s ->
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sharing until ${formatShareExpiry(s.expiresAt)}", fontSize = 12.sp, color = Ids.colors.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Real "extend while active" (2026-09-04) -- a fully-built backend
                    // (LiveLocationShareService.extendSharing, capped at MAX_DURATION_HOURS
                    // total from the share's own original creation) had zero UI on any
                    // platform since the feature shipped; once shared, a user could only
                    // let it lapse or stop it early, never lengthen it. Silent no-op on
                    // failure (e.g. already past the 6h cap) matches Stop's own existing
                    // convention below rather than introducing a new error-display path
                    // for this compact row.
                    Text(
                        "+1h", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                        modifier = Modifier.pressScaleClickable {
                            scope.launch {
                                try {
                                    val updated = NetworkClient.apiService.extendLocationShare(s.id, ExtendLocationShareRequest(1)).share
                                    myShares = myShares.map { if (it.id == s.id) updated else it }
                                } catch (_: Exception) {}
                            }
                        },
                    )
                    Text(
                        "Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.danger,
                        modifier = Modifier.pressScaleClickable {
                            scope.launch {
                                try { NetworkClient.apiService.stopLocationShare(s.id) } catch (_: Exception) {}
                                myShares = myShares.filter { it.id != s.id }
                            }
                        },
                    )
                }
            }
        }

        if (sharesWithMe.isNotEmpty()) {
            Text("Shared with you", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textTertiary, modifier = Modifier.padding(top = 8.dp))
            sharesWithMe.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Live location · until ${formatShareExpiry(s.expiresAt)}", fontSize = 12.sp, color = Ids.colors.textPrimary)
                    if (watchingShareId == s.id) {
                        Text("Stop watching", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.danger, modifier = Modifier.pressScaleClickable { watchingShareId = null })
                    } else {
                        Text("View on map", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand, modifier = Modifier.pressScaleClickable { watchingShareId = s.id })
                    }
                }
            }
        }
    }
}
