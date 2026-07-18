import Foundation

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names/nullability, so JSONDecoder reads the real backend's JSON directly.
struct RegisterRequest: Encodable {
    let phoneNumber: String
    let email: String?
    let firstName: String
    let lastName: String
    let password: String
    let referralCode: String?
}

struct LoginRequest: Encodable {
    let phoneNumber: String
    let password: String
}

struct RefreshRequest: Encodable {
    let refreshToken: String
}

struct LogoutRequest: Encodable {
    let refreshToken: String?
}

struct PublicUser: Decodable {
    let id: String
    let phoneNumber: String
    let email: String?
    let firstName: String
    let lastName: String
    let kycVerified: Bool
    let creditScore: Int
    let createdAt: String
}

struct AuthResponse: Decodable {
    let message: String
    let user: PublicUser
    let accessToken: String
    let refreshToken: String
}

enum NetworkError: Error {
    case invalidResponse
    case httpError(statusCode: Int)
}

/// Real login/session flow (2026-07-11) -- this app previously had no networking
/// layer at all backing SessionManager/LoginScreen; Core/Network's BffClient
/// protocol (see Core/Network/Sources/BffClient.swift) is unused SDUI scaffolding,
/// not a real HTTP client, same as Android's core/network module before this
/// session's fix lived in :app instead. Plain URLSession, no third-party dependency,
/// matching how minimal the rest of this app's networking surface already is.
final class NetworkClient {
    static let shared = NetworkClient()

    // iOS Simulator shares the host Mac's network directly ("localhost" resolves to
    // the Mac itself), unlike the Android emulator's 10.0.2.2 alias -- no special
    // address needed here. A physical iOS device would need the host's real LAN IP
    // instead (see android/app/build.gradle.kts's apiBaseUrl comment for the Android
    // equivalent of this same problem).
    // static/internal (2026-07-16) so SaroniteBrownfieldModule can reuse the exact same
    // value rather than a second hardcoded literal that could drift out of sync.
    static let baseURLString = "http://localhost:4001/"
    private let baseURL = URL(string: NetworkClient.baseURLString)!
    private let session = URLSession(configuration: .default)

    private lazy var encoder: JSONEncoder = JSONEncoder()
    private lazy var decoder: JSONDecoder = JSONDecoder()

    private init() {}

    func register(_ request: RegisterRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/register", body: request, authToken: nil)
    }

    func login(_ request: LoginRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/login", body: request, authToken: nil)
    }

    func refresh(_ request: RefreshRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/refresh", body: request, authToken: nil)
    }

    func logout(accessToken: String, request: LogoutRequest) async throws {
        _ = try await sendRequest(path: "api/v1/auth/logout", body: request, authToken: accessToken)
    }

    private func post<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body,
        authToken: String?
    ) async throws -> Response {
        let data = try await sendRequest(path: path, body: body, authToken: authToken)
        return try decoder.decode(Response.self, from: data)
    }

    @discardableResult
    private func sendRequest<Body: Encodable>(path: String, body: Body, authToken: String?) async throws -> Data {
        var urlRequest = URLRequest(url: baseURL.appendingPathComponent(path))
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let authToken {
            urlRequest.setValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        }
        urlRequest.httpBody = try encoder.encode(body)

        let (data, response) = try await session.data(for: urlRequest)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return data
    }
}

// Mirrors services/backend/core/.../domain/Wallet.kt / SavingsGoal.kt / InterestJar.kt
// exactly -- same field names, so JSONDecoder reads the real backend's JSON directly
// (2026-07-11, alongside BankView.swift's real-data wiring; same DTOs Android's
// ApiService.kt just gained).
struct Wallet: Decodable {
    let id: String
    let userId: String
    let accountNumber: String
    let accountName: String
    let type: String
    let balance: Double
    let availableBalance: Double
    let currency: String
    let isActive: Bool
}

struct WalletsResponse: Decodable { let success: Bool; let wallets: [Wallet] }

struct SavingsGoal: Decodable {
    let id: String
    let userId: String
    let walletId: String
    let name: String
    let targetAmount: Double
    let currentAmount: Double
    let monthlyContribution: Double
    let interestRate: Double
    let targetDate: String?
    let category: String
    let status: String
    let color: String
}

struct SavingsGoalsResponse: Decodable { let success: Bool; let goals: [SavingsGoal] }

struct InterestJar: Decodable {
    let userId: String
    let walletId: String
    let balance: Double
    let rate: Double
    let earnedThisMonth: Double
    let earnedTotal: Double
}

struct InterestJarResponse: Decodable { let success: Bool; let jar: InterestJar }

/// Authenticated GET helper for feature screens that need to call the rest of
/// services/backend's API once logged in -- reads the bearer token from
/// KeychainTokenStore so callers never have to thread it through manually. First
/// real use (2026-07-11): BankView.swift's wallet/savings/interest-jar data, closing
/// the "iOS has no real feature data-fetching wired in" gap this comment used to name.
extension NetworkClient {
    func getWallets() async throws -> WalletsResponse { try await get("api/v1/wallet") }
    func getSavingsGoals() async throws -> SavingsGoalsResponse { try await get("api/v1/savings/goals") }
    func getInterestJar() async throws -> InterestJarResponse { try await get("api/v1/savings/interest-jar") }
    func getTransactionHistory() async throws -> TransactionHistoryResponse { try await get("api/v1/wallet/transactions") }
    // Real account settings screen (2026-07-12).
    func getProfile() async throws -> ProfileResponse { try await get("api/v1/auth/profile") }
    func getNotifications() async throws -> NotificationsResponse { try await get("api/v1/notifications") }

    func markNotificationRead(_ id: String) async throws {
        _ = try await authenticatedPost("api/v1/notifications/\(id)/read", body: EmptyBody()) as MarkReadResponse
    }

    private func get<Response: Decodable>(_ path: String) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }

    /// Real query-param GET (2026-07-19) -- `get(_:)` above uses
    /// `appendingPathComponent`, which percent-encodes `?`/`=`/`&` and breaks a query
    /// string (same gotcha `searchDeliveryAddress` already worked around inline); this
    /// is the reusable version of that same fix for any future query-param endpoint.
    private func get<Response: Decodable>(_ path: String, query: [URLQueryItem]) async throws -> Response {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        components.queryItems = query.filter { $0.value != nil && !($0.value!.isEmpty) }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }

    /// Authenticated POST, with an optional Idempotency-Key -- every money-moving
    /// call below needs one so a retried tap after a timeout replays the original
    /// result instead of double-spending, same contract as Android's equivalent.
    fileprivate func authenticatedPost<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body,
        idempotencyKey: String? = nil
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let idempotencyKey {
            request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        }
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }
}

// Mirrors services/backend/wallet's WalletController.kt/TransferQuote.kt and
// services/backend/savings's SavingsController.kt exactly (2026-07-12) -- wires
// the real send-money and savings deposit/claim flows, same DTOs Android's
// ApiService.kt just gained.
struct QuoteTransferRequest: Encodable {
    let amount: Double
    let recipient: String
}

struct TransferQuoteDto: Decodable {
    let id: String
    let fromWalletId: String
    let recipient: String
    let amount: Double
    let fee: Double
    let totalDebit: Double
    let currency: String
    let expiresAt: String
}

struct QuoteTransferResponse: Decodable { let success: Bool; let quote: TransferQuoteDto }

struct ConfirmTransferRequest: Encodable { let quoteId: String }

struct TransactionDto: Decodable {
    let id: String
    let senderId: String
    let recipientId: String
    let type: String
    let amount: Double
    let fee: Double
    let currency: String
    let status: String
    let description: String
    let createdAt: String
}

struct TransactionHistoryResponse: Decodable { let success: Bool; let transactions: [TransactionDto] }

struct ConfirmTransferResponse: Decodable {
    let success: Bool
    let message: String
    let transaction: TransactionDto
    let newBalance: Double
}

struct DepositRequest: Encodable { let goalId: String; let amount: Double }
struct DepositResponse: Decodable { let success: Bool; let message: String; let goal: SavingsGoal }
struct ClaimInterestResponse: Decodable { let success: Bool; let message: String }

// Real offline-action-queue replay (2026-07-13) -- mirrors
// services/backend/offline/src/main/kotlin/rw/itunda/offline/web/ActionsBatchController.kt
// exactly. See OfflineActionQueue.swift for the local persisted queue this replays.
// Scoped to SAVINGS_DEPOSIT's real body shape rather than a generic [String: Any]
// body -- see OfflineActionQueue.swift's header for why that's the right scope here.
struct SavingsDepositActionBody: Encodable { let goalId: String; let amount: Double }

struct BatchActionRequest: Encodable {
    let clientActionId: String
    let type: String
    let idempotencyKey: String
    let body: SavingsDepositActionBody
}

struct BatchRequest: Encodable { let actions: [BatchActionRequest] }

struct BatchActionResultDto: Decodable {
    let clientActionId: String
    let type: String
    let status: Int
}

struct BatchResponse: Decodable { let success: Bool; let results: [BatchActionResultDto] }

// Mirrors services/backend/auth's AuthController.kt / notifications's
// NotificationController.kt (2026-07-12) -- backs the new Settings screen.
struct ProfileResponse: Decodable { let success: Bool; let user: PublicUser }

struct NotificationDto: Decodable, Identifiable {
    let id: String
    let userId: String
    let type: String
    let title: String
    let body: String
    let isRead: Bool
    let createdAt: String
}

struct NotificationsResponse: Decodable { let success: Bool; let notifications: [NotificationDto]; let unreadCount: Int }
struct MarkReadResponse: Decodable { let success: Bool }

extension NetworkClient {
    func quoteTransfer(amount: Double, recipient: String) async throws -> QuoteTransferResponse {
        try await authenticatedPost("api/v1/wallet/transfer/quote", body: QuoteTransferRequest(amount: amount, recipient: recipient))
    }

    func confirmTransfer(quoteId: String) async throws -> ConfirmTransferResponse {
        try await authenticatedPost(
            "api/v1/wallet/transfer/confirm",
            body: ConfirmTransferRequest(quoteId: quoteId),
            idempotencyKey: UUID().uuidString
        )
    }

    func depositToGoal(goalId: String, amount: Double) async throws -> DepositResponse {
        try await authenticatedPost(
            "api/v1/savings/deposit",
            body: DepositRequest(goalId: goalId, amount: amount),
            idempotencyKey: UUID().uuidString
        )
    }

    func claimInterest() async throws -> ClaimInterestResponse {
        try await authenticatedPost(
            "api/v1/savings/interest-jar/claim",
            body: EmptyBody(),
            idempotencyKey: UUID().uuidString
        )
    }

    // Real offline-action-queue replay (2026-07-13) -- see OfflineActionQueue.swift.
    // No Idempotency-Key header on the batch call itself -- each individual action
    // inside it carries its own, exactly like the backend controller expects.
    func submitActionBatch(_ request: BatchRequest) async throws -> BatchResponse {
        try await authenticatedPost("api/v1/actions/batch", body: request)
    }
}

private struct EmptyBody: Encodable {}

// MARK: - Messaging / Marketplace / Commerce (2026-07-18)
//
// Mirrors android/app/.../network/ApiService.kt's real DTOs exactly, field-for-field --
// the same three new backend modules (rw.itunda.messaging/marketplace/commerce) that
// Android's Home/Shop/Hood/Talk/My nav redesign wired up. See that file's own header
// comment for the full backend account (real pagination, real IDOR protection, honest
// "poll-based delivery"/"self-declared fulfillment" scope).

struct ConversationDto: Decodable {
    let id: String
    let participantAId: String
    let participantBId: String
    let lastMessageAt: String
    let createdAt: String
}

struct ConversationSummaryDto: Decodable, Identifiable {
    let conversationId: String
    let otherUserId: String
    let otherUserName: String
    let lastMessageAt: String
    let lastMessagePreview: String?
    let unreadCount: Int
    var id: String { conversationId }
}

struct MessageDto: Decodable, Identifiable {
    let id: String
    let conversationId: String
    let senderId: String
    let body: String
    let sentAt: String
    let readAt: String?
}

// Real WebSocket push envelopes (2026-07-18) -- see
// NetworkClient.connectMessagingSocket's own doc comment.
struct MessagingSocketTypeEnvelope: Decodable { let type: String }
struct MessagingSocketMessageEnvelope: Decodable { let type: String; let conversationId: String; let message: MessageDto }

struct StartConversationRequest: Encodable {
    let phoneNumber: String?
    let otherUserId: String?
}

struct SendMessageRequest: Encodable { let body: String }

struct ConversationResponse: Decodable { let success: Bool; let conversation: ConversationDto }
struct ConversationsResponse: Decodable { let success: Bool; let conversations: [ConversationSummaryDto] }
struct MessagesResponse: Decodable { let success: Bool; let messages: [MessageDto] }
struct MessageResponse: Decodable { let success: Bool; let message: MessageDto }

// Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
// memberPhoneNumbers is the real human-friendly entry point (same reasoning as
// StartConversationRequest.phoneNumber).
struct CreateGroupRequest: Encodable { let name: String; let memberPhoneNumbers: [String] }
struct SendGroupMessageRequest: Encodable { let body: String }

struct GroupSummaryDto: Decodable, Identifiable {
    let groupId: String
    let name: String
    let memberCount: Int
    let lastMessageAt: String
    let lastMessagePreview: String?
    let unreadCount: Int
    var id: String { groupId }
}
struct GroupMessageDto: Decodable, Identifiable {
    let id: String
    let groupConversationId: String
    let senderId: String
    let body: String
    let sentAt: String
}
struct GroupResponse: Decodable { let success: Bool; let group: GroupSummaryDto }
struct GroupsResponse: Decodable { let success: Bool; let groups: [GroupSummaryDto] }
struct GroupMessagesResponse: Decodable { let success: Bool; let messages: [GroupMessageDto] }
struct GroupMessageResponse: Decodable { let success: Bool; let message: GroupMessageDto }
struct LeaveGroupResponse: Decodable { let success: Bool }

// Real member list with real resolved display names (2026-07-18) -- closes the honest,
// named limitation this UI carried since group chat first shipped: message bubbles
// showing a truncated sender id instead of a real name.
struct GroupMemberDto: Decodable, Identifiable { let userId: String; let name: String; var id: String { userId } }
struct GroupMembersResponse: Decodable { let success: Bool; let members: [GroupMemberDto] }

// Real WebSocket push envelopes for group chat (2026-07-18).
struct MessagingSocketGroupEnvelope: Decodable { let type: String; let groupConversationId: String; let message: GroupMessageDto }

enum MessagingSocketPush {
    case directMessage(conversationId: String, message: MessageDto)
    case groupMessage(groupConversationId: String, message: GroupMessageDto)
}

struct ListingDto: Decodable, Identifiable {
    let id: String
    let sellerId: String
    let title: String
    let description: String
    let price: Double
    let category: String
    let status: String
    let createdAt: String
}

struct CreateListingRequest: Encodable {
    let title: String
    let description: String
    let price: Double
    let category: String
}

struct ListingResponse: Decodable { let success: Bool; let listing: ListingDto }
struct ListingsResponse: Decodable { let success: Bool; let listings: [ListingDto] }
struct ContactSellerResponse: Decodable { let success: Bool; let conversation: ConversationDto }

struct ShoppingMerchantDto: Decodable, Identifiable {
    let merchantId: String
    let businessName: String
    let category: String?
    let cashbackRate: String
    var id: String { merchantId }
}
struct ShoppingMerchantsResponse: Decodable { let success: Bool; let merchants: [ShoppingMerchantDto] }
struct MerchantCategoriesResponse: Decodable { let success: Bool; let categories: [String] }

struct MerchantProductDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let name: String
    let price: Double
    let active: Bool
    let createdAt: String
}
struct MerchantSummaryDto: Decodable { let id: String; let businessName: String }
struct MerchantProductsResponse: Decodable { let success: Bool; let merchant: MerchantSummaryDto; let products: [MerchantProductDto] }

struct OrderItemRequest: Encodable { let productId: String; let quantity: Int }
struct PlaceOrderRequest: Encodable {
    let merchantId: String
    let items: [OrderItemRequest]
    let deliveryAddress: String
}

struct OrderDto: Decodable, Identifiable {
    let id: String
    let buyerId: String
    let merchantId: String
    let deliveryAddress: String
    let totalAmount: Double
    let fee: Double
    let transactionId: String
    let status: String
    let createdAt: String
    let updatedAt: String
    let refundTransactionId: String?
}
struct OrderItemDto: Decodable, Identifiable { let id: String; let orderId: String; let productId: String; let productName: String; let unitPrice: Double; let quantity: Int }
struct OrderDetailResponse: Decodable { let success: Bool; let order: OrderDto; let items: [OrderItemDto] }
struct OrdersResponse: Decodable { let success: Bool; let orders: [OrderDto] }

/// Mirrors services/backend/eats's real DTOs exactly (2026-07-18) -- restaurant/menu
/// browsing reuses ShoppingMerchantDto/MerchantProductDto above (a restaurant IS a
/// Merchant, a menu item IS a MerchantProduct -- see rw.itunda.eats.EatsOrderService's
/// own doc comment).
struct EatsOrderItemRequest: Encodable { let menuItemId: String; let quantity: Int }
struct PlaceEatsOrderRequest: Encodable {
    let restaurantId: String
    let items: [EatsOrderItemRequest]
    let deliveryAddress: String
    let deliveryLatitude: Double?
    let deliveryLongitude: Double?
}
struct UpdateEatsOrderStatusRequest: Encodable { let status: String }
struct SetRiderAvailabilityRequest: Encodable { let available: Bool }

// Real self-hosted address-search autocomplete (2026-07-18) -- backed by itunda's own
// Nominatim geocoder, not a third-party Maps API. See
// EatsOrderService.searchDeliveryAddress's own doc comment.
struct AddressSuggestionDto: Decodable, Identifiable {
    var id: String { displayName }
    let displayName: String
    let latitude: Double
    let longitude: Double
}
struct AddressSearchResponse: Decodable { let success: Bool; let suggestions: [AddressSuggestionDto] }

// Real post-delivery ratings & reviews (2026-07-18) -- see EatsReviewService's own doc
// comment. Ported from bank-mfe's own review UI, the template for this iOS version.
struct SubmitEatsReviewRequest: Encodable {
    let restaurantRating: Int
    let restaurantComment: String?
    let riderRating: Int
    let riderComment: String?
}
struct EatsReviewDto: Decodable {
    let id: String
    let orderId: String
    let buyerId: String
    let restaurantId: String
    let riderId: String
    let restaurantRating: Int
    let restaurantComment: String?
    let riderRating: Int
    let riderComment: String?
    let createdAt: String
}
struct EatsReviewResponse: Decodable { let success: Bool; let review: EatsReviewDto }
struct EatsRatingResponse: Decodable { let success: Bool; let average: Double?; let count: Int }

struct EatsOrderDto: Decodable, Identifiable {
    let id: String
    let buyerId: String
    let restaurantId: String
    let riderId: String?
    let deliveryAddress: String
    let itemsSubtotal: Double
    let deliveryFee: Double
    let platformFee: Double
    let totalAmount: Double
    let transactionId: String
    let deliveryPayoutTransactionId: String?
    let status: String
    let createdAt: String
    let updatedAt: String
    let refundTransactionId: String?
    let deliveryLatitude: Double?
    let deliveryLongitude: Double?
    let distanceKm: Double?
}
struct EatsOrderItemDto: Decodable, Identifiable { let id: String; let orderId: String; let productId: String; let productName: String; let unitPrice: Double; let quantity: Int }
struct EatsOrderDetailResponse: Decodable { let success: Bool; let order: EatsOrderDto; let items: [EatsOrderItemDto] }
struct EatsOrdersResponse: Decodable { let success: Bool; let orders: [EatsOrderDto] }

struct RiderDto: Decodable, Identifiable { let id: String; let userId: String; let walletId: String; let status: String; let available: Bool; let createdAt: String }
struct RiderResponse: Decodable { let success: Bool; let rider: RiderDto }

// Real bookmarked/favorited restaurants (2026-07-19) -- add/remove are both idempotent
// on the backend, see EatsFavoriteService.kt's own doc comment.
struct FavoriteRestaurantDto: Decodable, Identifiable {
    let restaurantId: String
    let businessName: String
    let category: String?
    let favoritedAt: String
    var id: String { restaurantId }
}
struct FavoriteRestaurantsResponse: Decodable { let success: Bool; let favorites: [FavoriteRestaurantDto] }
struct SuccessResponse: Decodable { let success: Bool }

extension NetworkClient {
    func startConversation(phoneNumber: String) async throws -> ConversationResponse {
        try await authenticatedPost("api/v1/messages/conversations", body: StartConversationRequest(phoneNumber: phoneNumber, otherUserId: nil))
    }

    func getConversations() async throws -> ConversationsResponse { try await get("api/v1/messages/conversations") }

    func getMessages(conversationId: String) async throws -> MessagesResponse {
        try await get("api/v1/messages/conversations/\(conversationId)/messages")
    }

    func sendMessage(conversationId: String, body: String) async throws -> MessageResponse {
        try await authenticatedPost("api/v1/messages/conversations/\(conversationId)/messages", body: SendMessageRequest(body: body))
    }

    // Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
    func createGroup(name: String, memberPhoneNumbers: [String]) async throws -> GroupResponse {
        try await authenticatedPost("api/v1/messages/groups", body: CreateGroupRequest(name: name, memberPhoneNumbers: memberPhoneNumbers))
    }

    func getMyGroups() async throws -> GroupsResponse { try await get("api/v1/messages/groups") }

    func getGroupMessages(groupId: String) async throws -> GroupMessagesResponse {
        try await get("api/v1/messages/groups/\(groupId)/messages")
    }

    func sendGroupMessage(groupId: String, body: String) async throws -> GroupMessageResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/messages", body: SendGroupMessageRequest(body: body))
    }

    func getGroupMembers(groupId: String) async throws -> GroupMembersResponse {
        try await get("api/v1/messages/groups/\(groupId)/members")
    }

    /// Real WebSocket live-transport (2026-07-18) -- see
    /// rw.itunda.app.websocket.MessagingWebSocketHandler's own doc comment for the real
    /// backend push shape this mirrors exactly (also ported to Android the same day).
    /// Native `URLSessionWebSocketTask`, no third-party dependency. Routes both
    /// "message" (1:1) and "group_message" pushes -- group chat gained a real mobile UI
    /// the same day this was extended.
    func connectMessagingSocket(onPush: @escaping (MessagingSocketPush) -> Void) -> URLSessionWebSocketTask {
        let wsBase = NetworkClient.baseURLString
            .replacingOccurrences(of: "http://", with: "ws://")
            .replacingOccurrences(of: "https://", with: "wss://")
        let token = KeychainTokenStore.shared.getAccessToken() ?? ""
        let url = URL(string: "\(wsBase)ws/messaging?token=\(token)")!
        let task = session.webSocketTask(with: url)
        task.resume()
        receiveMessagingSocketFrame(task, onPush: onPush)
        return task
    }

    private func receiveMessagingSocketFrame(_ task: URLSessionWebSocketTask, onPush: @escaping (MessagingSocketPush) -> Void) {
        task.receive { [weak self] result in
            guard let self else { return }
            if case .success(.string(let text)) = result, let data = text.data(using: .utf8) {
                // Two-pass decode: only fully decode `message` once we've confirmed the
                // real `type` -- a group_message push's nested message object has a
                // different shape (groupConversationId, not conversationId) and would
                // otherwise fail MessageDto's strict decode, and vice versa.
                if let typeEnvelope = try? JSONDecoder().decode(MessagingSocketTypeEnvelope.self, from: data) {
                    if typeEnvelope.type == "message",
                       let envelope = try? JSONDecoder().decode(MessagingSocketMessageEnvelope.self, from: data) {
                        onPush(.directMessage(conversationId: envelope.conversationId, message: envelope.message))
                    } else if typeEnvelope.type == "group_message",
                              let envelope = try? JSONDecoder().decode(MessagingSocketGroupEnvelope.self, from: data) {
                        onPush(.groupMessage(groupConversationId: envelope.groupConversationId, message: envelope.message))
                    }
                }
            }
            // Real, non-critical -- a malformed/unexpected push or a transient receive
            // error shouldn't kill the app; the 4s poll stays as the real fallback
            // delivery path regardless. Only a genuinely closed socket stops the loop.
            if case .success = result {
                self.receiveMessagingSocketFrame(task, onPush: onPush)
            } else if case .failure = result {
                // Socket closed/errored -- stop listening, poll takes over.
            }
        }
    }

    func createListing(title: String, description: String, price: Double, category: String) async throws -> ListingResponse {
        try await authenticatedPost("api/v1/marketplace/listings", body: CreateListingRequest(title: title, description: description, price: price, category: category))
    }

    func browseListings(category: String? = nil) async throws -> ListingsResponse {
        var path = "api/v1/marketplace/listings"
        if let category, !category.isEmpty {
            path += "?category=\(category.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? category)"
        }
        return try await get(path)
    }

    func getMyListings() async throws -> ListingsResponse { try await get("api/v1/marketplace/my-listings") }

    func markListingSold(_ listingId: String) async throws -> ListingResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/mark-sold", body: EmptyBody())
    }

    func removeListing(_ listingId: String) async throws -> ListingResponse {
        try await authenticatedDelete("api/v1/marketplace/listings/\(listingId)")
    }

    func contactSeller(listingId: String) async throws -> ContactSellerResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/contact-seller", body: EmptyBody())
    }

    // Real category/search filter (2026-07-19) -- both optional and combinable. See
    // ShoppingController.getEligibleMerchants's own doc comment on the backend.
    func getShoppingMerchants(category: String? = nil, q: String? = nil) async throws -> ShoppingMerchantsResponse {
        try await get("api/v1/shopping/merchants", query: [URLQueryItem(name: "category", value: category), URLQueryItem(name: "q", value: q)])
    }

    // Real distinct category list -- see MerchantRepository.findDistinctCategories's own
    // doc comment on the backend.
    func getMerchantCategories() async throws -> MerchantCategoriesResponse { try await get("api/v1/shopping/merchants/categories") }

    func getMerchantProducts(merchantId: String) async throws -> MerchantProductsResponse {
        try await get("api/v1/shopping/merchants/\(merchantId)/products")
    }

    func placeOrder(_ request: PlaceOrderRequest) async throws -> OrderDetailResponse {
        try await authenticatedPost("api/v1/orders", body: request, idempotencyKey: UUID().uuidString)
    }

    func getMyOrders() async throws -> OrdersResponse { try await get("api/v1/orders/my-orders") }

    /// Real cancellation + refund (2026-07-18) -- buyer or seller, PLACED orders only.
    /// See rw.itunda.commerce.OrderService.cancelOrder's own doc comment.
    func cancelOrder(_ orderId: String) async throws -> OrderDetailResponse {
        try await authenticatedPost("api/v1/orders/\(orderId)/cancel", body: EmptyBody())
    }

    // Real Coupang Eats-style food delivery (2026-07-18) -- see rw.itunda.eats.web.EatsController.
    func placeEatsOrder(_ request: PlaceEatsOrderRequest) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders", body: request, idempotencyKey: UUID().uuidString)
    }

    func getMyEatsOrders() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/my-orders") }

    /// Real order detail, including items -- backs the real "Reorder" button (2026-07-19):
    /// a buyer can re-populate a cart from a past order's real items rather than retyping
    /// their whole order from scratch.
    func getEatsOrder(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await get("api/v1/eats/orders/\(orderId)")
    }

    /// Real address-search autocomplete (2026-07-18) -- backed by itunda's own
    /// self-hosted Nominatim geocoder. See EatsController.searchDeliveryAddress's own
    /// doc comment. Uses URLComponents (not appendingPathComponent) so the query string
    /// is encoded correctly -- the first query-param GET in this client.
    // Real post-delivery ratings & reviews (2026-07-18) -- see EatsController.submitReview.
    func submitEatsReview(orderId: String, restaurantRating: Int, restaurantComment: String?, riderRating: Int, riderComment: String?) async throws -> EatsReviewResponse {
        try await authenticatedPost(
            "api/v1/eats/orders/\(orderId)/review",
            body: SubmitEatsReviewRequest(restaurantRating: restaurantRating, restaurantComment: restaurantComment, riderRating: riderRating, riderComment: riderComment)
        )
    }

    func getRestaurantRating(_ restaurantId: String) async throws -> EatsRatingResponse {
        try await get("api/v1/eats/restaurants/\(restaurantId)/rating")
    }

    func searchDeliveryAddress(_ query: String) async throws -> AddressSearchResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/v1/eats/geocode/search"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "q", value: query)]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(AddressSearchResponse.self, from: data)
    }

    /// Real cancellation + refund (2026-07-18) -- buyer or restaurant, PLACED orders
    /// only. See rw.itunda.eats.EatsOrderService.cancelOrder's own doc comment.
    func cancelEatsOrder(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders/\(orderId)/cancel", body: EmptyBody())
    }
    func getRiderDeliveries() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/rider-deliveries") }
    func getAvailableDeliveries() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/available") }

    func claimDelivery(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders/\(orderId)/claim", body: EmptyBody())
    }

    func updateRiderOrderStatus(_ orderId: String, status: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders/\(orderId)/rider-status", body: UpdateEatsOrderStatusRequest(status: status))
    }

    func registerRider() async throws -> RiderResponse {
        try await authenticatedPost("api/v1/eats/riders/register", body: EmptyBody())
    }

    func getMyRiderProfile() async throws -> RiderResponse { try await get("api/v1/eats/riders/me") }

    func setRiderAvailability(_ available: Bool) async throws -> RiderResponse {
        try await authenticatedPost("api/v1/eats/riders/availability", body: SetRiderAvailabilityRequest(available: available))
    }

    // Real bookmarked/favorited restaurants (2026-07-19) -- see
    // EatsFavoriteService.kt's own doc comment for why add/remove are both idempotent.
    func addFavoriteRestaurant(_ restaurantId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/eats/restaurants/\(restaurantId)/favorite", body: EmptyBody())
    }

    func removeFavoriteRestaurant(_ restaurantId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/eats/restaurants/\(restaurantId)/favorite")
    }

    func getMyFavoriteRestaurants() async throws -> FavoriteRestaurantsResponse { try await get("api/v1/eats/favorites") }

    /// Real DELETE support -- every other authenticated call so far was GET/POST only,
    /// see `authenticatedPost`'s own doc comment for why the Idempotency-Key handling
    /// lives there; DELETE never needs one (removing an already-removed listing is
    /// naturally idempotent at the database level, unlike a real money-moving POST).
    fileprivate func authenticatedDelete<Response: Decodable>(_ path: String) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "DELETE"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }
}
