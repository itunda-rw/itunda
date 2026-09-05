package rw.itunda.feature.maps.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.StarRatingRow
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.BookingSlotDto
import rw.itunda.core.network.CreateBookingRequest
import rw.itunda.core.network.MerchantBookingDto
import rw.itunda.core.network.MerchantBookingRatingDto
import rw.itunda.core.network.MerchantBookingReviewDto
import rw.itunda.core.network.MerchantCouponPreviewDto
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.SubmitBookingReviewRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

/**
 * Real local-business appointment booking (2026-07-25) -- closes the "business profile
 * + real booking" gap independently converged on by Naver Smart Place, Kakao Hair Shop,
 * and Karrot's Business Profile research (docs/DESIGN_REFERENCES.md). Date picker is a
 * plain next-14-days strip (no calendar widget -- itunda has no calendar-sync/external
 * scheduling to justify one); slots come straight from the real backend-computed
 * availability (see MerchantBookingService.getAvailableSlots), never client-guessed.
 *
 * Moved here from :features:shop:impl (2026-08-25, direct user feedback: "booking...
 * that's features that supposed to be in itunda place not in itunda shopping") -- this
 * file's own original doc comment already named the real sourcing (Naver Smart Place/
 * Karrot Business Profile, both real *local-place* products, never a nationwide online
 * catalog), so a real-time appointment at a physical location belongs in itunda Place,
 * not Shop's "completely online" catalog. The backend (MerchantBookingController/
 * MerchantBookingService, :merchant module) was already vertical-neutral -- only this
 * client-side UI ownership needed to move, not the API.
 */
// Thin full-screen gate (2026-08-25) -- keeps MapScreen.kt's own call site to one
// line, same "extract instead of grow a baselined file" discipline
// docs/ARCHITECTURE_GUIDELINES.md §2 requires. Returns whether it rendered (and thus
// whether the caller should `return` early), same shape as MapScreen's other
// early-return checks.
@Composable
internal fun MerchantBookingGate(merchant: ShoppingMerchantDto?, service: MerchantProductDto?, onDismiss: () -> Unit): Boolean {
    if (merchant == null || service == null) return false
    MerchantBookingFlowView(merchant, service, onDismiss, onDismiss)
    return true
}

@Composable
internal fun MerchantBookingFlowView(
    merchant: ShoppingMerchantDto,
    service: MerchantProductDto,
    onBack: () -> Unit,
    onBooked: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val today = remember { java.time.LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }
    var slots by remember { mutableStateOf<List<BookingSlotDto>?>(null) }
    var selectedSlot by remember { mutableStateOf<BookingSlotDto?>(null) }
    var notes by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var booked by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun loadSlots() {
        selectedSlot = null
        slots = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getBookingSlots(merchant.merchantId, service.id, selectedDate.toString())
                slots = if (res.success) res.slots else emptyList()
            } catch (e: Exception) {
                error = "Couldn't load available times."
                slots = emptyList()
            }
        }
    }
    LaunchedEffect(selectedDate) { loadSlots() }

    if (booked) {
        AlertDialog(
            onDismissRequest = onBooked,
            title = { Text("Booking requested") },
            text = { Text("${service.name} on $selectedDate at ${selectedSlot?.startTime?.take(5)} -- ${merchant.businessName} will confirm shortly.") },
            confirmButton = { TextButton(onClick = onBooked) { Text("Done") } },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar("Book ${service.name}", onBack)
        Text(
            String.format(Locale.US, "${service.name} · ${service.durationMinutes} min · %,.0f RWF", service.price),
            color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        MerchantBookingInfoSection(merchant.merchantId)
        Text("Choose a date", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (0 until 14).map { today.plusDays(it.toLong()) }.forEach { date ->
                val selected = date == selectedDate
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Ids.colors.brand else Ids.colors.surface)
                        .pressScaleClickable { selectedDate = date }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                        color = if (selected) Color.White else Ids.colors.textSecondary, fontSize = 11.sp,
                    )
                    Text(date.dayOfMonth.toString(), color = if (selected) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
        Text("Choose a time", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Box(modifier = Modifier.weight(1f)) {
            val slotList = slots
            if (slotList == null) {
                SkeletonBlock()
            } else if (slotList.isEmpty()) {
                EmptyState("No open times on this date — try another day.", icon = Icons.Outlined.Storefront)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    gridItems(slotList) { slot ->
                        val selected = slot == selectedSlot
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Ids.colors.brand else Ids.colors.surface)
                                .pressScaleClickable { selectedSlot = slot }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(slot.startTime.take(5), color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        IdsTextField(
            value = notes,
            onValueChange = { if (it.length <= 500) notes = it },
            label = "Notes (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (submitting || selectedSlot == null) Ids.colors.textTertiary else Ids.colors.brand)
                .pressScaleClickable(enabled = !submitting && selectedSlot != null) {
                    val slot = selectedSlot ?: return@pressScaleClickable
                    submitting = true
                    error = null
                    coroutineScope.launch {
                        try {
                            val res = NetworkClient.apiService.createBooking(
                                CreateBookingRequest(merchant.merchantId, service.id, selectedDate.toString(), slot.startTime, notes.trim().ifBlank { null }),
                            )
                            if (res.success) booked = true
                        } catch (e: HttpException) {
                            error = superAppErrorMessage(e)
                        } catch (e: IOException) {
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally {
                            submitting = false
                        }
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (submitting) "Requesting…" else "Request booking", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

// Real pre-booking browsing (item 231) -- found via a defined-but-uncalled-endpoint
// sweep, see ApiService.getMerchantReviews/getCouponsForCustomer's own doc comments.
// bank-mfe shipped this first (2026-08-05); this is the Android port.
@Composable
internal fun MerchantBookingInfoSection(merchantId: String) {
    var reviews by remember { mutableStateOf<List<MerchantBookingReviewDto>?>(null) }
    var rating by remember { mutableStateOf<MerchantBookingRatingDto?>(null) }
    var coupons by remember { mutableStateOf<List<MerchantCouponPreviewDto>?>(null) }

    LaunchedEffect(merchantId) {
        try {
            val res = NetworkClient.apiService.getMerchantReviews(merchantId)
            if (res.success) {
                reviews = res.reviews
                rating = res.rating
            }
        } catch (e: Exception) {
            reviews = emptyList()
        }
        try {
            val res = NetworkClient.apiService.getCouponsForCustomer(merchantId)
            if (res.success) coupons = res.coupons
        } catch (e: Exception) {
            coupons = emptyList()
        }
    }

    val couponList = coupons
    val reviewList = reviews
    if (couponList.isNullOrEmpty() && reviewList.isNullOrEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        if (!couponList.isNullOrEmpty()) {
            Text("Coupons for you", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            couponList.forEach { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(c.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        c.description?.let { Text(it, color = Ids.colors.textSecondary, fontSize = 11.sp) }
                    }
                    Text(
                        if (c.discountType == "PERCENT") "${c.discountValue}% off" else String.format(Locale.US, "%,.0f RWF off", c.discountValue),
                        color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
            }
        }
        if (!reviewList.isNullOrEmpty()) {
            Text(
                "Reviews" + (rating?.average?.let { " · ⭐ %.1f (%d)".format(it, rating?.count ?: 0) } ?: ""),
                color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            )
            reviewList.take(3).forEach { r ->
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surface).padding(12.dp)) {
                    Text("${"⭐".repeat(r.rating)} · ${r.serviceName}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    r.comment?.let { Text(it, color = Ids.colors.textSecondary, fontSize = 12.sp) }
                    r.ownerReply?.let { Text("↳ $it", color = Ids.colors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
}

internal val BOOKING_STATUS_LABEL = mapOf(
    "REQUESTED" to "Requested",
    "CONFIRMED" to "Confirmed",
    "DECLINED" to "Declined",
    "CANCELLED" to "Cancelled",
    "COMPLETED" to "Completed",
)

@Composable
internal fun MyBookingsView() {
    var bookings by remember { mutableStateOf<List<MerchantBookingDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyBookings()
                if (res.success) bookings = res.bookings
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun cancel(bookingId: String) {
        cancellingId = bookingId
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.cancelBooking(bookingId)
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                cancellingId = null
            }
        }
    }

    val list = bookings
    if (list.isNullOrEmpty() && error == null) return
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text("Bookings", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
        if (error != null) {
            ErrorCard(error!!, onRetry = ::load)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list!!.forEach { b ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(b.serviceName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(BOOKING_STATUS_LABEL[b.status] ?: b.status, color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text("${b.bookingDate} at ${b.startTime.take(5)}", color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        if (b.status == "REQUESTED" || b.status == "CONFIRMED") {
                            Box(
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Ids.colors.danger)
                                    .pressScaleClickable(enabled = cancellingId != b.id) { cancel(b.id) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    if (cancellingId == b.id) "Cancelling…" else "Cancel booking",
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                )
                            }
                        }
                        if (b.status == "COMPLETED") {
                            BookingReviewButton(bookingId = b.id)
                        }
                    }
                    Divider(color = Ids.colors.divider, thickness = 0.5.dp)
                }
            }
        }
    }
}

// Real customer-side post-appointment review (item 143) -- see lib/booking.ts's own
// doc comment on bank-mfe. Mirrors ProductReviewRow's exact shape (star rating +
// optional comment, a real BOOKING_ALREADY_REVIEWED 409 is treated as already-done).
@Composable
internal fun BookingReviewButton(bookingId: String) {
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("Thanks for your review!", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        return
    }
    if (!open) {
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ids.colors.textTertiary)
                .pressScaleClickable { open = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Rate this visit", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
        StarRatingRow(rating) { rating = it }
        IdsTextField(
            value = comment,
            onValueChange = { comment = it },
            label = "How was it? (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).pressScaleClickable { open = false }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (submitting) Ids.colors.textTertiary else Ids.colors.brand)
                    .pressScaleClickable(enabled = !submitting) {
                        if (rating == 0) {
                            error = "Pick a star rating."
                            return@pressScaleClickable
                        }
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.submitBookingReview(bookingId, SubmitBookingReviewRequest(rating, comment.trim().ifBlank { null }))
                                done = true
                            } catch (e: HttpException) {
                                if (e.code() == 409) {
                                    done = true
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
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (submitting) "Submitting…" else "Submit review", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}
