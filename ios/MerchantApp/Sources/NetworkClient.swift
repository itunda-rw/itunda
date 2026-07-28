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
}
struct MerchantResponse: Decodable { let success: Bool; let merchant: MerchantDto }
struct RegisterMerchantRequest: Encodable { let businessName: String }

struct MerchantProductDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let name: String
    let price: Double
    let active: Bool
    let createdAt: String
}
struct MerchantProductResponse: Decodable { let success: Bool; let product: MerchantProductDto }
struct MerchantProductsResponse: Decodable { let success: Bool; let products: [MerchantProductDto] }
struct AddProductRequest: Encodable { let name: String; let price: Double }

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
}
struct EatsOrderDetailResponse: Decodable { let success: Bool; let order: EatsOrderDto }
struct EatsOrdersResponse: Decodable { let success: Bool; let orders: [EatsOrderDto] }
struct UpdateEatsOrderStatusRequest: Encodable { let status: String }

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

// Real push device-token registration (item 130) -- see the consumer app's own
// NetworkClient.swift doc comment (items 119-121) and RiderApp's own matching fix,
// same pass: PushNotificationService.sendToUser silently no-ops for every real user
// with no registered token, and this dedicated merchant app -- the one place a
// merchant owner actually needs an instant new-order/booking push -- never registered
// one at all. Reuses the same real per-install device id MerchantDeviceStore already
// established for trusted-device binding.
struct RegisterDeviceTokenRequest: Encodable { let platform: String; let token: String }
struct SuccessResponse: Decodable { let success: Bool }

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

    func addProduct(name: String, price: Double) async throws -> MerchantProductResponse {
        try await post("api/v1/merchant/products", body: AddProductRequest(name: name, price: price))
    }

    func removeProduct(_ productId: String) async throws -> MerchantProductResponse {
        let data = try await sendRequest(method: "DELETE", path: "api/v1/merchant/products/\(productId)", body: Optional<EmptyBody>.none)
        return try decoder.decode(MerchantProductResponse.self, from: data)
    }

    func getReport() async throws -> ReportResponse { try await get("api/v1/merchant/reports") }

    func openBusinessAccount() async throws -> BusinessWalletResponse {
        try await post("api/v1/merchant/business-account", body: EmptyBody())
    }

    func getBusinessAccount() async throws -> BusinessWalletResponse { try await get("api/v1/merchant/business-account") }

    func getBusinessTransactions() async throws -> BusinessTransactionsResponse { try await get("api/v1/merchant/business-account/transactions") }

    func moveToBusiness(amount: Double) async throws -> BusinessWalletResponse {
        try await postWithHeader("api/v1/merchant/business-account/move-to-business", body: MoveBusinessMoneyRequest(amount: amount), header: ("Idempotency-Key", UUID().uuidString))
    }

    func moveToPersonal(amount: Double) async throws -> BusinessWalletResponse {
        try await postWithHeader("api/v1/merchant/business-account/move-to-personal", body: MoveBusinessMoneyRequest(amount: amount), header: ("Idempotency-Key", UUID().uuidString))
    }

    func getRestaurantOrders() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/restaurant-orders") }

    func advanceRestaurantOrderStatus(_ orderId: String, status: String) async throws -> EatsOrderDetailResponse {
        try await post("api/v1/eats/orders/\(orderId)/status", body: UpdateEatsOrderStatusRequest(status: status))
    }

    // MARK: - Helpers

    private func get<Response: Decodable>(_ path: String) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
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
