package rw.itunda.feature.shop.impl

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.StatusBadge
import kotlin.math.roundToInt
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.QtyButton
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.components.SearchAndCategoryChips
import rw.itunda.core.designsystem.components.SimpleLiveRiderMiniMap
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.components.StarRatingRow
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.BookingSlotDto
import rw.itunda.core.network.CreateBookingRequest
import rw.itunda.core.network.CreateProductSubscriptionRequest
import rw.itunda.core.network.DealProductDto
import rw.itunda.core.network.RecentlyViewedProductDto
import rw.itunda.core.network.RecentlyViewedProductsStore
import rw.itunda.core.network.TimeDealViewDto
import rw.itunda.core.network.MembershipDayStatusResponse
import rw.itunda.core.network.ProductSubscriptionDto
import rw.itunda.core.network.ProductSearchResultDto
import rw.itunda.core.network.FavoriteProductDto
import rw.itunda.core.network.MerchantBookingDto
import rw.itunda.core.network.MerchantBookingRatingDto
import rw.itunda.core.network.MerchantBookingReviewDto
import rw.itunda.core.network.SubmitBookingReviewRequest
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.CreateAffiliateLinkRequest
import rw.itunda.core.network.DecideOrderReturnRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ORDER_RETURN_REASON_CODES
import rw.itunda.core.network.OrderDto
import rw.itunda.core.network.OrderItemDto
import rw.itunda.core.network.OrderItemRequest
import rw.itunda.core.network.OrderReturnRequestDto
import rw.itunda.core.network.PlaceOrderRequest
import rw.itunda.core.network.ProductRatingResponse
import rw.itunda.core.network.AskProductInquiryRequest
import rw.itunda.core.network.MerchantBillingPlanDto
import rw.itunda.core.network.MerchantBillingSubscriptionDto
import rw.itunda.core.network.NearbyMerchantAdDto
import rw.itunda.core.network.ProductInquiryDto
import rw.itunda.core.network.ProductReviewDto
import rw.itunda.core.network.CollectPaymentRequest
import rw.itunda.core.network.CollectPaymentResultDto
import rw.itunda.core.network.MerchantCouponPreviewDto
import rw.itunda.core.network.MerchantCouponViewDto
import rw.itunda.core.network.PaymentIntentPreviewResponse
import rw.itunda.core.network.RequestOrderReturnRequest
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.StaticQrPayRequest
import rw.itunda.core.network.SubmitProductReviewRequest
import rw.itunda.core.network.UpdateOrderStatusRequest
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID

// Extracted from ShopScreen.kt (2026-08-19) -- the real "pay a merchant" UI
// (PayAMerchantSection, its 3 real sub-modes CODE/SCAN/STATIC_QR, Face Pay
// settings, and the shared ListingActionButtonShop button) was already fully
// self-contained -- no shared state with the rest of ShopScreen.kt -- so this is
// a zero-risk move, not a redesign, matching MapsScreen.kt's own MapStyle.kt/
// MapUiComponents.kt precedent. ListingActionButtonShop changed from private to
// internal (Kotlin file-scoped -> module-scoped) since BillingPlanRow (still in
// ShopScreen.kt) also calls it -- the only real cross-file reference found.
/**
 * Real "pay a merchant" -- the manual-code-entry alternative to camera QR scanning
 * (this app has no scanner), mirrors bank-mfe's `PayByCodeCard`/`PayByStaticQrCard`
 * exactly. bank-mfe already has both; this is the first Android client for either --
 * previously neither the dynamic per-sale flow nor the static QR flow existed anywhere
 * on this native consumer app. Coupon-preview-before-pay (bank-mfe's own
 * `previewPaymentIntent` flow, item 149/146) closed 2026-08-01 -- see PayByCodeCard's
 * own doc comment.
 */
@Composable
// Real fix (2026-08-11) -- no longer private. This is itunda's real, working
// payment-collection UI (pay-by-code, pay-by-static-QR, Face Pay) -- see
// ItundaAppScreen.kt's own PayTab doc comment for the full account of why it now
// lives here (this is where the real backend calls already were) AND is called
// directly by the app module's Pay tab, the same "app calls a public composable in
// a feature's own impl module directly" precedent SavingsAmountScreen already
// establishes for :features:payments:impl.
fun PayAMerchantSection(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
) {
    var paymentResult by remember { mutableStateOf<CollectPaymentResultDto?>(null) }
    // Real Face Pay -- see FacePaySettingsCard/PayByCodeCard's own doc comments. Lifted
    // here, same as bank-mfe's own ShoppingView, so this card and PayByCodeCard don't
    // each fetch enrollment status independently (PayByCodeCard would otherwise never
    // learn about an enrollment that happened in the same session until a full reload).
    var facePayEnrolled by remember { mutableStateOf<Boolean?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadFacePayStatus() {
        coroutineScope.launch {
            try {
                facePayEnrolled = NetworkClient.apiService.getFacePayStatus().enrolled
            } catch (e: Exception) {
                // Real, non-critical -- the toggle just won't render if this fails.
            }
        }
    }
    LaunchedEffect(Unit) { loadFacePayStatus() }

    val result = paymentResult
    if (result != null) {
        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Payment complete", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(result.merchantName, color = Ids.colors.textPrimary, fontSize = 14.sp)
                Text("%,.0f RWF".format(result.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (result.cashbackEarned > java.math.BigDecimal.ZERO) {
                    Text("+ %,.0f RWF cashback".format(result.cashbackEarned), color = Ids.colors.brand, fontSize = 13.sp)
                }
                ListingActionButtonShop("Done", false) { paymentResult = null }
            }
        }
        return
    }
    // Real "one thing, one page" fix (2026-08-09) -- found via a real-screen audit prompted
    // by the same direct user feedback that fixed Maps ("flower of info, you can't just put
    // everything on one page"): this section stacked two genuinely competing, fully-formed
    // payment forms (Pay by code, Pay by static QR) at once, plus a settings toggle. Face Pay
    // is kept always-visible -- it's a real account SETTING, not a competing "how do I pay"
    // action (it changes how Pay-by-code itself behaves, per that card's own copy) -- but
    // Code vs. static QR are now mutually exclusive via a real mode picker, matching Toss's
    // own resolution for this exact class of violation.
    // Real camera scanning added (2026-08-11) as the new default -- matches real
    // KakaoPay/Toss Pay's own "QR스캔" primary in-store flow (see the user's own
    // reference screenshot). CODE (typed fallback) and STATIC_QR stay for when a
    // camera isn't usable, same "no typing unless you have to" principle MY_CODE's
    // own tap-to-reveal already established for the customer's own code.
    var payMode by remember { mutableStateOf(PayMerchantMode.SCAN) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FacePaySettingsCard(enrolled = facePayEnrolled, onChanged = ::loadFacePayStatus)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(PayMerchantMode.SCAN to "Scan QR", PayMerchantMode.CODE to "Pay by code", PayMerchantMode.STATIC_QR to "Merchant ID").forEach { (mode, label) ->
                val active = payMode == mode
                Text(
                    label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (active) androidx.compose.ui.graphics.Color.White else Ids.colors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft)
                        .clickable { payMode = mode }
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        when (payMode) {
            PayMerchantMode.SCAN -> PayByScanCard(deviceStepUpHost = deviceStepUpHost, facePayEnrolled = facePayEnrolled ?: false, onPaid = { paymentResult = it })
            PayMerchantMode.CODE -> PayByCodeCard(deviceStepUpHost = deviceStepUpHost, facePayEnrolled = facePayEnrolled ?: false, onPaid = { paymentResult = it })
            PayMerchantMode.STATIC_QR -> PayByStaticQrCard(onPaid = { paymentResult = it })
        }
    }
}

private enum class PayMerchantMode { SCAN, CODE, STATIC_QR }

/**
 * Real Face Pay enroll/disable toggle -- see rw.itunda.merchant.FacePayService's own
 * doc comment. bank-mfe already has this; this is the first Android client. Enrolling
 * swaps Pay-by-code's own collect call to the Face Pay channel -- same manual code
 * entry, just a different real ledger channel label, matching bank-mfe's own honest
 * scope exactly (no device biometric prompt gates it on any client, itunda's own).
 */
@Composable
private fun FacePaySettingsCard(enrolled: Boolean?, onChanged: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (enrolled == null) {
        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(64.dp)) {}
        return
    }
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("😊 Face Pay", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (enrolled) "Enabled -- authorize payment codes with your face, no code re-entry needed" else "Not enabled on this account",
                        color = Ids.colors.textSecondary, fontSize = 12.sp,
                    )
                }
                ListingActionButtonShop(if (busy) "…" else if (enrolled) "Disable" else "Enable", busy, filled = !enrolled) {
                    busy = true
                    error = null
                    coroutineScope.launch {
                        try {
                            if (enrolled) NetworkClient.apiService.revokeFacePay() else NetworkClient.apiService.enrollFacePay()
                            onChanged()
                        } catch (e: Exception) {
                            error = "Could not update Face Pay."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        }
    }
}

private fun couponDiscountLabel(c: MerchantCouponPreviewDto): String =
    if (c.discountType == "PERCENT") "${c.discountValue}% off" else "%,.0f RWF off".format(c.discountValue)

/**
 * Real coupon-preview-before-pay (item 149/146) -- closes the deliberate scope-down
 * this composable's own doc comment previously named. Mirrors bank-mfe's PayByCodeCard
 * exactly: a non-Face-Pay code with real eligible coupons stops at a preview step
 * (merchant/amount + coupon picker) before the actual collect() call; Face Pay and a
 * code with zero eligible coupons both skip straight to a direct pay, same as
 * bank-mfe's own payDirect()/handleSubmit() branching.
 */
@Composable
private fun PayByCodeCard(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    facePayEnrolled: Boolean,
    onPaid: (CollectPaymentResultDto) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsDeviceVerification by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PaymentIntentPreviewResponse?>(null) }
    var eligibleCoupons by remember { mutableStateOf<List<MerchantCouponViewDto>>(emptyList()) }
    var selectedCouponId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun payDirect(couponId: String? = null) {
        needsDeviceVerification = false
        submitting = true
        error = null
        coroutineScope.launch {
            try {
                val idempotencyKey = UUID.randomUUID().toString()
                val result = if (facePayEnrolled) {
                    NetworkClient.apiService.collectWithFacePay(code.trim(), idempotencyKey)
                } else {
                    NetworkClient.apiService.collectPayment(code.trim(), idempotencyKey, CollectPaymentRequest(couponId))
                }
                code = ""
                preview = null
                eligibleCoupons = emptyList()
                selectedCouponId = null
                onPaid(result)
            } catch (e: HttpException) {
                if (isDeviceNotVerifiedError(e)) {
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

    fun submit() {
        error = null
        if (facePayEnrolled) {
            payDirect()
            return
        }
        submitting = true
        coroutineScope.launch {
            try {
                val r = NetworkClient.apiService.previewPaymentIntent(code.trim())
                val eligible = r.coupons.filter { it.eligible && !it.alreadyRedeemed }
                if (eligible.isEmpty()) {
                    payDirect()
                } else {
                    preview = r
                    eligibleCoupons = eligible
                    submitting = false
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
                submitting = false
            } catch (e: IOException) {
                error = "Could not look up this payment code."
                submitting = false
            }
        }
    }

    fun cancelPreview() {
        preview = null
        eligibleCoupons = emptyList()
        selectedCouponId = null
        error = null
    }

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pay by code", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                if (facePayEnrolled) {
                    "Face Pay is on -- enter the code the merchant shows you to authorize with your face."
                } else {
                    "No scanner handy? Enter the payment code the merchant shows you to pay instantly and earn cashback."
                },
                color = Ids.colors.textSecondary, fontSize = 12.sp,
            )
            val currentPreview = preview
            if (currentPreview != null) {
                Text(currentPreview.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("%,.0f RWF".format(currentPreview.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Apply a coupon?", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedCouponId == null, onClick = { selectedCouponId = null })
                    Text("No coupon", color = Ids.colors.textPrimary, fontSize = 13.sp)
                }
                eligibleCoupons.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedCouponId == c.coupon.id, onClick = { selectedCouponId = c.coupon.id })
                        Text("${c.coupon.title} -- ${couponDiscountLabel(c.coupon)}", color = Ids.colors.textPrimary, fontSize = 13.sp)
                    }
                }
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListingActionButtonShop(if (submitting) "Paying…" else "Pay", submitting, filled = true) { payDirect(selectedCouponId) }
                    ListingActionButtonShop("Cancel", submitting, filled = false) { cancelPreview() }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IdsTextField(value = code, onValueChange = { code = it }, label = "Payment code", modifier = Modifier.weight(1f))
                    ListingActionButtonShop(
                        if (submitting) (if (facePayEnrolled) "Authorizing…" else "Paying…") else if (facePayEnrolled) "😊 Pay" else "Pay",
                        submitting || code.isBlank(), filled = true,
                    ) { submit() }
                }
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            }
        }
    }
    // Real fix (2026-08-10) -- see MultiCartView's own identical fix above for the
    // full account. payDirect resets needsDeviceVerification itself.
    deviceStepUpHost(needsDeviceVerification, { needsDeviceVerification = false }, { payDirect(selectedCouponId) })
}

/**
 * Real camera scanning for the existing merchant-generated PaymentIntent QR
 * (`itunda://pay?intentId=...`, see merchantapp's own `paymentIntentQrPayload` doc
 * comment) -- this is the exact same real `collectPayment`/`collectWithFacePay`
 * charge PayByCodeCard already makes, just sourced from a scanned QR instead of a
 * typed code, closing the real "no scanner" gap that card's own copy used to admit
 * ("No scanner handy? Enter the code..."). Deliberately mirrors PayByCodeCard's
 * device-step-up/coupon-preview handling rather than a simplified path, since it's
 * real money movement and the two entry points should behave identically.
 */
@Composable
private fun PayByScanCard(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    facePayEnrolled: Boolean,
    onPaid: (CollectPaymentResultDto) -> Unit,
) {
    var scannedIntentId by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsDeviceVerification by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PaymentIntentPreviewResponse?>(null) }
    var eligibleCoupons by remember { mutableStateOf<List<MerchantCouponViewDto>>(emptyList()) }
    var selectedCouponId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun payDirect(couponId: String? = null) {
        val intentId = scannedIntentId ?: return
        needsDeviceVerification = false
        submitting = true
        error = null
        coroutineScope.launch {
            try {
                val idempotencyKey = UUID.randomUUID().toString()
                val result = if (facePayEnrolled) {
                    NetworkClient.apiService.collectWithFacePay(intentId, idempotencyKey)
                } else {
                    NetworkClient.apiService.collectPayment(intentId, idempotencyKey, CollectPaymentRequest(couponId))
                }
                scannedIntentId = null
                preview = null
                eligibleCoupons = emptyList()
                selectedCouponId = null
                onPaid(result)
            } catch (e: HttpException) {
                if (isDeviceNotVerifiedError(e)) {
                    needsDeviceVerification = true
                } else {
                    error = superAppErrorMessage(e)
                    scannedIntentId = null
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
                scannedIntentId = null
            } finally {
                submitting = false
            }
        }
    }

    fun handleScanned(rawValue: String) {
        val intentId = try {
            android.net.Uri.parse(rawValue).getQueryParameter("intentId")
        } catch (e: Exception) {
            null
        }
        if (intentId.isNullOrBlank()) {
            error = "That doesn't look like an itunda payment QR code."
            return
        }
        error = null
        scannedIntentId = intentId
        if (facePayEnrolled) {
            payDirect()
            return
        }
        submitting = true
        coroutineScope.launch {
            try {
                val r = NetworkClient.apiService.previewPaymentIntent(intentId)
                val eligible = r.coupons.filter { it.eligible && !it.alreadyRedeemed }
                if (eligible.isEmpty()) {
                    payDirect()
                } else {
                    preview = r
                    eligibleCoupons = eligible
                    submitting = false
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
                scannedIntentId = null
                submitting = false
            } catch (e: IOException) {
                error = "Could not look up this payment code."
                scannedIntentId = null
                submitting = false
            }
        }
    }

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scan a merchant's QR", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val currentPreview = preview
            when {
                currentPreview != null -> {
                    Text(currentPreview.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("%,.0f RWF".format(currentPreview.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Apply a coupon?", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedCouponId == null, onClick = { selectedCouponId = null })
                        Text("No coupon", color = Ids.colors.textPrimary, fontSize = 13.sp)
                    }
                    eligibleCoupons.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedCouponId == c.coupon.id, onClick = { selectedCouponId = c.coupon.id })
                            Text("${c.coupon.title} -- ${couponDiscountLabel(c.coupon)}", color = Ids.colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                    error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ListingActionButtonShop(if (submitting) "Paying…" else "Pay", submitting, filled = true) { payDirect(selectedCouponId) }
                        ListingActionButtonShop("Cancel", submitting, filled = false) {
                            preview = null; eligibleCoupons = emptyList(); selectedCouponId = null; scannedIntentId = null; error = null
                        }
                    }
                }
                submitting -> {
                    Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                        Text(if (facePayEnrolled) "Authorizing…" else "Paying…", color = Ids.colors.textSecondary, fontSize = 14.sp)
                    }
                }
                else -> {
                    Text(
                        "Point your camera at the merchant's payment QR code.",
                        color = Ids.colors.textSecondary, fontSize = 12.sp,
                    )
                    CameraQrScanner(
                        onScanned = ::handleScanned,
                        modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
                }
            }
        }
    }
    deviceStepUpHost(needsDeviceVerification, { needsDeviceVerification = false }, { payDirect(selectedCouponId) })
}

@Composable
private fun PayByStaticQrCard(onPaid: (CollectPaymentResultDto) -> Unit) {
    var merchantId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pay a merchant's static QR", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "For a merchant with one permanent code (like a market stall) -- enter their merchant ID and how much you're paying.",
                color = Ids.colors.textSecondary, fontSize = 12.sp,
            )
            IdsTextField(value = merchantId, onValueChange = { merchantId = it }, label = "Merchant ID", modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IdsTextField(value = amount, onValueChange = { amount = it }, label = "Amount (RWF)", keyboardType = KeyboardType.Number, isAmount = true, modifier = Modifier.weight(1f))
                ListingActionButtonShop(
                    if (submitting) "Paying…" else "Pay",
                    submitting || merchantId.isBlank() || amount.toBigDecimalOrNull() == null,
                    filled = true,
                ) {
                    val numericAmount = amount.toBigDecimalOrNull()
                    if (numericAmount == null || numericAmount <= java.math.BigDecimal.ZERO) {
                        error = "Enter a valid amount."
                        return@ListingActionButtonShop
                    }
                    submitting = true
                    error = null
                    coroutineScope.launch {
                        try {
                            val result = NetworkClient.apiService.payByStaticQr(merchantId.trim(), UUID.randomUUID().toString(), StaticQrPayRequest(numericAmount))
                            merchantId = ""
                            amount = ""
                            onPaid(result)
                        } catch (e: HttpException) {
                            error = superAppErrorMessage(e)
                        } catch (e: IOException) {
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally {
                            submitting = false
                        }
                    }
                }
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        }
    }
}

@Composable
internal fun ListingActionButtonShop(label: String, disabled: Boolean, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (disabled) Ids.colors.textTertiary else if (filled) Ids.colors.brand else Ids.colors.surfaceSoft)
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (filled || disabled) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}
