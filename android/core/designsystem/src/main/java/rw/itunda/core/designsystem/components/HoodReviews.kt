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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.scale
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
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.CreateHoodReportRequest
import rw.itunda.core.network.HoodReviewDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SetNeighborhoodRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real fix (2026-08-26): split out of HoodShared.kt once that file grew past its
// file-size-lint baseline. HoodShared.kt itself is a grab-bag of things "promoted"
// so Feature modules can reach them (its own doc comments say as much, e.g.
// BackTopBar's), not a strict single-concern file -- this split pulls out the
// genuinely Hood/neighborhood-domain-specific pieces (post-transaction review
// checklist+form+result, location-permission request, report action, neighborhood
// setup prompt) from the truly generic cross-screen UI primitives left behind
// (SkeletonBlock/EmptyState/ErrorCard/TabHeader/TrustBadge/StatusBadge/ScrollFog/
// BackTopBar/StarRatingRow/SearchAndCategoryChips/QtyButton/ListingActionButton).
// Same package, so zero import changes at any call site.

// Real post-transaction review preset checklist labels (2026-07-24) -- ids must match
// backend HoodReviewService.GOOD_POINTS/UNCOMFORTABLE_POINTS exactly.
val HoodGoodPointLabels = mapOf(
    "RESPONSIVE" to "Quick to respond", "AS_DESCRIBED" to "As described", "ON_TIME" to "On time",
    "FRIENDLY" to "Friendly", "FAIR_PRICE" to "Fair price",
)
val HoodUncomfortablePointLabels = mapOf(
    "LATE" to "Was late", "NOT_AS_DESCRIBED" to "Not as described", "UNRESPONSIVE" to "Hard to reach",
    "RUDE" to "Rude", "PRICE_ISSUE" to "Price disagreement",
)

// Real post-transaction review with Karrot's own asymmetric public/private visibility
// (2026-07-24) -- closes docs/DESIGN_REFERENCES.md Section 4 recommendation #2. A
// preset checklist, not free text, matching Karrot's own real review UX: "good points"
// are shown publicly (feed into the trust score), "uncomfortable points" stay private
// between the two real parties to the transaction -- deliberate asymmetric visibility
// to avoid public-negative-review churn. Shared by Marketplace/Jobs/Property so all
// three verticals render the exact same checklist.
@Composable
fun HoodReviewForm(
    selectedGoodPoints: Set<String>,
    onToggleGoodPoint: (String) -> Unit,
    selectedUncomfortablePoints: Set<String>,
    onToggleUncomfortablePoint: (String) -> Unit,
    submitting: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("What went well? (shown publicly)", color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HoodGoodPointLabels.forEach { (id, label) ->
                val selected = id in selectedGoodPoints
                Box(
                    modifier = Modifier
                        .background(if (selected) Ids.colors.brand else Ids.colors.surfaceSoft, RoundedCornerShape(999.dp))
                        .clickable { onToggleGoodPoint(id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Ids.colors.textPrimary) }
            }
        }
        Text("Anything uncomfortable? (private -- only you two see this)", color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HoodUncomfortablePointLabels.forEach { (id, label) ->
                val selected = id in selectedUncomfortablePoints
                Box(
                    modifier = Modifier
                        .background(if (selected) Ids.colors.danger else Ids.colors.surfaceSoft, RoundedCornerShape(999.dp))
                        .clickable { onToggleUncomfortablePoint(id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Ids.colors.textPrimary) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ListingActionButton("Cancel", submitting, onClick = onCancel)
            ListingActionButton(if (submitting) "Submitting…" else "Submit review", submitting, filled = true, onClick = onSubmit)
        }
    }
}

// Real read-back for a submitted Hood transaction review (item 192/198) -- see
// bank-mfe's HoodReviewResultView (item 192) for the full account. Only ever rendered
// for a real party to the transaction (the fetch itself real-403s otherwise via
// HOOD_REVIEW_NOT_PARTY), so both "your review" and "their review of you" -- including
// uncomfortablePoints -- are honestly shown here, matching Karrot's own asymmetric
// visibility: private between the two real parties, not public to anyone else. Shared
// by Marketplace/Jobs/Property, same as HoodReviewForm above.
@Composable
fun HoodReviewResultView(reviews: List<HoodReviewDto>, myUserId: String?) {
    val mine = reviews.firstOrNull { it.reviewerId == myUserId }
    val theirs = reviews.firstOrNull { it.reviewerId != myUserId }
    if (mine == null && theirs == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        mine?.let { HoodReviewResultBlock("Your review", it) }
        theirs?.let { HoodReviewResultBlock("Their review of you", it) }
    }
}

@Composable
private fun HoodReviewResultBlock(title: String, review: HoodReviewDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Ids.colors.surfaceSoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        if (review.goodPoints.isNotEmpty()) {
            Text(
                "👍 ${review.goodPoints.joinToString(", ") { HoodGoodPointLabels[it] ?: it }}",
                color = Ids.colors.textSecondary, fontSize = 12.sp,
            )
        }
        if (review.uncomfortablePoints.isNotEmpty()) {
            Text(
                "⚠️ ${review.uncomfortablePoints.joinToString(", ") { HoodUncomfortablePointLabels[it] ?: it }}",
                color = Ids.colors.danger, fontSize = 12.sp,
            )
        }
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

/**
 * Real Kakao/Toss/iMessage-style collapsed-per-run timestamp convention
 * (docs/DESIGN_REFERENCES.md Talk section, recommendation #8's "remaining polish
 * gap": "each message shows its own timestamp, not grouped by consecutive-run").
 * A message shows its timestamp only when it's the last in a consecutive run from
 * the same sender within the same local minute. Compares full local date+minute, not
 * [chatMessageTime]'s "h:mm a" clock-face string -- that alone would false-positive
 * "same run" for two messages sent at the same clock time on different days (a real
 * risk for a chat search-results list, where adjacent entries aren't temporally
 * adjacent in the real conversation).
 */
fun <T> shouldShowChatTimestamp(messages: List<T>, index: Int, senderId: (T) -> String, sentAt: (T) -> String): Boolean {
    if (index == messages.lastIndex) return true
    val current = messages[index]
    val next = messages[index + 1]
    if (senderId(current) != senderId(next)) return true
    val minuteFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault())
    return try {
        minuteFormatter.format(java.time.Instant.parse(sentAt(current))) != minuteFormatter.format(java.time.Instant.parse(sentAt(next)))
    } catch (_: Exception) {
        true
    }
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
// isSecond (2026-08-04) -- real dual-neighborhood support, see User.secondNeighborhood's
// own doc comment on the backend. Same real GPS+reverse-geocode flow, routed to
// setSecondNeighborhood instead of setNeighborhood so a user can register an additional
// real place (e.g. a workplace) without overwriting their primary one.
@Composable
fun NeighborhoodSetupPrompt(isSecond: Boolean = false, onDone: (String) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val requestLocation = rememberRealLocationRequester(
        onLocating = { busy = it },
        onSuccess = { lat, lng ->
            coroutineScope.launch {
                busy = true
                try {
                    val res = if (isSecond) {
                        NetworkClient.authApi.setSecondNeighborhood(SetNeighborhoodRequest(lat, lng))
                    } else {
                        NetworkClient.authApi.setNeighborhood(SetNeighborhoodRequest(lat, lng))
                    }
                    busy = false
                    (if (isSecond) res.user.secondNeighborhood else res.user.neighborhood)?.let(onDone)
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
            Text(if (isSecond) "Add a second neighborhood" else "Set your neighborhood", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isSecond) "Share a second real place -- like work -- to see what's happening there too." else "Share your real location once to see what's happening near you.",
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
