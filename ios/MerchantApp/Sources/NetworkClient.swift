import Foundation

// Mirrors services/backend/auth's real AuthDtos exactly. A merchant owner logs into
// their existing itunda account first, then registers a business via this app --
// no separate registration screen for a brand-new itunda account.
struct LoginRequest: Encodable { let phoneNumber: String; let password: String }
struct PublicUser: Decodable { let id: String; let phoneNumber: String; let firstName: String; let lastName: String }
struct AuthResponse: Decodable { let message: String; let user: PublicUser; let accessToken: String; let refreshToken: String }

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

enum NetworkError: Error {
    case invalidResponse
    case httpError(statusCode: Int)
}

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

    func getProductCatalog() async throws -> MerchantProductsResponse { try await get("api/v1/merchant/products") }

    func addProduct(name: String, price: Double) async throws -> MerchantProductResponse {
        try await post("api/v1/merchant/products", body: AddProductRequest(name: name, price: price))
    }

    func removeProduct(_ productId: String) async throws -> MerchantProductResponse {
        let data = try await sendRequest(method: "DELETE", path: "api/v1/merchant/products/\(productId)", body: Optional<EmptyBody>.none)
        return try decoder.decode(MerchantProductResponse.self, from: data)
    }

    func getReport() async throws -> ReportResponse { try await get("api/v1/merchant/reports") }

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
        guard (200...299).contains(httpResponse.statusCode) else { throw NetworkError.httpError(statusCode: httpResponse.statusCode) }
        return data
    }
}

private struct EmptyBody: Encodable {}

/// itunda's real custom URL scheme for a customer's own app to resolve into a real
/// POST /api/v1/merchant/collect/{intentId} call -- the same client-side encoding
/// convention merchant-mfe's web POS screen and Android's own merchantapp already
/// established.
func paymentIntentQrPayload(_ intentId: String) -> String { "itunda://pay?intentId=\(intentId)" }
