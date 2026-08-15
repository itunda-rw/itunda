package rw.itunda.merchant.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import rw.itunda.merchant.BuildConfig
import java.util.UUID

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly.
// A merchant owner logs into their existing itunda account first, then registers as
// a merchant via this app -- no separate registration screen for a brand-new itunda
// account, same "existing account only" design as :riderapp's own login screen.
// deviceId/deviceName added 2026-07-28 -- real device binding (see DeviceStore.kt),
// mirrors bank-mfe/merchant-mfe's own login() calls exactly.
data class LoginRequest(val phoneNumber: String, val password: String, val deviceId: String? = null, val deviceName: String? = null)
data class VerifyDeviceRequest(val password: String)
data class TrustedDeviceDto(val id: String, val deviceId: String, val deviceName: String?, val trusted: Boolean)
data class VerifyDeviceResponse(val success: Boolean, val device: TrustedDeviceDto)
data class PublicUser(val id: String, val phoneNumber: String, val firstName: String, val lastName: String)
data class AuthResponse(val message: String, val user: PublicUser, val accessToken: String, val refreshToken: String)

interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/v1/auth/devices/verify")
    suspend fun verifyDevice(@Body request: VerifyDeviceRequest): VerifyDeviceResponse
}

// Same real check the main app's rw.itunda.core.network.isDeviceNotVerifiedError
// establishes -- a 403 alone isn't enough (other real 403s exist elsewhere in this
// backend), the real ApiError.code field DeviceVerificationFilter actually sets is
// what's checked. Standalone copy since merchantapp doesn't depend on :core:network.
fun isDeviceNotVerifiedError(e: retrofit2.HttpException): Boolean {
    if (e.code() != 403) return false
    return try {
        val body = e.response()?.errorBody()?.string() ?: return false
        com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString == "DEVICE_NOT_VERIFIED"
    } catch (_: Exception) {
        false
    }
}

// Same real backend-message pass-through rw.itunda.core.network.apiErrorMessage
// establishes for the consumer app -- DeviceStepUpDialog here was still discarding a
// real, specific backend message (e.g. a device-verification-specific decline) in
// favor of a hardcoded bucket. Standalone copy since merchantapp doesn't depend on
// :core:network, same as isDeviceNotVerifiedError above.
fun apiErrorMessage(e: retrofit2.HttpException): String? = try {
    val body = e.response()?.errorBody()?.string() ?: return null
    com.google.gson.JsonParser.parseString(body).asJsonObject.get("message")?.asString
} catch (_: Exception) {
    null
}

// Generic ApiError.code reader, same shape as isDeviceNotVerifiedError above --
// used for Toss-style resolve-forward handling (e.g. MERCHANT_ALREADY_REGISTERED).
fun apiErrorCode(e: retrofit2.HttpException): String? = try {
    val body = e.response()?.errorBody()?.string() ?: return null
    com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString
} catch (_: Exception) {
    null
}

// Mirrors rw.itunda.merchant's real Merchant/MerchantProduct/PaymentIntent entities
// exactly (same field names merchant-mfe's own lib/merchant.ts already uses).
data class MerchantDto(
    val id: String,
    val ownerUserId: String,
    val businessName: String,
    val webhookUrl: String?,
    val kybVerified: Boolean,
    val createdAt: String,
    val category: String?,
    val feeRateOverride: Double? = null,
    // Real business location (POST /api/v1/merchant/location) -- merchant-mfe already
    // has this; needed here for MerchantAdService's own radius-targeted ads (item 147).
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Real Baemin Club-style participating-restaurant opt-in -- see EatsMembership.kt's
    // own doc comment. Found 2026-08-01 (dead-field sweep): real on merchant-mfe since
    // 2026-07-26, zero native UI on either merchant app until now.
    val participatesInEatsMembership: Boolean = false,
    // Real restaurant-card photo, min-order, boosted-cashback, and scheduled-orders
    // opt-in -- see MerchantService.setPhotoUrl/setMinOrderAmount/setCashbackRate/
    // setAcceptsScheduledOrders's own doc comments. Found 2026-08-01 (dead-field sweep):
    // real on the backend since 2026-07-21/07-26, zero client anywhere (not even
    // merchant-mfe) until now.
    val photoUrl: String? = null,
    val minOrderAmount: Double? = null,
    val cashbackRate: Double? = null,
    val acceptsScheduledOrders: Boolean = false,
)
data class MerchantResponse(val success: Boolean, val merchant: MerchantDto)
data class SetMerchantLocationRequest(val latitude: Double, val longitude: Double)
data class SetCategoryRequest(val category: String)
data class SetMerchantPhotoUrlRequest(val photoUrl: String)
data class SetMinOrderAmountRequest(val minOrderAmount: Double?)
data class SetCashbackRateRequest(val rate: Double?)
data class SetAcceptsScheduledOrdersRequest(val accepts: Boolean)
data class SetParticipatesInEatsMembershipRequest(val participates: Boolean)

// Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads (item 147) -- see
// MerchantAd.kt's own doc comment for the sourced radius range and flat-fee tiers.
// merchant-mfe already has this (AdsScreen.tsx); zero native client until now.
data class CreateAdRequest(val title: String, val description: String?, val radiusMeters: Int, val days: Int)
data class MerchantAdDto(
    val id: String, val merchantId: String, val title: String, val description: String?,
    val radiusMeters: Int, val activeUntil: String, val createdAt: String, val updatedAt: String,
)
data class MerchantAdResponse(val success: Boolean, val ad: MerchantAdDto?)

// Real merchant coupons + 단골 (regular customer) loyalty gating -- mirrors
// merchant-mfe's lib/merchant.ts exactly.
data class CreateCouponRequest(
    val title: String, val description: String? = null, val discountType: String,
    val discountValue: java.math.BigDecimal, val regularsOnly: Boolean = false, val expiresAt: String? = null,
)
data class MerchantCouponDto(
    val id: String, val merchantId: String, val title: String, val description: String?,
    val discountType: String, val discountValue: java.math.BigDecimal, val regularsOnly: Boolean,
    val active: Boolean, val expiresAt: String?, val createdAt: String,
)
data class MerchantCouponResponse(val success: Boolean, val coupon: MerchantCouponDto)
data class MerchantCouponsResponse(val success: Boolean, val coupons: List<MerchantCouponDto>)

// Real B2B payroll -- real wallet-to-wallet money movement (see PayrollController.kt's
// own doc comment), real on merchant-mfe/web only until now -- zero native UI on
// either merchantapp or ItundaMerchantApp despite the backend being mature.
data class AddPayrollEmployeeRequest(val phoneNumber: String, val salaryAmount: java.math.BigDecimal)
data class PayrollEmployeeDto(
    val id: String, val merchantId: String, val employeeUserId: String, val employeeName: String,
    val salaryAmount: java.math.BigDecimal, val active: Boolean, val createdAt: String,
)
data class PayrollEmployeeResponse(val success: Boolean, val employee: PayrollEmployeeDto)
data class PayrollRosterResponse(val success: Boolean, val employees: List<PayrollEmployeeDto>)
data class PayslipDto(
    val id: String, val payrollRunId: String, val employeeUserId: String, val employeeName: String,
    val amount: java.math.BigDecimal, val transactionId: String, val createdAt: String,
)
// PayrollService.runPayroll's own response returns a lighter line-item shape than the
// full Payslip entity (no id/payrollRunId/employeeUserId/createdAt) -- distinct from
// PayslipDto above, which mirrors getPayslips()'s real entity-backed response.
data class RunPayslipDto(val employeeName: String, val amount: java.math.BigDecimal, val transactionId: String)
data class PayrollRunResponse(
    val success: Boolean, val payrollRunId: String, val totalAmount: java.math.BigDecimal,
    val employeeCount: Int, val completedAt: String, val payslips: List<RunPayslipDto>,
)
data class PayrollRunDto(
    val id: String, val merchantId: String, val ledgerTransactionId: String,
    val totalAmount: java.math.BigDecimal, val employeeCount: Int, val createdAt: String,
)
data class PayrollHistoryResponse(val success: Boolean, val runs: List<PayrollRunDto>)
data class PayslipsResponse(val success: Boolean, val payslips: List<PayslipDto>)

// Real Kakao Pay 정기결제/Toss Payments 빌링키-style recurring merchant billing (item 144)
// -- see MerchantBillingController.kt's own doc comment. merchant-mfe already has this
// (BillingScreen.tsx); this is the first native client (Android/iOS). Owner-facing
// plan-management half only -- customer subscribe/cancel is already real on all 3
// consumer clients via SubscriptionsScreen.kt/SubscriptionsScreenView.swift.
data class CreateBillingPlanRequest(val name: String, val description: String?, val amount: java.math.BigDecimal, val intervalDays: Int)
data class MerchantBillingPlanDto(
    val id: String, val merchantId: String, val name: String, val description: String?,
    val amount: java.math.BigDecimal, val intervalDays: Int, val active: Boolean, val createdAt: String,
)
data class MerchantBillingPlanResponse(val success: Boolean, val plan: MerchantBillingPlanDto)
data class MerchantBillingPlansResponse(val success: Boolean, val plans: List<MerchantBillingPlanDto>)

data class SetWebhookUrlRequest(val webhookUrl: String)

// Real API key + webhook delivery log/replay -- found via a fresh "defined but
// uncalled" endpoint sweep: real, working since ship day, zero client anywhere. See
// merchant-mfe's lib/merchant.ts own doc comment for the full account, including the
// real Toss Payments-sourced 7-attempt/4096-minute retry schedule this delivery log
// reflects.
data class GenerateApiKeyResponse(val success: Boolean, val apiKey: String)
data class WebhookDeliveryDto(
    val id: String, val eventType: String, val status: String, val attemptCount: Int,
    val createdAt: String, val nextAttemptAt: String, val deliveredAt: String? = null, val lastError: String? = null,
)
data class WebhookDeliveriesResponse(val success: Boolean, val deliveries: List<WebhookDeliveryDto>)
data class ReplayWebhookDeliveryResultDto(val id: String, val status: String, val replayOf: String)
data class ReplayWebhookDeliveryResponse(val success: Boolean, val delivery: ReplayWebhookDeliveryResultDto)

data class RegisterMerchantRequest(val businessName: String)

data class MerchantProductDto(
    val id: String, val merchantId: String, val name: String, val price: Double, val active: Boolean, val createdAt: String,
    // Real bookable-service duration (2026-07-25) -- see MerchantProduct.kt's own doc
    // comment on the backend. Non-null means this product is a real appointment-
    // bookable service (e.g. a 30-minute haircut), not a physical good.
    val durationMinutes: Int? = null,
    val imageUrl: String? = null,
    val originalPrice: Double? = null,
    val discountPercent: Int? = null,
    val description: String? = null,
    val stockQuantity: Int? = null,
)
data class MerchantProductResponse(val success: Boolean, val product: MerchantProductDto)
data class MerchantProductsResponse(val success: Boolean, val products: List<MerchantProductDto>)
data class AddProductRequest(
    val name: String,
    val price: Double,
    val durationMinutes: Int? = null,
    val imageUrl: String? = null,
    val originalPrice: Double? = null,
    val description: String? = null,
    val stockQuantity: Int? = null,
)
data class UpdateProductStockRequest(val stockQuantity: Int? = null)

// Real Coupang 타임특가 (Time Deal) -- see the backend's TimeDeal.kt/TimeDealService doc
// comments. TimeDealController's create/mine/end were live on the backend with zero
// merchant-app client (the consumer Android app already reads active deals via
// getActiveTimeDeals, mirroring this exact TimeDealDto/TimeDealViewDto shape -- see that
// app's ApiService.kt). create/end return the raw TimeDeal entity; mine returns it
// wrapped in TimeDealView (deal + productName/productImageUrl/businessName), since
// TimeDealService.getMyDeals batch-enriches for display the same way getActiveDeals does.
data class TimeDealDto(
    val id: String,
    val merchantId: String,
    val productId: String,
    val dealPrice: Double,
    val originalPrice: Double,
    val totalQuantity: Int,
    val remainingQuantity: Int,
    val startsAt: String,
    val endsAt: String,
    val createdAt: String,
)
data class TimeDealViewDto(val deal: TimeDealDto, val productName: String, val productImageUrl: String?, val businessName: String)
data class CreateTimeDealRequest(val productId: String, val dealPrice: Double, val totalQuantity: Int, val startsAt: String, val endsAt: String)
data class TimeDealResponse(val success: Boolean, val deal: TimeDealDto)
data class TimeDealsResponse(val success: Boolean, val deals: List<TimeDealViewDto>)

// Real Commerce product reviews + owner-side reply (item 187/188) -- see
// ProductReviewService.replyToProductReview's own doc comment. merchant-mfe already has
// this (item 187); this is the first Android client.
data class ProductReviewDto(
    val id: String,
    val orderItemId: String,
    val orderId: String,
    val buyerId: String,
    val productId: String,
    val merchantId: String,
    val rating: Int,
    val comment: String?,
    val ownerReply: String? = null,
    val ownerRepliedAt: String? = null,
    val createdAt: String,
)
data class ProductReviewResponse(val success: Boolean, val review: ProductReviewDto)
data class ProductReviewsResponse(val success: Boolean, val reviews: List<ProductReviewDto>)
data class ReplyToProductReviewRequest(val reply: String)

// Real Coupang-style pre-purchase product Q&A (상품문의) -- mirrors the consumer app's
// ProductInquiryDto field-for-field (see android/core/network's ApiService.kt).
data class ProductInquiryDto(
    val id: String, val productId: String, val merchantId: String, val buyerId: String,
    val question: String, val answer: String?, val answeredAt: String?, val createdAt: String,
)
data class ProductInquiryResponse(val success: Boolean, val inquiry: ProductInquiryDto)
data class ProductInquiriesResponse(val success: Boolean, val inquiries: List<ProductInquiryDto>)
data class AnswerProductInquiryRequest(val answer: String)

// Real bulk/wholesale pricing (2026-07-25) -- see ProductPriceTier.kt's own doc comment
// on the backend (closes the gap named in Baemin's own real 배민상회 B2B supplies
// marketplace research).
data class PriceTierDto(val minQuantity: Int, val unitPrice: Double)
data class SetPriceTiersRequest(val tiers: List<PriceTierDto>)
data class PriceTiersResponse(val success: Boolean, val tiers: List<PriceTierDto>)

// Real menu-item option groups (item 210) -- merchant-mfe already has this
// (ProductOptionsPanel); this is the first native-merchant-app client. v1 scope
// matches merchant-mfe's own: required, single-select groups only (e.g. "Size":
// Small/Medium/Large, exactly one choice) -- see MenuOptionService.addOptionGroup's
// own doc comment on the backend.
data class MenuOptionChoiceDto(val id: String, val name: String, val priceDelta: Double)
data class MenuOptionGroupDto(val id: String, val name: String, val required: Boolean, val multiSelect: Boolean, val choices: List<MenuOptionChoiceDto>)
data class MenuOptionChoiceRequest(val name: String, val priceDelta: Double = 0.0)
data class AddMenuOptionGroupRequest(val name: String, val choices: List<MenuOptionChoiceRequest>)
data class MenuOptionGroupResponse(val success: Boolean, val optionGroup: MenuOptionGroupDto)
data class MenuOptionGroupsResponse(val success: Boolean, val optionGroups: List<MenuOptionGroupDto>)

data class GenerateQrRequest(val amount: Double, val description: String)
// Real customer-presented payment code (2026-08-11) -- see backend's
// MerchantService.chargeByCustomerCode doc comment. Same real result shape as the
// customer app's own CollectPaymentResultDto (:core:network ApiService.kt) --
// chargeByCustomerCode returns the identical resultMap shape as collect().
data class ChargeByCustomerCodeRequest(val code: String, val amount: Double)
data class CollectPaymentResultDto(
    val success: Boolean, val transactionId: String, val merchantName: String,
    val amount: Double, val fee: Double, val status: String,
    val channel: String, val completedAt: String, val cashbackEarned: Double,
)
data class PaymentIntentDto(val id: String, val merchantId: String, val amount: Double, val description: String, val status: String, val expiresAt: String, val createdAt: String)
data class PaymentIntentResponse(val success: Boolean, val paymentIntent: PaymentIntentDto)

data class ChargeCardRequest(
    val amount: Double,
    val description: String,
    val cardNumber: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val cvc: String,
)
data class CardChargeResponse(
    val success: Boolean,
    val transactionId: String,
    val merchantName: String,
    val amount: Double,
    val fee: Double,
    val status: String,
    val channel: String,
    val cardLast4: String,
    val completedAt: String,
)

data class ReportDayDto(val date: String, val collectionCount: Int, val grossAmount: Double, val fees: Double, val netAmount: Double, val byChannel: Map<String, Double>)
data class ReportResponse(val success: Boolean, val from: String, val to: String, val days: List<ReportDayDto>)

// Real incoming Eats orders (restaurant side) -- previously only ever exposed in the
// consumer app's own bank-mfe (a structural gap for anyone using a dedicated merchant
// app), reused unmodified here. Trimmed to the fields this app's UI actually reads.
data class EatsOrderDto(
    val id: String,
    val buyerId: String,
    val restaurantId: String,
    val riderId: String?,
    val deliveryAddress: String,
    val itemsSubtotal: Double,
    val deliveryFee: Double,
    val totalAmount: Double,
    val status: String,
    val createdAt: String,
    val deliveryNotes: String? = null,
    // Real Baemin-style 포장주문 (Pickup) order type (item 208) -- a PICKUP order has
    // riderId: null for its whole lifecycle and reaches DELIVERED via completePickup
    // below, not a rider hand-off. See EatsOrderService.kt's own doc comment.
    val fulfillmentType: String? = null,
)
data class EatsOrderDetailResponse(val success: Boolean, val order: EatsOrderDto)
data class EatsOrdersResponse(val success: Boolean, val orders: List<EatsOrderDto>)
data class UpdateEatsOrderStatusRequest(val status: String)

// Real written-review list + owner-reply (item 184/185) -- see
// EatsReviewService.replyToRestaurantReview's own doc comment. bank-mfe already has
// this (item 184); this is the first Android client for the owner-reply side.
data class EatsReviewDto(
    val id: String,
    val orderId: String,
    val buyerId: String,
    val restaurantId: String,
    val riderId: String?,
    val restaurantRating: Int,
    val restaurantComment: String?,
    val riderRating: Int?,
    val riderComment: String?,
    val ownerReply: String? = null,
    val ownerRepliedAt: String? = null,
    val createdAt: String,
)
data class EatsReviewResponse(val success: Boolean, val review: EatsReviewDto)
data class EatsReviewsResponse(val success: Boolean, val reviews: List<EatsReviewDto>)
data class ReplyToEatsReviewRequest(val reply: String)

// Real local-business appointment booking (2026-07-25) -- see
// rw.itunda.merchant.MerchantBookingService on the backend. Date/time fields stay plain
// ISO strings (Gson has no java.time adapter registered), same convention every other
// temporal field in this file already uses.
data class AvailabilityWindowDto(val dayOfWeek: String, val startTime: String, val endTime: String)
data class SetAvailabilityRequest(val windows: List<AvailabilityWindowDto>)
data class AvailabilityResponse(val success: Boolean, val windows: List<AvailabilityWindowDto>)
data class MerchantBookingDto(
    val id: String, val merchantId: String, val customerId: String, val serviceId: String, val serviceName: String,
    val bookingDate: String, val startTime: String, val endTime: String, val status: String, val notes: String? = null, val createdAt: String,
)
data class MerchantBookingDetailResponse(val success: Boolean, val booking: MerchantBookingDto)
data class MerchantBookingsResponse(val success: Boolean, val bookings: List<MerchantBookingDto>)
data class RespondToBookingRequest(val confirm: Boolean)

// Real 배민오더-style table/QR in-store ordering (2026-07-25) -- previously only ever
// exposed via the consumer app's own checkout flow; this app's own restaurant-side
// queue reuses it unmodified, trimmed to the fields this app's UI actually reads.
data class DineInOrderDto(
    val id: String,
    val buyerId: String,
    val restaurantId: String,
    val tableNumber: String,
    val totalAmount: Double,
    val status: String,
    val notes: String? = null,
    val createdAt: String,
)
data class DineInOrderDetailResponse(val success: Boolean, val order: DineInOrderDto)
data class DineInOrdersResponse(val success: Boolean, val orders: List<DineInOrderDto>)
data class UpdateDineInOrderStatusRequest(val status: String)

// Real 토스뱅크 개인사업자 (business banking for sole proprietors) equivalent
// (2026-07-25) -- see rw.itunda.merchant.MerchantBusinessAccountService on the backend.
data class BusinessWalletDto(
    val id: String, val userId: String, val accountNumber: String, val accountName: String, val type: String,
    val balance: Double, val availableBalance: Double, val currency: String,
)
data class BusinessWalletResponse(val success: Boolean, val wallet: BusinessWalletDto)
data class BusinessLedgerEntryDto(
    val id: String, val transactionId: String, val accountId: String, val direction: String,
    val amount: Double, val currency: String, val balanceAfter: Double, val memo: String, val createdAt: String,
)
data class BusinessTransactionsResponse(val success: Boolean, val transactions: List<BusinessLedgerEntryDto>)
data class MoveBusinessMoneyRequest(val amount: Double)

// Real business expense summary (2026-08-11) -- see backend's
// WalletService.getBusinessExpenseSummary doc comment for the real Toss Bank
// 세금 신고용 이용내역 자동발송 (tax-filing usage summary) pattern this closes the
// honest slice of.
data class BusinessExpenseCategoryDto(val name: String, val amount: Double)
data class BusinessExpenseSummaryResponse(val success: Boolean, val categories: List<BusinessExpenseCategoryDto>, val totalSpent: Double, val sinceMonthsAgo: Long)

interface ApiService {
    @POST("api/v1/merchant/register")
    suspend fun registerMerchant(@Body request: RegisterMerchantRequest): MerchantResponse

    @GET("api/v1/merchant/me")
    suspend fun getMyMerchant(): MerchantResponse

    // Real business location -- see MerchantController.kt's own doc comment.
    // merchant-mfe already has this; this is the first native client.
    @POST("api/v1/merchant/location")
    suspend fun setMerchantLocation(@Body request: SetMerchantLocationRequest): MerchantResponse

    // Real radius-targeted local ads -- see MerchantAdController.kt's own doc comment.
    // merchant-mfe already has this; this is the first native client.
    @POST("api/v1/merchant/ads")
    suspend fun createOrExtendAd(@Body request: CreateAdRequest, @Header("Idempotency-Key") idempotencyKey: String = UUID.randomUUID().toString()): MerchantAdResponse

    @GET("api/v1/merchant/ads/me")
    suspend fun getMyAd(): MerchantAdResponse

    @POST("api/v1/merchant/qr/generate")
    suspend fun generateQr(@Body request: GenerateQrRequest): PaymentIntentResponse

    // Real customer-presented payment code (2026-08-11) -- see backend's
    // MerchantService.chargeByCustomerCode doc comment. Real KakaoPay/Toss Pay's
    // actual primary in-store flow, reversed from generateQr above: the CUSTOMER's
    // own app already shows a scannable code, this merchant app scans it (real
    // camera QR scanning, see PaymentScanScreen.kt) and enters the amount.
    @POST("api/v1/merchant/pay/charge-by-code")
    suspend fun chargeByCustomerCode(@Header("Idempotency-Key") idempotencyKey: String = UUID.randomUUID().toString(), @Body request: ChargeByCustomerCodeRequest): CollectPaymentResultDto

    @POST("api/v1/merchant/card/charge")
    suspend fun chargeCard(@Header("Idempotency-Key") idempotencyKey: String = UUID.randomUUID().toString(), @Body request: ChargeCardRequest): CardChargeResponse

    @GET("api/v1/merchant/products")
    suspend fun getProductCatalog(): MerchantProductsResponse

    // Real Commerce product reviews + owner-side reply (item 187/188). Lives under
    // /api/v1/orders (OrderController), not /api/v1/merchant -- see OrderController.kt's
    // real contract.
    @GET("api/v1/orders/products/{id}/reviews")
    suspend fun getProductReviews(@Path("id") productId: String): ProductReviewsResponse

    @POST("api/v1/orders/reviews/{reviewId}/reply")
    suspend fun replyToProductReview(@Path("reviewId") reviewId: String, @Body request: ReplyToProductReviewRequest): ProductReviewResponse

    // Real Coupang-style pre-purchase product Q&A (상품문의), owner-answer side --
    // ProductInquiryService.answerQuestion existed on the backend with genuinely zero
    // client anywhere (the consumer app's own ApiService.kt honestly notes this: "the
    // seller-answer flow has zero UI anywhere yet, not even on bank-mfe/merchant-mfe").
    // Mirrors ProductInquiryDto field-for-field from the consumer app.
    @GET("api/v1/orders/products/{id}/inquiries")
    suspend fun getProductInquiries(@Path("id") productId: String): ProductInquiriesResponse

    @POST("api/v1/orders/inquiries/{inquiryId}/answer")
    suspend fun answerProductInquiry(@Path("inquiryId") inquiryId: String, @Body request: AnswerProductInquiryRequest): ProductInquiryResponse

    // Real Coupang 타임특가 (Time Deal) -- see TimeDealDto's own doc comment. Lives under
    // /api/v1/time-deals (TimeDealController), not /api/v1/merchant.
    @POST("api/v1/time-deals")
    suspend fun createTimeDeal(@Body request: CreateTimeDealRequest): TimeDealResponse

    @GET("api/v1/time-deals/mine")
    suspend fun getMyTimeDeals(): TimeDealsResponse

    @POST("api/v1/time-deals/{dealId}/end")
    suspend fun endTimeDeal(@Path("dealId") dealId: String): TimeDealResponse

    @POST("api/v1/merchant/products")
    suspend fun addProduct(@Body request: AddProductRequest): MerchantProductResponse

    @PUT("api/v1/merchant/products/{id}")
    suspend fun updateProduct(@Path("id") productId: String, @Body request: AddProductRequest): MerchantProductResponse

    @PATCH("api/v1/merchant/products/{id}/stock")
    suspend fun updateProductStock(@Path("id") productId: String, @Body request: UpdateProductStockRequest): MerchantProductResponse

    @DELETE("api/v1/merchant/products/{id}")
    suspend fun removeProduct(@Path("id") productId: String): MerchantProductResponse

    @GET("api/v1/merchant/reports")
    suspend fun getReport(@Query("from") from: String? = null, @Query("to") to: String? = null): ReportResponse

    @GET("api/v1/eats/orders/restaurant-orders")
    suspend fun getRestaurantOrders(): EatsOrdersResponse

    @POST("api/v1/eats/orders/{id}/status")
    suspend fun advanceRestaurantOrderStatus(@Path("id") orderId: String, @Body request: UpdateEatsOrderStatusRequest): EatsOrderDetailResponse

    // Real Baemin-style 포장주문 (Pickup) terminal edge (item 208) -- see
    // EatsOrderDto.fulfillmentType's own doc comment.
    @POST("api/v1/eats/orders/{id}/complete-pickup")
    suspend fun completePickupOrder(@Path("id") orderId: String): EatsOrderDetailResponse

    // Real written-review list + owner-reply (item 184/185).
    @GET("api/v1/eats/restaurants/{id}/reviews")
    suspend fun getRestaurantReviews(@Path("id") restaurantId: String): EatsReviewsResponse

    @POST("api/v1/eats/reviews/{reviewId}/reply")
    suspend fun replyToRestaurantReview(@Path("reviewId") reviewId: String, @Body request: ReplyToEatsReviewRequest): EatsReviewResponse

    @GET("api/v1/eats/dine-in/orders/restaurant-orders")
    suspend fun getDineInOrders(): DineInOrdersResponse

    @POST("api/v1/eats/dine-in/orders/{id}/status")
    suspend fun advanceDineInOrderStatus(@Path("id") orderId: String, @Body request: UpdateDineInOrderStatusRequest): DineInOrderDetailResponse

    @POST("api/v1/merchant/booking/availability")
    suspend fun setAvailability(@Body request: SetAvailabilityRequest): AvailabilityResponse

    @GET("api/v1/merchant/booking/availability")
    suspend fun getMyAvailability(): AvailabilityResponse

    @GET("api/v1/merchant/bookings/merchant-bookings")
    suspend fun getMerchantBookings(): MerchantBookingsResponse

    @POST("api/v1/merchant/bookings/{id}/respond")
    suspend fun respondToBooking(@Path("id") bookingId: String, @Body request: RespondToBookingRequest): MerchantBookingDetailResponse

    @POST("api/v1/merchant/bookings/{id}/complete")
    suspend fun completeBooking(@Path("id") bookingId: String): MerchantBookingDetailResponse

    // Real business banking for sole proprietors (2026-07-25) -- see
    // rw.itunda.merchant.MerchantBusinessAccountController.
    @POST("api/v1/merchant/business-account")
    suspend fun openBusinessAccount(): BusinessWalletResponse

    @GET("api/v1/merchant/business-account")
    suspend fun getBusinessAccount(): BusinessWalletResponse

    @GET("api/v1/merchant/business-account/transactions")
    suspend fun getBusinessTransactions(): BusinessTransactionsResponse

    // Real business expense summary (2026-08-11) -- see WalletService.
    // getBusinessExpenseSummary's own doc comment. Lives under /api/v1/wallet, not
    // /api/v1/merchant/business-account, since it's WalletController's own endpoint
    // (real, same-backend, cross-controller call -- Retrofit doesn't care).
    @GET("api/v1/wallet/business-expense-summary")
    suspend fun getBusinessExpenseSummary(@Query("sinceMonthsAgo") sinceMonthsAgo: Long = 3): BusinessExpenseSummaryResponse

    @POST("api/v1/merchant/business-account/move-to-business")
    suspend fun moveToBusiness(@Header("Idempotency-Key") idempotencyKey: String, @Body request: MoveBusinessMoneyRequest): BusinessWalletResponse

    @POST("api/v1/merchant/business-account/move-to-personal")
    suspend fun moveToPersonal(@Header("Idempotency-Key") idempotencyKey: String, @Body request: MoveBusinessMoneyRequest): BusinessWalletResponse

    // Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support program) --
    // see rw.itunda.merchant.MerchantFeeWaiverService's own doc comment. merchant-mfe
    // already has this; this is the first Android client.
    @POST("api/v1/merchant/fee-waiver/apply")
    suspend fun applyForFeeWaiver(): MerchantResponse

    // Real merchant coupons + 단골 (regular customer) loyalty gating -- see
    // rw.itunda.merchant.MerchantCouponService's own doc comment. Merchant-owner-facing
    // create/list/deactivate only; a coupon redeems against a real Pay-by-code payment,
    // not here. merchant-mfe already has this; this is the first Android client.
    @POST("api/v1/merchant/coupons")
    suspend fun createCoupon(@Body request: CreateCouponRequest): MerchantCouponResponse

    // Real payment-event webhook URL settings -- see
    // rw.itunda.merchant.MerchantService.setWebhookUrl's own doc comment. merchant-mfe
    // already has this; this is the first Android client.
    @POST("api/v1/merchant/webhook-url")
    suspend fun setWebhookUrl(@Body request: SetWebhookUrlRequest): MerchantResponse

    @POST("api/v1/merchant/api-key/generate")
    suspend fun generateApiKey(): GenerateApiKeyResponse

    @GET("api/v1/merchant/webhook-deliveries")
    suspend fun getWebhookDeliveries(): WebhookDeliveriesResponse

    @POST("api/v1/merchant/webhook-deliveries/{id}/replay")
    suspend fun replayWebhookDelivery(@Path("id") deliveryId: String): ReplayWebhookDeliveryResponse

    // Real store-settings endpoints -- see MerchantController.kt's own doc comments.
    // Found 2026-08-01 via a dead-field sweep: category/photo/min-order/cashback-rate/
    // scheduled-orders/eats-membership were all real DTO fields with zero (or partial)
    // client anywhere.
    @POST("api/v1/merchant/category")
    suspend fun setCategory(@Body request: SetCategoryRequest): MerchantResponse

    @POST("api/v1/merchant/photo")
    suspend fun setMerchantPhotoUrl(@Body request: SetMerchantPhotoUrlRequest): MerchantResponse

    @POST("api/v1/merchant/min-order")
    suspend fun setMinOrderAmount(@Body request: SetMinOrderAmountRequest): MerchantResponse

    @POST("api/v1/merchant/cashback-rate")
    suspend fun setCashbackRate(@Body request: SetCashbackRateRequest): MerchantResponse

    @POST("api/v1/merchant/scheduled-orders-participation")
    suspend fun setAcceptsScheduledOrders(@Body request: SetAcceptsScheduledOrdersRequest): MerchantResponse

    @POST("api/v1/merchant/eats-membership-participation")
    suspend fun setParticipatesInEatsMembership(@Body request: SetParticipatesInEatsMembershipRequest): MerchantResponse

    @GET("api/v1/merchant/coupons")
    suspend fun getMyCoupons(): MerchantCouponsResponse

    @POST("api/v1/merchant/coupons/{couponId}/deactivate")
    suspend fun deactivateCoupon(@Path("couponId") couponId: String): MerchantCouponResponse

    // Real B2B payroll -- see PayrollController.kt's own doc comment. merchant-mfe
    // already has this; this is the first native client (Android/iOS).
    @POST("api/v1/merchant/payroll/employees")
    suspend fun addPayrollEmployee(@Body request: AddPayrollEmployeeRequest): PayrollEmployeeResponse

    @GET("api/v1/merchant/payroll/employees")
    suspend fun getPayrollRoster(): PayrollRosterResponse

    @DELETE("api/v1/merchant/payroll/employees/{employeeId}")
    suspend fun removePayrollEmployee(@Path("employeeId") employeeId: String): PayrollEmployeeResponse

    @POST("api/v1/merchant/payroll/run")
    suspend fun runPayroll(@Header("Idempotency-Key") idempotencyKey: String = UUID.randomUUID().toString()): PayrollRunResponse

    @GET("api/v1/merchant/payroll/runs")
    suspend fun getPayrollHistory(): PayrollHistoryResponse

    @GET("api/v1/merchant/payroll/runs/{runId}/payslips")
    suspend fun getPayslips(@Path("runId") runId: String): PayslipsResponse

    // Real recurring merchant billing (item 144) -- see MerchantBillingController.kt's
    // own doc comment. merchant-mfe already has this; this is the first native client.
    @POST("api/v1/merchant/billing-plans")
    suspend fun createBillingPlan(
        @Body request: CreateBillingPlanRequest,
        @Header("Idempotency-Key") idempotencyKey: String = UUID.randomUUID().toString(),
    ): MerchantBillingPlanResponse

    @GET("api/v1/merchant/billing-plans")
    suspend fun getMyBillingPlans(): MerchantBillingPlansResponse

    @POST("api/v1/merchant/billing-plans/{planId}/deactivate")
    suspend fun deactivateBillingPlan(@Path("planId") planId: String): MerchantBillingPlanResponse

    // Real bulk/wholesale pricing (2026-07-25) -- see
    // rw.itunda.merchant.MerchantProductController.setPriceTiers.
    @POST("api/v1/merchant/products/{id}/price-tiers")
    suspend fun setPriceTiers(@Path("id") productId: String, @Body request: SetPriceTiersRequest): PriceTiersResponse

    @GET("api/v1/merchant/products/{id}/price-tiers")
    suspend fun getPriceTiers(@Path("id") productId: String): PriceTiersResponse

    // Real menu-item option groups (item 210) -- see MenuOptionGroupDto's own doc comment.
    @POST("api/v1/merchant/products/{id}/option-groups")
    suspend fun addOptionGroup(@Path("id") productId: String, @Body request: AddMenuOptionGroupRequest): MenuOptionGroupResponse

    @GET("api/v1/merchant/products/{id}/option-groups")
    suspend fun getOptionGroups(@Path("id") productId: String): MenuOptionGroupsResponse

    @DELETE("api/v1/merchant/products/{id}/option-groups/{groupId}")
    suspend fun removeOptionGroup(@Path("id") productId: String, @Path("groupId") groupId: String)

    // Real push device-token registration (item 130) -- see riderapp's own
    // ApiService.kt doc comment, same pass: PushNotificationService.sendToUser
    // silently no-ops for every real user with no registered token, and this
    // dedicated merchant app -- the one place a merchant owner actually needs an
    // instant new-order/booking push -- never registered one at all.
    @POST("api/v1/notifications/device-tokens")
    suspend fun registerDeviceToken(@Body request: RegisterDeviceTokenRequest): SuccessResponse

    // Real Naver Smart Store-style "관심고객" (interested-customer) follower count +
    // broadcast-to-followers -- see MerchantFollowController's own doc comment.
    // Distinct from the customer-facing follow/unfollow already real on bank-mfe/
    // Android app/iOS app. merchant-mfe already has this (item 118); this is the
    // first native-merchant-app client for the owner-facing half.
    @GET("api/v1/merchant/followers/count")
    suspend fun getFollowerCount(): FollowerCountResponse

    @POST("api/v1/merchant/followers/broadcast")
    suspend fun broadcastToFollowers(@Body request: BroadcastToFollowersRequest): BroadcastToFollowersResponse

    // Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see
    // VendorCashAdvanceDto's own doc comment. merchant-mfe shipped first (its own doc
    // comment explicitly names Android/iOS as a follow-up, not built there); this is
    // the first native client. No Idempotency-Key on apply (row creation only, money
    // only moves at disburse); disburse/repay-early both require one.
    @GET("api/v1/vendor-advance/offer")
    suspend fun getVendorCashAdvanceOffer(@Query("merchantId") merchantId: String): VendorCashAdvanceOfferResponse

    @POST("api/v1/vendor-advance/apply")
    suspend fun applyForVendorCashAdvance(@Body request: ApplyForVendorCashAdvanceRequest): VendorCashAdvanceResponse

    @POST("api/v1/vendor-advance/{advanceId}/disburse")
    suspend fun disburseVendorCashAdvance(@Path("advanceId") advanceId: String, @Header("Idempotency-Key") idempotencyKey: String): VendorCashAdvanceResponse

    @GET("api/v1/vendor-advance/me")
    suspend fun getMyVendorCashAdvance(@Query("merchantId") merchantId: String): VendorCashAdvanceNullableResponse

    @POST("api/v1/vendor-advance/{advanceId}/repay-early")
    suspend fun repayVendorCashAdvanceEarly(@Path("advanceId") advanceId: String, @Body request: RepayVendorCashAdvanceEarlyRequest, @Header("Idempotency-Key") idempotencyKey: String): VendorCashAdvanceResponse
}

// Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see the backend's
// VendorCashAdvanceService.kt doc comment for the full sourced account. Genuinely
// distinct from every other lending product in this codebase: repayment is
// auto-collected as a variable % of this merchant's own real itunda-routed daily
// settlement inflow (QR/card collections), never a fixed installment the merchant
// initiates. Honest v1 limitation: a vendor's off-platform cash sales are invisible
// to both underwriting and collection -- surfaced directly in this screen's own copy.
// Mirrors merchant-mfe's lib/vendorCashAdvance.ts exactly.
data class VendorCashAdvanceDto(
    val id: String, val merchantId: String, val principalAmount: java.math.BigDecimal, val feeAmount: java.math.BigDecimal,
    val totalOwed: java.math.BigDecimal, val remainingOwed: java.math.BigDecimal, val collectionRatePercent: Double,
    val status: String, val requestedAt: String, val disbursedAt: String?, val repaidAt: String?, val lastCollectionAt: String?,
)
data class ApplyForVendorCashAdvanceRequest(val merchantId: String)
data class RepayVendorCashAdvanceEarlyRequest(val amount: java.math.BigDecimal)
data class VendorCashAdvanceResponse(val success: Boolean, val advance: VendorCashAdvanceDto)
data class VendorCashAdvanceNullableResponse(val success: Boolean, val advance: VendorCashAdvanceDto?)
data class VendorCashAdvanceOfferResponse(
    val success: Boolean, val eligible: Boolean, val reason: String? = null, val offerAmount: java.math.BigDecimal? = null,
    val feeAmount: java.math.BigDecimal? = null, val collectionRatePercent: Double? = null,
    val averageDailySettlement: java.math.BigDecimal? = null, val tradingDays: Int? = null,
)

enum class DevicePlatform { ANDROID, IOS, WEB }
data class RegisterDeviceTokenRequest(val platform: DevicePlatform, val token: String)
data class SuccessResponse(val success: Boolean)
data class FollowerCountResponse(val success: Boolean, val count: Int)
data class BroadcastToFollowersRequest(val title: String, val body: String)
data class BroadcastToFollowersResponse(val success: Boolean, val recipientCount: Int)

/**
 * Real, minimal Retrofit/OkHttp client, mirroring :riderapp's own NetworkClient
 * exactly -- this app's own copy, scoped to a small deliberately chosen subset of
 * the real backend's API surface (Merchant + the Eats restaurant-order endpoints).
 */
object NetworkClient {
    private const val BASE_URL = BuildConfig.API_BASE_URL

    private var tokenStore: TokenStore? = null
    private var deviceStore: DeviceStore? = null

    fun init(context: Context) {
        tokenStore = TokenStore(context.applicationContext)
        deviceStore = DeviceStore(context.applicationContext)
    }

    fun currentTokenStore(): TokenStore =
        tokenStore ?: throw IllegalStateException("NetworkClient.init() was never called")

    fun currentDeviceStore(): DeviceStore =
        deviceStore ?: throw IllegalStateException("NetworkClient.init() was never called")

    private val authInterceptor = Interceptor { chain ->
        val token = tokenStore?.getAccessToken()
        val newRequest = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
        }.build()
        chain.proceed(newRequest)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy { retrofit.create(ApiService::class.java) }
    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
}

/** itunda's real custom URL scheme for a customer's own app to resolve into a real
 * POST /api/v1/merchant/collect/{intentId} call -- the same client-side encoding
 * convention merchant-mfe's web POS screen already established
 * (paymentIntentQrPayload). */
fun paymentIntentQrPayload(intentId: String) = "itunda://pay?intentId=$intentId"

/** Real 배민오더-style per-table QR (2026-07-25) -- printed/displayed at a physical
 * table, resolved by a customer's own app into the dine-in ordering screen for this
 * exact restaurant + table. Same encoding convention as paymentIntentQrPayload above. */
fun dineInTableQrPayload(restaurantId: String, tableNumber: String) =
    "itunda://eats/dine-in?restaurantId=$restaurantId&table=${java.net.URLEncoder.encode(tableNumber, "UTF-8")}"
