import Foundation

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names, so JSONDecoder reads the real backend's JSON directly. A rider
// registers their itunda account through the consumer app first (this app has no
// register screen) -- this only needs to log an existing account in.
struct LoginRequest: Encodable {
    let phoneNumber: String
    let password: String
}

struct PublicUser: Decodable {
    let id: String
    let phoneNumber: String
    let firstName: String
    let lastName: String
}

struct AuthResponse: Decodable {
    let message: String
    let user: PublicUser
    let accessToken: String
    let refreshToken: String
}

// Mirrors rw.itunda.eats's real Rider/EatsOrder entities exactly (same field names
// as the consumer app's own equivalents) -- trimmed to only the fields this app's
// UI actually reads.
struct RiderDto: Decodable {
    let id: String
    let userId: String
    let accountId: String
    let status: String
    let available: Bool
    let createdAt: String
}
struct RiderResponse: Decodable { let success: Bool; let rider: RiderDto }

// Real rider rating (item 142) -- see EatsController.getRiderRating's own doc
// comment: a real average computed from post-delivery reviews, real, had zero client
// on any platform including this dedicated rider app.
struct RiderRatingResponse: Decodable { let success: Bool; let average: Double?; let count: Int }

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
    let deliveryLatitude: Double?
    let deliveryLongitude: Double?
    let distanceKm: Double?
    let deliveryNotes: String?
}
struct EatsOrderDetailResponse: Decodable { let success: Bool; let order: EatsOrderDto }
struct EatsOrdersResponse: Decodable { let success: Bool; let orders: [EatsOrderDto] }

struct SetRiderAvailabilityRequest: Encodable { let available: Bool }
struct UpdateRiderLocationRequest: Encodable { let latitude: Double; let longitude: Double }
struct UpdateEatsOrderStatusRequest: Encodable { let status: String }

struct ShoppingMerchantDto: Decodable { let merchantId: String; let businessName: String }
struct ShoppingMerchantsResponse: Decodable { let success: Bool; let merchants: [ShoppingMerchantDto] }

// Real itunda-own-fleet Commerce (Shop) delivery claim/tracking -- see Android
// riderapp's ApiService.kt CommerceOrderDto doc comment (2026-08-04) for the full
// backend account this ports: a real, working endpoint set with zero client anywhere,
// not even this dedicated rider app, which until now only ever saw Eats food
// deliveries. Mirrors rw.itunda.core.domain.Order exactly. Distinct status set from
// Eats: PACKED -> SHIPPED (claim) -> DELIVERED (complete), no RIDER_ASSIGNED/PICKED_UP
// midpoint.
struct CommerceOrderDto: Decodable, Identifiable, Equatable {
    let id: String
    let buyerId: String
    let merchantId: String
    let deliveryAddress: String
    let totalAmount: Double
    let fee: Double
    let status: String
    let createdAt: String
    let riderId: String?
}
struct CommerceOrderDetailResponse: Decodable { let success: Bool; let order: CommerceOrderDto }
struct CommerceOrdersResponse: Decodable { let success: Bool; let orders: [CommerceOrderDto] }

// Real automatic-dispatch offer notifications (type == "DELIVERY_OFFER") carry the
// offered order's id in dataJson (a raw JSON string, e.g. {"orderId":"..."}) -- see
// EatsOrderService's own dispatch code. Neither the consumer app nor bank-mfe has
// ever modeled this field (a real, separate gap this app closes for the rider side
// specifically, since accepting/declining an exclusive offer only matters to a rider).
struct NotificationDto: Decodable, Identifiable {
    let id: String
    let type: String
    let title: String
    let body: String
    let isRead: Bool
    let createdAt: String
    let dataJson: String?
}
struct NotificationsResponse: Decodable { let success: Bool; let notifications: [NotificationDto]; let unreadCount: Int }

// Real push device-token registration (item 130) -- see the consumer app's own
// NetworkClient.swift doc comment (items 119-121): PushNotificationService.sendToUser
// silently no-ops for every real user with no registered token, and this dedicated
// rider app -- the one place a rider actually needs an instant DELIVERY_OFFER/
// RIDE_TRIP_OFFER push, given its own real short accept-or-lose countdown window --
// never registered one at all. Reuses the same real, stable, per-install device id
// convention (see RiderKeychainTokenStore.getOrCreateDeviceId) as this demo's
// client-generated token.
struct RegisterDeviceTokenRequest: Encodable { let platform: String; let token: String }
struct SuccessResponse: Decodable { let success: Bool }

enum NetworkError: Error {
    case invalidResponse
    case httpError(statusCode: Int)
    // Real gap found 2026-09-04 (same pass that fixed the identical gap on the main
    // app's and MerchantApp's own separate NetworkClient copies -- see
    // MerchantApp's own httpErrorWithMessage doc comment for the full account):
    // sendRequest below only ever threw the bare, message-less httpError case, so
    // this app's screens fell back to one hardcoded string per failure, never the
    // backend's own real, specific message (e.g. RiderService's
    // "This account is already registered as a rider"). Purely additive -- the one
    // real UI pattern-match on the bare case (LoginScreen.swift) is updated in the
    // same commit, not left to silently degrade.
    case httpErrorWithMessage(statusCode: Int, message: String?)
}

private struct ApiErrorBody: Decodable { let message: String? }

/**
 * Real, minimal URLSession client, mirroring the consumer app's own
 * App/Sources/NetworkClient.swift (same plain-URLSession, no-third-party-dependency
 * convention) -- this app's own copy, not a shared dependency, since it hits a small
 * deliberately scoped subset of the real backend's API surface. iOS Simulator shares
 * the host Mac's network directly, same as the consumer app's own baseURL comment.
 */
final class RiderNetworkClient {
    static let shared = RiderNetworkClient()

    private let baseURL = URL(string: "http://localhost:4001/")!
    private let session = URLSession(configuration: .default)
    private lazy var encoder = JSONEncoder()
    private lazy var decoder = JSONDecoder()

    private init() {}

    func login(_ request: LoginRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/login", body: request, authenticated: false)
    }

    func registerRider() async throws -> RiderResponse {
        try await postEmpty("api/v1/eats/riders/register")
    }

    func getMyRiderProfile() async throws -> RiderResponse { try await get("api/v1/eats/riders/me") }

    func getRiderRating(riderId: String) async throws -> RiderRatingResponse { try await get("api/v1/eats/riders/\(riderId)/rating") }

    func setRiderAvailability(_ available: Bool) async throws -> RiderResponse {
        try await post("api/v1/eats/riders/availability", body: SetRiderAvailabilityRequest(available: available))
    }

    func updateRiderLocation(latitude: Double, longitude: Double) async throws -> RiderResponse {
        try await post("api/v1/eats/riders/location", body: UpdateRiderLocationRequest(latitude: latitude, longitude: longitude))
    }

    func getAvailableDeliveries() async throws -> EatsOrdersResponse {
        try await get("api/v1/eats/orders/available", query: [URLQueryItem(name: "size", value: "20")])
    }

    func getRiderDeliveries() async throws -> EatsOrdersResponse {
        try await get("api/v1/eats/orders/rider-deliveries", query: [URLQueryItem(name: "size", value: "50")])
    }

    func getOrder(_ orderId: String) async throws -> EatsOrderDetailResponse { try await get("api/v1/eats/orders/\(orderId)") }

    func claimDelivery(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await postEmpty("api/v1/eats/orders/\(orderId)/claim", idempotencyKey: UUID().uuidString)
    }

    func declineDelivery(_ orderId: String) async throws -> EatsOrderDetailResponse { try await postEmpty("api/v1/eats/orders/\(orderId)/decline") }

    func updateRiderOrderStatus(_ orderId: String, status: String) async throws -> EatsOrderDetailResponse {
        try await post("api/v1/eats/orders/\(orderId)/rider-status", body: UpdateEatsOrderStatusRequest(status: status))
    }

    func getShoppingMerchants() async throws -> ShoppingMerchantsResponse { try await get("api/v1/shopping/merchants") }

    func getAvailableCommerceDeliveries() async throws -> CommerceOrdersResponse {
        try await get("api/v1/orders/available-deliveries", query: [URLQueryItem(name: "size", value: "20")])
    }

    func getMyCommerceDeliveries() async throws -> CommerceOrdersResponse {
        try await get("api/v1/orders/my-deliveries", query: [URLQueryItem(name: "size", value: "50")])
    }

    func claimCommerceDelivery(_ orderId: String) async throws -> CommerceOrderDetailResponse {
        try await postEmpty("api/v1/orders/\(orderId)/claim-delivery", idempotencyKey: UUID().uuidString)
    }

    func completeCommerceDelivery(_ orderId: String) async throws -> CommerceOrderDetailResponse { try await postEmpty("api/v1/orders/\(orderId)/complete-delivery") }

    func getNotifications() async throws -> NotificationsResponse { try await get("api/v1/notifications") }

    @discardableResult
    func markNotificationRead(_ id: String) async throws -> Data { try await sendRequest(method: "POST", path: "api/v1/notifications/\(id)/read", body: Optional<EmptyBody>.none) }

    func registerDeviceToken(_ request: RegisterDeviceTokenRequest) async throws -> SuccessResponse {
        try await post("api/v1/notifications/device-tokens", body: request)
    }

    // MARK: - Helpers

    private func get<Response: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> Response {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        if let token = RiderKeychainTokenStore.shared.getAccessToken() {
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

    private func postEmpty<Response: Decodable>(_ path: String, idempotencyKey: String? = nil) async throws -> Response {
        let data = try await sendRequest(method: "POST", path: path, body: Optional<EmptyBody>.none, idempotencyKey: idempotencyKey)
        return try decoder.decode(Response.self, from: data)
    }

    @discardableResult
    private func sendRequest<Body: Encodable>(method: String, path: String, body: Body?, authenticated: Bool = true, idempotencyKey: String? = nil) async throws -> Data {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = method
        if authenticated, let token = RiderKeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let idempotencyKey {
            request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try encoder.encode(body)
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            let message = try? decoder.decode(ApiErrorBody.self, from: data).message
            throw NetworkError.httpErrorWithMessage(statusCode: httpResponse.statusCode, message: message ?? nil)
        }
        return data
    }
}

private struct EmptyBody: Encodable {}
