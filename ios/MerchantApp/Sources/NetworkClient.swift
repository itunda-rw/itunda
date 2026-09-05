import Foundation

// Mirrors services/backend/auth's real AuthDtos exactly. A merchant owner logs into
// their existing itunda account first, then registers a business via this app --
// no separate registration screen for a brand-new itunda account.
struct LoginRequest: Encodable { let phoneNumber: String; let password: String; let deviceId: String?; let deviceName: String? }
struct PublicUser: Decodable { let id: String; let phoneNumber: String; let firstName: String; let lastName: String }
struct AuthResponse: Decodable { let message: String; let user: PublicUser; let accessToken: String; let refreshToken: String }

// Real device binding (2026-07-28 port) -- mirrors ios/App's own CoreNetwork
// verifyDevice/TrustedDevice DTOs and Android merchantapp's ApiService.kt equivalent.
struct VerifyDeviceRequest: Encodable { let password: String }
struct TrustedDeviceDto: Decodable { let id: String; let deviceId: String; let deviceName: String?; let trusted: Bool }
struct VerifyDeviceResponse: Decodable { let success: Bool; let device: TrustedDeviceDto }

// Mirrors rw.itunda.merchant's real Merchant/MerchantProduct/PaymentIntent entities
// exactly (same field names merchant-mfe's own lib/merchant.ts already uses).
struct MerchantDto: Decodable {
    let id: String
    let ownerUserId: String
    let businessName: String
    let webhookUrl: String?
    let kybVerified: Bool
    let createdAt: String
    let category: String?
    let feeRateOverride: Double?
    // Real business location (POST /api/v1/merchant/location) -- merchant-mfe already
    // has this; needed here for MerchantAdService's own radius-targeted ads (item 147).
    let latitude: Double?
    let longitude: Double?
    // Real Baemin Club-style participating-restaurant opt-in -- see EatsMembership.kt's
    // own doc comment. Found 2026-08-01 (dead-field sweep): real on merchant-mfe since
    // 2026-07-26, zero native UI on either merchant app until now.
    let participatesInEatsMembership: Bool
    // Real restaurant-card photo, min-order, boosted-cashback, and scheduled-orders
    // opt-in -- see MerchantService.setPhotoUrl/setMinOrderAmount/setCashbackRate/
    // setAcceptsScheduledOrders's own doc comments. Found 2026-08-01 (dead-field
    // sweep): real on the backend since 2026-07-21/07-26, zero client anywhere (not
    // even merchant-mfe) until now.
    let photoUrl: String?
    // Real gap found live (uncalled-endpoint sweep, 2026-08-29/30) -- see
    // NetworkClient+MerchantExtras.swift's own doc comment. Comma-joined string on
    // the wire, not a JSON array (matching the raw JPA column) -- split client-side.
    let photoUrls: String?
    let minOrderAmount: Double?
    let cashbackRate: Double?
    let acceptsScheduledOrders: Bool
    // Real merchant-set phone/hours/prep-time/pickup-discount/accepting-orders/
    // closed-weekdays -- ported from merchant-mfe (2026-09-03). See
    // MerchantService.setPhoneNumber/etc.'s own doc comments on the backend.
    // closedWeekdays is comma-joined on the wire (matching photoUrls' own convention
    // for a rarely-multi-valued property), 1=Monday..7=Sunday (java.time.DayOfWeek).
    let phoneNumber: String?
    let openingHours: String?
    let avgPrepTimeMinutes: Int?
    let pickupDiscountPercent: Int?
    let isAcceptingOrders: Bool
    let closedWeekdays: String?
}
struct MerchantResponse: Decodable { let success: Bool; let merchant: MerchantDto }
// Real API key + webhook delivery log/replay -- found via a fresh "defined but
// uncalled" endpoint sweep: real, working since ship day, zero client anywhere. See
// merchant-mfe's lib/merchant.ts own doc comment for the full account, including the
// real Toss Payments-sourced 7-attempt/4096-minute retry schedule this delivery log
// reflects.
struct GenerateApiKeyResponse: Decodable { let success: Bool; let apiKey: String }
// Real webhook signature verification (2026-08-30) -- see backend
// WebhookDeliveryService's own doc comment for the full sourced account of the gap
// this closes (a forgeable PAYMENT_STATUS_CHANGED POST with no way to verify it
// genuinely came from itunda).
struct GenerateWebhookSecretResponse: Decodable { let success: Bool; let webhookSecret: String }
struct WebhookDeliveryDto: Decodable, Identifiable {
    let id: String; let eventType: String; let status: String; let attemptCount: Int
    let createdAt: String; let nextAttemptAt: String; let deliveredAt: String?; let lastError: String?
}
struct WebhookDeliveriesResponse: Decodable { let success: Bool; let deliveries: [WebhookDeliveryDto] }
struct ReplayWebhookDeliveryResultDto: Decodable { let id: String; let status: String; let replayOf: String }
struct ReplayWebhookDeliveryResponse: Decodable { let success: Bool; let delivery: ReplayWebhookDeliveryResultDto }
struct SetMerchantLocationRequest: Encodable { let latitude: Double; let longitude: Double }
struct SetCategoryRequest: Encodable { let category: String }
struct SetMerchantPhotoUrlRequest: Encodable { let photoUrl: String }
struct SetMinOrderAmountRequest: Encodable { let minOrderAmount: Double? }
struct SetCashbackRateRequest: Encodable { let rate: Double? }
struct SetMerchantPhoneNumberRequest: Encodable { let phoneNumber: String? }
struct SetMerchantOpeningHoursRequest: Encodable { let openingHours: String? }
struct SetMerchantAvgPrepTimeMinutesRequest: Encodable { let avgPrepTimeMinutes: Int? }
struct SetMerchantPickupDiscountRequest: Encodable { let pickupDiscountPercent: Int? }
struct SetAcceptingOrdersRequest: Encodable { let accepting: Bool }
struct SetClosedWeekdaysRequest: Encodable { let weekdays: [Int] }

// Real demo KYB structural pre-check -- ported from merchant-mfe's KybCard
// (2026-09-03), see DemoKybVerificationService.kt's own doc comment on the backend.
// This is the SAME generic /api/v1/identity/* endpoint the consumer app's own
// IdentityScreenView.swift already uses for personal NATIONAL_ID/PASSPORT
// submissions -- documentType is always BUSINESS_TIN here, a separate concern.
struct SubmitIdentityRequest: Encodable { let documentType: String; let documentNumber: String; let documentReference: String }
struct IdentitySubmissionDto: Decodable, Identifiable {
    let id: String; let userId: String; let documentType: String; let documentNumber: String; let documentReference: String
    let status: String; let submittedAt: String; let autoVerificationStatus: String?; let autoVerificationDetail: String?
}
struct SubmitIdentityResponse: Decodable { let success: Bool; let submission: IdentitySubmissionDto }
struct IdentityStatusResponse: Decodable { let success: Bool; let submissions: [IdentitySubmissionDto] }
struct SetAcceptsScheduledOrdersRequest: Encodable { let accepts: Bool }
struct SetParticipatesInEatsMembershipRequest: Encodable { let participates: Bool }

// Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads (item 147) -- see
// MerchantAdController.kt's own doc comment. merchant-mfe already has this
// (AdsScreen.tsx); this is the first iOS client.
struct CreateAdRequest: Encodable { let title: String; let description: String?; let radiusMeters: Int; let days: Int }
struct MerchantAdDto: Decodable {
    let id: String
    let merchantId: String
    let title: String
    let description: String?
    let radiusMeters: Int
    let activeUntil: String
    let createdAt: String
    let updatedAt: String
}
struct MerchantAdResponse: Decodable { let success: Bool; let ad: MerchantAdDto? }

// Real local-business appointment booking, owner side -- see
// rw.itunda.merchant.MerchantBookingService on the backend. merchant-mfe/Android
// already have this; this is the first iOS client. Date/time fields stay plain ISO
// strings, same convention every other temporal field in this file already uses.
struct AvailabilityWindowDto: Codable { let dayOfWeek: String; let startTime: String; let endTime: String }
struct SetAvailabilityRequest: Encodable { let windows: [AvailabilityWindowDto] }
struct AvailabilityResponse: Decodable { let success: Bool; let windows: [AvailabilityWindowDto] }
struct MerchantBookingDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let customerId: String
    let serviceId: String
    let serviceName: String
    let bookingDate: String
    let startTime: String
    let endTime: String
    let status: String
    let notes: String?
    let createdAt: String
}
struct MerchantBookingDetailResponse: Decodable { let success: Bool; let booking: MerchantBookingDto }
struct MerchantBookingsResponse: Decodable { let success: Bool; let bookings: [MerchantBookingDto] }
struct RespondToBookingRequest: Encodable { let confirm: Bool }

// Real merchant coupons + 단골 (regular customer) loyalty gating -- mirrors
// merchant-mfe's lib/merchant.ts exactly.
struct CreateCouponRequest: Encodable {
    let title: String
    let description: String?
    let discountType: String
    let discountValue: Double
    let regularsOnly: Bool
    let expiresAt: String?
    init(title: String, description: String?, discountType: String, discountValue: Double, regularsOnly: Bool, expiresAt: String?) {
        self.title = title; self.description = description; self.discountType = discountType
        self.discountValue = discountValue; self.regularsOnly = regularsOnly; self.expiresAt = expiresAt
    }
}
struct MerchantCouponDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let title: String
    let description: String?
    let discountType: String
    let discountValue: Double
    let regularsOnly: Bool
    let active: Bool
    let expiresAt: String?
    let createdAt: String
}
struct MerchantCouponResponse: Decodable { let success: Bool; let coupon: MerchantCouponDto }
struct MerchantCouponsResponse: Decodable { let success: Bool; let coupons: [MerchantCouponDto] }

// Real B2B payroll DTOs + methods moved to NetworkClient+Payroll.swift, 2026-08-30
// (see that file's own doc comment) -- same "extract instead of growing a baselined
// file" split as NetworkClient+VisitorAnalytics.swift already established.

// Real Kakao Pay 정기결제/Toss Payments 빌링키-style recurring merchant billing (item 144)
// -- see MerchantBillingController.kt's own doc comment. merchant-mfe already has this;
// this is the first native client. Owner-facing plan-management half only -- customer
// subscribe/cancel is already real on all 3 consumer clients.
struct CreateBillingPlanRequest: Encodable { let name: String; let description: String?; let amount: Double; let intervalDays: Int }
struct MerchantBillingPlanDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let name: String
    let description: String?
    let amount: Double
    let intervalDays: Int
    let active: Bool
    let createdAt: String
}
struct MerchantBillingPlanResponse: Decodable { let success: Bool; let plan: MerchantBillingPlanDto }
struct MerchantBillingPlansResponse: Decodable { let success: Bool; let plans: [MerchantBillingPlanDto] }

struct SetWebhookUrlRequest: Encodable {
    let webhookUrl: String
    init(webhookUrl: String) { self.webhookUrl = webhookUrl }
}
struct RegisterMerchantRequest: Encodable { let businessName: String }

struct MerchantProductDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let name: String
    let price: Double
    let active: Bool
    let createdAt: String
    let imageUrl: String?
    let originalPrice: Double?
    let discountPercent: Int?
    let description: String?
    let stockQuantity: Int?
}
struct MerchantProductResponse: Decodable { let success: Bool; let product: MerchantProductDto }
struct MerchantProductsResponse: Decodable { let success: Bool; let products: [MerchantProductDto] }
struct AddProductRequest: Encodable { let name: String; let price: Double; let imageUrl: String?; let originalPrice: Double?; let description: String?; let stockQuantity: Int? }

// Real bulk/wholesale pricing -- see backend MerchantProductService.setPriceTiers's own
// doc comment. merchant-mfe/Android already have this; this is the first iOS
// MerchantApp client.
struct PriceTierDto: Codable { let minQuantity: Int; let unitPrice: Double }
struct SetPriceTiersRequest: Encodable {
    let tiers: [PriceTierDto]
    init(tiers: [PriceTierDto]) { self.tiers = tiers }
}
struct PriceTiersResponse: Decodable { let success: Bool; let tiers: [PriceTierDto] }
struct ProductAnalyticsResponse: Decodable { let success: Bool; let viewCount: Int; let orderCount: Int }

// Real menu-item option groups (item 210) -- merchant-mfe/Android already have this;
// this is the iOS MerchantApp client. v1 scope matches merchant-mfe's own: required,
// single-select groups only (e.g. "Size": Small/Medium/Large, exactly one choice).
struct MenuOptionChoiceDto: Decodable, Identifiable { let id: String; let name: String; let priceDelta: Double }
struct MenuOptionGroupDto: Decodable, Identifiable { let id: String; let name: String; let required: Bool; let multiSelect: Bool; let choices: [MenuOptionChoiceDto] }
struct MenuOptionChoiceRequest: Encodable { let name: String; let priceDelta: Double }
struct AddMenuOptionGroupRequest: Encodable { let name: String; let choices: [MenuOptionChoiceRequest] }
struct MenuOptionGroupResponse: Decodable { let success: Bool; let optionGroup: MenuOptionGroupDto }
struct MenuOptionGroupsResponse: Decodable { let success: Bool; let optionGroups: [MenuOptionGroupDto] }
struct UpdateProductStockRequest: Encodable { let stockQuantity: Int? }

// Real Commerce product reviews + owner-side reply (item 187/188/189) -- see
// ProductReviewService.replyToProductReview's own doc comment. merchant-mfe (item 187)
// and Android (item 188) already have this; this is the first iOS client.
struct ProductReviewDto: Decodable, Identifiable {
    let id: String
    let orderItemId: String
    let orderId: String
    let buyerId: String
    let productId: String
    let merchantId: String
    let rating: Int
    let comment: String?
    let ownerReply: String?
    let ownerRepliedAt: String?
    let createdAt: String
}
struct ProductReviewResponse: Decodable { let success: Bool; let review: ProductReviewDto }
struct ProductReviewsResponse: Decodable { let success: Bool; let reviews: [ProductReviewDto] }
struct ReplyToProductReviewRequest: Encodable { let reply: String }

struct GenerateQrRequest: Encodable { let amount: Double; let description: String }
struct PaymentIntentDto: Decodable { let id: String; let merchantId: String; let amount: Double; let description: String; let status: String; let expiresAt: String; let createdAt: String }
struct PaymentIntentResponse: Decodable { let success: Bool; let paymentIntent: PaymentIntentDto }

struct ChargeCardRequest: Encodable {
    let amount: Double
    let description: String
    let cardNumber: String
    let expiryMonth: Int
    let expiryYear: Int
    let cvc: String
}
struct CardChargeResponse: Decodable {
    let success: Bool
    let transactionId: String
    let merchantName: String
    let amount: Double
    let fee: Double
    let status: String
    let channel: String
    let cardLast4: String
    let completedAt: String
}

// Real KakaoTalk-style 기프티콘 (mobile gift voucher) merchant-side redemption --
// see backend GiftVoucherService.redeemVoucher's own doc comment: the customer shows
// the merchant their voucher (its real id, from their own itunda app), the merchant
// enters it here to redeem -- never a self-serve redeem the customer could fake.
// Real, shipped on the backend + merchant-mfe/Android with zero iOS MerchantApp
// client until now -- found via a cross-platform-parity check.
struct RedeemedGiftVoucherDto: Decodable {
    let id: String
    let productNameSnapshot: String?
    let amount: Double
    let status: String
    let redeemedAt: String?
}
struct RedeemGiftVoucherResponse: Decodable { let success: Bool; let voucher: RedeemedGiftVoucherDto }

struct ReportDayDto: Decodable, Identifiable {
    var id: String { date }
    let date: String
    let collectionCount: Int
    let grossAmount: Double
    let fees: Double
    let netAmount: Double
    let byChannel: [String: Int]
}
struct ReportResponse: Decodable { let success: Bool; let from: String; let to: String; let days: [ReportDayDto] }

struct TopSellingProductDto: Decodable, Identifiable {
    var id: String { productId }
    let productId: String
    let productName: String
    let unitsSold: Int
    let revenue: Double
}
struct TopSellingProductsResponse: Decodable { let success: Bool; let from: String; let to: String; let products: [TopSellingProductDto] }

// Real incoming Eats orders (restaurant side) -- previously only ever exposed in the
// consumer app's own bank-mfe, reused unmodified here.
struct EatsOrderDto: Decodable, Identifiable {
    let id: String
    let buyerId: String
    let restaurantId: String
    let riderId: String?
    let deliveryAddress: String
    let itemsSubtotal: Double
    let deliveryFee: Double
    let totalAmount: Double
    let status: String
    let createdAt: String
    let deliveryNotes: String?
    // Real Baemin-style 포장주문 (Pickup) order type (item 208) -- a PICKUP order has
    // riderId: nil for its whole lifecycle and reaches DELIVERED via
    // completePickupOrder below, not a rider hand-off.
    let fulfillmentType: String?
}
struct EatsOrderDetailResponse: Decodable { let success: Bool; let order: EatsOrderDto }
struct EatsOrdersResponse: Decodable { let success: Bool; let orders: [EatsOrderDto] }
struct UpdateEatsOrderStatusRequest: Encodable { let status: String }

// Real written-review list + owner-reply (item 184/185/186) -- see
// EatsReviewService.replyToRestaurantReview's own doc comment. bank-mfe (item 184) and
// Android (item 185) already have this; this is the first iOS client for the
// owner-reply side.
struct EatsReviewDto: Decodable, Identifiable {
    let id: String
    let orderId: String
    let buyerId: String
    let restaurantId: String
    let riderId: String?
    let restaurantRating: Int
    let restaurantComment: String?
    let riderRating: Int?
    let riderComment: String?
    let ownerReply: String?
    let ownerRepliedAt: String?
    let createdAt: String
}
struct EatsReviewResponse: Decodable { let success: Bool; let review: EatsReviewDto }
struct EatsReviewsResponse: Decodable { let success: Bool; let reviews: [EatsReviewDto] }
struct ReplyToEatsReviewRequest: Encodable { let reply: String }

// Real 토스뱅크 개인사업자 (business banking for sole proprietors) equivalent (item 151)
// -- see MerchantBusinessAccountService.kt's own doc comment. Android's native
// merchantapp already has this (BusinessAccountScreen.kt); this is the iOS port,
// mirroring the same field shapes merchant-mfe's own lib/merchant.ts (item 150) uses.
struct BusinessAccountDto: Decodable {
    let id: String
    let userId: String
    let accountNumber: String
    let accountName: String
    let type: String
    let balance: Double
    let availableBalance: Double
    let currency: String
}
struct BusinessAccountResponse: Decodable { let success: Bool; let account: BusinessAccountDto }

struct BusinessLedgerEntryDto: Decodable, Identifiable {
    let id: String
    let transactionId: String
    let accountId: String
    let direction: String
    let amount: Double
    let currency: String
    let balanceAfter: Double
    let memo: String
    let createdAt: String
}
struct BusinessTransactionsResponse: Decodable { let success: Bool; let transactions: [BusinessLedgerEntryDto] }
struct MoveBusinessMoneyRequest: Encodable { let amount: Double }

// Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see the backend's
// VendorCashAdvanceService.kt doc comment for the full sourced account. Genuinely
// distinct from every other lending product in this codebase: repayment is
// auto-collected as a variable % of this merchant's own real itunda-routed daily
// settlement inflow (QR/card collections), never a fixed installment the merchant
// initiates. Honest v1 limitation: a vendor's off-platform cash sales are invisible
// to both underwriting and collection -- surfaced directly in this screen's own copy.
// Mirrors merchant-mfe's lib/vendorCashAdvance.ts / Android's VendorCashAdvanceDto exactly.
struct VendorCashAdvanceDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let principalAmount: Double
    let feeAmount: Double
    let totalOwed: Double
    let remainingOwed: Double
    let collectionRatePercent: Double
    let status: String
    let requestedAt: String
    let disbursedAt: String?
    let repaidAt: String?
    let lastCollectionAt: String?
}
struct ApplyForVendorCashAdvanceRequest: Encodable { let merchantId: String }
struct RepayVendorCashAdvanceEarlyRequest: Encodable { let amount: Double }
struct VendorCashAdvanceResponse: Decodable { let success: Bool; let advance: VendorCashAdvanceDto }
struct VendorCashAdvanceNullableResponse: Decodable { let success: Bool; let advance: VendorCashAdvanceDto? }
struct VendorCashAdvanceOfferResponse: Decodable {
    let success: Bool
    let eligible: Bool
    let reason: String?
    let offerAmount: Double?
    let feeAmount: Double?
    let collectionRatePercent: Double?
    let averageDailySettlement: Double?
    let tradingDays: Int?
}

// Real 배민오더-style table/QR in-store ordering, restaurant side (item 164) -- see
// DineInOrderController.kt on the backend. Android's native merchantapp already has
// this (DineInScreen.kt); this is the iOS port. Trimmed to the fields this app's UI
// actually reads, same discipline Android's own DineInOrderDto here already applies.
struct DineInOrderDto: Decodable, Identifiable {
    let id: String
    let buyerId: String
    let restaurantId: String
    let tableNumber: String
    let totalAmount: Double
    let status: String
    let notes: String?
    let createdAt: String
}
struct DineInOrderDetailResponse: Decodable { let success: Bool; let order: DineInOrderDto }
struct DineInOrdersResponse: Decodable { let success: Bool; let orders: [DineInOrderDto] }
struct UpdateDineInOrderStatusRequest: Encodable { let status: String }

/// Real 배민오더-style per-table QR -- printed/displayed at a physical table, resolved
/// by a customer's own app into the dine-in ordering screen for this exact restaurant +
/// table. Same encoding convention as paymentIntentQrPayload.
func dineInTableQrPayload(restaurantId: String, tableNumber: String) -> String {
    let encoded = tableNumber.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? tableNumber
    return "itunda://eats/dine-in?restaurantId=\(restaurantId)&table=\(encoded)"
}

// Real push device-token registration (item 130) -- see the consumer app's own
// NetworkClient.swift doc comment (items 119-121) and RiderApp's own matching fix,
// same pass: PushNotificationService.sendToUser silently no-ops for every real user
// with no registered token, and this dedicated merchant app -- the one place a
// merchant owner actually needs an instant new-order/booking push -- never registered
// one at all. Reuses the same real per-install device id MerchantDeviceStore already
// established for trusted-device binding.
struct RegisterDeviceTokenRequest: Encodable { let platform: String; let token: String }
struct SuccessResponse: Decodable { let success: Bool }

// Real Naver Smart Store-style "관심고객" (interested-customer) follower count +
// broadcast-to-followers -- see MerchantFollowController's own doc comment. Distinct
// from the customer-facing follow/unfollow already real on bank-mfe/Android app/iOS
// app. merchant-mfe already has this (item 118); this is the first native-merchant-app
// client for the owner-facing half.
struct FollowerCountResponse: Decodable { let success: Bool; let count: Int }
struct BroadcastToFollowersRequest: Encodable { let title: String; let body: String }
struct BroadcastToFollowersResponse: Decodable { let success: Bool; let recipientCount: Int }

enum NetworkError: Error {
    case invalidResponse
    case httpError(statusCode: Int)
    // Real device binding (2026-07-28 port) -- only ever thrown for a 403 on a call
    // that itself carried an Idempotency-Key, mirroring CoreNetwork's own
    // authenticatedPost so this never misclassifies an unrelated 403.
    case deviceNotVerified
    // Real gap found 2026-09-04 (same pass that added CoreNetwork's own
    // postEatsOrder/httpErrorWithMessage fix on the main app): sendRequest below only
    // ever threw the bare, message-less httpError case, so all ~65 catch sites across
    // this app's screens fell back to a hardcoded per-screen generic string for every
    // failure, never the backend's own real, specific validation message (e.g.
    // PayrollService's "Salary amount must be greater than zero", "Cannot add the
    // business owner as a payroll employee", etc.). Purely additive -- the existing
    // bare httpError case and its 3 real UI pattern-matches (BecomeMerchantScreen/
    // DeviceStepUpDialog/LoginScreen) are updated in the same commit, not left to
    // silently degrade.
    case httpErrorWithMessage(statusCode: Int, message: String?)
}

private struct ApiErrorBody: Decodable { let code: String?; let message: String? }

/// Real, minimal URLSession client, mirroring RiderApp's own NetworkClient.swift --
/// this app's own copy, scoped to rw.itunda.merchant's endpoints plus the Eats
/// restaurant-order endpoints.
final class MerchantNetworkClient {
    static let shared = MerchantNetworkClient()

    private let baseURL = URL(string: "http://localhost:4001/")!
    private let session = URLSession(configuration: .default)
    private lazy var encoder = JSONEncoder()
    private lazy var decoder = JSONDecoder()

    private init() {}

    func login(_ request: LoginRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/login", body: request, authenticated: false)
    }

    func registerMerchant(businessName: String) async throws -> MerchantResponse {
        try await post("api/v1/merchant/register", body: RegisterMerchantRequest(businessName: businessName))
    }

    func getMyMerchant() async throws -> MerchantResponse { try await get("api/v1/merchant/me") }

    // Real business location -- see MerchantController.kt's own doc comment.
    // merchant-mfe already has this; this is the first iOS client.
    func setMerchantLocation(_ request: SetMerchantLocationRequest) async throws -> MerchantResponse {
        try await post("api/v1/merchant/location", body: request)
    }

    // Real radius-targeted local ads -- see MerchantAdController.kt's own doc comment.
    // merchant-mfe already has this; this is the first iOS client.
    func createOrExtendAd(_ request: CreateAdRequest) async throws -> MerchantAdResponse {
        try await postWithHeader("api/v1/merchant/ads", body: request, header: ("Idempotency-Key", UUID().uuidString))
    }
    func getMyAd() async throws -> MerchantAdResponse { try await get("api/v1/merchant/ads/me") }

    // Real local-business appointment booking, owner side -- see
    // MerchantBookingController.kt's own doc comment. merchant-mfe/Android already
    // have this; this is the first iOS client.
    func setAvailability(_ request: SetAvailabilityRequest) async throws -> AvailabilityResponse {
        try await post("api/v1/merchant/booking/availability", body: request)
    }
    func getMyAvailability() async throws -> AvailabilityResponse { try await get("api/v1/merchant/booking/availability") }
    func getMerchantBookings() async throws -> MerchantBookingsResponse { try await get("api/v1/merchant/bookings/merchant-bookings") }
    func respondToBooking(_ bookingId: String, confirm: Bool) async throws -> MerchantBookingDetailResponse {
        try await post("api/v1/merchant/bookings/\(bookingId)/respond", body: RespondToBookingRequest(confirm: confirm))
    }
    func completeBooking(_ bookingId: String) async throws -> MerchantBookingDetailResponse {
        try await post("api/v1/merchant/bookings/\(bookingId)/complete", body: EmptyBody())
    }

    func generateQr(amount: Double, description: String) async throws -> PaymentIntentResponse {
        try await post("api/v1/merchant/qr/generate", body: GenerateQrRequest(amount: amount, description: description))
    }

    func chargeCard(_ request: ChargeCardRequest) async throws -> CardChargeResponse {
        try await postWithHeader("api/v1/merchant/card/charge", body: request, header: ("Idempotency-Key", UUID().uuidString))
    }

    // Real KakaoTalk-style 기프티콘 (mobile gift voucher) merchant-side redemption --
    // see RedeemedGiftVoucherDto's own doc comment for the full sourced account.
    func redeemGiftVoucher(_ voucherId: String) async throws -> RedeemGiftVoucherResponse {
        try await postWithHeader("api/v1/gift-vouchers/\(voucherId)/redeem", body: EmptyBody(), header: ("Idempotency-Key", UUID().uuidString))
    }

    func verifyDevice(password: String) async throws -> VerifyDeviceResponse {
        try await post("api/v1/auth/devices/verify", body: VerifyDeviceRequest(password: password))
    }

    func registerDeviceToken(_ request: RegisterDeviceTokenRequest) async throws -> SuccessResponse {
        try await post("api/v1/notifications/device-tokens", body: request)
    }

    func getProductCatalog() async throws -> MerchantProductsResponse { try await get("api/v1/merchant/products") }

    func getProductReviews(_ productId: String) async throws -> ProductReviewsResponse {
        try await get("api/v1/orders/products/\(productId)/reviews")
    }

    func replyToProductReview(_ reviewId: String, reply: String) async throws -> ProductReviewResponse {
        try await post("api/v1/orders/reviews/\(reviewId)/reply", body: ReplyToProductReviewRequest(reply: reply))
    }

    func addProduct(name: String, price: Double, imageUrl: String?, originalPrice: Double?, description: String?, stockQuantity: Int?) async throws -> MerchantProductResponse {
        try await post("api/v1/merchant/products", body: AddProductRequest(name: name, price: price, imageUrl: imageUrl, originalPrice: originalPrice, description: description, stockQuantity: stockQuantity))
    }

    func updateProductStock(_ productId: String, stockQuantity: Int?) async throws -> MerchantProductResponse {
        let data = try await sendRequest(method: "PATCH", path: "api/v1/merchant/products/\(productId)/stock", body: UpdateProductStockRequest(stockQuantity: stockQuantity))
        return try decoder.decode(MerchantProductResponse.self, from: data)
    }

    func removeProduct(_ productId: String) async throws -> MerchantProductResponse {
        let data = try await sendRequest(method: "DELETE", path: "api/v1/merchant/products/\(productId)", body: Optional<EmptyBody>.none)
        return try decoder.decode(MerchantProductResponse.self, from: data)
    }

    func getOptionGroups(_ productId: String) async throws -> MenuOptionGroupsResponse {
        try await get("api/v1/merchant/products/\(productId)/option-groups")
    }

    func addOptionGroup(_ productId: String, name: String, choices: [MenuOptionChoiceRequest]) async throws -> MenuOptionGroupResponse {
        try await post("api/v1/merchant/products/\(productId)/option-groups", body: AddMenuOptionGroupRequest(name: name, choices: choices))
    }

    func removeOptionGroup(_ productId: String, groupId: String) async throws {
        _ = try await sendRequest(method: "DELETE", path: "api/v1/merchant/products/\(productId)/option-groups/\(groupId)", body: Optional<EmptyBody>.none)
    }

    // Real bulk/wholesale pricing -- see backend MerchantProductService.setPriceTiers's
    // own doc comment. merchant-mfe/Android already have this; this is the first iOS
    // MerchantApp client.
    func getPriceTiers(_ productId: String) async throws -> PriceTiersResponse {
        try await get("api/v1/merchant/products/\(productId)/price-tiers")
    }

    // Real Coupang WING 상품분석 (product analytics) -- ported from merchant-mfe/
    // Android (2026-09-03).
    func getProductAnalytics(_ productId: String) async throws -> ProductAnalyticsResponse {
        try await get("api/v1/merchant/products/\(productId)/analytics")
    }

    func setPriceTiers(_ productId: String, tiers: [PriceTierDto]) async throws -> PriceTiersResponse {
        try await post("api/v1/merchant/products/\(productId)/price-tiers", body: SetPriceTiersRequest(tiers: tiers))
    }

    func getReport(from: String? = nil, to: String? = nil) async throws -> ReportResponse {
        var query: [URLQueryItem] = []
        if let from { query.append(URLQueryItem(name: "from", value: from)) }
        if let to { query.append(URLQueryItem(name: "to", value: to)) }
        return try await get("api/v1/merchant/reports", query: query)
    }

    // Real Coupang WING-style top-selling-products report -- merchant-mfe's own
    // ReportsScreen.tsx has had this since 2026-08-16, ported here via the
    // uncalled-endpoint sweep.
    func getTopSellingProducts(from: String? = nil, to: String? = nil) async throws -> TopSellingProductsResponse {
        var query: [URLQueryItem] = []
        if let from { query.append(URLQueryItem(name: "from", value: from)) }
        if let to { query.append(URLQueryItem(name: "to", value: to)) }
        return try await get("api/v1/merchant/reports/top-products", query: query)
    }

    func openBusinessAccount() async throws -> BusinessAccountResponse {
        try await post("api/v1/merchant/business-account", body: EmptyBody())
    }

    func getBusinessAccount() async throws -> BusinessAccountResponse { try await get("api/v1/merchant/business-account") }

    func getBusinessTransactions() async throws -> BusinessTransactionsResponse { try await get("api/v1/merchant/business-account/transactions") }

    // Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see
    // VendorCashAdvanceDto's own doc comment. merchant-mfe/Android already have this;
    // this is the first iOS MerchantApp client, found while confirming that gap on
    // this platform specifically (2026-08-05).
    // Correction, 2026-09-05: the "no Idempotency-Key on apply, row creation only"
    // reasoning missed the actual risk -- VendorCashAdvanceService.applyForAdvance's
    // own VendorCashAdvanceAlreadyActiveException guard fires on a legitimate
    // lost-response retry regardless of whether money moved yet (see
    // VendorCashAdvanceController.apply's own doc comment for the full gap) -- now
    // protected, same convention as disburse/repay-early below.
    func getVendorCashAdvanceOffer(merchantId: String) async throws -> VendorCashAdvanceOfferResponse {
        try await get("api/v1/vendor-advance/offer", query: [URLQueryItem(name: "merchantId", value: merchantId)])
    }
    func applyForVendorCashAdvance(merchantId: String) async throws -> VendorCashAdvanceResponse {
        try await postWithHeader("api/v1/vendor-advance/apply", body: ApplyForVendorCashAdvanceRequest(merchantId: merchantId), header: ("Idempotency-Key", UUID().uuidString))
    }
    func disburseVendorCashAdvance(_ advanceId: String) async throws -> VendorCashAdvanceResponse {
        try await postWithHeader("api/v1/vendor-advance/\(advanceId)/disburse", body: EmptyBody(), header: ("Idempotency-Key", UUID().uuidString))
    }
    func getMyVendorCashAdvance(merchantId: String) async throws -> VendorCashAdvanceNullableResponse {
        try await get("api/v1/vendor-advance/me", query: [URLQueryItem(name: "merchantId", value: merchantId)])
    }
    func repayVendorCashAdvanceEarly(_ advanceId: String, amount: Double) async throws -> VendorCashAdvanceResponse {
        try await postWithHeader("api/v1/vendor-advance/\(advanceId)/repay-early", body: RepayVendorCashAdvanceEarlyRequest(amount: amount), header: ("Idempotency-Key", UUID().uuidString))
    }

    func getDineInOrders() async throws -> DineInOrdersResponse { try await get("api/v1/eats/dine-in/orders/restaurant-orders") }

    func advanceDineInOrderStatus(_ orderId: String, status: String) async throws -> DineInOrderDetailResponse {
        try await post("api/v1/eats/dine-in/orders/\(orderId)/status", body: UpdateDineInOrderStatusRequest(status: status))
    }

    func moveToBusiness(amount: Double) async throws -> BusinessAccountResponse {
        try await postWithHeader("api/v1/merchant/business-account/move-to-business", body: MoveBusinessMoneyRequest(amount: amount), header: ("Idempotency-Key", UUID().uuidString))
    }

    func moveToPersonal(amount: Double) async throws -> BusinessAccountResponse {
        try await postWithHeader("api/v1/merchant/business-account/move-to-personal", body: MoveBusinessMoneyRequest(amount: amount), header: ("Idempotency-Key", UUID().uuidString))
    }

    // Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support program) --
    // see rw.itunda.merchant.MerchantFeeWaiverService's own doc comment. merchant-mfe and
    // Android already have this; this is the first iOS client.
    func applyForFeeWaiver() async throws -> MerchantResponse {
        try await post("api/v1/merchant/fee-waiver/apply", body: EmptyBody())
    }

    // Real merchant coupons + 단골 (regular customer) loyalty gating -- see
    // rw.itunda.merchant.MerchantCouponService's own doc comment. Merchant-owner-facing
    // create/list/deactivate half only. merchant-mfe/Android already have this; this is
    // the first iOS client.
    func createCoupon(_ request: CreateCouponRequest) async throws -> MerchantCouponResponse {
        try await post("api/v1/merchant/coupons", body: request)
    }
    func getMyCoupons() async throws -> MerchantCouponsResponse { try await get("api/v1/merchant/coupons") }
    func deactivateCoupon(_ couponId: String) async throws -> MerchantCouponResponse {
        try await post("api/v1/merchant/coupons/\(couponId)/deactivate", body: EmptyBody())
    }

    // Real B2B payroll methods moved to NetworkClient+Payroll.swift, 2026-08-30 -- see
    // that file's own doc comment. postWithHeader/delete widened from private to
    // internal (zero line-count change here) so that extension can reuse them,
    // matching NetworkClient+VisitorAnalytics.swift's own established precedent for
    // `get`/`post`.

    // Real recurring merchant billing (item 144) -- see MerchantBillingController.kt's
    // own doc comment. merchant-mfe already has this; this is the first iOS client.
    func createBillingPlan(_ request: CreateBillingPlanRequest) async throws -> MerchantBillingPlanResponse {
        try await postWithHeader("api/v1/merchant/billing-plans", body: request, header: ("Idempotency-Key", UUID().uuidString))
    }
    func getMyBillingPlans() async throws -> MerchantBillingPlansResponse { try await get("api/v1/merchant/billing-plans") }
    func deactivateBillingPlan(_ planId: String) async throws -> MerchantBillingPlanResponse {
        try await post("api/v1/merchant/billing-plans/\(planId)/deactivate", body: EmptyBody())
    }

    // Real payment-event webhook URL settings -- see
    // rw.itunda.merchant.MerchantService.setWebhookUrl's own doc comment. merchant-mfe/
    // Android already have this; this is the first iOS client.
    func setWebhookUrl(_ webhookUrl: String) async throws -> MerchantResponse {
        try await post("api/v1/merchant/webhook-url", body: SetWebhookUrlRequest(webhookUrl: webhookUrl))
    }

    func generateApiKey() async throws -> GenerateApiKeyResponse {
        try await post("api/v1/merchant/api-key/generate", body: EmptyBody())
    }

    func generateWebhookSecret() async throws -> GenerateWebhookSecretResponse {
        try await post("api/v1/merchant/webhook-secret/generate", body: EmptyBody())
    }

    func getWebhookDeliveries() async throws -> WebhookDeliveriesResponse { try await get("api/v1/merchant/webhook-deliveries") }

    func replayWebhookDelivery(_ deliveryId: String) async throws -> ReplayWebhookDeliveryResponse {
        try await post("api/v1/merchant/webhook-deliveries/\(deliveryId)/replay", body: EmptyBody())
    }

    // Real store-settings endpoints -- see MerchantController.kt's own doc comments.
    // Found 2026-08-01 via a dead-field sweep: category was a real MerchantDto field
    // with zero UI anywhere on this app; photo/min-order/cashback-rate/scheduled-
    // orders/eats-membership were real backend endpoints with zero client anywhere at
    // all (not even merchant-mfe, for the first four) until this same pass.
    func setCategory(_ category: String) async throws -> MerchantResponse {
        try await post("api/v1/merchant/category", body: SetCategoryRequest(category: category))
    }
    func setMerchantPhotoUrl(_ photoUrl: String) async throws -> MerchantResponse {
        try await post("api/v1/merchant/photo", body: SetMerchantPhotoUrlRequest(photoUrl: photoUrl))
    }
    func setMinOrderAmount(_ minOrderAmount: Double?) async throws -> MerchantResponse {
        try await post("api/v1/merchant/min-order", body: SetMinOrderAmountRequest(minOrderAmount: minOrderAmount))
    }
    func setCashbackRate(_ rate: Double?) async throws -> MerchantResponse {
        try await post("api/v1/merchant/cashback-rate", body: SetCashbackRateRequest(rate: rate))
    }
    func setAcceptsScheduledOrders(_ accepts: Bool) async throws -> MerchantResponse {
        try await post("api/v1/merchant/scheduled-orders-participation", body: SetAcceptsScheduledOrdersRequest(accepts: accepts))
    }
    func setParticipatesInEatsMembership(_ participates: Bool) async throws -> MerchantResponse {
        try await post("api/v1/merchant/eats-membership-participation", body: SetParticipatesInEatsMembershipRequest(participates: participates))
    }

    // Real merchant-set phone/hours/prep-time/pickup-discount/accepting-orders/
    // closed-weekdays -- see MerchantDto.phoneNumber's own doc comment.
    func setMerchantPhoneNumber(_ phoneNumber: String?) async throws -> MerchantResponse {
        try await post("api/v1/merchant/phone", body: SetMerchantPhoneNumberRequest(phoneNumber: phoneNumber))
    }
    func setMerchantOpeningHours(_ openingHours: String?) async throws -> MerchantResponse {
        try await post("api/v1/merchant/hours", body: SetMerchantOpeningHoursRequest(openingHours: openingHours))
    }
    func setMerchantAvgPrepTimeMinutes(_ avgPrepTimeMinutes: Int?) async throws -> MerchantResponse {
        try await post("api/v1/merchant/prep-time", body: SetMerchantAvgPrepTimeMinutesRequest(avgPrepTimeMinutes: avgPrepTimeMinutes))
    }
    func setMerchantPickupDiscount(_ pickupDiscountPercent: Int?) async throws -> MerchantResponse {
        try await post("api/v1/merchant/pickup-discount", body: SetMerchantPickupDiscountRequest(pickupDiscountPercent: pickupDiscountPercent))
    }
    func setAcceptingOrders(_ accepting: Bool) async throws -> MerchantResponse {
        try await post("api/v1/merchant/accepting-orders", body: SetAcceptingOrdersRequest(accepting: accepting))
    }
    func setClosedWeekdays(_ weekdays: [Int]) async throws -> MerchantResponse {
        try await post("api/v1/merchant/closed-weekdays", body: SetClosedWeekdaysRequest(weekdays: weekdays))
    }

    // Real demo KYB structural pre-check -- see IdentitySubmissionDto's own doc
    // comment.
    func submitIdentity(documentType: String, documentNumber: String, documentReference: String) async throws -> SubmitIdentityResponse {
        try await post("api/v1/identity/submit", body: SubmitIdentityRequest(documentType: documentType, documentNumber: documentNumber, documentReference: documentReference))
    }
    func getIdentityStatus() async throws -> IdentityStatusResponse { try await get("api/v1/identity/status") }

    func getFollowerCount() async throws -> FollowerCountResponse { try await get("api/v1/merchant/followers/count") }

    func broadcastToFollowers(title: String, body: String) async throws -> BroadcastToFollowersResponse {
        try await post("api/v1/merchant/followers/broadcast", body: BroadcastToFollowersRequest(title: title, body: body))
    }

    func getRestaurantOrders() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/restaurant-orders") }

    func advanceRestaurantOrderStatus(_ orderId: String, status: String) async throws -> EatsOrderDetailResponse {
        try await post("api/v1/eats/orders/\(orderId)/status", body: UpdateEatsOrderStatusRequest(status: status))
    }

    // Real Baemin-style 포장주문 (Pickup) terminal edge (item 208) -- see
    // EatsOrderDto.fulfillmentType's own doc comment.
    func completePickupOrder(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await post("api/v1/eats/orders/\(orderId)/complete-pickup", body: EmptyBody())
    }

    func getRestaurantReviews(_ restaurantId: String) async throws -> EatsReviewsResponse {
        try await get("api/v1/eats/restaurants/\(restaurantId)/reviews")
    }

    func replyToRestaurantReview(_ reviewId: String, reply: String) async throws -> EatsReviewResponse {
        try await post("api/v1/eats/reviews/\(reviewId)/reply", body: ReplyToEatsReviewRequest(reply: reply))
    }

    // MARK: - Helpers

    func get<Response: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> Response {
        guard var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false) else {
            throw NetworkError.invalidResponse
        }
        components.queryItems = query.isEmpty ? nil : query
        guard let url = components.url else { throw NetworkError.invalidResponse }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = MerchantKeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else { throw NetworkError.httpError(statusCode: httpResponse.statusCode) }
        return try decoder.decode(Response.self, from: data)
    }

    func post<Body: Encodable, Response: Decodable>(_ path: String, body: Body, authenticated: Bool = true) async throws -> Response {
        let data = try await sendRequest(method: "POST", path: path, body: body, authenticated: authenticated)
        return try decoder.decode(Response.self, from: data)
    }

    func postWithHeader<Body: Encodable, Response: Decodable>(_ path: String, body: Body, header: (String, String)) async throws -> Response {
        let data = try await sendRequest(method: "POST", path: path, body: body, extraHeader: header)
        return try decoder.decode(Response.self, from: data)
    }

    func delete<Response: Decodable>(_ path: String) async throws -> Response {
        let data = try await sendRequest(method: "DELETE", path: path, body: EmptyBody())
        return try decoder.decode(Response.self, from: data)
    }

    @discardableResult
    private func sendRequest<Body: Encodable>(method: String, path: String, body: Body?, authenticated: Bool = true, extraHeader: (String, String)? = nil) async throws -> Data {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = method
        if authenticated, let token = MerchantKeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let extraHeader {
            request.setValue(extraHeader.1, forHTTPHeaderField: extraHeader.0)
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try encoder.encode(body)
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            let errorBody = try? decoder.decode(ApiErrorBody.self, from: data)
            if extraHeader?.0 == "Idempotency-Key", httpResponse.statusCode == 403,
               errorBody?.code == "DEVICE_NOT_VERIFIED" {
                throw NetworkError.deviceNotVerified
            }
            throw NetworkError.httpErrorWithMessage(statusCode: httpResponse.statusCode, message: errorBody?.message)
        }
        return data
    }
}

struct EmptyBody: Encodable {}

/// itunda's real custom URL scheme for a customer's own app to resolve into a real
/// POST /api/v1/merchant/collect/{intentId} call -- the same client-side encoding
/// convention merchant-mfe's web POS screen and Android's own merchantapp already
/// established.
func paymentIntentQrPayload(_ intentId: String) -> String { "itunda://pay?intentId=\(intentId)" }
