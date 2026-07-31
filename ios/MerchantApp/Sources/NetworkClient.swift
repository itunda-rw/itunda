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
}
struct MerchantResponse: Decodable { let success: Bool; let merchant: MerchantDto }

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
struct BusinessWalletDto: Decodable {
    let id: String
    let userId: String
    let accountNumber: String
    let accountName: String
    let type: String
    let balance: Double
    let availableBalance: Double
    let currency: String
}
struct BusinessWalletResponse: Decodable { let success: Bool; let wallet: BusinessWalletDto }

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
}

private struct ApiErrorBody: Decodable { let code: String? }

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

    func generateQr(amount: Double, description: String) async throws -> PaymentIntentResponse {
        try await post("api/v1/merchant/qr/generate", body: GenerateQrRequest(amount: amount, description: description))
    }

    func chargeCard(_ request: ChargeCardRequest) async throws -> CardChargeResponse {
        try await postWithHeader("api/v1/merchant/card/charge", body: request, header: ("Idempotency-Key", UUID().uuidString))
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

    func setPriceTiers(_ productId: String, tiers: [PriceTierDto]) async throws -> PriceTiersResponse {
        try await post("api/v1/merchant/products/\(productId)/price-tiers", body: SetPriceTiersRequest(tiers: tiers))
    }

    func getReport(from: String? = nil, to: String? = nil) async throws -> ReportResponse {
        var query: [URLQueryItem] = []
        if let from { query.append(URLQueryItem(name: "from", value: from)) }
        if let to { query.append(URLQueryItem(name: "to", value: to)) }
        return try await get("api/v1/merchant/reports", query: query)
    }

    func openBusinessAccount() async throws -> BusinessWalletResponse {
        try await post("api/v1/merchant/business-account", body: EmptyBody())
    }

    func getBusinessAccount() async throws -> BusinessWalletResponse { try await get("api/v1/merchant/business-account") }

    func getBusinessTransactions() async throws -> BusinessTransactionsResponse { try await get("api/v1/merchant/business-account/transactions") }

    func getDineInOrders() async throws -> DineInOrdersResponse { try await get("api/v1/eats/dine-in/orders/restaurant-orders") }

    func advanceDineInOrderStatus(_ orderId: String, status: String) async throws -> DineInOrderDetailResponse {
        try await post("api/v1/eats/dine-in/orders/\(orderId)/status", body: UpdateDineInOrderStatusRequest(status: status))
    }

    func moveToBusiness(amount: Double) async throws -> BusinessWalletResponse {
        try await postWithHeader("api/v1/merchant/business-account/move-to-business", body: MoveBusinessMoneyRequest(amount: amount), header: ("Idempotency-Key", UUID().uuidString))
    }

    func moveToPersonal(amount: Double) async throws -> BusinessWalletResponse {
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

    // Real payment-event webhook URL settings -- see
    // rw.itunda.merchant.MerchantService.setWebhookUrl's own doc comment. merchant-mfe/
    // Android already have this; this is the first iOS client.
    func setWebhookUrl(_ webhookUrl: String) async throws -> MerchantResponse {
        try await post("api/v1/merchant/webhook-url", body: SetWebhookUrlRequest(webhookUrl: webhookUrl))
    }

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

    private func get<Response: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> Response {
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

    private func post<Body: Encodable, Response: Decodable>(_ path: String, body: Body, authenticated: Bool = true) async throws -> Response {
        let data = try await sendRequest(method: "POST", path: path, body: body, authenticated: authenticated)
        return try decoder.decode(Response.self, from: data)
    }

    private func postWithHeader<Body: Encodable, Response: Decodable>(_ path: String, body: Body, header: (String, String)) async throws -> Response {
        let data = try await sendRequest(method: "POST", path: path, body: body, extraHeader: header)
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
            if extraHeader?.0 == "Idempotency-Key", httpResponse.statusCode == 403,
               let errorBody = try? decoder.decode(ApiErrorBody.self, from: data),
               errorBody.code == "DEVICE_NOT_VERIFIED" {
                throw NetworkError.deviceNotVerified
            }
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return data
    }
}

private struct EmptyBody: Encodable {}

/// itunda's real custom URL scheme for a customer's own app to resolve into a real
/// POST /api/v1/merchant/collect/{intentId} call -- the same client-side encoding
/// convention merchant-mfe's web POS screen and Android's own merchantapp already
/// established.
func paymentIntentQrPayload(_ intentId: String) -> String { "itunda://pay?intentId=\(intentId)" }
