package rw.itunda.feature.shop.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
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
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.QtyButton
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.BookingSlotDto
import rw.itunda.core.network.CreateBookingRequest
import rw.itunda.core.network.CreateProductSubscriptionRequest
import rw.itunda.core.network.ProductSubscriptionDto
import rw.itunda.core.network.MerchantBookingRatingDto
import rw.itunda.core.network.MerchantBookingReviewDto
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.MerchantCouponPreviewDto
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID




/**
 * Real local-business appointment booking (2026-07-25) -- closes the "business profile
 * + real booking" gap independently converged on by Naver Smart Place, Kakao Hair Shop,
 * and Karrot's Business Profile research (docs/DESIGN_REFERENCES.md). Date picker is a
 * plain next-14-days strip (no calendar widget -- itunda has no calendar-sync/external
 * scheduling to justify one); slots come straight from the real backend-computed
 * availability (see MerchantBookingService.getAvailableSlots), never client-guessed.
 */
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
            "${service.name} · ${service.durationMinutes} min · %,.0f RWF".format(service.price),
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
                        if (c.discountType == "PERCENT") "${c.discountValue}% off" else "%,.0f RWF off".format(c.discountValue),
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

// Real dedicated product-detail screen (2026-07-21), closing
// docs/DESIGN_REFERENCES.md Section 5 recommendation #6 -- until now tapping a product
// anywhere in Commerce only ever revealed the flat catalog grid's inline qty stepper;
// there was no tap-through view showing the full-size image, discount breakdown, a
// description, and written reviews together. Reuses already-proven pieces
// (ProductImageThumb larger, ProductPriceRow, ProductRatingBadge which already lazily
// expands into the written-review list, QtyButton) rather than inventing new ones --
// this is a real second surface for the same real data, not new business logic.
// Real Coupang 정기배송 (subscribe & save) -- see core/network's ProductSubscriptionDto
// doc comment. A minimal delivery-address prompt via AlertDialog rather than a full
// address form, matching bank-mfe's own compact-card scope (fixed qty=1, every 30d).
@Composable
internal fun SubscribeAndSaveButton(merchantId: String, productId: String) {
    var showDialog by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("✓ Subscribed -- delivered every 30 days", color = Ids.colors.success, fontSize = 13.sp)
        return
    }

    TextButton(onClick = { showDialog = true }) {
        Text("Subscribe & save (every 30 days)", fontSize = 13.sp)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Subscribe & save") },
            text = {
                Column {
                    Text("Delivered every 30 days. Cancel anytime.", color = Ids.colors.textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    IdsTextField(value = address, onValueChange = { address = it }, label = "Delivery address")
                    error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            },
            confirmButton = {
                TextButton(enabled = address.isNotBlank() && !busy, onClick = {
                    busy = true
                    coroutineScope.launch {
                        try {
                            NetworkClient.apiService.subscribeToProduct(
                                CreateProductSubscriptionRequest(merchantId, productId, 1, 30, address.trim()),
                                UUID.randomUUID().toString(),
                            )
                            done = true
                            showDialog = false
                        } catch (e: HttpException) {
                            error = superAppErrorMessage(e)
                        } catch (e: IOException) {
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(if (busy) "…" else "Subscribe") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        )
    }
}

