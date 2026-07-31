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
)
data class MerchantResponse(val success: Boolean, val merchant: MerchantDto)

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

data class SetWebhookUrlRequest(val webhookUrl: String)

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

interface ApiService {
    @POST("api/v1/merchant/register")
    suspend fun registerMerchant(@Body request: RegisterMerchantRequest): MerchantResponse

    @GET("api/v1/merchant/me")
    suspend fun getMyMerchant(): MerchantResponse

    @POST("api/v1/merchant/qr/generate")
    suspend fun generateQr(@Body request: GenerateQrRequest): PaymentIntentResponse

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

    @GET("api/v1/merchant/coupons")
    suspend fun getMyCoupons(): MerchantCouponsResponse

    @POST("api/v1/merchant/coupons/{couponId}/deactivate")
    suspend fun deactivateCoupon(@Path("couponId") couponId: String): MerchantCouponResponse

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
}

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
