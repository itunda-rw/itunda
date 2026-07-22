package rw.itunda.core.designsystem.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreateHoodReportRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetNeighborhoodRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Relocated 2026-07-22 from app/ui/SuperAppTabs.kt while extracting Marketplace into
// :features:marketplace:impl -- these are the true cross-feature UI atoms Marketplace,
// Community, Jobs, and Property all share (verified via a repo-wide call-site audit
// before moving anything). Living here in :core:designsystem, rather than in :app or
// duplicated per Feature module, is what lets :features:marketplace:impl depend on them
// without depending on :app -- the same Gradle-module-boundary mechanism the existing
// :features:payments:impl relies on ("this Feature module can't depend back on App").
// Every call site across the app (Community/Jobs/Property, still in :app for now) was
// repointed to this single shared copy rather than left duplicated.

@Composable
fun SkeletonBlock(height: Dp = 120.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "skeletonOffset",
    )
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        modifier = modifier.fillMaxWidth().height(height),
        colors = CardDefaults.cardColors(containerColor = Ids.colors.surfaceSoft),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    colors = listOf(Ids.colors.surfaceSoft, Ids.colors.surface, Ids.colors.surfaceSoft),
                    start = Offset(offset * 600f, 0f),
                    end = Offset(offset * 600f + 400f, 400f),
                ),
            ),
        )
    }
}

@Composable
fun EmptyState(message: String, icon: ImageVector = Icons.Outlined.Inbox) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Ids.colors.surfaceSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Ids.colors.textSecondary)
        }
        Text(message, color = Ids.colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(message, color = Ids.colors.danger, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Retry", color = Ids.colors.brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onRetry))
        }
    }
}

@Composable
fun TabHeader(title: String) {
    Text(title, color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun ListingActionButton(label: String, disabled: Boolean, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Ids.colors.brand else Ids.colors.surfaceSoft)
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (filled) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Real device-location fetch, shared by NewListingForm's "share my location" toggle and
// ListingCard's "directions to this seller" -- same runtime-permission-gated
// FusedLocationProviderClient technique MapScreen.kt already established.
@Composable
fun rememberRealLocationRequester(
    onLocating: (Boolean) -> Unit,
    onSuccess: (Double, Double) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    fun fetch() {
        onLocating(true)
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                onLocating(false)
                if (location != null) onSuccess(location.latitude, location.longitude)
                else onError("Could not access your real location right now.")
            }
            .addOnFailureListener {
                onLocating(false)
                onError("Could not access your real location right now.")
            }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetch() else onError("Location permission was denied.")
    }
    return {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) fetch() else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

fun relativeTimeAgo(isoTimestamp: String): String {
    val postedAt = try {
        java.time.Instant.parse(isoTimestamp)
    } catch (e: Exception) {
        return ""
    }
    val elapsed = java.time.Duration.between(postedAt, java.time.Instant.now())
    return when {
        elapsed.toMinutes() < 1 -> "Just now"
        elapsed.toMinutes() < 60 -> "${elapsed.toMinutes()}m ago"
        elapsed.toHours() < 24 -> "${elapsed.toHours()}h ago"
        elapsed.toDays() < 7 -> "${elapsed.toDays()}d ago"
        else -> "${elapsed.toDays() / 7}w ago"
    }
}

fun chatMessageTime(isoTimestamp: String): String = try {
    java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())
        .withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.parse(isoTimestamp))
} catch (_: Exception) {
    ""
}

@Composable
fun HoodReportAction(targetType: String, targetId: String) {
    var showChoices by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    fun send(reason: String) {
        showChoices = false
        sending = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.reportHoodContent(CreateHoodReportRequest(targetType, targetId, reason))
                message = "Thanks. Your report was sent for review."
            } catch (e: HttpException) {
                message = if (e.code() == 409) "You already reported this post." else superAppErrorMessage(e)
            } catch (e: IOException) {
                message = "Couldn't send the report. Check your connection and try again."
            } finally {
                sending = false
            }
        }
    }
    ListingActionButton(if (sending) "Reporting…" else "Report", sending) { showChoices = true }
    message?.let { Text(it, color = if (it.startsWith("Thanks")) Ids.colors.success else Ids.colors.danger, fontSize = 12.sp) }
    if (showChoices) {
        AlertDialog(
            onDismissRequest = { showChoices = false },
            title = { Text("Report this content") },
            text = { Text("Choose the best reason. Itunda’s review team will assess it.", color = Ids.colors.textSecondary) },
            confirmButton = { TextButton(onClick = { send("Unsafe payment, contact request, or scam") }) { Text("Unsafe or scam") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { send("Misleading, unavailable, or spam content") }) { Text("Misleading or spam") }
                    TextButton(onClick = { send("Harassment, hateful, illegal, or prohibited content") }) { Text("Abusive or illegal") }
                }
            },
        )
    }
}

// Real hyperlocal neighborhood setup (2026-07-20) -- shared across every Hood-mode
// content composable (Marketplace/Community/Jobs/Property), mirroring bank-mfe's
// NeighborhoodSetupPrompt exactly. Reuses rememberRealLocationRequester, the same real
// FusedLocationProviderClient helper NewListingForm's own "share my location" already
// established -- one location permission flow, not a second one invented for this.
@Composable
fun NeighborhoodSetupPrompt(onDone: (String) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val requestLocation = rememberRealLocationRequester(
        onLocating = { busy = it },
        onSuccess = { lat, lng ->
            coroutineScope.launch {
                busy = true
                try {
                    val res = NetworkClient.authApi.setNeighborhood(SetNeighborhoodRequest(lat, lng))
                    busy = false
                    res.user.neighborhood?.let(onDone)
                } catch (e: HttpException) {
                    busy = false
                    error = superAppErrorMessage(e)
                } catch (e: IOException) {
                    busy = false
                    error = "Couldn't reach itunda. Check your connection and try again."
                }
            }
        },
        onError = { error = it },
    )

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Set your neighborhood", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Share your real location once to see what's happening near you.",
                color = Ids.colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ids.colors.brand)
                    .clickable(enabled = !busy) { requestLocation() }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(if (busy) "Finding your neighborhood…" else "📍 Share my location", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            error?.let { Spacer(modifier = Modifier.height(12.dp)); Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        }
    }
}
