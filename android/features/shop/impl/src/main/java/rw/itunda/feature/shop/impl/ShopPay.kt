package rw.itunda.feature.shop.impl

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
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

// Split from the original ShopPay.kt (2026-08-19, same pass) -- the 3 real
// payment-collection UIs (PayByCodeCard/PayByScanCard/PayByStaticQrCard) moved to
// ShopPayCards.kt so ShopPay.kt itself stays under file-size-lint.py's 500-line
// guideline instead of the split just re-creating the exact problem it fixed.

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
        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- an
        // inline confirmation panel replacing this screen's content, not a real
        // Dialog (docs/UI_UX_GUIDELINES.md §10).
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Payment complete", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(result.merchantName, color = Ids.colors.textPrimary, fontSize = 14.sp)
            Text(String.format(Locale.US, "%,.0f RWF", result.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (result.cashbackEarned > java.math.BigDecimal.ZERO) {
                Text(String.format(Locale.US, "+ %,.0f RWF cashback", result.cashbackEarned), color = Ids.colors.brand, fontSize = 13.sp)
            }
            ListingActionButtonShop("Done", false) { paymentResult = null }
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
                        .pressScaleClickable { payMode = mode }
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
    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- the
    // pay-mode picker Row right below acts as the next section's natural visual
    // start (docs/UI_UX_GUIDELINES.md §10).
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    } catch (e: HttpException) {
                        error = superAppErrorMessage(e)
                    } catch (e: IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } finally {
                        busy = false
                    }
                }
            }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
    }
}

internal fun couponDiscountLabel(c: MerchantCouponPreviewDto): String =
    if (c.discountType == "PERCENT") "${c.discountValue}% off" else String.format(Locale.US, "%,.0f RWF off", c.discountValue)

/**
 * Real coupon-preview-before-pay (item 149/146) -- closes the deliberate scope-down
 * this composable's own doc comment previously named. Mirrors bank-mfe's PayByCodeCard
 * exactly: a non-Face-Pay code with real eligible coupons stops at a preview step
 * (merchant/amount + coupon picker) before the actual collect() call; Face Pay and a
 * code with zero eligible coupons both skip straight to a direct pay, same as
 * bank-mfe's own payDirect()/handleSubmit() branching.
 */
@Composable
internal fun ListingActionButtonShop(label: String, disabled: Boolean, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (disabled) Ids.colors.textTertiary else if (filled) Ids.colors.brand else Ids.colors.surfaceSoft)
            .pressScaleClickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (filled || disabled) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}
