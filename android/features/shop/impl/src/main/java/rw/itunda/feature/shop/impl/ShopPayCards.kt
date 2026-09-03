package rw.itunda.feature.shop.impl

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
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.CameraQrScanner
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

// Extracted from ShopPay.kt (2026-08-19, same pass as its own extraction from
// ShopScreen.kt) -- see ShopPay.kt's own header comment. PayByCodeCard/
// PayByScanCard/PayByStaticQrCard changed from private to internal since
// PayAMerchantSection (staying in ShopPay.kt) calls all three as its 3 real
// pay-mode branches; couponDiscountLabel (staying in ShopPay.kt, internal for the
// same reason) is used by PayByCodeCard/PayByScanCard here.
@Composable
internal fun PayByCodeCard(
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
                error = "Couldn't reach itunda. Check your connection and try again."
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

    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a lone
    // form section (docs/UI_UX_GUIDELINES.md §10).
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().pressScaleClickable { selectedCouponId = null }) {
                    RadioButton(selected = selectedCouponId == null, onClick = { selectedCouponId = null })
                    Text("No coupon", color = Ids.colors.textPrimary, fontSize = 13.sp)
                }
                eligibleCoupons.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().pressScaleClickable { selectedCouponId = c.coupon.id }) {
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
internal fun PayByScanCard(
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
                error = "Couldn't reach itunda. Check your connection and try again."
                scannedIntentId = null
                submitting = false
            }
        }
    }

    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a lone
    // form section (docs/UI_UX_GUIDELINES.md §10).
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scan a merchant's QR", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val currentPreview = preview
            when {
                currentPreview != null -> {
                    Text(currentPreview.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("%,.0f RWF".format(currentPreview.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Apply a coupon?", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().pressScaleClickable { selectedCouponId = null }) {
                        RadioButton(selected = selectedCouponId == null, onClick = { selectedCouponId = null })
                        Text("No coupon", color = Ids.colors.textPrimary, fontSize = 13.sp)
                    }
                    eligibleCoupons.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().pressScaleClickable { selectedCouponId = c.coupon.id }) {
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
    deviceStepUpHost(needsDeviceVerification, { needsDeviceVerification = false }, { payDirect(selectedCouponId) })
}

@Composable
internal fun PayByStaticQrCard(onPaid: (CollectPaymentResultDto) -> Unit) {
    var merchantId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a lone
    // form section (docs/UI_UX_GUIDELINES.md §10).
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

