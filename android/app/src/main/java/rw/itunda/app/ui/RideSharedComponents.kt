package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.itundaface.ClockGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddRideTrustedContactRequest
import rw.itunda.core.network.MapBookmarkDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RequestRideTripRequest
import rw.itunda.core.network.RideDailyEarnings
import rw.itunda.core.network.RideDriverDto
import rw.itunda.core.network.RideDriverRatingResponse
import rw.itunda.core.network.RideStopRequestDto
import rw.itunda.core.network.RideTripDto
import rw.itunda.core.network.RideTripReviewDto
import rw.itunda.core.network.RideTripStopDto
import rw.itunda.core.network.RideTrustedContactDto
import rw.itunda.core.network.SetRideDriverAvailabilityRequest
import rw.itunda.core.network.SetRideDriverDestinationRequest
import rw.itunda.core.network.StartRideTripRequest
import rw.itunda.core.network.SubmitRideReviewRequest
import rw.itunda.core.network.UpdateRideDriverLocationRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.time.Instant
import java.util.UUID

// Real fix (2026-08-26): split out of RideScreen.kt once that file grew past its
// file-size-lint baseline. Shared components used by BOTH RidePassengerContent
// and RideDriverContent (RideTripCard, driver rating, trip status label/color,
// money formatting) plus the review row / trusted-contacts section used only by
// the passenger flow. All flipped private -> internal since their callers stay
// in the original file.

// Real Kakao T-style post-trip driver rating (item 213) -- see this file's own doc
// comment. Simple 5-star tap-to-rate row, matching bank-mfe's RideReviewPrompt shape.
@Composable
internal fun RideReviewRow(busy: Boolean, onSubmit: (Int, String) -> Unit) {
    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Rate your driver", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (n in 1..5) {
                Text(
                    "★", fontSize = 20.sp, color = if (n <= rating) Color(0xFFFFC107) else Ids.colors.textSecondary,
                    modifier = Modifier.pressScaleClickable { rating = n },
                )
            }
        }
        if (rating > 0) {
            IdsTextField(value = comment, onValueChange = { comment = it }, label = "Comment (optional)", modifier = Modifier.fillMaxWidth())
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                    .pressScaleClickable(enabled = !busy) { onSubmit(rating, comment) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (busy) "Submitting…" else "Submit rating", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

// Real Uber Safety "Trusted Contacts" (item 160, help.uber.com) -- a persistent,
// up-to-5 contact list set up once, distinct from the per-share conversation pick a
// completed shareTripStatus already offers. First Android client for RideController's
// trusted-contacts endpoints; mirrors bank-mfe's TrustedContactsSection (2026-08-18)
// exactly, adapted to this file's own Compose/Box-button conventions rather than React.
@Composable
internal fun TrustedContactsSection(
    contacts: List<RideTrustedContactDto>?,
    phone: String, onPhoneChange: (String) -> Unit,
    name: String, onNameChange: (String) -> Unit,
    adding: Boolean, onAdd: () -> Unit,
    removingContactId: String?, onRemove: (String) -> Unit,
    error: String?,
) {
    // Real fix (flat-design sweep): dropped the Card wrapper -- a section on an
    // otherwise-flat linear screen.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Trusted contacts", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Add up to 5 people who can get your live trip status with one tap.",
                color = Ids.colors.textSecondary, fontSize = 12.sp,
            )
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            val list = contacts
            if (list == null) {
                Text("Loading…", color = Ids.colors.textSecondary, fontSize = 12.sp)
            } else {
                list.forEach { contact ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ids.colors.surfaceSoft)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(contact.contactName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            if (removingContactId == contact.id) "…" else "Remove",
                            color = Ids.colors.danger, fontSize = 12.sp,
                            modifier = Modifier.pressScaleClickable(enabled = removingContactId != contact.id) { onRemove(contact.id) },
                        )
                    }
                }
                if (list.size < 5) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IdsTextField(value = phone, onValueChange = onPhoneChange, label = "Phone number", modifier = Modifier.weight(1f))
                        IdsTextField(value = name, onValueChange = onNameChange, label = "Name", modifier = Modifier.weight(1f))
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (phone.isNotBlank()) Ids.colors.brand else Ids.colors.surfaceSoft)
                            .pressScaleClickable(enabled = !adding && phone.isNotBlank()) { onAdd() }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (adding) "Adding…" else "Add trusted contact", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                } else {
                    Text("You've reached the limit of 5 trusted contacts.", color = Ids.colors.textSecondary, fontSize = 12.sp)
                }
            }
    }
}

// Real fix (flat-design sweep): dropped the Card wrapper -- used both as the sole
// "Your ride" status widget and as a repeated past/active-trips list row, and a
// per-row card was the same anti-pattern already fixed for ShellSection. Flat rows
// with spacing alone match this session's established list convention.
@Composable
internal fun RideTripCard(trip: RideTripDto, stops: List<RideTripStopDto>? = null, action: (@Composable () -> Unit)? = null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(trip.pickupAddress, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            stops?.forEach { stop ->
                Text(
                    "${if (stop.arrivedAt != null) "✓" else "→"} ${stop.address}", fontSize = 12.sp,
                    color = if (stop.arrivedAt != null) Ids.colors.textSecondary else Ids.colors.textPrimary,
                )
            }
            Text("→ ${trip.dropoffAddress}", color = Ids.colors.textSecondary, fontSize = 13.sp)
            val scheduledFor = trip.scheduledFor
            if (scheduledFor != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    ClockGlyph(size = 12.dp)
                    Text("Scheduled for ${scheduledFor.take(16).replace("T", " ")}", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Real Toss reference (2026-08-11, "토스 인터랙션 디자이너의 모든 것"): a
                // real-world matching wait (finding a driver, here) is exactly the shape
                // of their own "실시간 대출 처리" (real-time loan processing) fix -- they
                // replaced a static wait screen with a dynamic indicator showing the
                // process is actively running, instead of a screen that looks identical
                // whether it's working or frozen. This was previously plain static text
                // for a wait whose real duration is genuinely unknown (depends on a real
                // driver accepting) -- a subtle pulse is the honest signal here, not a
                // fake progress bar with no real percentage to report.
                val searching = trip.status == "REQUESTED" && trip.scheduledFor == null
                val pulseAlpha = if (searching) {
                    val transition = rememberInfiniteTransition(label = "searchingPulse")
                    val alpha by transition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 900),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "searchingPulseAlpha",
                    )
                    alpha
                } else 1f
                Text(
                    rideTripStatusLabel(trip),
                    color = rideTripStatusColor(trip.status),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.alpha(pulseAlpha),
                )
                Text("${formatMoneyRide(trip.fare)} RWF · ${"%.1f".format(trip.distanceKm)} km", color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
            action?.let {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                it()
            }
        }
}

// Real "meet your driver" rating + reviews during an active trip (item 233) -- found
// via the uncalled-endpoint sweep, see ApiService.getRideDriverReviews's own doc
// comment. bank-mfe shipped this first (2026-08-05); this is the Android port. Honest
// v1: RideDriverDto has no name/vehicle field on the backend at all, so this shows the
// driver's real rating + written reviews only, never a name that doesn't exist.
@Composable
internal fun DriverRatingSection(driverId: String) {
    var rating by remember { mutableStateOf<RideDriverRatingResponse?>(null) }
    var reviews by remember { mutableStateOf<List<RideTripReviewDto>?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(driverId) {
        try { rating = NetworkClient.apiService.getRideDriverRating(driverId) } catch (_: Exception) {}
    }

    val r = rating
    if (r == null || r.count == 0L) return

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            "★ %.1f".format(r.average ?: 0.0) + " (${r.count} rating${if (r.count == 1L) "" else "s"}) ${if (expanded) "▲" else "▼"}",
            color = androidx.compose.ui.graphics.Color(0xFFFFC107), fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier.pressScaleClickable {
                expanded = !expanded
                if (expanded && reviews == null) {
                    coroutineScope.launch {
                        try { reviews = NetworkClient.apiService.getRideDriverReviews(driverId).reviews } catch (_: Exception) { reviews = emptyList() }
                    }
                }
            },
        )
        if (expanded) {
            val list = reviews
            if (list == null) {
                Text("Loading reviews…", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            } else if (list.isEmpty()) {
                Text("No written reviews yet.", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            } else {
                Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { review ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(Ids.colors.surface).padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text("⭐".repeat(review.rating), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            review.comment?.let { Text(it, color = Ids.colors.textPrimary, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

internal fun rideTripStatusLabel(trip: RideTripDto): String {
    if (trip.status == "REQUESTED" && trip.scheduledFor != null) return "Scheduled"
    return when (trip.status) {
        "REQUESTED" -> "Finding a driver…"
        "DRIVER_ASSIGNED" -> "Driver assigned"
        "IN_PROGRESS" -> "In progress"
        "COMPLETED" -> "Completed"
        "CANCELLED" -> "Cancelled"
        else -> trip.status
    }
}

@Composable
internal fun rideTripStatusColor(status: String): Color = when (status) {
    "COMPLETED" -> Ids.colors.success
    "CANCELLED" -> Ids.colors.danger
    else -> Ids.colors.brand
}

internal fun formatMoneyRide(value: java.math.BigDecimal): String {
    val rounded = value.stripTrailingZeros()
    return if (rounded.scale() <= 0) "%,d".format(rounded.toBigInteger()) else "%,.2f".format(rounded)
}

// Real Uber post-trip tipping -- ported from bank-mfe (2026-09-03), see
// RideTripDto.tipAmount's own doc comment. Same real device step-up pattern every
// other money-moving action in this app needs (a tip is a real account-to-account
// transfer, gated by DeviceVerificationFilter same as TransferFlow).
private val RIDE_TIP_PRESETS = listOf(500, 1000, 2000)

@Composable
internal fun TipDriverPrompt(tripId: String, onTipped: () -> Unit) {
    var amount by remember { mutableStateOf<Int?>(null) }
    var customAmount by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun submit(overrideAmount: Int? = null) {
        val finalAmount = overrideAmount ?: amount ?: customAmount.toIntOrNull()
        if (finalAmount == null || finalAmount <= 0) return
        needsDeviceVerification = false
        submitting = true
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.tipRideDriver(
                    tripId,
                    rw.itunda.core.network.TipRideTripRequest(java.math.BigDecimal(finalAmount)),
                    UUID.randomUUID().toString(),
                )
                onTipped()
            } catch (e: HttpException) {
                if (rw.itunda.core.network.isDeviceNotVerifiedError(e)) {
                    needsDeviceVerification = true
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                submitting = false
            }
        }
    }

    if (needsDeviceVerification) {
        rw.itunda.core.designsystem.components.DeviceStepUpHost(
            visible = true,
            onDismiss = { needsDeviceVerification = false },
            onVerified = { submit() },
        )
        return
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Tip your driver", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            RIDE_TIP_PRESETS.forEach { preset ->
                val selected = amount == preset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Ids.colors.brand else Color.Transparent)
                        .pressScaleClickable(enabled = !submitting) { amount = preset; customAmount = ""; submit(preset) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("%,d".format(preset), color = if (selected) Color.White else Ids.colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IdsTextField(
                value = customAmount,
                onValueChange = { customAmount = it; amount = null },
                label = "Custom amount (RWF)",
                modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (submitting || (customAmount.toIntOrNull() ?: 0) <= 0) Ids.colors.textTertiary else Ids.colors.brand)
                    .pressScaleClickable(enabled = !submitting && (customAmount.toIntOrNull() ?: 0) > 0) { submit() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) { Text(if (submitting) "…" else "Tip", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}
